package com.fukuemon.depwalk.javaanalyzer.protocol;

/**
 * Analyzer -> Core へ stdout JSONL として出力される record の共通 marker interface。
 * 出力 record 種別は analyzer-protocol feature doc で定義済みの 4 種に限る。
 */
public sealed interface ProtocolRecord permits MethodSymbol, CallEdge, Diagnostic, ErrorRecord {

    String schemaVersion();

    /** record 種別を識別する安定文字列。 */
    String recordType();
}
