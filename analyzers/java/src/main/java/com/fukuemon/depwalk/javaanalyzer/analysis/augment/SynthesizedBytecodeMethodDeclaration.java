package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.ast.Node;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;
import java.util.Optional;

/**
 * scope 内 source 型の宣言に合成される bytecode-only method (spec #24 D31)。
 * SootUp が classes output から読んだ {@code MethodCandidate} を JavaParser の
 * 解決結果として振る舞わせ、Lombok 等の生成 member を含む式の型伝播
 * (chained call / stream 連鎖) を solver 層で成立させる。
 * source AST を持たないため {@code toAst()} は empty。
 */
public final class SynthesizedBytecodeMethodDeclaration implements ResolvedMethodDeclaration {

    private final ResolvedReferenceTypeDeclaration declaringType;
    private final SootUpTypeHierarchyIndex.MethodCandidate candidate;
    private final BytecodeTypeResolver typeResolver;
    private final java.util.function.Supplier<ResolvedType> genericReturnType;

    /** binary name → ResolvedType の変換 (primitive / array / 参照型)。 */
    public interface BytecodeTypeResolver {
        ResolvedType resolve(String binaryName);
    }

    public SynthesizedBytecodeMethodDeclaration(
            ResolvedReferenceTypeDeclaration declaringType,
            SootUpTypeHierarchyIndex.MethodCandidate candidate,
            BytecodeTypeResolver typeResolver) {
        this(declaringType, candidate, typeResolver, null);
    }

    /**
     * @param genericReturnType 戻り値だけを generic Signature 由来で解決する
     *     supplier (D32)。引数型は常に {@code typeResolver} の erasure を使う
     */
    public SynthesizedBytecodeMethodDeclaration(
            ResolvedReferenceTypeDeclaration declaringType,
            SootUpTypeHierarchyIndex.MethodCandidate candidate,
            BytecodeTypeResolver typeResolver,
            java.util.function.Supplier<ResolvedType> genericReturnType) {
        this.declaringType = declaringType;
        this.candidate = candidate;
        this.typeResolver = typeResolver;
        this.genericReturnType = genericReturnType;
    }

    /** 合成元の bytecode candidate (owner metadata 構築用)。 */
    public SootUpTypeHierarchyIndex.MethodCandidate candidate() {
        return candidate;
    }

    @Override
    public ResolvedType getReturnType() {
        if (genericReturnType != null) {
            return genericReturnType.get();
        }
        return typeResolver.resolve(candidate.returnType());
    }

    @Override
    public String getName() {
        return candidate.methodName();
    }

    @Override
    public ResolvedReferenceTypeDeclaration declaringType() {
        return declaringType;
    }

    @Override
    public int getNumberOfParams() {
        return candidate.parameterTypes().size();
    }

    @Override
    public ResolvedParameterDeclaration getParam(int index) {
        String parameterType = candidate.parameterTypes().get(index);
        return new ResolvedParameterDeclaration() {
            @Override
            public String getName() {
                return "arg" + index;
            }

            @Override
            public ResolvedType getType() {
                return typeResolver.resolve(parameterType);
            }

            @Override
            public boolean isVariadic() {
                return false;
            }
        };
    }

    @Override
    public int getNumberOfSpecifiedExceptions() {
        return 0;
    }

    @Override
    public ResolvedType getSpecifiedException(int index) {
        throw new UnsupportedOperationException("synthesized bytecode member has no declared exceptions");
    }

    @Override
    public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
        return List.of();
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isDefaultMethod() {
        return false;
    }

    @Override
    public boolean isStatic() {
        return candidate.isStatic();
    }

    @Override
    public com.github.javaparser.ast.AccessSpecifier accessSpecifier() {
        return com.github.javaparser.ast.AccessSpecifier.PUBLIC;
    }

    @Override
    public String toDescriptor() {
        // JVM descriptor は erasure 済み binary name から決定的に構築できるが、
        // 呼び出し経路が無い限り実装しない (使用箇所が現れたら実装する)。
        throw new UnsupportedOperationException("descriptor of a synthesized bytecode member");
    }

    @Override
    public Optional<Node> toAst() {
        return Optional.empty();
    }
}
