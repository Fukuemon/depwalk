package com.fukuemon.depwalk.javaanalyzer.analysis.normalize;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;

/**
 * D5 正規化規則 (型名 = JVM binary name / generics erasure / 配列・varargs) の実装。
 *
 * <p>JavaParser の {@code ResolvedReferenceTypeDeclaration#getQualifiedName()} は匿名クラスに対して
 * 呼び出しごとに異なるランダム UUID を返す (非決定的、実測で確認済み)。本クラスはこれに依存せず、
 * {@code toAst()} で得られる AST ノードからソース構造 (パッケージ宣言 + 型宣言の入れ子 + 匿名クラスの
 * ソース出現順) を辿って自前で binary name を計算することで、決定性を担保する。scope 外
 * (JDK reflection / jar 由来で AST を持たない) 宣言のみ、シンボルソルバの
 * {@code getPackageName()} / {@code getClassName()} にフォールバックする。
 */
public final class BinaryNames {

    private BinaryNames() {
    }

    /**
     * 型宣言 (named type or 匿名クラスの {@link ObjectCreationExpr}) の JVM binary name を計算する。
     */
    public static String forTypeLikeNode(Node typeLikeNode) {
        CompilationUnit cu = typeLikeNode.findCompilationUnit()
                .orElseThrow(() -> new IllegalArgumentException("node has no enclosing CompilationUnit"));
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<String> segments = new ArrayList<>();
        Node current = typeLikeNode;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> td) {
                segments.add(0, td.getNameAsString());
            } else if (current instanceof ObjectCreationExpr oce && oce.getAnonymousClassBody().isPresent()) {
                segments.add(0, String.valueOf(anonymousOrdinal(oce)));
            }
            current = current.getParentNode().orElse(null);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("node is not a type declaration or anonymous class: " + typeLikeNode);
        }
        String joined = String.join("$", segments);
        return packageName.isEmpty() ? joined : packageName + "." + joined;
    }

    /**
     * 解決済み参照型宣言の binary name。AST を持つ場合 ({@code toAst()} が非空) は
     * {@link #forTypeLikeNode(Node)} に委譲し、AST を持たない場合 (JDK reflection / jar 由来) は
     * シンボルソルバの qualified name を binary 表記 ({@code $} 区切り) に変換する。
     */
    public static String forResolvedDeclaration(ResolvedReferenceTypeDeclaration decl) {
        Node ast = decl.toAst().orElse(null);
        if (ast != null) {
            return forTypeLikeNode(ast);
        }
        String packageName = decl.getPackageName();
        String className = decl.getClassName().replace('.', '$');
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /**
     * D5: generics erasure + 配列/varargs 正規化を適用した binary 表記を返す。
     */
    public static String erasureOf(ResolvedType type) {
        ResolvedType erased = type.erasure();
        return describe(erased);
    }

    private static String describe(ResolvedType type) {
        if (type.isArray()) {
            return describe(type.asArrayType().getComponentType()) + "[]";
        }
        if (type.isPrimitive()) {
            return type.asPrimitive().describe();
        }
        if (type.isVoid()) {
            return "void";
        }
        if (type.isReferenceType()) {
            ResolvedReferenceTypeDeclaration decl = type.asReferenceType().getTypeDeclaration()
                    .orElseThrow(() -> new IllegalStateException("reference type without declaration: " + type));
            return forResolvedDeclaration(decl);
        }
        // フォールバック (type variable の erasure 失敗など、通常到達しない)
        return type.describe();
    }

    private static int anonymousOrdinal(ObjectCreationExpr target) {
        TypeDeclaration<?> topLevel = topLevelTypeOf(target);
        List<ObjectCreationExpr> anonymousInOrder = new ArrayList<>();
        topLevel.walk(ObjectCreationExpr.class, oce -> {
            if (oce.getAnonymousClassBody().isPresent()) {
                anonymousInOrder.add(oce);
            }
        });
        for (int i = 0; i < anonymousInOrder.size(); i++) {
            if (anonymousInOrder.get(i) == target) {
                return i + 1;
            }
        }
        throw new IllegalStateException("anonymous class not found within its own top-level type: " + target);
    }

    private static TypeDeclaration<?> topLevelTypeOf(Node node) {
        TypeDeclaration<?> result = null;
        Node current = node;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> td
                    && td.getParentNode().map(p -> p instanceof CompilationUnit).orElse(false)) {
                result = td;
                break;
            }
            current = current.getParentNode().orElse(null);
        }
        if (result == null) {
            throw new IllegalStateException("no top-level type declaration found for: " + node);
        }
        return result;
    }
}
