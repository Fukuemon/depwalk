package com.fukuemon.depwalk.javaanalyzer.io;

/**
 * stderr へ出力する計測サマリ。protocol record ではない (analyzer-protocol の対象外)。
 * 値の集計は P2_01 の責務であり、本 prompt では枠のみを用意する。
 *
 * @param analyzedFileCount   解析したファイル数
 * @param durationMillis      解析に要した時間 (ミリ秒)
 * @param unresolvedCount     未解決件数
 */
public record MetricsSummary(long analyzedFileCount, long durationMillis, long unresolvedCount) {
}
