package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * inventory の各 {@link CallSiteId} へ primary 終端種別をちょうど 1 件対応付ける
 * 内部台帳 (spec #24 D14 / D17 / D20)。ID の欠落・重複・未分類・二重分類は
 * {@link IllegalStateException} とし、Analyzer が {@code JAVA_INTERNAL_ERROR} の
 * fatal に変換する。Protocol へは出力しない。
 */
public final class CallSiteOutcomeLedger {

    /** primary 終端種別。 */
    public enum OutcomeKind {
        EMITTED,
        EXCLUDED,
        DIAGNOSTIC
    }

    /** 仕様上の明示除外理由 (これ以外の excluded は許可しない)。 */
    public static final String REASON_EXTERNAL_TARGET = "external-target";
    public static final String REASON_LIFT_EXCLUDED_PACKAGE = "lift-excluded-package";

    /**
     * 1 entry の primary outcome。
     *
     * @param kind 終端種別
     * @param code DIAGNOSTIC のみ: 元 diagnostic code
     * @param reason EXCLUDED / DIAGNOSTIC の安定 reason
     * @param target 判明している場合のみ: 呼出先の自己完結な表現
     * @param candidates 判明している場合のみ: 候補の自己完結な表現 (決定順)
     */
    public record Outcome(OutcomeKind kind, String code, String reason, String target, List<String> candidates) {

        static Outcome emitted() {
            return new Outcome(OutcomeKind.EMITTED, null, null, null, null);
        }

        static Outcome excluded(String reason) {
            return new Outcome(OutcomeKind.EXCLUDED, null, reason, null, null);
        }

        static Outcome diagnostic(String code, String reason, String target, List<String> candidates) {
            return new Outcome(OutcomeKind.DIAGNOSTIC, code, reason, target,
                    candidates == null ? null : List.copyOf(candidates));
        }
    }

    private final Map<CallSiteId, Outcome> outcomes = new LinkedHashMap<>();
    private final CallSiteInventory inventory;

    public CallSiteOutcomeLedger(CallSiteInventory inventory) {
        this.inventory = inventory;
    }

    /** edge を出力した entry を確定する。補助 diagnostic 併存時も EMITTED が優先される。 */
    public void commitEmitted(CallSiteId id) {
        commit(id, Outcome.emitted());
    }

    /** 仕様上の根拠付き明示除外を確定する。 */
    public void commitExcluded(CallSiteId id, String reason) {
        if (!REASON_EXTERNAL_TARGET.equals(reason) && !REASON_LIFT_EXCLUDED_PACKAGE.equals(reason)) {
            throw new IllegalStateException("unsupported excluded reason: " + reason);
        }
        commit(id, Outcome.excluded(reason));
    }

    /** primary diagnostic を確定する (完全性 gate の対象)。 */
    public void commitDiagnostic(CallSiteId id, String code, String reason, String target, List<String> candidates) {
        commit(id, Outcome.diagnostic(code, reason, target, candidates));
    }

    private void commit(CallSiteId id, Outcome outcome) {
        if (!inventory.contains(id)) {
            throw new IllegalStateException("outcome committed for a call site missing from the inventory: " + id);
        }
        Outcome existing = outcomes.get(id);
        if (existing == null) {
            outcomes.put(id, outcome);
            return;
        }
        // edge と補助 diagnostic が併存する entry は primary EMITTED とする (D14)。
        if (existing.kind() == OutcomeKind.DIAGNOSTIC && outcome.kind() == OutcomeKind.EMITTED) {
            outcomes.put(id, outcome);
            return;
        }
        if (existing.kind() == OutcomeKind.EMITTED && outcome.kind() == OutcomeKind.DIAGNOSTIC) {
            return;
        }
        if (existing.equals(outcome)) {
            return;
        }
        throw new IllegalStateException(
                "call site received two conflicting primary outcomes (" + existing.kind() + " and "
                        + outcome.kind() + "): " + id);
    }

    /**
     * 全 entry が分類済みであることを検証し、未分類件数 (silentOmission) が
     * 0 でなければ内部不変条件違反にする。
     */
    public void validateComplete() {
        long silentOmission = inventory.ids().stream().filter(id -> !outcomes.containsKey(id)).count();
        if (silentOmission > 0) {
            throw new IllegalStateException(
                    "call sites were silently omitted without a primary outcome: " + silentOmission);
        }
    }

    /** primary diagnostic に残った entry を決定順で返す。 */
    public Map<CallSiteId, Outcome> primaryDiagnostics() {
        Map<CallSiteId, Outcome> result = new TreeMap<>();
        for (Map.Entry<CallSiteId, Outcome> entry : outcomes.entrySet()) {
            if (entry.getValue().kind() == OutcomeKind.DIAGNOSTIC) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** production stderr 用の総数と終端種別・理由別の集計。 */
    public String summary() {
        long emitted = 0;
        Map<String, Long> excludedByReason = new TreeMap<>();
        Map<String, Long> diagnosticByReason = new TreeMap<>();
        for (Outcome outcome : outcomes.values()) {
            switch (outcome.kind()) {
                case EMITTED -> emitted++;
                case EXCLUDED -> excludedByReason.merge(outcome.reason(), 1L, Long::sum);
                case DIAGNOSTIC -> diagnosticByReason.merge(outcome.code() + ":" + outcome.reason(), 1L, Long::sum);
            }
        }
        long silentOmission = inventory.ids().size() - outcomes.size();
        StringBuilder text = new StringBuilder("callSites=").append(inventory.ids().size())
                .append(" emitted=").append(emitted);
        excludedByReason.forEach((reason, count) ->
                text.append(" excluded[").append(reason).append("]=").append(count));
        diagnosticByReason.forEach((reason, count) ->
                text.append(" diagnostic[").append(reason).append("]=").append(count));
        return text.append(" silentOmission=").append(silentOmission).toString();
    }
}
