package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Java source と project bytecode から Spring Bean と注入点を収集し、静的な候補解決結果を構築する。
 *
 * <p>stereotype class と {@code @Bean} factory method を Bean 定義として索引化し、constructor、
 * field、setter の各注入点へ型・qualifier・primary の規則を適用する。条件付き Bean は実行時条件を
 * 評価せず曖昧候補として保持し、Spring Data Repository と MyBatis Mapper は実行時生成実装として
 * 区別する。Spring application context は起動せず、source file ごとの AST も保持しない。
 */
public final class SpringDiIndex {

    /** Bean 定義を source 上で発見した方法。 */
    public enum BeanKind {
        /** stereotype annotation が付いた具象 class。 */
        STEREOTYPE,
        /** configuration class 内の {@code @Bean} method。 */
        FACTORY_METHOD
    }

    /** Spring dependency injection の注入方法。 */
    public enum InjectionKind {
        /** 明示 constructor または bytecode で検出した生成 constructor。 */
        CONSTRUCTOR,
        /** {@code @Autowired} field。 */
        FIELD,
        /** 単一引数の {@code @Autowired} setter。 */
        SETTER
    }

    /** 注入点に対する静的候補解決の状態。 */
    public enum ResolutionStatus {
        /** 条件なしの候補が一意に決まった。 */
        UNIQUE,
        /** 複数候補または条件付き候補が残った。 */
        AMBIGUOUS,
        /** 対応する Bean 定義を発見できなかった。 */
        UNRESOLVED,
        /** framework が実行時に実装を生成する既知の型である。 */
        RUNTIME_PROVIDED
    }

    /**
     * 静的解析で収集した Spring Bean 定義。
     *
     * @param beanType 注入型との適合判定に使う宣言型の binary name
     * @param implementationType 実装メソッド解決に使う具象型の binary name
     * @param names Bean 名と alias
     * @param qualifiers Bean に直接指定された qualifier
     * @param primary {@code @Primary} が直接指定されているか
     * @param conditionTypes 評価せず保持する条件 annotation の FQN
     * @param kind stereotype class または factory method の別
     * @param declaringType Bean を宣言した class の binary name
     * @param factoryMethodName factory method の名前。stereotype class では {@code null}
     */
    public record BeanDefinition(
            String beanType,
            String implementationType,
            List<String> names,
            List<String> qualifiers,
            boolean primary,
            List<String> conditionTypes,
            BeanKind kind,
            String declaringType,
            String factoryMethodName) {
        /** collection component を防御的コピーして Bean 定義を生成する。 */
        public BeanDefinition {
            names = List.copyOf(names);
            qualifiers = List.copyOf(qualifiers);
            conditionTypes = List.copyOf(conditionTypes);
        }
    }

    /**
     * source または生成 constructor から収集した dependency injection の注入点。
     *
     * @param ownerType 注入先 class の binary name
     * @param kind constructor、field、setter の別
     * @param targetName 注入対象の parameter または field 名
     * @param receiverNames 呼び出しレシーバーとして同じ注入を指すparameter名と代入先field名
     * @param injectedType 注入される型の binary name
     * @param qualifier 注入点に指定された qualifier。未指定なら {@code null}
     * @param bytecodeGenerated source にない生成 constructor から検出したか
     * @param sourcePath 注入先 source file の絶対 path。取得できなければ {@code null}
     * @param sourceLine 注入点の行番号。取得できなければ {@code 0}
     */
    public record InjectionPoint(
            String ownerType,
            InjectionKind kind,
            String targetName,
            List<String> receiverNames,
            String injectedType,
            String qualifier,
            boolean bytecodeGenerated,
            String sourcePath,
            int sourceLine) {
        /** receiver name 配列を防御的コピーして注入点を生成する。 */
        public InjectionPoint {
            receiverNames = List.copyOf(receiverNames);
        }
    }

    /**
     * 注入候補となった Bean と、その候補を得た解析根拠。
     *
     * @param bean 候補 Bean の定義
     * @param provenance 候補を導出した解析器名の重複なし配列
     */
    public record BeanCandidate(BeanDefinition bean, List<String> provenance) {
        /** provenance を防御的コピーして候補を生成する。 */
        public BeanCandidate {
            provenance = List.copyOf(provenance);
        }
    }

