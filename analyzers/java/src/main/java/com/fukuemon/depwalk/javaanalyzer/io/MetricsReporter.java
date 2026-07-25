package com.fukuemon.depwalk.javaanalyzer.io;

import java.io.PrintStream;

/**
 * 計測サマリ (解析ファイル数 / 所要時間 / 未解決件数) を stderr へ出力する。
 * protocol record ではないため JSONL ではなく human-readable な 1 行で出力する。
 */
public final class MetricsReporter {

    private MetricsReporter() {
    }

    /**
     * 計測サマリを human-readable な1行として出力し、stream を flush する。
     *
     * @param err metrics の出力先
     * @param summary 解析実行の計測値
     */
    public static void report(PrintStream err, MetricsSummary summary) {
        err.printf(
                "analyzedFiles=%d durationMs=%d parsePreflightMs=%d contextBuildMs=%d unresolvedSymbols=%d%n",
                summary.analyzedFileCount(),
                summary.durationMillis(),
                summary.parsePreflightMillis(),
                summary.contextBuildMillis(),
                summary.unresolvedCount());
        err.flush();
    }
}
