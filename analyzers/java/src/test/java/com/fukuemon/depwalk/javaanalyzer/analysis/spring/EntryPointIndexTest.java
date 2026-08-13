package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryPointIndexTest {

    @Test
    void detectsKnownEntryPointAnnotationsFromExplicitImports() {
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import jakarta.annotation.PostConstruct;
                import javax.annotation.PreDestroy;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.ModelAttribute;

                class Jobs {
                    @PostConstruct void init() { }
                    @PreDestroy void shutdown() { }
                    @Scheduled(cron = "0 0 * * * *") void nightly() { }
                    @GetMapping("/items") void list() { }
                    @ExceptionHandler void onError() { }
                    @ModelAttribute void common() { }
                    void plain() { }
                }
                """);
        EntryPointIndex index = new EntryPointIndex();
        index.accept(unit);
        ClassOrInterfaceDeclaration type = unit.getClassByName("Jobs").orElseThrow();

        assertEquals(List.of("jakarta.annotation.PostConstruct"), entryPointsOf(index, type, "init"));
        assertEquals(List.of("javax.annotation.PreDestroy"), entryPointsOf(index, type, "shutdown"));
        assertEquals(
                List.of("org.springframework.scheduling.annotation.Scheduled"),
                entryPointsOf(index, type, "nightly"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.GetMapping"),
                entryPointsOf(index, type, "list"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.ExceptionHandler"),
                entryPointsOf(index, type, "onError"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.ModelAttribute"),
                entryPointsOf(index, type, "common"));
        assertTrue(entryPointsOf(index, type, "plain").isEmpty());
    }

    @Test
    void detectsEntryPointAnnotationsFromWildcardImports() {
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import org.springframework.web.bind.annotation.*;

                class Api {
                    @PostMapping("/items") void create() { }
                }
                """);
        EntryPointIndex index = new EntryPointIndex();
        index.accept(unit);
        ClassOrInterfaceDeclaration type = unit.getClassByName("Api").orElseThrow();

        assertEquals(
                List.of("org.springframework.web.bind.annotation.PostMapping"),
                entryPointsOf(index, type, "create"));
    }

    @Test
    void sortsAndDeduplicatesMultipleMarkers() {
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;

                class Api {
                    @RequestMapping("/x")
                    @GetMapping("/x")
                    void both() { }
                }
                """);
        EntryPointIndex index = new EntryPointIndex();
        index.accept(unit);
        ClassOrInterfaceDeclaration type = unit.getClassByName("Api").orElseThrow();

        assertEquals(
                List.of(
                        "org.springframework.web.bind.annotation.GetMapping",
                        "org.springframework.web.bind.annotation.RequestMapping"),
                entryPointsOf(index, type, "both"));
    }

    private static List<String> entryPointsOf(
            EntryPointIndex index, ClassOrInterfaceDeclaration type, String methodName) {
        MethodDeclaration method = type.getMethodsByName(methodName).get(0);
        return index.entryPointsOf(method);
    }
}
