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
 * java-analyzer feature doc「Parse・resolution・call 完全性」: receiver 型が取れない
 * call の external-target 分類。(i) chain 起点遡及 — 起点の静的型が scope 外なら後続 call を external
 * へ、scope 内型が現れたら diagnostic のまま。(ii) lambda parameter — 引数先
 * functional interface が scope 外型なら external へ。
 */
class ExternalChainClassificationTest {

    @TempDir
    Path temp;

    @Test
    void chainWithExternalRootIsClassifiedExternal() throws Exception {
        // Ext.make() は自己境界 generic 戻り値型 (<T extends Ext> T) で、target-type
        // context が無い chain 途中の呼び出しでは JavaParser が解決に失敗する
        // (実測パターンと同型: メソッド自体は classfile 上に実在する)。chain 起点
        // e (Ext = scope 外 classes-only 型) から、forwardVerifyExternalChain が
        // make() を classfile 上で一意に確認できるため、両 call とも
        // external-target 除外になる (multi-agent review 指摘反映: 2026-07-22。
        // 存在しないメソッド名で解決失敗を模擬すると forward 検証が前進できず
        // 別の分類になるため、実在するメソッドで解決だけが失敗する形にした)。
        Path classes = compile("ext-src", "ext-classes",
                Map.of("com/example/ext/Ext.java", """
                        package com.example.ext;
                        public class Ext {
                            @SuppressWarnings("unchecked")
                            public <T extends Ext> T make() {
                                return (T) this;
                            }
                            public String text() { return "x"; }
                        }
                        """));

        Path workspace = temp.resolve("chain-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.Ext;
                public class Caller {
                    String use(Ext e) { return e.make().text(); }
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
    void chainWithExternalRootStaysDiagnosticWhenIntermediateLinkNotOnClasspath() throws Exception {
        // multi-agent review 指摘反映 (2026-07-22): root が external というだけで
        // 中間 link を無条件に external とみなさない。intermediate link
        // (missingLink) が Ext の classfile に存在しない場合、forward 検証が前進
        // できず diagnostic を維持することを確認する (false exclusion の回帰ガード)。
        Path classes = compile("ext2-src", "ext2-classes",
                Map.of("com/example/ext/Ext.java", """
                        package com.example.ext;
                        public class Ext {
                        }
                        """));

        Path workspace = temp.resolve("chain-unknown-link-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.Ext;
                public class Caller {
                    String use(Ext e) { return e.missingLink().text(); }
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
                () -> "unverifiable intermediate link must keep text() as a diagnostic, not silently excluded: "
                        + details);
    }

    @SuppressWarnings("unchecked")
    @Test
    void chainWithInScopeRootStaysDiagnosticWhenForwardResolutionIsAmbiguous() throws Exception {
        // Holder は scope 内 source 型。make(String) / make(Integer) の同名同
        // arity overload により bytecode 候補が一意に決まらず、chain の前進解決
        // は行えない。chain 起点 h (Holder = scope 内) のため external へ
        // 倒さず、make / text とも diagnostic に残す (保守側)。
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
                                    public Dep make(String key) { return null; }
                                    public Dep make(Integer key) { return null; }
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
                    String use(Holder h) { return h.make("k").text(); }
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
                () -> "ambiguous forward resolution must keep the diagnostic for text(): " + details);
    }

    @SuppressWarnings("unchecked")
    @Test
    void lambdaPassedToExternalMethodStaysDiagnosticEvenWhenReceiverIsExternal() throws Exception {
        // PR review 指摘反映 (2026-07-22, 規則 (ii) 撤回の回帰ガード): 受け手
        // method (ExtApi.each) の receiver 型が external (classes-only) でも、
        // それだけを根拠に lambda parameter (value) の型を external と決め付け
        // ない。value の実際の型 (functional interface の型引数) は不明なため、
        // 保守的に diagnostic を維持する (in-scope 型を引数に取る external API
        // の呼び出しで false exclusion を起こさないことの確認)。
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

        assertEquals(1, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<String, Object>) d.get("metadata")).get("target"))),
                () -> "lambda param passed to an external method must keep the diagnostic for text(): " + details);
    }

    @SuppressWarnings("unchecked")
    @Test
    void lambdaAssignedToExternalFunctionalInterfaceIsClassifiedExternal() throws Exception {
        // 規則 (ii) の維持範囲: lambda 自体が代入される変数の宣言型
        // (functional interface 型そのもの) が external なら、lambda parameter
        // の型もその関数型が定義する型引数で決まるため、external 判定の根拠として
        // 使ってよい (受け手 method の receiver 型を見る旧経路とは異なり、
        // functional interface 自体の宣言を直接見ている)。
        Path classes = compile("extfn2-src", "extfn2-classes",
                Map.of("com/example/ext/ExtConsumer.java", """
                        package com.example.ext;
                        public interface ExtConsumer<T> {
                            void accept(T value);
                        }
                        """));

        Path workspace = temp.resolve("lambda-assign-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.ExtConsumer;
                public class Caller {
                    void run() {
                        ExtConsumer<Object> fn = value -> value.text();
                    }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.stderr().contains("excluded[external-target]="),
                () -> "lambda assigned to an external functional interface type must be excluded: " + ran.stderr());
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

    @SuppressWarnings("unchecked")
    @Test
    void forwardResolvesChainThroughBytecodeReturnTypes() throws Exception {
        // scope 内 chain の各 link を classfile の戻り値型で前進
        // 解決する。Holder.make() / Dep.text() とも source に無い bytecode-only
        // member で、make の戻り値型 Dep (classfile 由来) から text() を救済する。
        Path classes = compile("fwd-src", "fwd-classes",
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

        Path workspace = temp.resolve("fwd-workspace");
        write(workspace, "com/example/Dep.java", """
                package com.example;
                public class Dep {
                }
                """);
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

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.Dep#text()".equals(e.get("calleeMethodId"))),
                () -> "chain forward resolution must rescue text(): " + edges);
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
