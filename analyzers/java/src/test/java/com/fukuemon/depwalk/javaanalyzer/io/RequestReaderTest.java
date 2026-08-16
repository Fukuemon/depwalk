package com.fukuemon.depwalk.javaanalyzer.io;

import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("stdin から読む analysisRequest の解釈の契約")
class RequestReaderTest {

    private final RequestReader reader = new RequestReader(ProtocolObjectMapper.create());

    @Test
    @DisplayName("妥当な analysisRequest JSON を読むとき、各フィールドがそのまま取り出される")
    void readsValidAnalysisRequest() throws IOException {
        String json = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"include\":[\"src/**/*.java\"],"
                + "\"metadata\":{\"classpath\":[]}}";

        AnalysisRequest request = reader.read(inputStream(json));

        assertEquals("1", request.schemaVersion());
        assertEquals("analysisRequest", request.recordType());
        assertEquals("req-1", request.requestId());
        assertEquals("/workspace/depwalk", request.workspaceRoot());
        assertEquals("java", request.language());
        assertEquals(1, request.include().size());
    }

    @Test
    @DisplayName("sourceRoots を読むとき、記載どおりの順序が保たれる")
    void readsSourceRootsPreservingOrder() throws IOException {
        String json = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\","
                + "\"sourceRoots\":[\"module-b/src/main/java\",\"module-a/src/main/java\",\".\"],"
                + "\"metadata\":{\"classpath\":[]}}";

        AnalysisRequest request = reader.read(inputStream(json));

        assertEquals(
                List.of("module-b/src/main/java", "module-a/src/main/java", "."),
                request.sourceRoots());
    }

    @Test
    @DisplayName("sourceRoots が省略されているとき、空 list へ補完せずに null のままになる")
    void leavesOmittedSourceRootsNull() throws IOException {
        String json = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

        AnalysisRequest request = reader.read(inputStream(json));

        assertEquals(null, request.sourceRoots());
    }

    @Test
    @DisplayName("JSON として壊れた入力のとき、IOException として失敗する")
    void throwsOnMalformedJson() {
        String malformed = "{\"schemaVersion\":\"1\", this is not valid json";

        assertThrows(IOException.class, () -> reader.read(inputStream(malformed)));
    }

    @Test
    @DisplayName("stdin が空のとき、IOException として失敗する")
    void throwsOnEmptyStdin() {
        assertThrows(IOException.class, () -> reader.read(inputStream("")));
    }

    @Test
    @DisplayName("未知のフィールドが含まれる場合でも、失敗せずに既知のフィールドだけが読み取られる")
    void ignoresUnknownFields() throws IOException {
        String json = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]},"
                + "\"someFutureField\":{\"nested\":true}}";

        AnalysisRequest request = reader.read(inputStream(json));

        assertTrue(request.metadata().containsKey("classpath"));
    }

    private static ByteArrayInputStream inputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
