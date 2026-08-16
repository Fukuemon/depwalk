package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;

/**
 * parse 後に AST へ注入した宣言 (bytecode-only member) の標識。注入宣言かどうかの
 * 判定をここへ一本化し、caller 帰属・walk skip・emit 分岐が同じ基準を使う。
 */
public final class InjectedDeclarations {

    /** 注入宣言に付ける標識。由来の bytecode candidate を保持する。 */
    public static final DataKey<SootUpTypeHierarchyIndex.MethodCandidate> KEY = new DataKey<>() {
    };

    private InjectedDeclarations() {
    }

    /** node が注入宣言なら true。 */
    public static boolean isInjected(Node node) {
        return node.containsData(KEY);
    }
}
