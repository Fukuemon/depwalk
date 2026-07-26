package com.fukuemon.depwalk.javaanalyzer.analysis.attribution;

import java.nio.file.Path;
import java.util.Set;

/**
 * 帰属型の決定規則 (design/features/java-analyzer/DesignDoc_java-analyzer.md 「帰属型の決定規則」) の実装。
 *
 * <table>
 * <caption>4 分岐</caption>
 * <tr><td>宣言サイトが scope 内</td><td>宣言型 (宣言の所在型)</td></tr>
 * <tr><td>宣言サイトが scope 外、レシーバの静的型が scope 内、宣言型が引き上げ除外 package でない</td>
 *     <td>レシーバの静的型へ引き上げる</td></tr>
 * <tr><td>宣言サイトが scope 外で、宣言型が引き上げ除外 package</td><td>出力しない</td></tr>
 * <tr><td>宣言サイトが scope 外で、レシーバの静的型も scope 外</td><td>出力しない</td></tr>
 * </table>
 *
 * <p>constructor (「new Foo()」) は継承されないため引き上げは発生しない
 * ({@link #resolveConstructor(TypeSite)} を使う)。
 */
public final class AttributionResolver {

    private final Set<Path> scopeFiles;
    private final LiftExcludePackages liftExcludePackages;

    /**
     * @param scopeFiles 解析対象 source file の正規化済み path 集合 (正規化前の path は一致しない)
     */
    public AttributionResolver(Set<Path> scopeFiles, LiftExcludePackages liftExcludePackages) {
        this.scopeFiles = scopeFiles;
        this.liftExcludePackages = liftExcludePackages;
    }

    private boolean isScopeInternal(TypeSite site) {
        return site.sourceFilePath() != null && scopeFiles.contains(site.sourceFilePath());
    }

    /**
     * method 呼び出し (static / instance / this / super) の帰属型を決定する。
     *
     * @param receiverSite 呼び出し式の静的 receiver 型。取得できなければ {@code null} (引き上げは発生しない)
     */
    public AttributionResult resolveMethod(TypeSite declaringSite, TypeSite receiverSite) {
        if (isScopeInternal(declaringSite)) {
            return AttributionResult.scopeInternal(declaringSite.binaryName());
        }
        if (liftExcludePackages.excludes(declaringSite.binaryName())) {
            return AttributionResult.omitExcluded();
        }
        if (receiverSite != null && isScopeInternal(receiverSite)) {
            return AttributionResult.lifted(receiverSite.binaryName(), declaringSite.binaryName());
        }
        return AttributionResult.omitOutOfScope();
    }

    /**
     * constructor 呼び出し ({@code new Foo(...)} / {@code this(...)} / {@code super(...)}) の帰属型を決定する。
     */
    public AttributionResult resolveConstructor(TypeSite declaringSite) {
        if (isScopeInternal(declaringSite)) {
            return AttributionResult.scopeInternal(declaringSite.binaryName());
        }
        return AttributionResult.omitOutOfScope();
    }
}
