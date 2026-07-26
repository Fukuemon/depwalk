package com.fukuemon.depwalk.javaanalyzer.analysis.context;

/**
 * solver entry / 解決結果の内部 origin
 * (java-analyzer feature doc「Source root discovery と解析 context」)。
 * source 再対応付けは
 * 本 origin と context 到達可能性だけを根拠にし、名前一致で external / JDK /
 * 非依存 context の宣言を workspace source へ戻さない。Protocol へは出力しない。
 *
 * @param kind origin 種別
 * @param identity {@code SOURCE} / {@code PROJECT_CLASSES} は context id、
 *     {@code EXTERNAL_ARTIFACT} は artifact path、{@code JDK} は空文字
 */
public record ResolvedDeclarationOrigin(Kind kind, String identity) {

    /** origin 種別。 */
    public enum Kind {
        /** 解析 context の source root 由来。 */
        SOURCE,
        /** 解析 context が所有する project classes output 由来。 */
        PROJECT_CLASSES,
        /** classpath 上の external artifact 由来。 */
        EXTERNAL_ARTIFACT,
        /** JDK 由来。 */
        JDK
    }

    /**
     * source root 由来の origin を作る。
     *
     * @param contextId 所有解析 context の id
     * @return {@code SOURCE} origin
     */
    public static ResolvedDeclarationOrigin source(String contextId) {
        return new ResolvedDeclarationOrigin(Kind.SOURCE, contextId);
    }

    /**
     * project classes output 由来の origin を作る。
     *
     * @param contextId 所有解析 context の id
     * @return {@code PROJECT_CLASSES} origin
     */
    public static ResolvedDeclarationOrigin projectClasses(String contextId) {
        return new ResolvedDeclarationOrigin(Kind.PROJECT_CLASSES, contextId);
    }

    /**
     * external artifact 由来の origin を作る。
     *
     * @param identity artifact path
     * @return {@code EXTERNAL_ARTIFACT} origin
     */
    public static ResolvedDeclarationOrigin externalArtifact(String identity) {
        return new ResolvedDeclarationOrigin(Kind.EXTERNAL_ARTIFACT, identity);
    }

    /**
     * JDK 由来の origin を作る (identity は空文字)。
     *
     * @return {@code JDK} origin
     */
    public static ResolvedDeclarationOrigin jdk() {
        return new ResolvedDeclarationOrigin(Kind.JDK, "");
    }
}
