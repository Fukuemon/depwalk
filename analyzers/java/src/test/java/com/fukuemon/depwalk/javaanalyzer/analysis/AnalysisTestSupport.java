package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.Main;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * analysis の test が共有する補助。{@code analysisRequest} を組み立てて {@link Main#run} を実行し、
 * stdout の JSONL を {@code Map} の record として返す。fixture の source を javac で compile する。
 */
final class AnalysisTestSupport {

    private AnalysisTestSupport() {
    }

    record Ran(int exitCode, List<Map<String, Object>> records, String stderr) {
        List<Map<String, Object>> byType(String recordType) {
            return records.stream().filter(r -> recordType.equals(r.get("recordType"))).toList();
        }
    }

    /**
     * compile 済み fixture を生成する javac の {@code --release} 既定値。
     *
     * <p>Analyzer runtime と同じ Java 25 に揃える。SootUp が最新の classfile major を
     * 読めなくなると bytecode 救済が例外なく静かに無効化されるため、既定値を runtime に
     * 合わせておくことで、その回帰を test が検出する。
     */
    static final String FIXTURE_RELEASE = "25";

    /** JSON の object を {@code Map<String, Object>} として読むための Jackson の型。 */
    static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    static Ran run(Path workspaceRoot, Map<String, Object> metadata) throws Exception {
        return run(workspaceRoot, metadata, null, null, null, null);
    }

    static Ran run(
            Path workspaceRoot,
            Map<String, Object> metadata,
            List<String> include,
            List<String> exclude,
            List<Map<String, String>> entrypoints,
            String analysisMode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("schemaVersion", "1");
        request.put("recordType", "analysisRequest");
        request.put("requestId", "test-request");
        request.put("workspaceRoot", workspaceRoot.toAbsolutePath().toString());
        // Gradle の discovery を走らせないため、source root を明示する。
        request.put("sourceRoots", List.of("."));
        request.put("language", "java");
        if (include != null) {
            request.put("include", include);
        }
        if (exclude != null) {
            request.put("exclude", exclude);
        }
        if (entrypoints != null) {
            request.put("entrypoints", entrypoints);
        }
        if (analysisMode != null) {
            request.put("analysisMode", analysisMode);
        }
        request.put("metadata", metadata);

        String json = mapper.writeValueAsString(request);
        ByteArrayInputStream stdin = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(stdin, stdout, stderr);

        List<Map<String, Object>> records = new ArrayList<>();
        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        for (String line : stdoutText.split("\n")) {
            if (!line.isBlank()) {
                records.add(mapper.readValue(line, JSON_OBJECT));
            }
        }
        return new Ran(exitCode, records, stderr.toString(StandardCharsets.UTF_8));
    }

    static Map<String, Object> classpathMetadata(String... jars) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(jars));
        metadata.put("javaLanguageLevel", List.of("25"));
        return metadata;
    }

    /** compile 済み classes dir を classpath に載せた metadata ({@link #FIXTURE_RELEASE} 前提)。 */
    static Map<String, Object> classesDirMetadata(Path classesDir) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(classesDir.toString()));
        metadata.put("javaLanguageLevel", List.of(FIXTURE_RELEASE));
        return metadata;
    }

    /**
     * fixture source を javac で compile して {@code classesDir} へ出力する。
     * {@code buildDir} は compile 用 source の置き場で、呼び出し側が意図の分かる
     * 名前を明示する (walk 対象の workspace と混ざらない場所にすること)。
     */
    static void compileFixture(
            Path buildDir, Path classesDir, String release, List<Path> classpath, Map<String, String> sources)
            throws Exception {
        List<String> args = new ArrayList<>(List.of("--release", release, "-d", classesDir.toString()));
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(String.join(
                    java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            writeSource(buildDir, source.getKey(), source.getValue());
            args.add(buildDir.resolve(source.getKey()).toString());
        }
        // rc だけでは何が壊れたか分からないので、javac の診断を失敗の message に載せる。
        ByteArrayOutputStream compilerOut = new ByteArrayOutputStream();
        ByteArrayOutputStream compilerErr = new ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler()
                .run(null, compilerOut, compilerErr, args.toArray(String[]::new));
        assertEquals(0, rc, () -> "fixture compile failed (rc=" + rc + ")\nstdout:\n"
                + compilerOut.toString(StandardCharsets.UTF_8)
                + "\nstderr:\n" + compilerErr.toString(StandardCharsets.UTF_8));
    }

    /** source を {@code root} 配下の相対 path へ書き込む (親 directory は作成する)。 */
    static void writeSource(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    /** record の {@code metadata} を返す。無ければ空の Map を返す。 */
    static Map<String, Object> metadataOf(Map<String, Object> record) {
        Map<String, Object> metadata = field(record, "metadata");
        return metadata == null ? Map.of() : metadata;
    }

    /**
     * record の {@code key} の値を、代入先の型として返す。値が無ければ null を返す。
     * 値の型が違えば代入した時点で、型引数が違えば要素を使った時点で {@link ClassCastException} になる。
     */
    // 値は Object として入っており、型引数の付いた型への cast は erasure のため実行時に検査できない。
    // 抑制はこのメソッドだけに置く。
    @SuppressWarnings("unchecked")
    static <T> T field(Map<?, ?> record, String key) {
        return (T) record.get(key);
    }
}
