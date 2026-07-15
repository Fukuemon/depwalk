package com.fukuemon.depwalk.javaanalyzer.analysis.sootup;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SootUpTypeHierarchyIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsViewLazilyAndIndexesConcreteOverrideCandidates() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        assertFalse(index.isInitialized());

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.Shape", "draw", List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(
                        new SootUpTypeHierarchyIndex.MethodCandidate(
                                "com.example.BaseShape", "draw", List.of()),
                        new SootUpTypeHierarchyIndex.MethodCandidate(
                                "com.example.Circle", "draw", List.of())),
                resolution.candidates());
        assertTrue(index.isInitialized());
    }

    @Test
    void readsConstructorGeneratedByLombokFromBytecode() throws Exception {
        Path classesDir = compileFixture("sootup-lombok", true);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution = index.resolveConstructors("com.example.LombokService");

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.LombokService", "<init>", List.of("com.example.Shape"))),
                resolution.candidates());
    }

    @Test
    void readsInterfaceImplementationsFromDependencyJar() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        Path jar = createJar(classesDir);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(jar.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.Shape", "draw", List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(2, resolution.candidates().size());
    }

    @Test
    void indexesInterfaceDefaultMethodAsItsDeclaredTarget() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.DefaultShape", "draw", List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.DefaultShape", "draw", List.of())),
                resolution.candidates());
    }

    @Test
    void keepsInheritedDefaultMethodWhenReceiverIsAChildInterface() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution = index.resolveMethod(
                "com.example.DefaultShape",
                "com.example.ChildDefaultShape",
                "draw",
                List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.DefaultShape", "draw", List.of())),
                resolution.candidates());
    }

    @Test
    void indexesOverrideOfConcreteBaseMethod() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.ConcreteBase", "execute", List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.ConcreteChild", "execute", List.of())),
                resolution.candidates());
    }

    @Test
    void limitsCandidatesToImplementationsOfTheStaticReceiverType() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution resolution = index.resolveMethod(
                "com.example.RootRenderer",
                "com.example.NarrowRenderer",
                "render",
                List.of());

        assertTrue(resolution.isAvailable(), resolution.unavailableReason());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.NarrowRendererImpl", "render", List.of())),
                resolution.candidates());
    }

    @Test
    void resolvesEffectiveMethodForConcreteSpringBeanIncludingInterfaceDefault() throws Exception {
        Path classesDir = compileFixture("sootup-dispatch", false);
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(classesDir.toString()));

        SootUpTypeHierarchyIndex.Resolution override =
                index.resolveImplementationMethod("com.example.ConcreteChild", "execute", List.of());
        SootUpTypeHierarchyIndex.Resolution defaultMethod =
                index.resolveImplementationMethod("com.example.DefaultShapeImpl", "draw", List.of());

        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.ConcreteChild", "execute", List.of())),
                override.candidates());
        assertEquals(
                List.of(new SootUpTypeHierarchyIndex.MethodCandidate(
                        "com.example.DefaultShape", "draw", List.of())),
                defaultMethod.candidates());
    }

    @Test
    void returnsUnavailableInsteadOfThrowingWhenProjectClassIsAbsent() {
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of());

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.Missing", "run", List.of());

        assertFalse(resolution.isAvailable());
        assertTrue(resolution.unavailableReason().contains("com.example.Missing"));
    }

    @Test
    void returnsUnavailableInsteadOfThrowingForUnreadableClassFile() throws Exception {
        Path classFile = tempDir.resolve("broken/com/example/Broken.class");
        Files.createDirectories(classFile.getParent());
        Files.writeString(classFile, "not bytecode");
        SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(List.of(tempDir.resolve("broken").toString()));

        SootUpTypeHierarchyIndex.Resolution resolution =
                index.resolveMethod("com.example.Broken", "run", List.of());

        assertFalse(resolution.isAvailable());
        assertTrue(resolution.unavailableReason().contains("com.example.Broken"));
    }

    @Test
    void returnsUnavailableInsteadOfPropagatingClasspathLinkageError() {
        SootUpTypeHierarchyIndex.Resolution resolution = SootUpTypeHierarchyIndex.guardQuery(
                "com.example.Dependent",
                () -> {
                    throw new NoClassDefFoundError("com/example/MissingDependency");
                });

        assertFalse(resolution.isAvailable());
        assertTrue(resolution.unavailableReason().contains("com.example.Dependent"));
        assertTrue(resolution.unavailableReason().contains("com/example/MissingDependency"));
    }

    private Path compileFixture(String fixtureName, boolean enableLombok) throws Exception {
        Path sourceRoot = Path.of(getClass().getResource("/fixtures/" + fixtureName).toURI());
        Path classesDir = tempDir.resolve(fixtureName + "-classes");
        Files.createDirectories(classesDir);
        List<String> sources;
        try (var stream = Files.walk(sourceRoot)) {
            sources = stream.filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var args = new java.util.ArrayList<String>();
        args.addAll(List.of("--release", "21", "-d", classesDir.toString()));
        if (enableLombok) {
            String lombokJar = lombokJar();
            args.addAll(List.of("-classpath", lombokJar, "-processorpath", lombokJar));
        }
        args.addAll(sources);
        assertEquals(0, compiler.run(null, null, null, args.toArray(String[]::new)));
        return classesDir;
    }

    private static String lombokJar() throws URISyntaxException {
        return Path.of(RequiredArgsConstructor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }

    private Path createJar(Path classesDir) throws Exception {
        Path jar = tempDir.resolve("dependency.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var stream = Files.walk(classesDir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }
}
