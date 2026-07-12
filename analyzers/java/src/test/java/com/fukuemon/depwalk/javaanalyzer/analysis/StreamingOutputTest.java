package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fukuemon.depwalk.javaanalyzer.Main;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 / D9: {@code fullGraph} は record をファイル単位で逐次 flush し、母集合全体の解析完了を待たない。
 * {@code A.java} (declaration のみ) / {@code B.java} (unresolved call を含む) / {@code C.java}
 * (declaration のみ) を {@code ScopeFiles} の決定的順序 (path 昇順、A → B → C) で解析したとき、
 * B の {@code JAVA_UNRESOLVED_SYMBOL} diagnostic は B 自身の解析直後に flush されるため、
 * C の {@code methodSymbol} より先に書き出される。バッチ実装 (全 node → 全 edge → 全 diagnostic の順)
 * ではこの関係が逆転する (diagnostic は常に最後) ため、この順序で streaming か batch かを判別できる。
 */
class StreamingOutputTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/streamingorder");

    @Test
    void fullGraphFlushesEachFilesRecordsBeforeLaterFilesAreFullyProcessed() throws IOException {
        String requestJson = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(FIXTURE) + "\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

        RecordCapturingOutputStream stdout = new RecordCapturingOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(
                new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8)), stdout, stderr);

        assertEquals(0, exitCode);

        List<String> chunks = stdout.flushedChunks();
        int bDiagnosticIndex = indexOfContaining(chunks, "JAVA_UNRESOLVED_SYMBOL");
        int cNodeIndex = indexOfContaining(chunks, "java:com.example.C#baz()");

        assertTrue(bDiagnosticIndex >= 0, "expected a JAVA_UNRESOLVED_SYMBOL diagnostic from B.java: " + chunks);
        assertTrue(cNodeIndex >= 0, "expected C#baz() methodSymbol: " + chunks);
        assertTrue(bDiagnosticIndex < cNodeIndex,
                "B.java's diagnostic must be flushed before C.java's declarations are written "
                        + "(per-file streaming, not batched at the end): " + chunks);
    }

    private static int indexOfContaining(List<String> chunks, String needle) {
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    /**
     * 書き込み側 ({@link com.fukuemon.depwalk.javaanalyzer.io.RecordWriter}) は record ごとに書き込み後
     * 即 flush するため、この {@link OutputStream#flush()} 呼び出しの単位で record の書き込み順序を
     * 観測できる。
     */
    private static final class RecordCapturingOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final List<String> flushedChunks = new ArrayList<>();
        private int lastFlushedSize = 0;

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        @Override
        public void flush() {
            byte[] all = buffer.toByteArray();
            if (all.length > lastFlushedSize) {
                String chunk = new String(all, lastFlushedSize, all.length - lastFlushedSize, StandardCharsets.UTF_8);
                if (!chunk.isBlank()) {
                    flushedChunks.add(chunk);
                }
                lastFlushedSize = all.length;
            }
        }

        List<String> flushedChunks() {
            return flushedChunks;
        }
    }
}
