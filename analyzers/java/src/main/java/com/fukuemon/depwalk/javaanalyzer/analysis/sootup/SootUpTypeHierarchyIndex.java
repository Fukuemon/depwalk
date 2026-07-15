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
import java.util.function.Supplier;
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

    /**
     * bytecode 上で実在するメソッドまたは constructor の識別情報。
     *
     * @param declaringType メソッドを実際に宣言する型の binary name
     * @param methodName メソッド名。constructor は {@code <init>}
     * @param parameterTypes erasure 済み引数型の binary name 配列
     */
    public record MethodCandidate(String declaringType, String methodName, List<String> parameterTypes) {
        /** 引数型配列を防御的コピーして候補を生成する。 */
        public MethodCandidate {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    /**
     * 型階層問い合わせの結果。問い合わせ自体の成否と候補0件を区別する。
     *
     * @param candidates 決定的順序に並べた実装候補。候補がなければ空
     * @param unavailableReason classpath または class file を利用できなかった理由。正常なら {@code null}
     */
    public record Resolution(List<MethodCandidate> candidates, String unavailableReason) {
        /** 候補配列を防御的コピーして問い合わせ結果を生成する。 */
        public Resolution {
            candidates = List.copyOf(candidates);
        }

        /**
         * 正常に完了した問い合わせ結果を生成する。
         *
         * @param candidates 発見した候補。候補0件も正常な結果として許容する
         * @return 利用可能な問い合わせ結果
         */
        public static Resolution available(List<MethodCandidate> candidates) {
            return new Resolution(candidates, null);
        }

        /**
         * classpath または bytecode を利用できなかった問い合わせ結果を生成する。
         *
         * @param reason 利用不能になった具体的な理由
         * @return 候補を持たない利用不能結果
         */
        public static Resolution unavailable(String reason) {
            return new Resolution(List.of(), reason);
        }

        /**
         * 問い合わせが正常に完了したかを返す。
         *
         * @return 候補件数にかかわらず正常に照会できた場合は {@code true}
         */
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
    private final Map<MethodKey, Resolution> implementationMethodCache = new LinkedHashMap<>();
    private final Map<String, Resolution> constructorCache = new LinkedHashMap<>();
    private JavaView view;
    private TypeHierarchy hierarchy;

    private SootUpTypeHierarchyIndex(List<Path> classpath) {
        this.classpath = List.copyOf(classpath);
    }

    /**
     * classpath entry を正規化し、遅延初期化される型階層索引を生成する。
     *
     * @param classpath 依存 jar と project classes directory の path
     * @return まだ bytecode を読み込んでいない型階層索引
     */
    public static SootUpTypeHierarchyIndex fromClasspath(List<String> classpath) {
        return new SootUpTypeHierarchyIndex(classpath.stream()
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList());
    }

    /**
     * 宣言メソッドに対する具象実装候補を型階層から返す。
     *
     * @param declaringType interface、abstract class、または基底 class の binary name
     * @param methodName 宣言メソッド名
     * @param parameterTypes erasure 済み引数型の binary name 配列
     * @return 具象 subtype の実装候補、または bytecode を利用できなかった理由
     */
    public Resolution resolveMethod(String declaringType, String methodName, List<String> parameterTypes) {
        MethodKey key = new MethodKey(declaringType, methodName, parameterTypes);
        return methodCache.computeIfAbsent(key, this::resolveMethodUncached);
    }

    /**
     * 指定 class の constructor を bytecode から返す。
     *
     * <p>Lombok などが生成し source に現れない constructor も、コンパイル済み class に存在すれば
     * 候補へ含める。
     *
     * @param declaringType constructor を持つ class の binary name
     * @return constructor 候補、または bytecode を利用できなかった理由
     */
    public Resolution resolveConstructors(String declaringType) {
        return constructorCache.computeIfAbsent(declaringType, this::resolveConstructorsUncached);
    }

    /**
     * 具象 receiver から呼ばれる実効メソッドを返す。
     *
     * <p>receiver 自身、superclass、implemented interface の順に検索し、継承メソッドと interface
     * default method を含めて実際の宣言元を特定する。
     *
     * @param implementationType 具象 receiver の binary name
     * @param methodName 呼び出すメソッド名
     * @param parameterTypes erasure 済み引数型の binary name 配列
     * @return 実効メソッド1件、候補なし、または bytecode を利用できなかった理由
     */
    public Resolution resolveImplementationMethod(
            String implementationType,
            String methodName,
            List<String> parameterTypes) {
        MethodKey key = new MethodKey(implementationType, methodName, parameterTypes);
        return implementationMethodCache.computeIfAbsent(key, this::resolveImplementationMethodUncached);
    }

    boolean isInitialized() {
        return view != null;
    }

    private Resolution resolveMethodUncached(MethodKey key) {
        return guardQuery(key.declaringType(), () -> {
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
        });
    }

    private Resolution resolveConstructorsUncached(String declaringType) {
        return guardQuery(declaringType, () -> {
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
        });
    }

    private Resolution resolveImplementationMethodUncached(MethodKey key) {
        return guardQuery(key.declaringType(), () -> {
            ClassType receiverType = view().getIdentifierFactory().getClassType(key.declaringType());
            if (view().getClass(receiverType).isEmpty()) {
                return unavailable(key.declaringType(), "class was not found in the supplied classpath");
            }
            return findEffectiveMethod(receiverType, key)
                    .map(candidate -> Resolution.available(List.of(candidate)))
                    .orElseGet(() -> Resolution.available(List.of()));
        });
    }

    /**
     * SootUp問い合わせ中の入力不正と依存classのlink失敗を、解析全体を停止させない利用不能結果へ変換する。
     * {@link LinkageError}はJVM自体の回復不能errorではなく、対象classpathの不足・不整合として発生し得るため、
     * この外部ライブラリ境界に限って捕捉する。
     *
     * @param binaryName 問い合わせ対象型のbinary name
     * @param query 実行するSootUp問い合わせ
     * @return 問い合わせ結果、または失敗理由を保持する利用不能結果
     */
    static Resolution guardQuery(String binaryName, Supplier<Resolution> query) {
        try {
            return query.get();
        } catch (RuntimeException | LinkageError failure) {
            return unavailable(binaryName, describe(failure));
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
        for (ClassType interfaceType : hierarchy().implementedInterfacesOf(receiverType).toList()) {
            Optional<JavaSootClass> owner = view().getClass(interfaceType);
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

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
