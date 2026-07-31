package com.fukuemon.depwalk.javaanalyzer.analysis.attribution;

/**
 * 帰属型の決定規則の判定結果。
 *
 * @param outcome              4 分岐の判定結果
 * @param attributedBinaryName 出力する場合の帰属型 binary name ({@link Outcome#SCOPE_INTERNAL} は宣言型、
 *                              {@link Outcome#LIFTED} はレシーバの静的型。omit 系は {@code null})
 * @param inherited             引き上げ node かどうか ({@link Outcome#LIFTED} のときのみ {@code true})
 * @param declaringTypeBinaryName 引き上げ元の宣言型 binary name ({@link Outcome#LIFTED} のときのみ非 {@code null}。
 *                                 {@code methodSymbol.metadata.declaringType} に載せる)
 */
public record AttributionResult(
        Outcome outcome,
        String attributedBinaryName,
        boolean inherited,
        String declaringTypeBinaryName) {

    /** 帰属型判定で選択された分岐。 */
    public enum Outcome {
        /** 宣言サイトが scope 内。帰属型 = 宣言型。 */
        SCOPE_INTERNAL,
        /** 宣言サイトが scope 外、レシーバの静的型へ引き上げ。 */
        LIFTED,
        /** 宣言サイトが scope 外で、宣言型が引き上げ除外 package。出力しない ({@code diagnostic} も出さない)。 */
        OMIT_EXCLUDED,
        /** 宣言サイトが scope 外で、レシーバの静的型も scope 外。出力しない ({@code diagnostic} も出さない)。 */
        OMIT_OUT_OF_SCOPE
    }

    public static AttributionResult scopeInternal(String binaryName) {
        return new AttributionResult(Outcome.SCOPE_INTERNAL, binaryName, false, null);
    }

    /**
     * scope 内の receiver 型へ引き上げる結果を生成する。
     *
     * @param declaringTypeBinaryName scope 外にある引き上げ元の宣言型 binary name
     */
    public static AttributionResult lifted(String receiverBinaryName, String declaringTypeBinaryName) {
        return new AttributionResult(Outcome.LIFTED, receiverBinaryName, true, declaringTypeBinaryName);
    }

    /** 除外 package のため出力しない結果を生成する。 */
    public static AttributionResult omitExcluded() {
        return new AttributionResult(Outcome.OMIT_EXCLUDED, null, false, null);
    }

    /** 宣言型と receiver 型が共に scope 外のため出力しない結果を生成する。 */
    public static AttributionResult omitOutOfScope() {
        return new AttributionResult(Outcome.OMIT_OUT_OF_SCOPE, null, false, null);
    }

    /** graph 出力対象外 (いずれかの omit 分岐) かを返す。 */
    public boolean isOmitted() {
        return outcome == Outcome.OMIT_EXCLUDED || outcome == Outcome.OMIT_OUT_OF_SCOPE;
    }
}