    /**
     * 1つの注入点に対する候補一覧と解決状態。
     *
     * @param injectionPoint 解決対象の注入点
     * @param candidates 選択規則の適用後に残った候補
     * @param status 解決状態
     * @param reason 曖昧または未解決になった理由。理由が不要なら {@code null}
     */
    public record InjectionResolution(
            InjectionPoint injectionPoint,
            List<BeanCandidate> candidates,
            ResolutionStatus status,
            String reason) {
        /** candidate 配列を防御的コピーして解決結果を生成する。 */
        public InjectionResolution {
            candidates = List.copyOf(candidates);
        }
    }

    /**
     * 解析対象全体から構築した Spring DI 索引の不変スナップショット。
     *
     * @param beans 検出した Bean 定義
     * @param injections 検出した注入点
     * @param resolutions 各注入点の候補解決結果
     */
    public record Result(
            List<BeanDefinition> beans,
            List<InjectionPoint> injections,
            List<InjectionResolution> resolutions) {
        /** すべての collection component を防御的コピーしてスナップショットを生成する。 */
        public Result {
            beans = List.copyOf(beans);
            injections = List.copyOf(injections);
            resolutions = List.copyOf(resolutions);
        }
    }

    private record BeanEntry(BeanDefinition definition, Set<String> assignableTypes) {
    }

    private record TypeInfo(
            Set<String> assignableTypes,
            Set<String> annotationTypes) {
    }

    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final Map<String, TypeInfo> typeInfoByBinaryName = new LinkedHashMap<>();
    private final List<BeanEntry> beanEntries = new ArrayList<>();
    private final List<InjectionPoint> injectionPoints = new ArrayList<>();

    private SpringDiIndex(SootUpTypeHierarchyIndex sootUpIndex) {
        this.sootUpIndex = sootUpIndex;
    }

    /**
     * 指定した bytecode 型階層索引を constructor と実装型の補完に使う空の DI 索引を生成する。
     *
     * @param sootUpIndex project class と依存 class を検索する bytecode 型階層索引
     * @return compilation unit を順次受け付ける空の DI 索引
     */
    public static SpringDiIndex create(SootUpTypeHierarchyIndex sootUpIndex) {
        return new SpringDiIndex(sootUpIndex);
    }

