package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * spec #27 ⑥ (P3_01 承認規則): receiver 型が取れない call の external-target
 * 分類。(i) chain 起点遡及 — 起点の静的型が scope 外なら後続 call を external
 * へ、scope 内型が現れたら diagnostic のまま。(ii) lambda parameter — 引数先
 * functional interface が scope 外型なら external へ。
 */
class ExternalChainClassificationTest {

    @TempDir
    Path temp;

    @Test
    void chainWithExternalRootIsClassifiedExternal() throws Exception {
        // Ext に存在しない make1() とその後続 text() は解決できない。chain 起点
        // e (Ext = scope 外 classes-only 型) により、両 call とも external-target
        // 除外になる (make1 は既存 D18 経路、text は chain 起点遡及規則)。
        Path classes = compile("ext-src", "ext-classes",
                Map.of("com/example/ext/Ext.java", """
                        package com.example.ext;
                        public class Ext {
                            public Ext make() { return this; }
                        }
                        """));

        Path workspace = temp.resolve("chain-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.Ext;
                public class Caller {
                    String use(Ext e) { return e.make1().text(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("diagnostic").isEmpty(),
                () -> "chain with an external root must be excluded, not diagnosed: " + ran.byType("diagnostic"));
        assertTrue(ran.stderr().contains("excluded[external-target]=2"),
                () -> "both chain links must be external-target: " + ran.stderr());
    }

    @SuppressWarnings("unchecked")
    @Test
    void chainWithInScopeRootStaysDiagnostic() throws Exception {
        // Holder は scope 内 source 型。bytecode-only の make() は救済されるが、
        // 戻り値型 Dep が classpath に無いため後続 text() は解決できない。
        // chain 起点 h (Holder = scope 内) のため external へ倒さず diagnostic に残す。
        Path classes = compile("holder-src", "holder-classes",
                Map.of(
                        "com/example/Dep.java", """
                                package com.example;
                                public class Dep {
                                    public String text() { return "x"; }
                                }
                                """,
                        "com/example/Holder.java", """
                                package com.example;
                                public class Holder {
                                    public Dep make() { return null; }
                                }
                                """));
        Files.delete(classes.resolve("com/example/Dep.class"));

        Path workspace = temp.resolve("holder-workspace");
        write(workspace, "com/example/Holder.java", """
                package com.example;
                public class Holder {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    String use(Holder h) { return h.make().text(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(1, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<String, Object>) d.get("metadata")).get("target"))),
                () -> "in-scope chain root must keep the diagnostic for text(): " + details);
    }

    @Test
    void lambdaParamOfExternalFunctionalInterfaceIsClassifiedExternal() throws Exception {
        // scope 外 (classes-only) API の generic functional 引数では lambda
        // parameter の型変数を推論できず、後続 call が解決できない。引数先
        // (ExtApi = scope 外) により external-target 除外になる。
        Path classes = compile("extfn-src", "extfn-classes",
                Map.of("com/example/ext/ExtApi.java", """
                        package com.example.ext;
                        public final class ExtApi {
                            private ExtApi() {}
                            public static <T> void each(java.util.function.Consumer<T> fn) {}
                        }
                        """));

        Path workspace = temp.resolve("lambda-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.ExtApi;
                public class Caller {
                    void run() {
                        ExtApi.each(value -> value.text());
                    }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.stderr().contains("excluded[external-target]="),
                () -> "lambda param call must be excluded as external-target: " + ran.stderr());
    }

    @SuppressWarnings("unchecked")
    @Test
    void lambdaParamOfInScopeFunctionalInterfaceStaysDiagnostic() throws Exception {
        // scope 内 functional interface (引数型が classpath に無く param 型不明) の
        // lambda は、受け手が暗黙 this (scope 内) のため diagnostic に残す。
        Path workspace = temp.resolve("infn-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    interface MyFn {
                        void apply(Missing value);
                    }
                    void accept(MyFn fn) {}
                    void run() {
                        accept(value -> value.mystery());
                    }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        assertTrue(details.stream().anyMatch(d ->
                        "mystery".equals(((Map<String, Object>) d.get("metadata")).get("target"))),
                () -> "in-scope functional interface must keep the diagnostic: " + details);
    }

    private Path compile(String srcDir, String classesDir, Map<String, String> sources) throws Exception {
        Path build = temp.resolve(srcDir);
        List<String> files = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            write(build, entry.getKey(), entry.getValue());
            files.add(build.resolve(entry.getKey()).toString());
        }
        Path classes = temp.resolve(classesDir);
        Files.createDirectories(classes);
        List<String> args = new java.util.ArrayList<>(List.of("--release", "17", "-d", classes.toString()));
        args.addAll(files);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "fixture compile failed");
        return classes;
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
