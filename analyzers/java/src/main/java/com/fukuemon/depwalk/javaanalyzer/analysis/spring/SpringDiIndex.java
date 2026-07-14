package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring Bean 定義と constructor / field / setter injection を source から収集し、P3 が
 * call edge metadata へ変換できる中間解決結果を構築する。Spring context は起動しない。
 */
public final class SpringDiIndex {

    public enum BeanKind {
        STEREOTYPE,
        FACTORY_METHOD
    }

    public enum InjectionKind {
        CONSTRUCTOR,
        FIELD,
        SETTER
    }

    public enum ResolutionStatus {
        UNIQUE,
        AMBIGUOUS,
        UNRESOLVED,
        RUNTIME_PROVIDED
    }

    public record BeanDefinition(
            String beanType,
            List<String> names,
            List<String> qualifiers,
            boolean primary,
            List<String> conditionTypes,
            BeanKind kind,
            String declaringType,
            String factoryMethodName) {
        public BeanDefinition {
            names = List.copyOf(names);
            qualifiers = List.copyOf(qualifiers);
            conditionTypes = List.copyOf(conditionTypes);
        }
    }

    public record InjectionPoint(
            String ownerType,
            InjectionKind kind,
            String targetName,
            String injectedType,
            String qualifier,
            boolean bytecodeGenerated,
            int sourceLine) {
    }

    public record BeanCandidate(BeanDefinition bean, List<String> provenance) {
        public BeanCandidate {
            provenance = List.copyOf(provenance);
        }
    }

    public record InjectionResolution(
            InjectionPoint injectionPoint,
            List<BeanCandidate> candidates,
            ResolutionStatus status,
            String reason) {
        public InjectionResolution {
            candidates = List.copyOf(candidates);
        }
    }

    public record Result(
            List<BeanDefinition> beans,
            List<InjectionPoint> injections,
            List<InjectionResolution> resolutions) {
        public Result {
            beans = List.copyOf(beans);
            injections = List.copyOf(injections);
            resolutions = List.copyOf(resolutions);
        }
    }

    private record BeanEntry(BeanDefinition definition, Set<String> assignableTypes) {
    }

    private record TypeInfo(
            ClassOrInterfaceDeclaration declaration,
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

    public static SpringDiIndex create(SootUpTypeHierarchyIndex sootUpIndex) {
        return new SpringDiIndex(sootUpIndex);
    }

    /** AST を保持せず、1 compilation unit 分の compact な索引情報だけを追加する。 */
    public void accept(CompilationUnit unit) {
        List<ClassOrInterfaceDeclaration> types = unit.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration type : types) {
            String binaryName = BinaryNames.forTypeLikeNode(type);
            typeInfoByBinaryName.put(binaryName, new TypeInfo(
                    type,
                    assignableTypesOf(type),
                    annotationTypesOf(type)));
        }
        beanEntries.addAll(collectBeans(types));
        injectionPoints.addAll(collectInjections(types));
    }

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
        String qualifier = SpringAnnotations.qualifier(method);
        BeanDefinition definition = new BeanDefinition(
                beanType,
                names,
                qualifier == null ? List.of() : List.of(qualifier),
                SpringAnnotations.has(method, SpringAnnotations.PRIMARY),
                SpringAnnotations.conditionTypes(method),
                BeanKind.FACTORY_METHOD,
                BinaryNames.forTypeLikeNode(configuration),
                method.getNameAsString());
        return new BeanEntry(definition, assignableTypesOf(returnType));
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
        List<ConstructorDeclaration> selected = constructors.stream()
                .filter(constructor -> SpringAnnotations.has(constructor, SpringAnnotations.AUTOWIRED))
                .toList();
        if (selected.isEmpty() && constructors.size() == 1) {
            selected = constructors;
        }
        for (ConstructorDeclaration constructor : selected) {
            for (Parameter parameter : constructor.getParameters()) {
                injections.add(injectionPoint(
                        type,
                        InjectionKind.CONSTRUCTOR,
                        parameter.getNameAsString(),
                        parameter.getType().resolve(),
                        SpringAnnotations.qualifier(parameter),
                        false,
                        lineOf(parameter)));
            }
        }
        if (constructors.isEmpty()) {
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
        return new InjectionPoint(
                BinaryNames.forTypeLikeNode(owner),
                kind,
                targetName,
                BinaryNames.erasureOf(injectedType),
                qualifier,
                bytecodeGenerated,
                sourceLine);
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
        } catch (RuntimeException ignored) {
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
            } catch (RuntimeException ignored) {
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
}
