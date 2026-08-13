package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring DI candidate resolution keeps resolvable ancestors even when another
 * ancestor of the implementation cannot be resolved (best-effort collection,
 * ADR-0012): an impl extending an unresolvable external base must still be a
 * bean candidate of its workspace interface, including across modules.
 */
class DiCandidateAncestorTest {

    private static final String RELEASE = "17";

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
    void crossModuleImplWithUnresolvableAncestorBecomesBeanCandidate() throws Exception {
        write(domainSrc, "com/example/domain/Repo.java", """
                package com.example.domain;
                public interface Repo {
                    String find();
                }
                """);
        compile(domainClasses, List.of(), "domain-full", "com/example/domain/Repo.java",
                Files.readString(domainSrc.resolve("com/example/domain/Repo.java")));

        // The source declares an unresolvable external base; the classes are compiled
        // from a cleaned shape so SootUp can index the implementation method.
        write(infraSrc, "com/example/infra/RepoImpl.java", """
                package com.example.infra;
                import com.example.domain.Repo;
                import org.springframework.stereotype.Component;
                @Component
                public class RepoImpl extends com.missing.ExternalBase implements Repo {
                    public String find() { return "x"; }
                }
                """);
        write(infraSrc, "org/springframework/stereotype/Component.java", """
                package org.springframework.stereotype;
                public @interface Component {
                }
                """);
        compile(infraClasses, List.of(domainClasses), "infra-clean", "com/example/infra/RepoImpl.java", """
                package com.example.infra;
                import com.example.domain.Repo;
                public class RepoImpl implements Repo {
                    public String find() { return "x"; }
                }
                """);

        write(appSrc, "com/example/app/Finder.java", """
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
        write(appSrc, "org/springframework/beans/factory/annotation/Autowired.java", """
                package org.springframework.beans.factory.annotation;
                public @interface Autowired {
                }
                """);
        write(appSrc, "org/springframework/stereotype/Service.java", """
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

    private void compile(Path classesDir, List<Path> classpath, String srcDirName, String relative, String source)
            throws Exception {
        Path build = temp.resolve(srcDirName);
        write(build, relative, source);
        List<String> args = new ArrayList<>(List.of("--release", RELEASE, "-d", classesDir.toString()));
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        args.add(build.resolve(relative).toString());
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "fixture compile failed");
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
