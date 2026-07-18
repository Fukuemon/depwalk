package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedVoidType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;

import java.util.List;
import java.util.Optional;

/**
 * source の class 宣言を継承し、source で解決できない method 呼び出しだけを
 * 同一 context の classes output の一意 member へ fallback する宣言
 * (spec #24 D31)。{@code instanceof JavaParserClassDeclaration} に依存する
 * solver 内部経路を壊さないため、wrapper でなく subclass にする。
 */
public final class AugmentedJavaParserClassDeclaration extends JavaParserClassDeclaration {

    private final TypeSolver typeSolver;
    private final ProjectBytecodeMemberIndex bytecodeIndex;

    public AugmentedJavaParserClassDeclaration(
            ClassOrInterfaceDeclaration wrappedNode,
            TypeSolver typeSolver,
            ProjectBytecodeMemberIndex bytecodeIndex) {
        super(wrappedNode, typeSolver);
        this.typeSolver = typeSolver;
        this.bytecodeIndex = bytecodeIndex;
    }

    @Override
    public SymbolReference<ResolvedMethodDeclaration> solveMethod(
            String name, List<ResolvedType> argumentsTypes, boolean staticOnly) {
        SymbolReference<ResolvedMethodDeclaration> solved = super.solveMethod(name, argumentsTypes, staticOnly);
        if (solved.isSolved()) {
            return solved;
        }
        // source AST に無い member を同一 context の classes output から合成する。
        // 一意な name + arity の場合だけ採用し、曖昧なら合成しない (D18 と同じ規則)。
        Optional<SootUpTypeHierarchyIndex.MethodCandidate> candidate =
                bytecodeIndex.uniqueMethod(binaryName(), name, argumentsTypes.size());
        if (candidate.isEmpty()) {
            return solved;
        }
        return SymbolReference.solved(
                new SynthesizedBytecodeMethodDeclaration(this, candidate.get(), this::resolveBinaryName));
    }

    @Override
    public java.util.Optional<com.github.javaparser.resolution.MethodUsage> solveMethodAsUsage(
            String name,
            List<ResolvedType> argumentTypes,
            com.github.javaparser.resolution.Context invokationContext,
            List<ResolvedType> typeParameterValues) {
        java.util.Optional<com.github.javaparser.resolution.MethodUsage> solved =
                super.solveMethodAsUsage(name, argumentTypes, invokationContext, typeParameterValues);
        if (solved.isPresent()) {
            return solved;
        }
        // 式の型伝播 (chained call) は usage 経路を通るため、宣言 fallback と
        // 同じ規則で合成 member を MethodUsage 化する。
        return bytecodeIndex.uniqueMethod(binaryName(), name, argumentTypes.size())
                .map(candidate -> new com.github.javaparser.resolution.MethodUsage(
                        new SynthesizedBytecodeMethodDeclaration(this, candidate, this::resolveBinaryName)));
    }

    @Override
    public java.util.Set<ResolvedMethodDeclaration> getDeclaredMethods() {
        // 継承した生成 member の解決は JavaParser の階層走査 (各祖先の
        // getDeclaredMethods) を通るため、宣言一覧にも bytecode-only member を
        // 合成する。source に同じ name + arity がある member は合成しない。
        java.util.Set<ResolvedMethodDeclaration> declared =
                new java.util.LinkedHashSet<>(super.getDeclaredMethods());
        java.util.Set<String> sourceKeys = new java.util.HashSet<>();
        for (ResolvedMethodDeclaration method : declared) {
            sourceKeys.add(method.getName() + "/" + method.getNumberOfParams());
        }
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate
                : bytecodeIndex.declaredCallableMethods(binaryName())) {
            if (sourceKeys.add(candidate.methodName() + "/" + candidate.parameterTypes().size())) {
                declared.add(new SynthesizedBytecodeMethodDeclaration(this, candidate, this::resolveBinaryName));
            }
        }
        return declared;
    }

    /** JavaParser の qualified name (nested は {@code .}) を binary name へ変換する。 */
    private String binaryName() {
        // AST 構造から nested を $ で連結する (BinaryNames と同じ規則)。
        return com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames.forTypeLikeNode(getWrappedNode());
    }

    /**
     * erasure 済み binary name を参照型へ解決する。generic 宣言をそのまま
     * ReferenceTypeImpl 化すると型変数が自分自身へ写像され JavaParser の
     * 型引数置換が無限再帰するため (実環境検証で StackOverflowError)、
     * 型引数は {@code Object} で埋めた erased 形にする。
     */
    private ResolvedType referenceType(String binaryName) {
        var declaration = typeSolver.solveType(binaryName);
        if (declaration.getTypeParameters().isEmpty()) {
            return new com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl(declaration);
        }
        ResolvedType objectType = new com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl(
                typeSolver.solveType("java.lang.Object"));
        return new com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl(
                declaration,
                declaration.getTypeParameters().stream().map(tp -> objectType).toList());
    }

    private ResolvedType resolveBinaryName(String binaryName) {
        if (binaryName.endsWith("[]")) {
            return new ResolvedArrayType(resolveBinaryName(binaryName.substring(0, binaryName.length() - 2)));
        }
        return switch (binaryName) {
            case "void" -> ResolvedVoidType.INSTANCE;
            case "boolean" -> ResolvedPrimitiveType.BOOLEAN;
            case "byte" -> ResolvedPrimitiveType.BYTE;
            case "short" -> ResolvedPrimitiveType.SHORT;
            case "int" -> ResolvedPrimitiveType.INT;
            case "long" -> ResolvedPrimitiveType.LONG;
            case "char" -> ResolvedPrimitiveType.CHAR;
            case "float" -> ResolvedPrimitiveType.FLOAT;
            case "double" -> ResolvedPrimitiveType.DOUBLE;
            default -> referenceType(binaryName);
        };
    }
}
