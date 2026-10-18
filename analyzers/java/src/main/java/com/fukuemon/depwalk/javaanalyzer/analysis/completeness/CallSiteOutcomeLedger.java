package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * inventory の各 {@link CallSiteId} へ primary 終端種別をちょうど 1 件対応付ける
 * 内部台帳。call site に属さない解析の欠け ({@link AnalysisGap}) も併せて記録し、
 * primary diagnostic と同じく完全性 gate の対象にする。
 * ID の欠落・重複・未分類・二重分類は
 * {@link IllegalStateException} とし、Analyzer が {@code JAVA_INTERNAL_ERROR} の
 * fatal に変換する。Protocol へは出力しない。
 */
public final class CallSiteOutcomeLedger {

    /** primary 終端種別。 */
    public enum OutcomeKind {
        /** edge を出力した。 */
        EMITTED,
        /** 仕様上の根拠付きで明示除外した。 */
        EXCLUDED,
        /** primary diagnostic として終端した (完全性 gate の対象)。 */
        DIAGNOSTIC
    }

    /** 仕様上の明示除外理由 (これ以外の excluded は許可しない)。 */
    public static final String REASON_EXTERNAL_TARGET = "external-target";
    /** 帰属型が引き上げ除外 package ({@code LiftExcludePackages}) に属する場合の除外理由。 */
    public static final String REASON_LIFT_EXCLUDED_PACKAGE = "lift-excluded-package";
    /** method / constructor の宣言を resolve できず、その宣言の node を出せなかった場合の reason。 */
    public static final String REASON_UNRESOLVED_DECLARATION = "unresolved-declaration";
    /** Spring DI の索引で例外が起き、その file の索引を見送った場合の reason。 */
    public static final String REASON_SPRING_DI_INDEX_FAILED = "spring-di-index-failed";

    /**
     * 1 entry の primary outcome。
     *
     * @param kind 終端種別
     * @param code DIAGNOSTIC のみ: 元 diagnostic code
     * @param reason EXCLUDED / DIAGNOSTIC の安定 reason
     * @param target 判明している場合のみ: 呼出先の自己完結な表現
     * @param candidates 判明している場合のみ: 候補の自己完結な表現 (決定順)
     * @param diagnosticMetadata DIAGNOSTIC のみ: sanitize 済み診断項目。
     *     primary diagnostic として終端した場合だけ {@code error.details.metadata} へ
     *     合流し、救済成功 (EMITTED) 時は Protocol へ出力されない
     */
    public record Outcome(
            OutcomeKind kind,
            String code,
            String reason,
            String target,
            List<String> candidates,
            Map<String, Object> diagnosticMetadata) {

        static Outcome emitted() {
            return new Outcome(OutcomeKind.EMITTED, null, null, null, null, null);
        }

        static Outcome excluded(String reason) {
            return new Outcome(OutcomeKind.EXCLUDED, null, reason, null, null, null);
        }

        static Outcome diagnostic(
                String code, String reason, String target, List<String> candidates,
                Map<String, Object> diagnosticMetadata) {
            return new Outcome(OutcomeKind.DIAGNOSTIC, code, reason, target,
                    candidates == null ? null : List.copyOf(candidates),
                    diagnosticMetadata == null ? null : Map.copyOf(diagnosticMetadata));
        }
    }

    /**
     * call site に属さない解析の欠け。primary diagnostic と同じく完全性 gate の対象になる。
     *
     * @param code 元 diagnostic code
     * @param reason {@link #REASON_UNRESOLVED_DECLARATION} または {@link #REASON_SPRING_DI_INDEX_FAILED}
     * @param sourceLocation 位置が判明している場合のみ: 欠けた箇所
     * @param target 判明している場合のみ: 欠けた宣言の名前
     * @param metadata sanitize 済み診断項目 (例外のクラス名など)
     */
    public record AnalysisGap(
            String code,
            String reason,
            SourceLocation sourceLocation,
            String target,
            Map<String, Object> metadata) {
    }

    private record GapKey(String path, int line, String reason, String target) implements Comparable<GapKey> {

        private static final Comparator<GapKey> ORDER = Comparator.comparing(GapKey::path)
                .thenComparingInt(GapKey::line)
                .thenComparing(GapKey::reason)
                .thenComparing(GapKey::target);

        @Override
        public int compareTo(GapKey other) {
            return ORDER.compare(this, other);
        }
    }

    private final Map<CallSiteId, Outcome> outcomes = new LinkedHashMap<>();
    private final Map<GapKey, AnalysisGap> gaps = new TreeMap<>();
    private final CallSiteInventory inventory;

    /**
     * @param inventory commit 対象 ID の母集合となる call site inventory
     */
    public CallSiteOutcomeLedger(CallSiteInventory inventory) {
        this.inventory = inventory;
    }

    /**
     * edge を出力した entry を確定する。補助 diagnostic 併存時も EMITTED が優先される。
     *
     * @param id inventory に登録済みの call site id
     */
    public void commitEmitted(CallSiteId id) {
        commit(id, Outcome.emitted());
    }

