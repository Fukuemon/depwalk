package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 分類が救済にも external にも至らなかったときに、diagnostic と ledger に残す内容。
 *
 * @param reason ledger の reason
 * @param target ledger の target
 * @param metadata 診断の metadata。diagnostic に残すときだけ評価する
 */
record PendingDiagnostic(String reason, String target, Supplier<Map<String, Object>> metadata) {
}
