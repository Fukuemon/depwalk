package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;

import java.util.HashMap;
import java.util.Map;

/**
 * scope 内 source root の {@link JavaParserTypeSolver} を包み、source 解決した
 * class 宣言へ同一 context の classes output にしかない callable member を
 * 解決時に合成する。本クラスの契約の正本は java-analyzer feature doc
 * 「solver 層の bytecode member 合成」(context の対応づけは「Source root discovery と解析 context」、
 * 帰属規則は「Parse・resolution・call 完全性」)。
 * source 宣言と帰属規則は変更せず、source AST に無い member の解決だけを bytecode で補う。
 * 合成は {@link AugmentedJavaParserClassDeclaration#solveMethod} の fallback で
 * 行い、source で解決できる member には一切影響しない。
 */
public final class MemberAugmentingTypeSolver implements TypeSolver {

    private final JavaParserTypeSolver delegate;
    private final ProjectBytecodeMemberIndex bytecodeIndex;
    private TypeSolver parent;
    // hot path のため、型名ごとに augmented 宣言を 1 instance へ固定する
    // (delegate の cache と同様の identity 安定化)。
    private final Map<String, SymbolReference<ResolvedReferenceTypeDeclaration>> cache = new HashMap<>();

    /**
     * @param bytecodeIndex delegate と同一解析 context の classes output を引く member 索引
     *     (別 context の索引を渡すと source と bytecode の対応が崩れる)
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
