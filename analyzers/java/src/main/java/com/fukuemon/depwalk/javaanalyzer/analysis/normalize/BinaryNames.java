package com.fukuemon.depwalk.javaanalyzer.analysis.normalize;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
 * {@code toAst()} で得られる AST ノードからソース構造 (パッケージ宣言 + 型宣言の入れ子 + 匿名/ローカル
 * クラスのソース出現順) を辿って自前で binary name を計算することで、決定性を担保する。scope 外
 * (JDK reflection / jar 由来で AST を持たない) 宣言のみ、シンボルソルバの
 * {@code getPackageName()} / {@code getClassName()} にフォールバックする。
 *
 * <p>匿名クラス / ローカルクラスの採番は javac の JVM binary name 規則と互換 (M3、javac 実測で確認):
 * <ul>
 *   <li>匿名クラス: 直近の enclosing class ごとに 1 始まりのソース出現順 ({@code Outer$Nested} 内の
 *       最初の匿名クラスは、{@code Outer} 直下に先行する匿名クラスがあっても {@code Outer$Nested$1})。</li>
 *   <li>ローカルクラス: {@code Outer$<n><Name>} 形式 (例: {@code Outer$1Local})。n は同名ローカル
 *       クラスの enclosing class 内出現順。匿名クラスの採番とは独立したカウンタ。</li>
 * </ul>
 * 採番はソース内容のみに依存するため、同じソースなら常に同じ名前になる (D5 の決定性要件)。
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
                segments.add(0, segmentOf(td));
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

    /** named type 1 つ分の binary name segment。ローカルクラスは {@code <n><Name>} 形式 (javac 互換)。 */
    private static String segmentOf(TypeDeclaration<?> td) {
        if (isLocalClass(td)) {
            return localOrdinal(td) + td.getNameAsString();
        }
        return td.getNameAsString();
    }

    private static boolean isLocalClass(TypeDeclaration<?> td) {
        return td instanceof ClassOrInterfaceDeclaration cid && cid.isLocalClassDeclaration();
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

    /**
     * 匿名クラスの採番 (javac 互換): 直近の enclosing class 内での 1 始まりソース出現順。
     * enclosing class が同じ匿名クラスのみを数える (別の nested / 匿名クラス内のものは数えない)。
     */
    private static int anonymousOrdinal(ObjectCreationExpr target) {
        Node enclosing = immediateEnclosingClass(target);
        List<ObjectCreationExpr> siblings = new ArrayList<>();
        enclosing.walk(ObjectCreationExpr.class, oce -> {
            if (oce.getAnonymousClassBody().isPresent() && immediateEnclosingClass(oce) == enclosing) {
                siblings.add(oce);
            }
        });
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i) == target) {
                return i + 1;
            }
        }
        throw new IllegalStateException("anonymous class not found within its enclosing class: " + target);
    }

    /**
     * ローカルクラスの採番 (javac 互換): 直近の enclosing class 内で「同名の」ローカルクラスのみを
     * 数えた 1 始まりソース出現順。匿名クラスのカウンタとは独立。
     */
    private static int localOrdinal(TypeDeclaration<?> target) {
        Node enclosing = immediateEnclosingClass(target);
        String name = target.getNameAsString();
        List<TypeDeclaration<?>> siblings = new ArrayList<>();
        enclosing.walk(ClassOrInterfaceDeclaration.class, cid -> {
            if (cid.isLocalClassDeclaration()
                    && name.equals(cid.getNameAsString())
                    && immediateEnclosingClass(cid) == enclosing) {
                siblings.add(cid);
            }
        });
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i) == target) {
                return i + 1;
            }
        }
        throw new IllegalStateException("local class not found within its enclosing class: " + target);
    }

    /**
     * 型 (named / 匿名 / ローカル) の直近の enclosing class ノードを返す
     * ({@link TypeDeclaration} または匿名クラス body を持つ {@link ObjectCreationExpr})。
     * 匿名クラス生成式の引数内のノードは、その匿名クラスにではなく外側の class に属する
     * (javac のスコープ規則) ため、匿名クラス body の中を通って到達した場合のみ enclosing と見なす。
     */
    private static Node immediateEnclosingClass(Node node) {
        Node previous = node;
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?>) {
                return current;
            }
            if (current instanceof ObjectCreationExpr oce
                    && oce.getAnonymousClassBody().isPresent()
                    && isDirectBodyMember(oce, previous)) {
                return current;
            }
            previous = current;
            current = current.getParentNode().orElse(null);
        }
        throw new IllegalStateException("no enclosing class found for: " + node);
    }

    private static boolean isDirectBodyMember(ObjectCreationExpr oce, Node candidate) {
        for (Node member : oce.getAnonymousClassBody().get()) {
            if (member == candidate) {
                return true;
            }
        }
        return false;
    }
}
