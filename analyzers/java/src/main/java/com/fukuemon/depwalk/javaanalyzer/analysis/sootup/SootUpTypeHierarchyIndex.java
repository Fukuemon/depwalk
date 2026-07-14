package com.fukuemon.depwalk.javaanalyzer.analysis.sootup;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SourceType;
import sootup.core.typehierarchy.TypeHierarchy;
import sootup.core.typehierarchy.ViewTypeHierarchy;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.bytecode.frontend.inputlocation.PathBasedAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * SootUp 2.0.0 の bytecode view を使い、interface / abstract / virtual dispatch の実装候補と
 * bytecode 上の constructor を引くための run-local index。
 *
 * <p>view と type hierarchy は最初の問い合わせまで生成しない。SootUp の
 * {@link ViewTypeHierarchy} 自体も最初の階層問い合わせまで classpath を走査しない。static call
 * だけの解析では SootUp の class scan は発生しない。classpath entry の path 名から project
 * classes dir を推測せず、JavaParser で解決した binary name と {@code .class} の一致だけで
 * 問い合わせる。
 */
public final class SootUpTypeHierarchyIndex {

    public record MethodCandidate(String declaringType, String methodName, List<String> parameterTypes) {
        public MethodCandidate {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    public record Resolution(List<MethodCandidate> candidates, String unavailableReason) {
        public Resolution {
            candidates = List.copyOf(candidates);
        }

        public static Resolution available(List<MethodCandidate> candidates) {
            return new Resolution(candidates, null);
        }

        public static Resolution unavailable(String reason) {
            return new Resolution(List.of(), reason);
        }

        public boolean isAvailable() {
            return unavailableReason == null;
        }
    }

    private record MethodKey(String declaringType, String methodName, List<String> parameterTypes) {
        private MethodKey {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private final List<Path> classpath;
    private final Map<MethodKey, Resolution> methodCache = new LinkedHashMap<>();
    private final Map<String, Resolution> constructorCache = new LinkedHashMap<>();
    private JavaView view;
    private TypeHierarchy hierarchy;

    private SootUpTypeHierarchyIndex(List<Path> classpath) {
        this.classpath = List.copyOf(classpath);
    }

    public static SootUpTypeHierarchyIndex fromClasspath(List<String> classpath) {
        return new SootUpTypeHierarchyIndex(classpath.stream()
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList());
    }

    /** interface / abstract / virtual declaration に対する concrete implementation method を返す。 */
    public Resolution resolveMethod(String declaringType, String methodName, List<String> parameterTypes) {
        MethodKey key = new MethodKey(declaringType, methodName, parameterTypes);
        return methodCache.computeIfAbsent(key, this::resolveMethodUncached);
    }

    /** source に現れない Lombok 等の生成 constructor を bytecode から返す。 */
    public Resolution resolveConstructors(String declaringType) {
        return constructorCache.computeIfAbsent(declaringType, this::resolveConstructorsUncached);
    }

    boolean isInitialized() {
        return view != null;
    }

    private Resolution resolveMethodUncached(MethodKey key) {
        try {
            ClassType declaredType = view().getIdentifierFactory().getClassType(key.declaringType());
            Optional<JavaSootClass> declaredClass = view().getClass(declaredType);
            if (declaredClass.isEmpty()) {
                return unavailable(key.declaringType(), "class was not found in the supplied classpath");
            }

            Map<String, MethodCandidate> candidates = new LinkedHashMap<>();
            Optional<JavaSootMethod> declaredMethod = findDeclaredMethod(declaredClass.get(), key);
            if (declaredMethod.isEmpty()) {
                return unavailable(key.declaringType(), "method was not found in the matching class file");
            }
            if (declaredMethod.get().isPrivate() || declaredMethod.get().isFinal()) {
                return Resolution.available(List.of());
            }
            if (declaredClass.get().isInterface() && declaredMethod.get().isConcrete()) {
                MethodCandidate candidate = toCandidate(declaredMethod.get());
                candidates.put(candidateKey(candidate), candidate);
            }
            Stream<ClassType> subtypeStream = declaredClass.get().isInterface()
                    ? hierarchy().implementersOf(declaredType)
                    : hierarchy().subclassesOf(declaredType);
            List<ClassType> concreteSubtypes = subtypeStream
                    .filter(type -> view().getClass(type).map(JavaSootClass::isConcrete).orElse(false))
                    .sorted(Comparator.comparing(ClassType::getFullyQualifiedName))
                    .toList();

            for (ClassType subtype : concreteSubtypes) {
                findEffectiveMethod(subtype, key).ifPresent(candidate ->
                        candidates.putIfAbsent(candidateKey(candidate), candidate));
            }
            return Resolution.available(candidates.values().stream().sorted(candidateComparator()).toList());
        } catch (RuntimeException e) {
            return unavailable(key.declaringType(), describe(e));
        }
    }

    private Resolution resolveConstructorsUncached(String declaringType) {
        try {
            ClassType classType = view().getIdentifierFactory().getClassType(declaringType);
            Optional<JavaSootClass> sootClass = view().getClass(classType);
            if (sootClass.isEmpty()) {
                return unavailable(declaringType, "class was not found in the supplied classpath");
            }
            List<MethodCandidate> constructors = sootClass.get().getMethods().stream()
                    .filter(method -> "<init>".equals(method.getName()))
                    .map(this::toCandidate)
                    .sorted(candidateComparator())
                    .toList();
            return Resolution.available(constructors);
        } catch (RuntimeException e) {
            return unavailable(declaringType, describe(e));
        }
    }

    private Optional<MethodCandidate> findEffectiveMethod(ClassType receiverType, MethodKey key) {
        List<ClassType> lookupOrder = new ArrayList<>();
        lookupOrder.add(receiverType);
        hierarchy().superClassesOf(receiverType).forEach(lookupOrder::add);
        for (ClassType ownerType : lookupOrder) {
            Optional<JavaSootClass> owner = view().getClass(ownerType);
            if (owner.isEmpty()) {
                continue;
            }
            Optional<JavaSootMethod> match = owner.get().getMethodsByName(key.methodName()).stream()
                    .filter(method -> method.isConcrete() && parameterTypesOf(method).equals(key.parameterTypes()))
                    .findFirst();
            if (match.isPresent()) {
                return match.map(this::toCandidate);
            }
        }
        return Optional.empty();
    }

    private Optional<JavaSootMethod> findDeclaredMethod(JavaSootClass owner, MethodKey key) {
        return owner.getMethodsByName(key.methodName()).stream()
                .filter(method -> parameterTypesOf(method).equals(key.parameterTypes()))
                .findFirst();
    }

    private JavaView view() {
        if (view == null) {
            List<AnalysisInputLocation> locations = classpath.stream()
                    .map(path -> (AnalysisInputLocation) PathBasedAnalysisInputLocation.create(path, SourceType.Library))
                    .toList();
            view = new JavaView(locations);
        }
        return view;
    }

    private TypeHierarchy hierarchy() {
        if (hierarchy == null) {
            hierarchy = new ViewTypeHierarchy(view());
        }
        return hierarchy;
    }

    private MethodCandidate toCandidate(JavaSootMethod method) {
        return new MethodCandidate(
                method.getDeclClassType().getFullyQualifiedName(),
                method.getName(),
                parameterTypesOf(method));
    }

    private static List<String> parameterTypesOf(JavaSootMethod method) {
        return method.getParameterTypes().stream().map(SootUpTypeHierarchyIndex::binaryNameOf).toList();
    }

    private static String binaryNameOf(Type type) {
        return type.toString();
    }

    private static Comparator<MethodCandidate> candidateComparator() {
        return Comparator.comparing(MethodCandidate::declaringType)
                .thenComparing(MethodCandidate::methodName)
                .thenComparing(candidate -> String.join(",", candidate.parameterTypes()));
    }

    private static String candidateKey(MethodCandidate candidate) {
        return candidate.declaringType() + "#" + candidate.methodName() + "(" +
                String.join(",", candidate.parameterTypes()) + ")";
    }

    private static Resolution unavailable(String binaryName, String detail) {
        return Resolution.unavailable("SootUp could not index " + binaryName + ": " + detail);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
