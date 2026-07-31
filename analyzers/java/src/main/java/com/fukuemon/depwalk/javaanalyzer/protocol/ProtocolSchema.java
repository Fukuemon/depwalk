package com.fukuemon.depwalk.javaanalyzer.protocol;

/** 全 JSONL record が共有する Protocol 全体の定数。 */
public final class ProtocolSchema {

    /** 現在の Analyzer Protocol がすべての JSONL record に設定する {@code schemaVersion}。 */
    public static final String VERSION = "1";

    private ProtocolSchema() {
    }
}
