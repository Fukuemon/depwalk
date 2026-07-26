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
 * scope 内 source 型の宣言に合成される bytecode-only method
 * (java-analyzer feature doc「solver 層の bytecode member 合成」)。
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
        /**
         * erasure 済み binary name を ResolvedType へ解決する。
         *
         * @param binaryName primitive / 配列 / 参照型の binary name
         * @return 対応する ResolvedType
         */
        ResolvedType resolve(String binaryName);
    }

    /**
     * 戻り値も erasure で解決する合成 member を作る。
     *
     * @param declaringType 合成先の source 型宣言
     * @param candidate SootUp が classes output から読んだ member 候補
     * @param typeResolver binary name → ResolvedType の変換
     */
    public SynthesizedBytecodeMethodDeclaration(
            ResolvedReferenceTypeDeclaration declaringType,
            SootUpTypeHierarchyIndex.MethodCandidate candidate,
            BytecodeTypeResolver typeResolver) {
        this(declaringType, candidate, typeResolver, null);
    }

    /**
     * 戻り値の解決方法を指定して合成 member を作る。
     *
     * @param declaringType 合成先の source 型宣言
     * @param candidate SootUp が classes output から読んだ member 候補
     * @param typeResolver binary name → ResolvedType の変換
     * @param genericReturnType 戻り値だけを generic Signature 由来で解決する
     *     supplier (feature doc「solver 層の bytecode member 合成」)。引数型は常に
     *     {@code typeResolver} の erasure を使う。{@code null} なら戻り値も erasure
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
        // 宣言済み例外は 0 件 (getNumberOfSpecifiedExceptions)。範囲外 index は
        // 呼び出し側の契約違反であり、UOE で resolution failure に化けさせない。
        throw new IndexOutOfBoundsException(
                "synthesized bytecode member has no declared exceptions: index " + index);
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
        // JVM descriptor を erasure 済み binary name から決定的に構築する
        // (UOE を投げると JavaParser 内部経路経由で resolution failure に化ける)。
        StringBuilder descriptor = new StringBuilder("(");
        for (String parameterType : candidate.parameterTypes()) {
            descriptor.append(jvmTypeDescriptor(parameterType));
        }
        return descriptor.append(')').append(jvmTypeDescriptor(candidate.returnType())).toString();
    }

    private static String jvmTypeDescriptor(String binaryName) {
        if (binaryName.endsWith("[]")) {
            return "[" + jvmTypeDescriptor(binaryName.substring(0, binaryName.length() - 2));
        }
        return switch (binaryName) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "char" -> "C";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + binaryName.replace('.', '/') + ";";
        };
    }

    @Override
    public Optional<Node> toAst() {
        return Optional.empty();
    }
}
