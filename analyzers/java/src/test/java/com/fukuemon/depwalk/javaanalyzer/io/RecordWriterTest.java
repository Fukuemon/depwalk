package com.fukuemon.depwalk.javaanalyzer.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.ErrorRecord;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordWriterTest {

    private final ObjectMapper mapper = ProtocolObjectMapper.create();

    @Test
    void everyRecordIncludesSchemaVersionAndRecordType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordWriter writer = new RecordWriter(out, mapper);

        writer.write(MethodSymbol.of("java:com.example.A#run()", "java", "method", "com.example.A.run", "run()", null, null));
        writer.write(CallEdge.of("java:edge-1", "java:com.example.A#run()", "java:com.example.B#call()", null, null));
        writer.write(Diagnostic.of("warning", "JAVA_UNRESOLVED_SYMBOL", "could not resolve", null, null, null));
        writer.write(ErrorRecord.of("JAVA_INTERNAL_ERROR", "boom"));

        List<String> lines = linesOf(out);
        assertEquals(4, lines.size());
        for (String line : lines) {
            JsonNode node = mapper.readTree(line);
            assertEquals("1", node.get("schemaVersion").asText());
            assertTrue(node.has("recordType"));
            assertTrue(!node.get("recordType").asText().isBlank());
        }
    }

    @Test
    void recordsAreFlushedImmediately() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordWriter writer = new RecordWriter(out, mapper);

        writer.write(ErrorRecord.of("JAVA_INTERNAL_ERROR", "boom"));

        // flush happens inside write(); no close() call needed for the content to be observable.
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("JAVA_INTERNAL_ERROR"));
    }

    private static List<String> linesOf(ByteArrayOutputStream out) {
        String content = out.toString(StandardCharsets.UTF_8);
        return content.lines().toList();
    }
}
