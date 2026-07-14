package com.fukuemon.depwalk.javaanalyzer.protocol;

/** Protocol-wide constants shared by every JSONL record. */
public final class ProtocolSchema {

    /** 現在の Analyzer Protocol がすべての JSONL record に設定する {@code schemaVersion}。 */
    public static final String VERSION = "1";

    private ProtocolSchema() {
    }
}
