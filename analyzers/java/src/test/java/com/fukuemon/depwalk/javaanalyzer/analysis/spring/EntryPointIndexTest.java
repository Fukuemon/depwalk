package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("メソッド annotation からの入口 (entry point) 検出")
class EntryPointIndexTest {

    @Test
    @DisplayName("明示 import された既知の入口 annotation を FQN として検出し、annotation の無いメソッドは入口にしない")
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
    @DisplayName("event listener 系と残りの web mapping 系の annotation も、入口として検出する")
    void detectsListenerAndRemainingWebAnnotations() {
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import org.springframework.context.event.EventListener;
                import org.springframework.transaction.event.TransactionalEventListener;
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.PatchMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.RequestMapping;

                class Handlers {
                    @EventListener void onEvent() { }
                    @TransactionalEventListener void afterCommit() { }
                    @RequestMapping("/x") void root() { }
                    @PutMapping("/x") void put() { }
                    @DeleteMapping("/x") void delete() { }
                    @PatchMapping("/x") void patch() { }
                }
                """);
        EntryPointIndex index = new EntryPointIndex();
        index.accept(unit);
        ClassOrInterfaceDeclaration type = unit.getClassByName("Handlers").orElseThrow();

        assertEquals(
                List.of("org.springframework.context.event.EventListener"),
                entryPointsOf(index, type, "onEvent"));
        assertEquals(
                List.of("org.springframework.transaction.event.TransactionalEventListener"),
                entryPointsOf(index, type, "afterCommit"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.RequestMapping"),
                entryPointsOf(index, type, "root"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.PutMapping"),
                entryPointsOf(index, type, "put"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.DeleteMapping"),
                entryPointsOf(index, type, "delete"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.PatchMapping"),
                entryPointsOf(index, type, "patch"));
    }

    @Test
    @DisplayName("javax と jakarta の wildcard import が両方あり simple name の FQN を 1 つに確定できないとき、保守的に何も検出しない")
    void ambiguousWildcardImportsAcrossJavaxAndJakartaDetectNothing() {
        // wildcard import の復元は曖昧な候補を拒否する: javax と jakarta の star
        // import が両方あると simple name が既知 FQN 2 つに対応するため、標識も
        // 診断も出さない。この保守的な挙動を固定する。
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import jakarta.annotation.*;
                import javax.annotation.*;

                class Ambiguous {
                    @PostConstruct void init() { }
                }
                """);
        EntryPointIndex index = new EntryPointIndex();
        index.accept(unit);
        ClassOrInterfaceDeclaration type = unit.getClassByName("Ambiguous").orElseThrow();

        assertTrue(entryPointsOf(index, type, "init").isEmpty());
    }

    @Test
    @DisplayName("wildcard import 経由の場合でも、既知の入口 annotation を FQN へ復元して検出する")
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
    @DisplayName("複数の入口 annotation が付いたメソッドでは、検出結果が sort され重複なく返る")
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
