package com.fukuemon.depwalk.javaanalyzer.io;

/**
 * stderr へ出力する計測サマリ。protocol record ではない (analyzer-protocol の対象外)。
 * 解析 runner が実行中に集計したファイル数、所要時間、未解決件数を process 終了時に報告する。
 *
 * @param analyzedFileCount    解析したファイル数
 * @param durationMillis       解析全体に要した時間 (ミリ秒)
 * @param parsePreflightMillis parse pre-flight に要した時間 (通常解析と分離)
 * @param contextBuildMillis   context 別 solver / parser 構築に要した時間
 * @param unresolvedCount      未解決件数
 */
public record MetricsSummary(
        long analyzedFileCount,
        long durationMillis,
        long parsePreflightMillis,
        long contextBuildMillis,
        long unresolvedCount) {
}
