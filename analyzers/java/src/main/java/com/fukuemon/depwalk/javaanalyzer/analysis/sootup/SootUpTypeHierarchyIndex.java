package com.fukuemon.depwalk.javaanalyzer.analysis.sootup;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.MethodModifier;
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
     * @param returnType erasure 済み戻り値型の binary name。constructor / void は {@code void}
     * @param isStatic static member かどうか
     */
    public record MethodCandidate(
            String declaringType,
            String methodName,
            List<String> parameterTypes,
            String returnType,
            boolean isStatic) {
        /** 引数型配列を防御的コピーして候補を生成する。 */
        public MethodCandidate {
            parameterTypes = List.copyOf(parameterTypes);
        }

        /** 戻り値型・static 性を使わない照会用の互換 constructor。 */
        public MethodCandidate(String declaringType, String methodName, List<String> parameterTypes) {
            this(declaringType, methodName, parameterTypes, "void", false);
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

    private record DispatchKey(
            String declaringType,
            String receiverType,
            String methodName,
            List<String> parameterTypes) {
        private DispatchKey {
            parameterTypes = List.copyOf(parameterTypes);
        }

        private MethodKey methodKey() {
            return new MethodKey(declaringType, methodName, parameterTypes);
        }
    }

    /** receiver が同時に満たす複数の上限型 (交差境界) を鍵にした照会。receiverTypes は重複排除済み。 */
    private record IntersectionKey(
            String declaringType,
            List<String> receiverTypes,
            String methodName,
            List<String> parameterTypes) {
        private IntersectionKey {
            receiverTypes = List.copyOf(receiverTypes);
            parameterTypes = List.copyOf(parameterTypes);
        }

        private MethodKey methodKey() {
            return new MethodKey(declaringType, methodName, parameterTypes);
        }
    }

    private final List<Path> classpath;
    private final Map<MethodKey, Resolution> methodCache = new LinkedHashMap<>();
    private final Map<DispatchKey, Resolution> receiverMethodCache = new LinkedHashMap<>();
    private final Map<IntersectionKey, Resolution> intersectionMethodCache = new LinkedHashMap<>();
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
     * 宣言メソッドに対する実装候補を、call siteで観測した静的receiver型のsubtypeに限定して返す。
     * 親interfaceから継承したメソッドを子interface型で呼ぶ場合に、子interfaceを実装しない
     * 親interfaceだけの実装を候補へ混入させないため、宣言型と探索起点を別々に受け取る。
     *
     * @param declaringType 解決済みメソッドを宣言する型のbinary name
     * @param receiverType call siteにおけるreceiverの静的型のbinary name
     * @param methodName 宣言メソッド名
     * @param parameterTypes erasure済み引数型のbinary name配列
     * @return receiver型から到達可能な具象実装候補、またはbytecodeを利用できなかった理由
     */
    public Resolution resolveMethod(
            String declaringType,
            String receiverType,
            String methodName,
            List<String> parameterTypes) {
        if (declaringType.equals(receiverType)) {
            return resolveMethod(declaringType, methodName, parameterTypes);
        }
        DispatchKey key = new DispatchKey(declaringType, receiverType, methodName, parameterTypes);
        return receiverMethodCache.computeIfAbsent(key, this::resolveReceiverMethodUncached);
    }

    /**
     * 宣言メソッドに対する実装候補を、receiverが満たすすべての上限型の積集合に限定して返す。
     * {@code T extends A & B} のような交差境界で、先頭境界だけを実装する型が候補へ混入することを防ぐ。
     * いずれかの境界をclasspathから解決できない場合は、境界を無視して誤候補を返さず利用不能とする。
     *
     * <p>重複排除後の上限が1件なら積集合を取る余地がないため単一receiver版へ委譲する。これにより
     * 呼び出し経路によらず結果キャッシュとinterface defaultの実効候補判定が共有される。
     *
     * @param declaringType 解決済みメソッドを宣言する型のbinary name
     * @param receiverTypes call siteのreceiverが同時に満たす静的型のbinary name配列
     * @param methodName 宣言メソッド名
     * @param parameterTypes erasure済み引数型のbinary name配列
     * @return 全receiver型から到達可能な候補の積集合、またはbytecodeを利用できなかった理由
     */
    public Resolution resolveMethod(
            String declaringType,
            List<String> receiverTypes,
            String methodName,
            List<String> parameterTypes) {
        List<String> bounds = receiverTypes.stream().distinct().toList();
        if (bounds.size() == 1) {
            // 上限が 1 つなら積集合を取る余地がなく単一 receiver 版と同じ意味論になる。
            // 委譲することで結果キャッシュと interface default の実効候補判定を共有する
            // (交差境界だけが本メソッド固有の処理)。
            return resolveMethod(declaringType, bounds.get(0), methodName, parameterTypes);
        }
        IntersectionKey key = new IntersectionKey(declaringType, bounds, methodName, parameterTypes);
        return intersectionMethodCache.computeIfAbsent(key, this::resolveIntersectionMethodUncached);
    }

    private Resolution resolveIntersectionMethodUncached(IntersectionKey key) {
        String declaringType = key.declaringType();
        return guardQuery(declaringType, () -> {
            MethodKey methodKey = key.methodKey();
            ClassType declaredType = view().getIdentifierFactory().getClassType(declaringType);
            Optional<JavaSootClass> declaredClass = view().getClass(declaredType);
            if (declaredClass.isEmpty()) {
                return unavailable(declaringType, "class was not found in the supplied classpath");
            }
            Optional<JavaSootMethod> declaredMethod = findDeclaredMethod(declaredClass.get(), methodKey);
            if (declaredMethod.isEmpty()) {
                return unavailable(declaringType, "method was not found in the matching class file");
            }
            if (declaredMethod.get().isPrivate() || declaredMethod.get().isFinal()) {
                return Resolution.available(List.of());
            }

            Map<String, ClassType> receiverIntersection = null;
            for (String receiverName : key.receiverTypes()) {
                ClassType receiverType = view().getIdentifierFactory().getClassType(receiverName);
                Optional<JavaSootClass> receiverClass = view().getClass(receiverType);
                if (receiverClass.isEmpty()) {
                    return unavailable(receiverName, "class was not found in the supplied classpath");
                }
                Stream<ClassType> receiverCandidates = receiverClass.get().isInterface()
                        ? hierarchy().implementersOf(receiverType)
                        : Stream.concat(
                                receiverClass.get().isConcrete() ? Stream.of(receiverType) : Stream.empty(),
                                hierarchy().subclassesOf(receiverType));
                Map<String, ClassType> concreteReceivers = new LinkedHashMap<>();
                receiverCandidates
                        .filter(type -> view().getClass(type).map(JavaSootClass::isConcrete).orElse(false))
                        .forEach(type -> concreteReceivers.put(type.getFullyQualifiedName(), type));
                if (receiverIntersection == null) {
                    receiverIntersection = concreteReceivers;
                } else {
                    receiverIntersection.keySet().retainAll(concreteReceivers.keySet());
                }
            }

            Map<String, MethodCandidate> candidates = new LinkedHashMap<>();
            if (receiverIntersection != null) {
                receiverIntersection.values().stream()
                        .sorted(Comparator.comparing(ClassType::getFullyQualifiedName))
                        .forEach(type -> findEffectiveMethod(type, methodKey).ifPresent(candidate ->
                                candidates.putIfAbsent(candidateKey(candidate), candidate)));
            }
            return Resolution.available(candidates.values().stream().sorted(candidateComparator()).toList());
        });
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

    /**
     * 宣言 class 自身の指定名 callable method を bytecode から列挙する
     * (bridge / synthetic を除外)。bytecode-only member 救済
     * (adr/0005-adopt-sootup-and-spring-di-resolution.md) の
     * 照会用で、型階層は辿らない。
     *
     * @param declaringType 所有 class の binary name
     * @param methodName 照会する method 名
     * @return 一致した declared method の候補一覧、または unavailable
     */
    public Resolution resolveDeclaredCallableMethods(String declaringType, String methodName) {
        return declaredCallableMethods(declaringType, methodName);
    }

    /** 宣言 class 自身の全 callable method (bridge / synthetic 除外)。 */
    public Resolution resolveDeclaredCallableMethods(String declaringType) {
        return declaredCallableMethods(declaringType, null);
    }

    private Resolution declaredCallableMethods(String declaringType, String methodName) {
        return guardQuery(declaringType, () -> {
            ClassType classType = view().getIdentifierFactory().getClassType(declaringType);
            Optional<JavaSootClass> sootClass = view().getClass(classType);
            if (sootClass.isEmpty()) {
                return unavailable(declaringType, "class was not found in the supplied classpath");
            }
            List<MethodCandidate> methods = sootClass.get().getMethods().stream()
                    .filter(method -> methodName == null || method.getName().equals(methodName))
                    .filter(method -> !method.getModifiers().contains(MethodModifier.BRIDGE)
                            && !method.getModifiers().contains(MethodModifier.SYNTHETIC))
                    .map(this::toCandidate)
                    .sorted(candidateComparator())
                    .toList();
            return Resolution.available(methods);
        });
    }

    /**
     * 宣言 class 自身の bytecode field の型 (binary name) を返す。bytecode-only
     * field は receiver 型解決の補完にだけ使い、node 化しない。
     */
    public Optional<String> resolveDeclaredFieldType(String declaringType, String fieldName) {
        try {
            ClassType classType = view().getIdentifierFactory().getClassType(declaringType);
            Optional<JavaSootClass> sootClass = view().getClass(classType);
            if (sootClass.isEmpty()) {
                return Optional.empty();
            }
            return sootClass.get().getFields().stream()
                    .filter(field -> field.getName().equals(fieldName))
                    .map(field -> binaryNameOf(field.getType()))
                    .findFirst();
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
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

    private Resolution resolveReceiverMethodUncached(DispatchKey key) {
        return guardQuery(key.receiverType(), () -> {
            MethodKey methodKey = key.methodKey();
            ClassType declaredType = view().getIdentifierFactory().getClassType(key.declaringType());
            Optional<JavaSootClass> declaredClass = view().getClass(declaredType);
            if (declaredClass.isEmpty()) {
                return unavailable(key.declaringType(), "class was not found in the supplied classpath");
            }
            Optional<JavaSootMethod> declaredMethod = findDeclaredMethod(declaredClass.get(), methodKey);
            if (declaredMethod.isEmpty()) {
                return unavailable(key.declaringType(), "method was not found in the matching class file");
            }
            if (declaredMethod.get().isPrivate() || declaredMethod.get().isFinal()) {
                return Resolution.available(List.of());
            }

            ClassType receiverType = view().getIdentifierFactory().getClassType(key.receiverType());
            Optional<JavaSootClass> receiverClass = view().getClass(receiverType);
            if (receiverClass.isEmpty()) {
                return unavailable(key.receiverType(), "class was not found in the supplied classpath");
            }
            Stream<ClassType> receiverCandidates = receiverClass.get().isInterface()
                    ? hierarchy().implementersOf(receiverType)
                    : Stream.concat(
                            receiverClass.get().isConcrete() ? Stream.of(receiverType) : Stream.empty(),
                            hierarchy().subclassesOf(receiverType));
            Map<String, MethodCandidate> candidates = new LinkedHashMap<>();
            if (receiverClass.get().isInterface()) {
                findEffectiveInterfaceDefault(receiverType, methodKey).ifPresent(candidate ->
                        candidates.put(candidateKey(candidate), candidate));
            }
            receiverCandidates
                    .filter(type -> view().getClass(type).map(JavaSootClass::isConcrete).orElse(false))
                    .sorted(Comparator.comparing(ClassType::getFullyQualifiedName))
                    .forEach(type -> findEffectiveMethod(type, methodKey).ifPresent(candidate ->
                            candidates.putIfAbsent(candidateKey(candidate), candidate)));
            return Resolution.available(candidates.values().stream().sorted(candidateComparator()).toList());
        });
    }

    /**
     * interface receiverから継承される最も具体的なdefault methodを返す。
     *
     * <p>子interfaceが親のdefault methodをabstractとして再宣言した場合、親defaultは実効候補ではない。
     * そのためreceiverと全親interfaceの同一signature宣言を集め、より具体的な宣言に覆われたものを除外する。
     * 有効なJava bytecodeでは最も具体的な宣言は1件に定まるため、複数残る不整合時は誤候補を返さない。
     *
     * @param receiverType call siteで観測したinterface型
     * @param key 探索するmethod signature
     * @return 継承可能なdefault method。abstract再宣言または不整合時は空
     */
    private Optional<MethodCandidate> findEffectiveInterfaceDefault(ClassType receiverType, MethodKey key) {
        List<Map.Entry<ClassType, JavaSootMethod>> declarations = Stream.concat(
                        Stream.of(receiverType),
                        hierarchy().implementedInterfacesOf(receiverType))
                .distinct()
                .map(type -> Map.entry(type, view().getClass(type)
                        .flatMap(owner -> findDeclaredMethod(owner, key))))
                .filter(entry -> entry.getValue().isPresent())
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().orElseThrow()))
                .toList();
        List<JavaSootMethod> mostSpecific = declarations.stream()
                .filter(candidate -> declarations.stream().noneMatch(other ->
                        !candidate.getKey().equals(other.getKey())
                                && hierarchy().isSubtype(candidate.getKey(), other.getKey())))
                .map(Map.Entry::getValue)
                .toList();
        if (mostSpecific.size() != 1 || !mostSpecific.get(0).isConcrete()) {
            return Optional.empty();
        }
        return Optional.of(toCandidate(mostSpecific.get(0)));
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
                parameterTypesOf(method),
                binaryNameOf(method.getReturnType()),
                method.isStatic());
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
