package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 帰属型決定の入力となる {@link TypeSite} (宣言型 / レシーバ型)、dispatch 種別、
 * パラメータ erasure 型列を AST と解決済み宣言から求める。
 */
final class AttributionSites {

    private AttributionSites() {
    }

    static TypeSite typeSiteOf(ResolvedReferenceTypeDeclaration decl) {
        Node ast = decl.toAst().orElse(null);
        String binaryName = BinaryNames.forResolvedDeclaration(decl);
        Path filePath = ast != null ? SourceLocations.filePathOf(ast) : null;
        return new TypeSite(binaryName, filePath);
    }

    /**
     * mce の scope (レシーバ式) から帰属型決定に使う {@link TypeSite} を決める。
     *
     * <p>scope が空 (無修飾呼び出し) のとき、通常は enclosing class を「参照した型」とみなす
     * (自クラス呼び出し / 継承 static・instance メソッドの暗黙 this 呼び出し)。ただし static かつ
     * 宣言型が enclosing class の継承階層に含まれない場合は、無修飾 static import
     * ({@code import static pkg.Type.member;}) 由来の呼び出しであり、「参照した型」は enclosing class
     * ではなく宣言型そのものである。そのため receiverSite = declaringSite として扱い、宣言型が
     * scope 外なら enclosing への誤った引き上げを起こさず出力を省略する。
     */
    static TypeSite receiverSiteOf(
            MethodCallExpr mce,
            Node enclosingTypeNode,
            ResolvedMethodDeclaration resolved,
            TypeSite declaringSite) {
        if (mce.getScope().isEmpty()) {
            if (enclosingTypeNode == null) {
                return null;
            }
            if (resolved.isStatic() && !declaringTypeInEnclosingHierarchy(enclosingTypeNode, resolved.declaringType())) {
                return declaringSite;
            }
            return new TypeSite(
                    BinaryNames.forTypeLikeNode(enclosingTypeNode), SourceLocations.filePathOf(enclosingTypeNode));
        }
        return typeSiteOfExpression(mce.getScope().get());
    }

    /**
     * 式を評価した静的型の {@link TypeSite} を返す。
     *
     * <p>型変数や wildcard は、そのままでは reference type 宣言を取得できないため erasure を使う。
     * たとえば {@code T extends ChildService} の receiver は {@code ChildService} として扱い、
     * 上限境界に含まれない実装を dispatch 候補へ混入させない。型解決または erasure に失敗した場合は
     * {@code null} を返す。
     */
    static TypeSite typeSiteOfExpression(Expression expr) {
        try {
            ResolvedType erasedReceiverType = expr.calculateResolvedType().erasure();
            if (!erasedReceiverType.isReferenceType()) {
                return null;
            }
            ResolvedReferenceTypeDeclaration decl =
                    erasedReceiverType.asReferenceType().getTypeDeclaration().orElse(null);
            if (decl == null) {
                return null;
            }
            return typeSiteOf(decl);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * declaringType が enclosingTypeNode 自身または、その継承階層 (supertype / interface) に含まれるか
     * を判定する。判定不能な場合 (enclosing の型解決失敗 / ancestor 列挙が unresolved symbol で失敗) は、
     * 従来の「enclosing への引き上げ」挙動を壊さないよう保守的に {@code true} を返す。
     */
    private static boolean declaringTypeInEnclosingHierarchy(
            Node enclosingTypeNode, ResolvedReferenceTypeDeclaration declaringType) {
        String declaringBinaryName = BinaryNames.forResolvedDeclaration(declaringType);
        String enclosingBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        if (declaringBinaryName.equals(enclosingBinaryName)) {
            return true;
        }
        ResolvedReferenceTypeDeclaration enclosingDecl = resolveTypeLikeNode(enclosingTypeNode);
        if (enclosingDecl == null) {
            return true;
        }
        try {
            for (ResolvedReferenceType ancestor : enclosingDecl.getAllAncestors()) {
                ResolvedReferenceTypeDeclaration ancestorDecl = ancestor.getTypeDeclaration().orElse(null);
                if (ancestorDecl != null && declaringBinaryName.equals(BinaryNames.forResolvedDeclaration(ancestorDecl))) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
        return false;
    }

    private static ResolvedReferenceTypeDeclaration resolveTypeLikeNode(Node typeLikeNode) {
        try {
            if (typeLikeNode instanceof TypeDeclaration<?> td) {
                return td.resolve();
            }
            if (typeLikeNode instanceof ObjectCreationExpr oce) {
                ResolvedType type = oce.calculateResolvedType();
                return type.isReferenceType() ? type.asReferenceType().getTypeDeclaration().orElse(null) : null;
            }
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    static String dispatchOf(ResolvedMethodDeclaration resolved) {
        if (resolved.isStatic()) {
            return "static";
        }
        if (resolved.declaringType().isInterface()) {
            return "interface";
        }
        if (resolved.isAbstract()) {
            return "abstract";
        }
        return "virtual";
    }

    static List<String> paramBinaryNames(ResolvedMethodLikeDeclaration resolved) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            names.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
        }
        return names;
    }
}