    /**
     * 1つの compilation unit から Bean 定義、注入点、実行時生成マーカーを収集する。
     *
     * <p>呼び出し後に compilation unit やその AST node への参照は保持しない。型解決不能な必須情報で
     * 例外が発生した場合は呼び出し元へ伝播し、解析実行単位の diagnostic へ変換させる。
     *
     * @param unit 収集対象の compilation unit
     */
    public void accept(CompilationUnit unit) {
        List<ClassOrInterfaceDeclaration> types = unit.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration type : types) {
            String binaryName = BinaryNames.forTypeLikeNode(type);
            typeInfoByBinaryName.put(binaryName, new TypeInfo(
                    assignableTypesOf(type),
                    annotationTypesOf(type)));
        }
        List<BeanEntry> collectedBeans = collectBeans(types);
        List<InjectionPoint> collectedInjections = collectInjections(types);
        beanEntries.addAll(collectedBeans);
        injectionPoints.addAll(collectedInjections);
    }

    /**
     * 収集済み情報へ候補選択規則を適用し、不変の解決結果を返す。
     *
     * @return Bean、注入点、注入解決結果を決定的順序に並べたスナップショット
     */
    public Result build() {
        List<BeanEntry> sortedBeans = beanEntries.stream()
                .sorted(Comparator.comparing(entry -> beanSortKey(entry.definition())))
                .toList();
        List<InjectionPoint> sortedInjections = injectionPoints.stream()
                .sorted(Comparator.comparing(InjectionPoint::ownerType)
                        .thenComparing(InjectionPoint::targetName)
                        .thenComparing(injection -> injection.kind().name()))
                .toList();
        List<InjectionResolution> resolutions = sortedInjections.stream()
                .map(injection -> resolve(injection, sortedBeans))
                .toList();
        return new Result(
                sortedBeans.stream().map(BeanEntry::definition).toList(),
                sortedInjections,
                resolutions);
    }

    /**
     * 複数の compilation unit を一括して収集・解決する convenience method。
     *
     * @param units 解析対象の compilation unit
     * @param sootUpIndex constructor と実装型の補完に使う bytecode 型階層索引
     * @return 全 unit を収集した DI 解決結果
     */
    public static Result analyze(List<CompilationUnit> units, SootUpTypeHierarchyIndex sootUpIndex) {
        SpringDiIndex index = create(sootUpIndex);
        units.forEach(index::accept);
        return index.build();
    }

    private List<BeanEntry> collectBeans(List<ClassOrInterfaceDeclaration> types) {
        List<BeanEntry> beans = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : types) {
            AnnotationExpr stereotype = SpringAnnotations.findAny(type, SpringAnnotations.STEREOTYPES);
            if (stereotype != null && !type.isInterface() && !type.isAbstract()) {
                beans.add(stereotypeBean(type, stereotype));
            }
            if (SpringAnnotations.has(type, SpringAnnotations.CONFIGURATION)) {
                for (MethodDeclaration method : type.getMethods()) {
                    AnnotationExpr beanAnnotation = SpringAnnotations.find(method, SpringAnnotations.BEAN);
                    if (beanAnnotation != null) {
                        beans.add(factoryMethodBean(type, method, beanAnnotation));
                    }
                }
            }
        }
        return beans;
    }

    private BeanEntry stereotypeBean(ClassOrInterfaceDeclaration type, AnnotationExpr stereotype) {
        String beanType = BinaryNames.forTypeLikeNode(type);
        List<String> explicitNames = SpringAnnotations.stringValues(stereotype, "value");
        List<String> names = explicitNames.isEmpty()
                ? List.of(Introspector.decapitalize(type.getNameAsString()))
                : explicitNames;
        String qualifier = SpringAnnotations.qualifier(type);
        BeanDefinition definition = new BeanDefinition(
                beanType,
                beanType,
                names,
                qualifier == null ? List.of() : List.of(qualifier),
                SpringAnnotations.has(type, SpringAnnotations.PRIMARY),
                SpringAnnotations.conditionTypes(type),
                BeanKind.STEREOTYPE,
                beanType,
                null);
        return new BeanEntry(definition, typeInfoByBinaryName.get(beanType).assignableTypes());
    }

    private BeanEntry factoryMethodBean(
            ClassOrInterfaceDeclaration configuration,
            MethodDeclaration method,
            AnnotationExpr beanAnnotation) {
        List<String> names = SpringAnnotations.stringValues(beanAnnotation, "name", "value");
        if (names.isEmpty()) {
            names = List.of(method.getNameAsString());
        }
        ResolvedType returnType = method.getType().resolve();
        String beanType = BinaryNames.erasureOf(returnType);
        String implementationType = factoryImplementationType(method).orElse(beanType);
        String qualifier = SpringAnnotations.qualifier(method);
        BeanDefinition definition = new BeanDefinition(
                beanType,
                implementationType,
                names,
                qualifier == null ? List.of() : List.of(qualifier),
                SpringAnnotations.has(method, SpringAnnotations.PRIMARY),
                SpringAnnotations.conditionTypes(method),
                BeanKind.FACTORY_METHOD,
                BinaryNames.forTypeLikeNode(configuration),
                method.getNameAsString());
        return new BeanEntry(definition, assignableTypesOf(returnType));
    }

    private static Optional<String> factoryImplementationType(MethodDeclaration method) {
        Set<String> returnedTypes = new LinkedHashSet<>();
        for (ReturnStmt returnStmt : method.findAll(ReturnStmt.class)) {
            if (!isDirectMethodReturn(returnStmt, method) || returnStmt.getExpression().isEmpty()) {
                continue;
            }
            if (returnStmt.getExpression().get() instanceof ObjectCreationExpr creation) {
                try {
                    returnedTypes.add(BinaryNames.erasureOf(creation.calculateResolvedType()));
                } catch (RuntimeException | LinkageError ignored) {
                    // 動的・未解決 factory は宣言戻り値のまま扱い、実装型を推測しない。
                }
            }
        }
        return returnedTypes.size() == 1 ? Optional.of(returnedTypes.iterator().next()) : Optional.empty();
    }

    private static boolean isDirectMethodReturn(ReturnStmt returnStmt, MethodDeclaration method) {
        Node current = returnStmt.getParentNode().orElse(null);
        while (current != null && current != method) {
            if (current instanceof LambdaExpr || current instanceof MethodDeclaration) {
                return false;
            }
            current = current.getParentNode().orElse(null);
        }
        return current == method;
    }

    private List<InjectionPoint> collectInjections(List<ClassOrInterfaceDeclaration> types) {
        List<InjectionPoint> injections = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : types) {
            if (type.isInterface()) {
                continue;
            }
            collectConstructorInjections(type, injections);
            collectFieldInjections(type, injections);
            collectSetterInjections(type, injections);
        }
        return injections;
    }

    private void collectConstructorInjections(
            ClassOrInterfaceDeclaration type,
            List<InjectionPoint> injections) {
        List<ConstructorDeclaration> constructors = type.getConstructors();
        boolean stereotypeBean = SpringAnnotations.findAny(type, SpringAnnotations.STEREOTYPES) != null;
        List<ConstructorDeclaration> selected = constructors.stream()
                .filter(constructor -> SpringAnnotations.has(constructor, SpringAnnotations.AUTOWIRED))
                .toList();
        if (selected.isEmpty() && constructors.size() == 1 && stereotypeBean) {
            selected = constructors;
        }
        for (ConstructorDeclaration constructor : selected) {
            for (Parameter parameter : constructor.getParameters()) {
                injections.add(injectionPoint(
                        type,
                        InjectionKind.CONSTRUCTOR,
                        parameter.getNameAsString(),
                        receiverNamesOf(constructor, parameter),
                        parameter.getType().resolve(),
                        SpringAnnotations.qualifier(parameter),
                        false,
                        lineOf(parameter)));
            }
        }
        if (constructors.isEmpty() && stereotypeBean) {
            collectBytecodeConstructorInjections(type, injections);
        }
    }

    private void collectBytecodeConstructorInjections(
            ClassOrInterfaceDeclaration type,
            List<InjectionPoint> injections) {
        List<VariableDeclarator> instanceFields = type.getFields().stream()
                .filter(field -> !field.isStatic())
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> variable.getInitializer().isEmpty())
                .toList();
        if (instanceFields.isEmpty()) {
            return;
        }
        List<VariableDeclarator> requiredFields = type.getFields().stream()
                .filter(field -> !field.isStatic() && field.isFinal())
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> variable.getInitializer().isEmpty())
                .toList();
        String ownerType = BinaryNames.forTypeLikeNode(type);
        List<String> requiredTypes = requiredFields.stream()
                .map(variable -> BinaryNames.erasureOf(variable.getType().resolve()))
                .toList();
        List<String> allInstanceTypes = instanceFields.stream()
                .map(variable -> BinaryNames.erasureOf(variable.getType().resolve()))
                .toList();
        SootUpTypeHierarchyIndex.Resolution constructors = sootUpIndex.resolveConstructors(ownerType);
        if (!constructors.isAvailable()) {
            return;
        }
        List<VariableDeclarator> constructorFields;
        if (constructors.candidates().stream().anyMatch(candidate -> candidate.parameterTypes().equals(requiredTypes))
                && !requiredFields.isEmpty()) {
            constructorFields = requiredFields;
        } else if (constructors.candidates().stream()
                .anyMatch(candidate -> candidate.parameterTypes().equals(allInstanceTypes))) {
            constructorFields = instanceFields;
        } else {
            return;
        }
        for (VariableDeclarator field : constructorFields) {
            injections.add(injectionPoint(
                    type,
                    InjectionKind.CONSTRUCTOR,
                    field.getNameAsString(),
                    field.getType().resolve(),
                    null,
                    true,
                    lineOf(field)));
        }
    }

    private void collectFieldInjections(ClassOrInterfaceDeclaration type, List<InjectionPoint> injections) {
        for (FieldDeclaration field : type.getFields()) {
            if (field.isStatic() || !SpringAnnotations.has(field, SpringAnnotations.AUTOWIRED)) {
                continue;
            }
            String qualifier = SpringAnnotations.qualifier(field);
            for (VariableDeclarator variable : field.getVariables()) {
                injections.add(injectionPoint(
                        type,
                        InjectionKind.FIELD,
                        variable.getNameAsString(),
                        variable.getType().resolve(),
                        qualifier,
                        false,
                        lineOf(variable)));
            }
        }
    }

    private void collectSetterInjections(ClassOrInterfaceDeclaration type, List<InjectionPoint> injections) {
        for (MethodDeclaration method : type.getMethods()) {
            if (!SpringAnnotations.has(method, SpringAnnotations.AUTOWIRED)
                    || method.isStatic()
                    || !method.getNameAsString().startsWith("set")
                    || method.getParameters().size() != 1) {
                continue;
            }
            Parameter parameter = method.getParameter(0);
            String qualifier = SpringAnnotations.qualifier(parameter);
            if (qualifier == null) {
                qualifier = SpringAnnotations.qualifier(method);
            }
            injections.add(injectionPoint(
                    type,
                    InjectionKind.SETTER,
                    parameter.getNameAsString(),
                    receiverNamesOf(method, parameter),
                    parameter.getType().resolve(),
                    qualifier,
                    false,
                    lineOf(parameter)));
        }
    }

    private InjectionPoint injectionPoint(
            ClassOrInterfaceDeclaration owner,
            InjectionKind kind,
            String targetName,
            ResolvedType injectedType,
            String qualifier,
            boolean bytecodeGenerated,
            int sourceLine) {
        return injectionPoint(
                owner,
                kind,
                targetName,
                List.of(targetName),
                injectedType,
                qualifier,
                bytecodeGenerated,
                sourceLine);
    }

    private InjectionPoint injectionPoint(
            ClassOrInterfaceDeclaration owner,
            InjectionKind kind,
            String targetName,
            List<String> receiverNames,
            ResolvedType injectedType,
            String qualifier,
            boolean bytecodeGenerated,
            int sourceLine) {
        return new InjectionPoint(
                BinaryNames.forTypeLikeNode(owner),
                kind,
                targetName,
                receiverNames,
                BinaryNames.erasureOf(injectedType),
                qualifier,
                bytecodeGenerated,
                sourcePathOf(owner),
                sourceLine);
    }

    /**
     * constructor または setter の注入parameterについて、parameter自身と
     * {@code this.field = parameter} 形式で代入されるfieldを同じ呼び出しレシーバーとして収集する。
     * nested callable 内の代入は別スコープなので対象外とする。
     *
     * @param callable 注入parameterを宣言するconstructorまたはmethod
     * @param parameter 注入対象parameter
     * @return parameter名を先頭に、代入先field名をsource順で重複なく並べた一覧
     */
    private static List<String> receiverNamesOf(Node callable, Parameter parameter) {
        Set<String> names = new LinkedHashSet<>();
        names.add(parameter.getNameAsString());
        for (AssignExpr assignment : callable.findAll(AssignExpr.class)) {
            if (!belongsToCallable(assignment, callable)
                    || !refersToParameter(assignment.getValue(), parameter)) {
                continue;
            }
            assignedFieldName(assignment.getTarget()).ifPresent(names::add);
        }
        return List.copyOf(names);
    }

    private static boolean belongsToCallable(Node node, Node callable) {
        Node current = node.getParentNode().orElse(null);
        while (current != null && current != callable) {
            if (current instanceof ConstructorDeclaration || current instanceof MethodDeclaration || current instanceof LambdaExpr) {
                return false;
            }
            current = current.getParentNode().orElse(null);
        }
        return current == callable;
    }

    private static boolean refersToParameter(Expression expression, Parameter parameter) {
        while (expression.isEnclosedExpr()) {
            expression = expression.asEnclosedExpr().getInner();
        }
        if (!(expression instanceof NameExpr name)
                || !name.getNameAsString().equals(parameter.getNameAsString())) {
            return false;
        }
        try {
            return name.resolve().toAst().map(parameter::equals).orElse(false);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Optional<String> assignedFieldName(Expression expression) {
        while (expression.isEnclosedExpr()) {
            expression = expression.asEnclosedExpr().getInner();
        }
        if (expression instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
            return Optional.of(field.getNameAsString());
        }
        if (expression instanceof NameExpr name) {
            try {
                return name.resolve().isField() ? Optional.of(name.getNameAsString()) : Optional.empty();
            } catch (RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private InjectionResolution resolve(InjectionPoint injection, List<BeanEntry> beanEntries) {
        List<BeanEntry> assignable = beanEntries.stream()
                .filter(entry -> entry.assignableTypes().contains(injection.injectedType()))
                .toList();
        if (injection.qualifier() != null) {
            assignable = assignable.stream()
                    .filter(entry -> matchesQualifier(entry.definition(), injection.qualifier()))
                    .toList();
        }
        if (assignable.isEmpty()) {
            boolean runtimeProvided = isRuntimeProvided(injection.injectedType());
            return new InjectionResolution(
                    injection,
                    List.of(),
                    runtimeProvided ? ResolutionStatus.RUNTIME_PROVIDED : ResolutionStatus.UNRESOLVED,
                    runtimeProvided ? "runtime-provided" : "no bean candidate");
        }

        boolean containsConditional = assignable.stream()
                .anyMatch(entry -> !entry.definition().conditionTypes().isEmpty());
        List<BeanEntry> selected = assignable;
        if (!containsConditional && assignable.size() > 1) {
            List<BeanEntry> primaries = assignable.stream()
                    .filter(entry -> entry.definition().primary())
                    .toList();
            if (primaries.size() == 1) {
                selected = primaries;
            }
        }
        List<BeanCandidate> candidates = selected.stream()
                .map(entry -> new BeanCandidate(entry.definition(), List.of("spring-di")))
                .toList();
        ResolutionStatus status = selected.size() == 1 && !containsConditional
                ? ResolutionStatus.UNIQUE
                : ResolutionStatus.AMBIGUOUS;
        return new InjectionResolution(
                injection,
                candidates,
                status,
                containsConditional ? "conditional bean was not evaluated" : null);
    }

    private boolean isRuntimeProvided(String injectedType) {
        TypeInfo typeInfo = typeInfoByBinaryName.get(injectedType);
        if (typeInfo == null) {
            return false;
        }
        return typeInfo.assignableTypes().contains(SPRING_DATA_REPOSITORY)
                || typeInfo.annotationTypes().contains(SpringAnnotations.MAPPER);
    }

    private static boolean matchesQualifier(BeanDefinition bean, String qualifier) {
        return bean.qualifiers().contains(qualifier) || bean.names().contains(qualifier);
    }

    private static Set<String> assignableTypesOf(ClassOrInterfaceDeclaration type) {
        Set<String> types = new LinkedHashSet<>();
        types.add(BinaryNames.forTypeLikeNode(type));
        try {
            type.resolve().getAllAncestors().stream()
                    .map(ancestor -> ancestor.getTypeDeclaration().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(BinaryNames::forResolvedDeclaration)
                    .forEach(types::add);
        } catch (RuntimeException | LinkageError ignored) {
            // 未解決 ancestor は候補にせず、解決できた型だけを中間索引へ保持する。
        }
        return Set.copyOf(types);
    }

    private static Set<String> assignableTypesOf(ResolvedType type) {
        Set<String> types = new LinkedHashSet<>();
        types.add(BinaryNames.erasureOf(type));
        if (type.isReferenceType()) {
            try {
                ResolvedReferenceTypeDeclaration declaration = type.asReferenceType().getTypeDeclaration().orElse(null);
                if (declaration != null) {
                    declaration.getAllAncestors().stream()
                            .map(ancestor -> ancestor.getTypeDeclaration().orElse(null))
                            .filter(java.util.Objects::nonNull)
                            .map(BinaryNames::forResolvedDeclaration)
                            .forEach(types::add);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // return type 自体は保持済み。未解決 ancestor だけを除外する。
            }
        }
        return Set.copyOf(types);
    }

    private static Set<String> annotationTypesOf(ClassOrInterfaceDeclaration type) {
        Set<String> annotations = new LinkedHashSet<>();
        for (AnnotationExpr annotation : type.getAnnotations()) {
            String fqn = SpringAnnotations.fqn(annotation);
            if (fqn != null) {
                annotations.add(fqn);
            }
        }
        return Set.copyOf(annotations);
    }

    private static String beanSortKey(BeanDefinition bean) {
        return bean.beanType() + "#" + String.join(",", bean.names());
    }

    private static int lineOf(com.github.javaparser.ast.Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    private static String sourcePathOf(com.github.javaparser.ast.Node node) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toAbsolutePath().normalize().toString())
                .orElse(null);
    }
}
