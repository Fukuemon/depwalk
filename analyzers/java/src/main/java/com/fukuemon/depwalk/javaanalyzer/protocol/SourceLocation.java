package com.fukuemon.depwalk.javaanalyzer.protocol;

/**
 * {@code methodSymbol.sourceLocation} / {@code callEdge.callSite} に埋め込む value object。
 * 独立した JSONL record ではない。
 *
 * @param path        workspaceRoot からの相対 path (必須)
 * @param startLine   1-based の開始行 (必須)
 * @param startColumn 1-based の開始 column (任意)
 * @param endLine     1-based の終了行 (任意)
 * @param endColumn   1-based の終了 column (任意)
 */
public record SourceLocation(
        String path,
        int startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn) {

    /**
     * 行番号だけが既知の source location を生成する。
     *
     * @param path workspace root からの相対 path
     * @param startLine 1-based の開始行
     */
    public static SourceLocation of(String path, int startLine) {
        return new SourceLocation(path, startLine, null, null, null);
    }
}
