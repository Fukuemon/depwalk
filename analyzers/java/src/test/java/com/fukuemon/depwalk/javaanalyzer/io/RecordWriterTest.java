package com.fukuemon.depwalk.javaanalyzer.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.ErrorRecord;
import com.fukuemon.depwalk.javaanalyzer.protocol.FailureDetail;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.ProtocolSchema;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void writesErrorDetailsPreservingOrderAndOpaqueMetadata() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordWriter writer = new RecordWriter(out, mapper);

        Map<String, Object> detailMetadata = new HashMap<>();
        detailMetadata.put("callKind", "virtual");
        detailMetadata.put("candidates", List.of(Map.of("qualifiedName", "com.example.A")));
        detailMetadata.put("resolvedTarget", null);
        writer.write(new ErrorRecord(
                ProtocolSchema.VERSION,
                ErrorRecord.RECORD_TYPE,
                "JAVA_INCOMPLETE_ANALYSIS",
                "unresolved call sites remain",
                null,
                null,
                List.of(
                        new FailureDetail(
                                "JAVA_UNRESOLVED_SYMBOL",
                                "first",
                                SourceLocation.of("module-a/src/App.java", 10),
                                detailMetadata),
                        new FailureDetail("JAVA_UNRESOLVED_SYMBOL", "second", null, null))));

        JsonNode node = mapper.readTree(linesOf(out).get(0));
        JsonNode details = node.get("details");
        assertEquals(2, details.size());
        assertEquals("first", details.get(0).get("message").asText());
        assertEquals("second", details.get(1).get("message").asText());
        assertEquals("module-a/src/App.java", details.get(0).get("sourceLocation").get("path").asText());
        assertEquals("com.example.A", details.get(0).get("metadata").get("candidates").get(0).get("qualifiedName").asText());
        assertTrue(details.get(0).get("metadata").get("resolvedTarget").isNull());
        assertFalse(details.get(1).has("sourceLocation"));
        assertFalse(details.get(1).has("metadata"));
    }

    @Test
    void writesBytecodeOnlySymbolWithoutSourceLocation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordWriter writer = new RecordWriter(out, mapper);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("declarationOrigin", "projectClasses");
        metadata.put("ownerSourceLocation", Map.of("path", "module-a/src/Owner.java", "startLine", 3));
        metadata.put("sourceAnchor", null);
        writer.write(MethodSymbol.of(
                "java:com.example.Owner#builder()",
                "java",
                "method",
                "com.example.Owner.builder",
                "builder():com.example.Owner$Builder",
                null,
                metadata));

        JsonNode node = mapper.readTree(linesOf(out).get(0));
        assertFalse(node.has("sourceLocation"));
        assertEquals("module-a/src/Owner.java", node.get("metadata").get("ownerSourceLocation").get("path").asText());
        assertEquals(3, node.get("metadata").get("ownerSourceLocation").get("startLine").asInt());
        assertTrue(node.get("metadata").get("sourceAnchor").isNull());
    }

    private static List<String> linesOf(ByteArrayOutputStream out) {
        String content = out.toString(StandardCharsets.UTF_8);
        return content.lines().toList();
    }
}
