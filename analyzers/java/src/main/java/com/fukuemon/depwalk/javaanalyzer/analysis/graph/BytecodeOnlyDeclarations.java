package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.InjectedDeclarations;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserConstructorDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;

/**
 * solver が解決した宣言が、source に無く bytecode にだけある member かを判定する。
 *
 * <p>そうした member は、solver が合成した宣言か、AST へ注入した宣言として solver に見えている。
 */
final class BytecodeOnlyDeclarations {

    private BytecodeOnlyDeclarations() {
    }

    /** 解決結果が AST 注入の bytecode-only constructor ならその candidate。 */
    static SootUpTypeHierarchyIndex.MethodCandidate constructorCandidate(
            ResolvedConstructorDeclaration resolved) {
        if (resolved instanceof JavaParserConstructorDeclaration<?> declaration
                && InjectedDeclarations.isInjected(declaration.getWrappedNode())) {
            return declaration.getWrappedNode().getData(InjectedDeclarations.KEY);
        }
        return null;
    }

    /** 解決結果が solver 合成または AST 注入の bytecode-only member ならその candidate。 */
    static SootUpTypeHierarchyIndex.MethodCandidate methodCandidate(
            ResolvedMethodDeclaration resolved) {
        if (resolved instanceof SynthesizedBytecodeMethodDeclaration synthesized) {
            return synthesized.candidate();
        }
        if (resolved instanceof JavaParserMethodDeclaration declaration
                && InjectedDeclarations.isInjected(declaration.getWrappedNode())) {
            return declaration.getWrappedNode().getData(InjectedDeclarations.KEY);
        }
        return null;
    }
}
