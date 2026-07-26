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

    public enum Kind {
        SOURCE,
        PROJECT_CLASSES,
        EXTERNAL_ARTIFACT,
        JDK
    }

    public static ResolvedDeclarationOrigin source(String contextId) {
        return new ResolvedDeclarationOrigin(Kind.SOURCE, contextId);
    }

    public static ResolvedDeclarationOrigin projectClasses(String contextId) {
        return new ResolvedDeclarationOrigin(Kind.PROJECT_CLASSES, contextId);
    }

    public static ResolvedDeclarationOrigin externalArtifact(String identity) {
        return new ResolvedDeclarationOrigin(Kind.EXTERNAL_ARTIFACT, identity);
    }

    public static ResolvedDeclarationOrigin jdk() {
        return new ResolvedDeclarationOrigin(Kind.JDK, "");
    }
}
