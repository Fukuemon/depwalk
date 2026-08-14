package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring DI 候補解決の ancestor 収集が best-effort
 * (adr/0012-implicit-call-resolution-and-type-propagation-rescue.md) であることの検証。
 * 実装 class の ancestor に解決できないものが混ざっても、解決できた ancestor は
 * 生き残る: 解決不能な外部基底を extends する impl も、workspace の interface の
 * bean 候補になる (module を跨ぐ場合を含む)。
 */
@DisplayName("Spring DI 候補の ancestor 収集 (best-effort)")
class DiCandidateAncestorTest {

    @TempDir
    Path temp;

    private Path workspace;
    private Path domainSrc;
    private Path domainClasses;
    private Path infraSrc;
    private Path infraClasses;
    private Path appSrc;
    private Path appClasses;

    @BeforeEach
    void layoutWorkspace() throws Exception {
        workspace = Files.createDirectories(temp.resolve("workspace"));
        domainSrc = Files.createDirectories(workspace.resolve("domain/src"));
        domainClasses = Files.createDirectories(workspace.resolve("domain/classes"));
        infraSrc = Files.createDirectories(workspace.resolve("infra/src"));
        infraClasses = Files.createDirectories(workspace.resolve("infra/classes"));
        appSrc = Files.createDirectories(workspace.resolve("app/src"));
        appClasses = Files.createDirectories(workspace.resolve("app/classes"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("解決不能な外部基底を extends する別 module の impl でも、workspace interface の bean 候補のままになる")
    void crossModuleImplWithUnresolvableAncestorBecomesBeanCandidate() throws Exception {
        AnalysisTestSupport.writeSource(domainSrc, "com/example/domain/Repo.java", """
                package com.example.domain;
                public interface Repo {
                    String find();
                }
                """);
        AnalysisTestSupport.compileFixture(
                temp.resolve("domain-full"), domainClasses, AnalysisTestSupport.FIXTURE_RELEASE, List.of(),
                Map.of("com/example/domain/Repo.java",
                        Files.readString(domainSrc.resolve("com/example/domain/Repo.java"))));

        // source は解決不能な外部基底を宣言する。classes は基底を除いた形から compile し、
        // SootUp が実装メソッドを index できる状態にする。
        AnalysisTestSupport.writeSource(infraSrc, "com/example/infra/RepoImpl.java", """
                package com.example.infra;
                import com.example.domain.Repo;
                import org.springframework.stereotype.Component;
                @Component
                public class RepoImpl extends com.missing.ExternalBase implements Repo {
                    public String find() { return "x"; }
                }
                """);
        AnalysisTestSupport.writeSource(infraSrc, "org/springframework/stereotype/Component.java", """
                package org.springframework.stereotype;
                public @interface Component {
                }
                """);
        AnalysisTestSupport.compileFixture(
                temp.resolve("infra-clean"), infraClasses, AnalysisTestSupport.FIXTURE_RELEASE,
                List.of(domainClasses),
                Map.of("com/example/infra/RepoImpl.java", """
                        package com.example.infra;
                        import com.example.domain.Repo;
                        public class RepoImpl implements Repo {
                            public String find() { return "x"; }
                        }
                        """));

        AnalysisTestSupport.writeSource(appSrc, "com/example/app/Finder.java", """
                package com.example.app;
                import com.example.domain.Repo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Service;
                @Service
                public class Finder {
                    @Autowired
                    private Repo repo;
                    String use() { return repo.find(); }
                }
                """);
        AnalysisTestSupport.writeSource(appSrc, "org/springframework/beans/factory/annotation/Autowired.java", """
                package org.springframework.beans.factory.annotation;
                public @interface Autowired {
                }
                """);
        AnalysisTestSupport.writeSource(appSrc, "org/springframework/stereotype/Service.java", """
                package org.springframework.stereotype;
                public @interface Service {
                }
                """);

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(
                workspace, projects(), Map.of("allowIncompleteAnalysis", List.of("true")));

        assertTrue(ran.byType("diagnostic").stream().noneMatch(diagnostic ->
                        String.valueOf(diagnostic.get("message")).startsWith("no Spring Bean candidate")),
                "resolvable ancestors must survive an unresolvable one: " + ran.byType("diagnostic"));
        Map<String, Object> candidateEdge = ran.byType("callEdge").stream()
                .filter(edge -> "java:com.example.app.Finder#use()".equals(edge.get("callerMethodId"))
                        && "java:com.example.infra.RepoImpl#find()".equals(edge.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "candidate edge to the cross-module impl is missing: " + ran.byType("callEdge")));
        Map<String, Object> metadata = (Map<String, Object>) candidateEdge.get("metadata");
        assertTrue(((List<String>) metadata.get("provenance")).contains("spring-di"), metadata.toString());
    }

    private List<MultiContextAnalysisTestSupport.Project> projects() {
        MultiContextAnalysisTestSupport.Project domain = new MultiContextAnalysisTestSupport.Project(
                ":domain", workspace.resolve("domain"),
                List.of(domainSrc), List.of(), List.of(domainClasses), List.of());
        MultiContextAnalysisTestSupport.Project infra = new MultiContextAnalysisTestSupport.Project(
                ":infra", workspace.resolve("infra"),
                List.of(infraSrc), List.of(domainClasses), List.of(infraClasses), List.of(":domain"));
        MultiContextAnalysisTestSupport.Project app = new MultiContextAnalysisTestSupport.Project(
                ":app", workspace.resolve("app"),
                List.of(appSrc), List.of(domainClasses, infraClasses), List.of(appClasses),
                List.of(":domain", ":infra"));
        return List.of(domain, infra, app);
    }
}
