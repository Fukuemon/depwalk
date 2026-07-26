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
 * (java-analyzer feature doc「solver 層の bytecode member 合成」)。
 * {@code instanceof JavaParserClassDeclaration} に依存する
 * solver 内部経路を壊さないため、wrapper でなく subclass にする。
 */
public final class AugmentedJavaParserClassDeclaration extends JavaParserClassDeclaration {

    private final TypeSolver typeSolver;
    private final ProjectBytecodeMemberIndex bytecodeIndex;

    /**
     * source の class 宣言を包み、bytecode 合成の fallback を持つ宣言を作る。
     *
     * @param wrappedNode 対象の source class 宣言
     * @param typeSolver 型解決に使う solver
     * @param bytecodeIndex 同一 context の classes output を引く member 索引
     */
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
        // 一意な name + arity の場合だけ採用し、曖昧なら合成しない
        // (adr/0005-adopt-sootup-and-spring-di-resolution.md の
        //  project bytecode member index と同じ規則)。
        // static context の解決 (staticOnly) では instance member を採用しない。
        return synthesizedInHierarchy(name, argumentsTypes.size())
                .filter(synthesized -> !staticOnly || synthesized.isStatic())
                .<SymbolReference<ResolvedMethodDeclaration>>map(SymbolReference::solved)
                .orElse(solved);
    }

    @Override
    public java.util.Optional<com.github.javaparser.resolution.MethodUsage> solveMethodAsUsage(
            String name,
            List<ResolvedType> argumentTypes,
            com.github.javaparser.resolution.Context invokationContext,
            List<ResolvedType> typeParameterValues) {
        java.util.Optional<com.github.javaparser.resolution.MethodUsage> solved;
        try {
            solved = super.solveMethodAsUsage(name, argumentTypes, invokationContext, typeParameterValues);
        } catch (RuntimeException e) {
            // JavaParser は未解決を Optional.empty でなく例外で返す経路があるため、
            // 合成 fallback まで到達させる。
            solved = java.util.Optional.empty();
        }
        if (solved.isPresent()) {
            return solved;
        }
        // 式の型伝播 (chained call) は usage 経路を通るため、宣言 fallback と
        // 同じ規則で合成 member を MethodUsage 化する。MethodUsage の構築は
        // 全 param 型を即時解決するため、classpath に無い型を含む member は
        // 合成せず未解決のまま返す
        // (単発呼び出し側は project bytecode member index 経路が拾う)。
        try {
            return synthesizedInHierarchy(name, argumentTypes.size())
                    .map(com.github.javaparser.resolution.MethodUsage::new);
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
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
                declared.add(synthesize(this, candidate));
            }
        }
        return declared;
    }

    /**
     * 自型と source 祖先 (augmented) の bytecode declared member から一意な
     * name + arity の候補を探す。usage 経路の階層走査は JavaParser 内部で
     * 完結しないため、fallback 側で祖先まで探索する。
     */
    private Optional<SynthesizedBytecodeMethodDeclaration> synthesizedInHierarchy(String name, int arity) {
        Optional<SootUpTypeHierarchyIndex.MethodCandidate> own =
                bytecodeIndex.uniqueMethod(binaryName(), name, arity);
        if (own.isPresent()) {
            return Optional.of(synthesize(this, own.get()));
        }
        try {
            for (var ancestor : getAllAncestors()) {
                var declaration = ancestor.getTypeDeclaration().orElse(null);
                if (declaration instanceof AugmentedJavaParserClassDeclaration augmented) {
                    Optional<SootUpTypeHierarchyIndex.MethodCandidate> inherited =
                            augmented.bytecodeIndex.uniqueMethod(augmented.binaryName(), name, arity);
                    if (inherited.isPresent()) {
                        return Optional.of(augmented.synthesize(augmented, inherited.get()));
                    }
                }
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * generic Signature (feature doc「solver 層の bytecode member 合成」) があれば
     * 実型引数付きの戻り値 resolver で合成する。
     */
    private SynthesizedBytecodeMethodDeclaration synthesize(
            AugmentedJavaParserClassDeclaration owner, SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        var genericReturn = owner.bytecodeIndex.genericReturnType(candidate);
        if (genericReturn.isEmpty()) {
            return new SynthesizedBytecodeMethodDeclaration(owner, candidate, owner::resolveBinaryName);
        }
        var model = genericReturn.get();
        // 戻り値専用の supplier で解決し、同じ erasure 名を持つ引数型へ
        // 戻り値の generic model が漏れないようにする (引数型は erasure のまま)。
        return new SynthesizedBytecodeMethodDeclaration(
                owner, candidate, owner::resolveBinaryName, () -> owner.resolveGenericModel(model));
    }

    /** {@link GenericSignatureReader.BytecodeType} を ResolvedType へ解決する。 */
    ResolvedType resolveGenericModel(com.fukuemon.depwalk.javaanalyzer.analysis.augment.GenericSignatureReader.BytecodeType model) {
        if (model.typeVariable()) {
            // 型変数は erasure (Object) へ写像し、自己写像の無限再帰を避ける。
            return referenceType("java.lang.Object");
        }
        ResolvedType base;
        if (model.typeArguments().isEmpty()) {
            base = resolveBinaryName(model.binaryName());
        } else {
            var declaration = typeSolver.solveType(model.binaryName());
            List<ResolvedType> arguments = new java.util.ArrayList<>();
            for (var argument : model.typeArguments()) {
                arguments.add(resolveGenericModel(argument));
            }
            // 宣言の型変数数と一致しない場合は Object で補正する (raw / 内部 class 差)。
            while (arguments.size() < declaration.getTypeParameters().size()) {
                arguments.add(referenceType("java.lang.Object"));
            }
            if (arguments.size() > declaration.getTypeParameters().size()) {
                arguments = arguments.subList(0, declaration.getTypeParameters().size());
            }
            base = new com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl(declaration, arguments);
        }
        for (int i = 0; i < model.arrayDims(); i++) {
            base = new ResolvedArrayType(base);
        }
        return base;
    }

    /**
     * JavaParser の {@code JavaParserClassDeclaration#equals} は {@code getClass()}
     * 一致を要求するため、subclass のままでは同じ型の plain 宣言 (Context 経由で
     * 生成される) と等価にならず、階層走査の重複や偽の型不一致を生む。同じ
     * wrappedNode を指す {@code JavaParserClassDeclaration} 系宣言を等価として
     * 扱う (plain 側から見た比較は false のままの非対称性が残るが、集合への
     * 追加順で吸収される。根本解消は JavaParser 側の equality 契約に依存)。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof JavaParserClassDeclaration otherDeclaration
                && getWrappedNode().equals(otherDeclaration.getWrappedNode());
    }

    @Override
    public int hashCode() {
        return getWrappedNode().hashCode();
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