    /**
     * 仕様上の根拠付き明示除外を確定する。
     *
     * @param id inventory に登録済みの call site id
     * @param reason {@link #REASON_EXTERNAL_TARGET} または {@link #REASON_LIFT_EXCLUDED_PACKAGE}
     * @throws IllegalStateException 上記以外の reason を渡した場合
     */
    public void commitExcluded(CallSiteId id, String reason) {
        if (!REASON_EXTERNAL_TARGET.equals(reason) && !REASON_LIFT_EXCLUDED_PACKAGE.equals(reason)) {
            throw new IllegalStateException("unsupported excluded reason: " + reason);
        }
        commit(id, Outcome.excluded(reason));
    }

    /**
     * primary diagnostic を確定する (完全性 gate の対象)。
     *
     * @param id inventory に登録済みの call site id
     * @param code 元 diagnostic code
     * @param reason 安定 reason
     * @param target 判明している場合のみ: 呼出先の自己完結な表現
     * @param candidates 判明している場合のみ: 候補の自己完結な表現 (決定順)
     */
    public void commitDiagnostic(CallSiteId id, String code, String reason, String target, List<String> candidates) {
        commitDiagnostic(id, code, reason, target, candidates, null);
    }

    /**
     * sanitize 済み診断項目付きで primary diagnostic を確定する。
     * {@code diagnosticMetadata} には source 本文・絶対 path・raw exception message を
     * 含めてはならない (呼び出し側が安定値だけを渡す)。
     *
     * @param id inventory に登録済みの call site id
     * @param code 元 diagnostic code
     * @param reason 安定 reason
     * @param target 判明している場合のみ: 呼出先の自己完結な表現
     * @param candidates 判明している場合のみ: 候補の自己完結な表現 (決定順)
     * @param diagnosticMetadata sanitize 済み診断項目 (無ければ {@code null})
     */
    public void commitDiagnostic(
            CallSiteId id, String code, String reason, String target, List<String> candidates,
            Map<String, Object> diagnosticMetadata) {
        commit(id, Outcome.diagnostic(code, reason, target, candidates, diagnosticMetadata));
    }

    /**
     * method / constructor の宣言の resolve の失敗を記録する。同じ宣言を複数回記録しても 1 件にまとめる。
     *
     * @param code 元 diagnostic code
     * @param sourceLocation 宣言の位置 (不明なら {@code null})
     * @param declarationName 宣言の名前
     * @param failure resolve が投げた例外。クラス名だけを記録し、message は含めない
     */
    public void recordUnresolvedDeclaration(
            String code, SourceLocation sourceLocation, String declarationName, Throwable failure) {
        recordGap(code, REASON_UNRESOLVED_DECLARATION, sourceLocation, declarationName, failure);
    }

    /**
     * Spring DI の索引の失敗を記録する。
     *
     * @param code 元 diagnostic code
     * @param sourceLocation 索引を見送った file の位置
     * @param failure 索引が投げた例外。クラス名だけを記録し、message は含めない
     */
    public void recordSpringDiIndexFailure(String code, SourceLocation sourceLocation, Throwable failure) {
        recordGap(code, REASON_SPRING_DI_INDEX_FAILED, sourceLocation, "", failure);
    }

    private void recordGap(
            String code, String reason, SourceLocation sourceLocation, String target, Throwable failure) {
        GapKey key = new GapKey(
                sourceLocation == null ? "" : sourceLocation.path(),
                sourceLocation == null ? 0 : sourceLocation.startLine(),
                reason,
                target);
        gaps.putIfAbsent(key, new AnalysisGap(
                code, reason, sourceLocation, target.isEmpty() ? null : target,
                Map.of("exceptionClass", failure.getClass().getName())));
    }

    /** call site に属さない解析の欠けを、path、行、reason、target の順で返す。 */
    public List<AnalysisGap> analysisGaps() {
        return List.copyOf(gaps.values());
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
        // edge と補助 diagnostic が併存する entry は primary EMITTED とする。
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

    /** production stderr 用の総数と終端種別・理由別の集計。call site に属さない欠けは {@code gap[...]} で数える。 */
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
        Map<String, Long> gapByReason = new TreeMap<>();
        for (AnalysisGap gap : gaps.values()) {
            gapByReason.merge(gap.code() + ":" + gap.reason(), 1L, Long::sum);
        }
        long silentOmission = inventory.ids().size() - outcomes.size();
        StringBuilder text = new StringBuilder("callSites=").append(inventory.ids().size())
                .append(" emitted=").append(emitted);
        excludedByReason.forEach((reason, count) ->
                text.append(" excluded[").append(reason).append("]=").append(count));
        diagnosticByReason.forEach((reason, count) ->
                text.append(" diagnostic[").append(reason).append("]=").append(count));
        gapByReason.forEach((reason, count) ->
                text.append(" gap[").append(reason).append("]=").append(count));
        return text.append(" silentOmission=").append(silentOmission).toString();
    }
}
