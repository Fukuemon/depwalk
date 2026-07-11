package com.fukuemon.depwalk.javaanalyzer.analysis.attribution;

import java.util.List;
import java.util.Map;

/**
 * 帰属型の決定規則における「引き上げ除外 package」。
 * 既定値 {@code java} / {@code javax} / {@code jakarta}。{@code analysisRequest.metadata} の
 * {@code liftExcludePackages} 指定時は既定値を置き換える (追加ではない)。
 * 判定は宣言型の binary name に対する {@code .} 区切り segment 単位の prefix 一致。
 */
public final class LiftExcludePackages {

    private static final String METADATA_KEY = "liftExcludePackages";
    private static final List<String> DEFAULT_PREFIXES = List.of("java", "javax", "jakarta");

    private final List<String> prefixes;

    private LiftExcludePackages(List<String> prefixes) {
        this.prefixes = prefixes;
    }

    public static LiftExcludePackages fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || !metadata.containsKey(METADATA_KEY)) {
            return new LiftExcludePackages(DEFAULT_PREFIXES);
        }
        Object raw = metadata.get(METADATA_KEY);
        if (!(raw instanceof List<?> rawList)) {
            return new LiftExcludePackages(DEFAULT_PREFIXES);
        }
        List<String> values = rawList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        return new LiftExcludePackages(values);
    }

    /** 宣言型の binary name が除外 package に属するか (segment 単位 prefix 一致)。 */
    public boolean excludes(String declaringTypeBinaryName) {
        for (String prefix : prefixes) {
            if (declaringTypeBinaryName.equals(prefix) || declaringTypeBinaryName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }
}
