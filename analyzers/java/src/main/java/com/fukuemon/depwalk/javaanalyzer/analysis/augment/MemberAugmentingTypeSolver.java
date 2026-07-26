package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;

/**
 * scope 内 source root の {@link JavaParserTypeSolver} を包み、source 解決した
 * class 宣言へ同一 context の classes output にしかない callable member を
 * 解決時に合成する (java-analyzer feature doc「solver 層の bytecode member 合成」)。
 * source 宣言と帰属規則 (feature doc「Source root discovery と解析 context」/
 * 「Parse・resolution・call 完全性」) は
 * 変更せず、source AST に無い member の解決だけを bytecode で補う。
 * 合成は {@link AugmentedJavaParserClassDeclaration#solveMethod} の fallback で
 * 行い、source で解決できる member には一切影響しない。
 */
public final class MemberAugmentingTypeSolver implements TypeSolver {

    private final JavaParserTypeSolver delegate;
    private final ProjectBytecodeMemberIndex bytecodeIndex;
    private TypeSolver parent;
    // hot path のため、型名ごとに augmented 宣言を 1 instance へ固定する
    // (delegate の cache と同様の identity 安定化)。
    private final java.util.Map<String, SymbolReference<ResolvedReferenceTypeDeclaration>> cache =
            new java.util.HashMap<>();

    /**
     * source root の solver を包み、member 合成を差し込む solver を作る。
     *
     * @param delegate scope 内 source root の型解決を担う solver
     * @param bytecodeIndex 同一 context の classes output を引く member 索引
     */
    public MemberAugmentingTypeSolver(JavaParserTypeSolver delegate, ProjectBytecodeMemberIndex bytecodeIndex) {
        this.delegate = delegate;
        this.bytecodeIndex = bytecodeIndex;
    }

    @Override
    public TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(TypeSolver parent) {
        this.parent = parent;
        delegate.setParent(parent);
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(String moduleName, String name) {
        // module 境界は delegate と同じ規則 (module 非対応の source solver)。
        return tryToSolveType(name);
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        SymbolReference<ResolvedReferenceTypeDeclaration> solved = delegate.tryToSolveType(name);
        if (!solved.isSolved()) {
            return solved;
        }
        ResolvedReferenceTypeDeclaration declaration = solved.getCorrespondingDeclaration();
        // Lombok 等の生成 member は class 宣言に付く。他種別 (interface / enum /
        // record) は source 宣言をそのまま使う (必要になったら種別を追加する)。
        if (declaration instanceof JavaParserClassDeclaration classDeclaration
                && !(declaration instanceof AugmentedJavaParserClassDeclaration)) {
            return cache.computeIfAbsent(name, key -> {
                ClassOrInterfaceDeclaration wrapped = classDeclaration.getWrappedNode();
                return SymbolReference.solved(
                        new AugmentedJavaParserClassDeclaration(wrapped, getRoot(), bytecodeIndex));
            });
        }
        return solved;
    }

}
