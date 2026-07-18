package com.fukuemon.depwalk.javaanalyzer.io;

import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestReaderTest {

    private final RequestReader reader = new RequestReader(ProtocolObjectMapper.create());

    @Test
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
    void leavesOmittedSourceRootsNull() throws IOException {
        String json = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

        AnalysisRequest request = reader.read(inputStream(json));

        assertEquals(null, request.sourceRoots());
    }

    @Test
    void throwsOnMalformedJson() {
        String malformed = "{\"schemaVersion\":\"1\", this is not valid json";

        assertThrows(IOException.class, () -> reader.read(inputStream(malformed)));
    }

    @Test
    void throwsOnEmptyStdin() {
        assertThrows(IOException.class, () -> reader.read(inputStream("")));
    }

    @Test
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
