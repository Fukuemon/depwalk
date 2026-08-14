package com.fukuemon.depwalk.javaanalyzer.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("provider jar と init script を展開する一時領域の隔離")
class ProviderWorkspaceTest {

    private final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
    private final PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

    @TempDir
    Path workspaceRoot;

    @Test
    @DisplayName("provider jar と init script は workspace の外の一時 directory だけに展開され、close で directory ごと削除される")
    void extractsProviderAndInitScriptIntoTemporaryDirectoryOnly() throws Exception {
        Path directory;
        try (ProviderWorkspace provider = ProviderWorkspace.create(stderr)) {
            directory = provider.directory();
            assertTrue(Files.isRegularFile(provider.providerJar()));
            assertTrue(Files.isRegularFile(provider.initScript()));
            assertFalse(directory.startsWith(workspaceRoot), "must not extract into the workspace");

            String script = Files.readString(provider.initScript());
            assertTrue(script.contains(provider.providerJar().toString().replace("\\", "\\\\")));
            assertTrue(script.contains("com.fukuemon.depwalk.gradleprovider.DepwalkModelPlugin"));

            try (Stream<Path> files = Files.walk(workspaceRoot)) {
                assertEquals(1, files.count(), "workspace must stay untouched");
            }
        }
        assertFalse(Files.exists(directory), "temporary directory must be cleaned up on close");
    }

    @Test
    @DisplayName("同時に複数作成した場合でも、それぞれ別の一時 directory が使われる")
    void createsUniqueDirectoryPerRun() throws Exception {
        try (ProviderWorkspace first = ProviderWorkspace.create(stderr);
                ProviderWorkspace second = ProviderWorkspace.create(stderr)) {
            assertFalse(first.directory().equals(second.directory()));
        }
    }

    @Test
    @DisplayName("生成される init script は project の task 実行や project の依存 (dependsOn) を参照せず、plugin の適用 (plugins.apply) を含む")
    void initScriptDoesNotRunTasksOrGenerateSources() {
        String script = ProviderWorkspace.initScriptContent(Path.of("/tmp/provider.jar"));
        assertFalse(script.contains("tasks."), "the init script must not reference task execution");
        assertFalse(script.contains("dependsOn"));
        assertTrue(script.contains("plugins.apply"));
    }
}
