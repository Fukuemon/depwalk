package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.fukuemon.depwalk.javaanalyzer.analysis.TypeSolverFactory;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringDiIndexTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/spring-di");

    @TempDir
    Path tempDir;

    private SpringDiIndex.Result result;

    @BeforeEach
    void analyzeFixture() throws Exception {
        Path classesDir = compileLombokFixture();
        ParserConfiguration.LanguageLevel languageLevel = ParserConfiguration.LanguageLevel.JAVA_25;
        var typeSolver = TypeSolverFactory.create(FIXTURE, List.of(classesDir.toString()), languageLevel);
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(languageLevel)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver)));
        List<CompilationUnit> units = new ArrayList<>();
        try (var files = Files.walk(FIXTURE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                units.add(parser.parse(file).getResult().orElseThrow());
            }
        }
        SpringDiIndex index = SpringDiIndex.create(
                SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString())));
        units.forEach(index::accept);
        result = index.build();
    }

    @Test
    void collectsStereotypeAndBeanMethodNamesAliasesAndQualifiers() {
        SpringDiIndex.BeanDefinition defaultNamed = bean("com.example.URLService");
        assertEquals(List.of("URLService"), defaultNamed.names());

        SpringDiIndex.BeanDefinition explicitlyNamed = bean("com.example.ExplicitService");
        assertEquals(List.of("explicitService"), explicitlyNamed.names());

        SpringDiIndex.BeanDefinition factory = result.beans().stream()
                .filter(bean -> bean.names().contains("factoryService"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("factoryService", "factoryAlias"), factory.names());
        assertEquals(List.of("factoryQualifier"), factory.qualifiers());
        assertEquals("com.example.NameQualifiedService", factory.implementationType());

        SpringDiIndex.BeanDefinition defaultFactory = result.beans().stream()
                .filter(bean -> bean.names().contains("defaultFactory"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("defaultFactory"), defaultFactory.names());

        SpringDiIndex.BeanDefinition conditionalFactory = result.beans().stream()
                .filter(bean -> bean.names().contains("conditionalFactory"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty"),
                conditionalFactory.conditionTypes());

        SpringDiIndex.BeanDefinition lambdaFactory = result.beans().stream()
                .filter(bean -> bean.names().contains("lambdaFactory"))
                .findFirst()
                .orElseThrow();
        assertEquals("java.util.function.Supplier", lambdaFactory.implementationType());
    }

    @Test
    void detectsConstructorFieldSetterAndLombokGeneratedConstructorInjection() {
        assertEquals(SpringDiIndex.InjectionKind.CONSTRUCTOR, injection("com.example.ConstructorConsumer", "service").kind());
        assertEquals(SpringDiIndex.InjectionKind.FIELD, injection("com.example.FieldConsumer", "service").kind());
        assertEquals(SpringDiIndex.InjectionKind.SETTER, injection("com.example.SetterConsumer", "service").kind());

        SpringDiIndex.InjectionPoint lombok = injection("com.example.LombokConsumer", "lombokService");
        assertEquals(SpringDiIndex.InjectionKind.CONSTRUCTOR, lombok.kind());
        assertTrue(lombok.bytecodeGenerated());

        SpringDiIndex.InjectionPoint allArgs = injection("com.example.LombokAllArgsConsumer", "mutableService");
        assertEquals(SpringDiIndex.InjectionKind.CONSTRUCTOR, allArgs.kind());
        assertTrue(allArgs.bytecodeGenerated());
    }

    @Test
    void appliesQualifierAgainstQualifierBeanNameAndAlias() {
        assertUnique("byQualifier", "com.example.ValueQualifiedService");
        assertUnique("byBeanName", "com.example.NameQualifiedService");
        SpringDiIndex.InjectionResolution alias = resolution("byAlias");
        assertEquals(SpringDiIndex.ResolutionStatus.UNIQUE, alias.status());
        assertTrue(alias.candidates().get(0).bean().names().contains("factoryAlias"));
        assertEquals(SpringDiIndex.ResolutionStatus.UNRESOLVED, resolution("qualifierMissing").status());
    }

    @Test
    void appliesSinglePrimaryAndKeepsMultipleOrUnspecifiedCandidatesAmbiguous() {
        assertUnique("primarySelected", "com.example.PrimarySelected");

        SpringDiIndex.InjectionResolution multiplePrimary = resolution("multiplePrimary");
        assertEquals(SpringDiIndex.ResolutionStatus.AMBIGUOUS, multiplePrimary.status());
        assertEquals(2, multiplePrimary.candidates().size());

        SpringDiIndex.InjectionResolution unspecified = resolution("unspecifiedMultiple");
        assertEquals(SpringDiIndex.ResolutionStatus.AMBIGUOUS, unspecified.status());
        assertTrue(unspecified.candidates().size() >= 2);
    }

    @Test
    void keepsSelectedConditionalCandidateAmbiguousWithoutEvaluatingCondition() {
        SpringDiIndex.InjectionResolution conditional = resolution("conditionalOnly");

        assertEquals(SpringDiIndex.ResolutionStatus.AMBIGUOUS, conditional.status());
        assertEquals(1, conditional.candidates().size());
        assertEquals(
                List.of("org.springframework.context.annotation.Profile"),
                conditional.candidates().get(0).bean().conditionTypes());

        assertUnique("conditionalWithPrimary", "com.example.ConditionalPrimary");
    }

    @Test
    void distinguishesRuntimeProvidedSpringDataAndMyBatisFromUnresolvedInterface() {
        assertEquals(SpringDiIndex.ResolutionStatus.RUNTIME_PROVIDED, resolution("springDataRepo").status());
        assertEquals(SpringDiIndex.ResolutionStatus.RUNTIME_PROVIDED, resolution("myBatisRepo").status());
        assertEquals(SpringDiIndex.ResolutionStatus.UNRESOLVED, resolution("plainRepo").status());
        assertFalse(result.beans().stream().anyMatch(bean -> bean.beanType().contains("SpringDataRepoImpl")));
    }

    private SpringDiIndex.BeanDefinition bean(String beanType) {
        return result.beans().stream().filter(bean -> beanType.equals(bean.beanType())).findFirst().orElseThrow();
    }

    private SpringDiIndex.InjectionPoint injection(String ownerType, String targetName) {
        return result.injections().stream()
                .filter(injection -> ownerType.equals(injection.ownerType()) && targetName.equals(injection.targetName()))
                .findFirst()
                .orElseThrow();
    }

    private SpringDiIndex.InjectionResolution resolution(String targetName) {
        return result.resolutions().stream()
                .filter(resolution -> targetName.equals(resolution.injectionPoint().targetName()))
                .findFirst()
                .orElseThrow();
    }

    private void assertUnique(String targetName, String beanType) {
        SpringDiIndex.InjectionResolution resolution = resolution(targetName);
        assertEquals(SpringDiIndex.ResolutionStatus.UNIQUE, resolution.status());
        assertEquals(List.of(beanType), resolution.candidates().stream()
                .map(candidate -> candidate.bean().beanType())
                .toList());
        assertEquals(List.of("spring-di"), resolution.candidates().get(0).provenance());
    }

    private Path compileLombokFixture() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        String lombokJar = lombokJar();
        int exit = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "--release",
                "21",
                "-parameters",
                "-classpath",
                lombokJar,
                "-processorpath",
                lombokJar,
                "-d",
                classesDir.toString(),
                FIXTURE.resolve("org/springframework/stereotype/Component.java").toString(),
                FIXTURE.resolve("com/example/LombokContract.java").toString(),
                FIXTURE.resolve("com/example/LombokConsumer.java").toString(),
                FIXTURE.resolve("com/example/LombokAllArgsConsumer.java").toString());
        assertEquals(0, exit);
        return classesDir;
    }

    private static String lombokJar() throws URISyntaxException {
        return Path.of(RequiredArgsConstructor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }
}
