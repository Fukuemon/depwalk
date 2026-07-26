package com.fukuemon.depwalk.javaanalyzer.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * provider artifact と一時 init script を、実行ごとに一意な OS temporary
 * directory へ展開する。対象 workspace へは一切書き込まない。
 */
public final class ProviderWorkspace implements AutoCloseable {

    /** shadowJar へ同梱される provider artifact の resource path。 */
    static final String PROVIDER_RESOURCE = "/gradle-model-provider/depwalk-gradle-model-provider.jar";

    private static final String PLUGIN_CLASS = "com.fukuemon.depwalk.gradleprovider.DepwalkModelPlugin";

    private final Path directory;
    private final Path providerJar;
    private final Path initScript;
    private final PrintStream stderr;

    private ProviderWorkspace(Path directory, Path providerJar, Path initScript, PrintStream stderr) {
        this.directory = directory;
        this.providerJar = providerJar;
        this.initScript = initScript;
        this.stderr = stderr;
    }

    /**
     * provider jar resource と init script を一時 directory へ展開する。
     *
     * @param stderr cleanup warning の出力先
     * @return 展開済み workspace
     * @throws IOException 展開に失敗した場合 (temporary 領域のみ。workspace へ
     *     fallback 配置しない)
     */
    public static ProviderWorkspace create(PrintStream stderr) throws IOException {
        Path directory = Files.createTempDirectory("depwalk-gradle-provider-");
        try {
            Path providerJar = directory.resolve("depwalk-gradle-model-provider.jar");
            try (InputStream resource = ProviderWorkspace.class.getResourceAsStream(PROVIDER_RESOURCE)) {
                if (resource == null) {
                    throw new IOException("bundled gradle model provider resource is missing");
                }
                Files.copy(resource, providerJar, StandardCopyOption.REPLACE_EXISTING);
            }
            Path initScript = directory.resolve("depwalk-model-provider.init.gradle");
            Files.writeString(initScript, initScriptContent(providerJar));
            return new ProviderWorkspace(directory, providerJar, initScript, stderr);
        } catch (IOException e) {
            deleteRecursively(directory);
            throw e;
        }
    }

    /** Groovy DSL init script。7.6.5〜9.6.x の両系列で有効な構文だけを使う。 */
    static String initScriptContent(Path providerJar) {
        String escapedPath = providerJar.toString().replace("\\", "\\\\").replace("'", "\\'");
        return "initscript {\n"
                + "    dependencies {\n"
                + "        classpath files('" + escapedPath + "')\n"
                + "    }\n"
                + "}\n"
                + "gradle.rootProject { project ->\n"
                + "    project.plugins.apply(" + PLUGIN_CLASS + ")\n"
                + "}\n";
    }

    /** provider を登録する一時 init script の絶対 path。 */
    public Path initScript() {
        return initScript;
    }

    public Path providerJar() {
        return providerJar;
    }

    /** {@link #close()} で削除される一時 directory。 */
    public Path directory() {
        return directory;
    }

    /**
     * temporary directory を best effort で削除する。失敗しても解析結果を
     * 失敗させず、絶対 path や raw 例外を含まない安定 category だけを
     * stderr へ記録する。
     */
    @Override
    public void close() {
        try {
            deleteRecursively(directory);
        } catch (IOException e) {
            stderr.println("depwalk: warning category=provider-temp-cleanup-failed");
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
