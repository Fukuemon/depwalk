package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
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
 * receiver の型が取れない呼び出しを external とみなす根拠は 2 つに限る。
 * chain の起点の静的型が scope 外であること (途中で scope 内の型が現れたら diagnostic のまま残す) と、
 * lambda を代入した先の functional interface が scope 外の型であることである。
 */
@DisplayName("receiver 型が取れない呼び出しの external 分類 (chain 起点の遡及と lambda 引数)")
class ExternalChainClassificationTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("chain の起点が scope 外型のとき、解決に失敗した後続の呼び出しも external として除外され、診断は出ない")
    void chainWithExternalRootIsClassifiedExternal() throws Exception {
        // Ext.make() の戻り値は自己境界の generic (<T extends Ext> T) なので、target type のない
        // chain の途中では JavaParser が解決に失敗する。存在しないメソッド名で失敗を模すと、
        // classfile での前進の検証が進めず別の分類になるので、classfile に実在するメソッドで
        // 解決だけが失敗する形にする。
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

    @Test
    @DisplayName("chain の起点が scope 外型でも、途中の呼び出しを classfile 上で確認できないとき、後続は診断のまま残る")
    void chainWithExternalRootStaysDiagnosticWhenIntermediateLinkNotOnClasspath() throws Exception {
        // missingLink は Ext の classfile にないので、classfile での前進の検証が進めない。
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
        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<?, ?>) d.get("metadata")).get("target"))),
                () -> "unverifiable intermediate link must keep text() as a diagnostic, not silently excluded: "
                        + details);
    }

    @Test
    @DisplayName("chain の起点が scope 内型で前進解決の候補を 1 つに確定できないとき、external へ倒さず診断のまま残る")
    void chainWithInScopeRootStaysDiagnosticWhenForwardResolutionIsAmbiguous() throws Exception {
        // Holder は scope 内の source の型で、同名・同 arity の make(String) と make(Integer) があるので、
        // bytecode の候補が 1 つに決まらない。
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
        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<?, ?>) d.get("metadata")).get("target"))),
                () -> "ambiguous forward resolution must keep the diagnostic for text(): " + details);
    }

    @Test
    @DisplayName("scope 外メソッドへ渡した lambda の引数への呼び出しは、受け手の型が scope 外の場合でも診断のまま残る")
    void lambdaPassedToExternalMethodStaysDiagnosticEvenWhenReceiverIsExternal() throws Exception {
        // 受け手のメソッド (ExtApi.each) の receiver の型が scope 外であることは、lambda の parameter
        // (value) を external とみなす根拠にしない。理由は、value の型は functional interface の
        // 型引数で決まり、scope 内の型を引数に取る外部の API では誤って除外するからである。
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
        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<?, ?>) d.get("metadata")).get("target"))),
                () -> "lambda param passed to an external method must keep the diagnostic for text(): " + details);
    }

    @Test
    @DisplayName("scope 外の functional interface 型変数へ代入した lambda の引数への呼び出しは、external として除外される")
    void lambdaAssignedToExternalFunctionalInterfaceIsClassifiedExternal() throws Exception {
        // lambda を代入する変数の宣言型 (functional interface そのもの) が scope 外なら、lambda の
        // parameter の型はその型の型引数で決まるので、external とみなす根拠に使える。受け手の
        // メソッドの receiver の型と違い、functional interface の宣言を直接見ている。
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

    @Test
    @DisplayName("scope 内 functional interface の lambda 引数の型が解決できないとき、診断のまま残る")
    void lambdaParamOfInScopeFunctionalInterfaceStaysDiagnostic() throws Exception {
        // 引数の型が classpath にないので parameter の型が分からず、受け手は scope 内の暗黙の this である。
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
        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        assertTrue(details.stream().anyMatch(d ->
                        "mystery".equals(((Map<?, ?>) d.get("metadata")).get("target"))),
                () -> "in-scope functional interface must keep the diagnostic: " + details);
    }

    @Test
    @DisplayName("scope 内 chain の各呼び出しを classfile の戻り値型で前進解決し、bytecode にしか無い member への呼び出しを呼び出し関係 (edge) として救済する")
    void forwardResolvesChainThroughBytecodeReturnTypes() throws Exception {
        // Holder.make() と Dep.text() はどちらも source にない bytecode-only member で、
        // classfile にある make の戻り値型 Dep から text() を救済する。
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

    @Test
    @DisplayName("lambda 引数が同名の field を隠しているとき、field の型を owner として採用せず、除外もせずに診断のまま残る")
    void shadowedFieldNameStaysDiagnosticInsteadOfAdoptingFieldType() throws Exception {
        // 囲み型に external 型の field value があり、external method へ渡した
        // lambda の parameter (型推論不能) が同名で field を shadowing している。
        // field 型へフォールバックすると lambda parameter への呼び出しを
        // external と誤除外するため、diagnostic 維持を固定する回帰ガード。
        // field の classfile 根拠を成立させるため、Handler は field だけの
        // 縮小版を compile して classes に置く (lambda 側は javac では型が
        // 付かないため compile しない)。
        Path classes = compile("shadow-src", "shadow-classes",
                Map.of(
                        "com/example/ext/ExtDep.java", """
                                package com.example.ext;
                                public class ExtDep {
                                    public String text() { return "x"; }
                                }
                                """,
                        "com/example/ext/ExtApi.java", """
                                package com.example.ext;
                                public final class ExtApi {
                                    private ExtApi() {}
                                    public static <T> void each(java.util.function.Consumer<T> fn) {}
                                }
                                """,
                        "com/example/Handler.java", """
                                package com.example;
                                import com.example.ext.ExtDep;
                                public class Handler {
                                    ExtDep value;
                                }
                                """));

        Path workspace = temp.resolve("shadow-workspace");
        write(workspace, "com/example/Handler.java", """
                package com.example;
                import com.example.ext.ExtApi;
                import com.example.ext.ExtDep;
                public class Handler {
                    ExtDep value;
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
        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        assertTrue(details.stream().anyMatch(d ->
                        "text".equals(((Map<?, ?>) d.get("metadata")).get("target"))),
                () -> "shadowed lambda parameter must keep text() as a diagnostic, not excluded via the field type: "
                        + details);
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
        List<String> args = new java.util.ArrayList<>(List.of("--release", "25", "-d", classes.toString()));
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
