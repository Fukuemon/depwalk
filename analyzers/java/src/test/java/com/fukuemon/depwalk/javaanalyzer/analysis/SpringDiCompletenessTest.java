package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring DI の索引の失敗は graph の欠けとして完全性 gate で {@code JAVA_INCOMPLETE_ANALYSIS} にし、
 * 候補のない注入点は宣言型への edge が残るため gate に入れないことを検証する。
 */
@DisplayName("Spring DI の索引の失敗と候補のない注入点の、完全性 gate (JAVA_INCOMPLETE_ANALYSIS) での扱い")
class SpringDiCompletenessTest {

    @TempDir
    Path workspace;

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Spring DI の索引で例外が起きた file があるとき、索引の失敗を details に載せて解析を fatal にする")
    void springDiIndexFailureFailsTheRequest() throws Exception {
        writeSpringDiIndexFailure();

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        assertEquals("JAVA_INCOMPLETE_ANALYSIS", errors.get(0).get("code"));
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        Map<String, Object> detail = details.stream()
                .filter(d -> "spring-di-index-failed".equals(metadataOf(d).get("reason")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a spring-di-index-failed detail: " + details));
        assertEquals("JAVA_UNRESOLVED_SYMBOL", detail.get("code"));
        assertEquals("com/example/AppConfig.java",
                ((Map<String, Object>) detail.get("sourceLocation")).get("path"));
        assertTrue(((String) metadataOf(detail).get("exceptionClass")).matches("[\\w.$]+"),
                () -> "exceptionClass must be a bare class name: " + detail);

        Map<String, Object> metadata = (Map<String, Object>) errors.get(0).get("metadata");
        Map<String, Object> reasonCounts = (Map<String, Object>) metadata.get("reasonCounts");
        assertEquals(1, ((Number) reasonCounts.get("JAVA_UNRESOLVED_SYMBOL:spring-di-index-failed")).intValue());
        assertEquals(details.size(), ((Number) metadata.get("total")).intValue());
    }

    @Test
    @DisplayName("allowIncompleteAnalysis を有効にしたとき、Spring DI の索引の失敗があっても warning を出して解析を続ける")
    void allowIncompleteAnalysisContinuesPastSpringDiIndexFailure() throws Exception {
        writeSpringDiIndexFailure();

        Map<String, Object> metadata = new LinkedHashMap<>(AnalysisTestSupport.classpathMetadata());
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(workspace, metadata, null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("error").isEmpty(), () -> "no fatal error expected: " + ran.byType("error"));
        assertTrue(ran.byType("diagnostic").stream().anyMatch(d ->
                        "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code"))
                                && "warning".equals(d.get("severity"))
                                && String.valueOf(d.get("message")).startsWith("failed to index Spring DI metadata")),
                () -> "the index failure must stay visible as a warning: " + ran.byType("diagnostic"));
        assertTrue(ran.stderr().contains("gap[JAVA_UNRESOLVED_SYMBOL:spring-di-index-failed]=1"),
                () -> "callSiteSummary must report the index failure: " + ran.stderr());
    }

    @Test
    @DisplayName("Spring Bean の候補が 0 件の注入点だけがあるとき、allowIncompleteAnalysis の指定がなくても warning を出して解析を成功させる")
    void injectionPointWithoutCandidateDoesNotFailTheRequest() throws Exception {
        write("com/example/Repo.java", """
                package com.example;
                public interface Repo {
                    String find();
                }
                """);
        write("com/example/Finder.java", """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Service;
                @Service
                public class Finder {
                    @Autowired
                    private Repo repo;
                    String use() { return repo.find(); }
                }
                """);
        write("org/springframework/beans/factory/annotation/Autowired.java", """
                package org.springframework.beans.factory.annotation;
                public @interface Autowired {
                }
                """);
        write("org/springframework/stereotype/Service.java", """
                package org.springframework.stereotype;
                public @interface Service {
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("error").isEmpty(), () -> "no fatal error expected: " + ran.byType("error"));
        assertTrue(ran.byType("diagnostic").stream().anyMatch(d ->
                        "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code"))
                                && "warning".equals(d.get("severity"))
                                && String.valueOf(d.get("message")).startsWith("no Spring Bean candidate")),
                () -> "the injection point without a candidate must stay visible as a warning: "
                        + ran.byType("diagnostic"));
        assertTrue(ran.byType("callEdge").stream().anyMatch(e ->
                        "java:com.example.Finder#use()".equals(e.get("callerMethodId"))
                                && "java:com.example.Repo#find()".equals(e.get("calleeMethodId"))),
                () -> "the edge to the declared type must remain: " + ran.byType("callEdge"));
    }

    /** {@code @Bean} の戻り型を resolve できず、Spring DI の索引が例外を投げる file を置く。 */
    private void writeSpringDiIndexFailure() throws Exception {
        write("com/example/AppConfig.java", """
                package com.example;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                @Configuration
                public class AppConfig {
                    @Bean
                    Missing missing() { return null; }
                }
                """);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataOf(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("metadata");
    }

    private void write(String relative, String source) throws Exception {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
