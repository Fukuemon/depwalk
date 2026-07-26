package com.fukuemon.depwalk.javaanalyzer.analysis.attribution;

import java.util.List;
import java.util.Map;

/**
 * 帰属型の決定規則における「引き上げ除外 package」。
 * 既定値 {@code java} / {@code javax} / {@code jakarta}。{@code analysisRequest.metadata} の
 * {@code liftExcludePackages} 指定時は既定値を置き換える (追加ではない)。
 * 判定は宣言型の binary name に対する {@code .} 区切り segment 単位の prefix 一致。
 *
 * <p>{@code liftExcludePackages} の型検証 (List か、要素が String か) は
 * {@link com.fukuemon.depwalk.javaanalyzer.preflight.PreflightValidator} が解析開始前に行う。
 * 本クラスは検証済みの入力を受け取る前提で、型不正時のフォールバックは持たない。
 */
public final class LiftExcludePackages {

    private static final String METADATA_KEY = "liftExcludePackages";
    private static final List<String> DEFAULT_PREFIXES = List.of("java", "javax", "jakarta");

    private final List<String> prefixes;

    private LiftExcludePackages(List<String> prefixes) {
        this.prefixes = prefixes;
    }

    /**
     * @param metadata 検証済みの analysis request metadata ({@code null} 可)
     * @return metadata に指定があればその値、なければ既定 package を使う規則
     */
    public static LiftExcludePackages fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || !metadata.containsKey(METADATA_KEY)) {
            return new LiftExcludePackages(DEFAULT_PREFIXES);
        }
        List<?> rawList = (List<?>) metadata.get(METADATA_KEY);
        List<String> values = rawList.stream().map(String.class::cast).toList();
        return new LiftExcludePackages(values);
    }

    /** 宣言型の binary name が除外 package に属するかを {@code .} 区切り segment 単位の prefix 一致で判定する。 */
    public boolean excludes(String declaringTypeBinaryName) {
        for (String prefix : prefixes) {
            if (declaringTypeBinaryName.equals(prefix) || declaringTypeBinaryName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }
}
