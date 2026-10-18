package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.List;
import java.util.function.Predicate;

/**
 * 型解決を使わずに、local 変数の名前から AST 上の宣言を探す。
 *
 * <p>使う側ごとに、採ってよい宣言の規則が違うので、規則ごとに別のメソッドにしている。
 * <ul>
 *   <li>{@link #precedingInEnclosingBlocks}: 宣言の順序と block の入れ子だけで決められる宣言に限り、
 *       兄弟の block の同名の宣言を結びつけない。block の直下以外の宣言は見ないので、
 *       field で補う前に {@link #sameNameDeclaredInMember} で同名の local がないことを確かめる。</li>
 *   <li>{@link #uniqueVisible}: 同名の宣言が複数あると、どれが使う位置から見えるかを決められないので、
 *       一意な宣言だけを採り、兄弟の block と匿名 class の宣言は可視範囲で除く。</li>
 *   <li>{@link #uniqueUnreassignedBefore}: callable の実体は呼び出す時点の値で決まるので、
 *       再代入のある変数は initializer から実体を決められない。</li>
 * </ul>
 */
final class LocalDeclarations {

    private LocalDeclarations() {
    }

    /**
     * {@code use} から外側へ block を辿り、各 block の直下で {@code use} を含む文より前にある同名の宣言を返す。
     * 内側の block の宣言を優先し、method、constructor、initializer、lambda の境界で探すのをやめる。
     */
    static VariableDeclarator precedingInEnclosingBlocks(NameExpr use) {
        String name = use.getNameAsString();
        Node child = use;
        Node parent = child.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof BlockStmt block) {
                VariableDeclarator declarator = declaratorBeforeInBlock(block, child, name);
                if (declarator != null) {
                    return declarator;
                }
            }
            if (parent instanceof MethodDeclaration
                    || parent instanceof ConstructorDeclaration
                    || parent instanceof InitializerDeclaration
                    || parent instanceof LambdaExpr) {
                // 境界の外の同名の宣言は字句のスコープの外にあることがあり、結びつけると誤るので探さない。
                return null;
            }
            child = parent;
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    /**
     * {@code block} の直下の文のうち、{@code child} を含む文より前にある同名の宣言。
     * 使う位置を含まない兄弟の文の中の宣言は対象にしない。
     */
    private static VariableDeclarator declaratorBeforeInBlock(BlockStmt block, Node child, String name) {
        for (Statement statement : block.getStatements()) {
            if (statement == child || statement.isAncestorOf(child)) {
                break;
            }
            if (statement instanceof ExpressionStmt exprStmt
                    && exprStmt.getExpression() instanceof VariableDeclarationExpr varDecl) {
                for (VariableDeclarator declarator : varDecl.getVariables()) {
                    if (declarator.getNameAsString().equals(name)) {
                        return declarator;
                    }
                }
            }
        }
        return null;
    }

    /**
     * {@code use} を囲む member の中に、同名の local の宣言（変数の宣言、for のヘッダ、try-resource、
     * catch 節、パターン変数、parameter）があるか。
     * 使う位置で実際に shadowing しているかまでは判定しない。多く見積もっても diagnostic が残るだけで済む。
     */
    static boolean sameNameDeclaredInMember(NameExpr use) {
        String name = use.getNameAsString();
        Node member = use;
        Node parent = use.getParentNode().orElse(null);
        while (parent != null && !(parent instanceof TypeDeclaration)) {
            member = parent;
            parent = parent.getParentNode().orElse(null);
        }
        // FieldDeclaration の直下の declarator は field なので、field の initializer の中から辿った場合や、
        // member の中の local class の field を、同名の local と取り違えないように除く。
        return declaratorsNamed(member, name).stream()
                        .anyMatch(declarator ->
                                !(declarator.getParentNode().orElse(null) instanceof FieldDeclaration))
                || member.findAll(Parameter.class).stream()
                        .anyMatch(parameter -> parameter.getNameAsString().equals(name))
                || member.findAll(TypePatternExpr.class).stream()
                        .anyMatch(pattern -> pattern.getNameAsString().equals(name));
    }

    /**
     * {@code anchor} を囲む最も内側の callable か lambda の中で、同名の宣言が 1 つだけあり、
     * それが {@code anchor} から見えるなら返す。囲む callable も lambda もなければ、AST の全体から探す。
     */
    static VariableDeclarator uniqueVisible(Node anchor, String name) {
        Node enclosing = nearestAncestor(anchor,
                node -> node instanceof CallableDeclaration<?> || node instanceof LambdaExpr);
        Node root = enclosing != null ? enclosing : anchor.findRootNode();
        List<VariableDeclarator> matches = declaratorsNamed(root, name);
        if (matches.size() != 1) {
            return null;
        }
        return visibleFrom(matches.get(0), anchor) ? matches.get(0) : null;
    }

    /**
     * 宣言の可視範囲（宣言文を含む block、for 系は文そのもの）が anchor を含むか。
     * 兄弟の block や内側の匿名 class の同名の宣言を名前だけで採ると、誤った型から誤った owner に繋がる。
     */
    private static boolean visibleFrom(VariableDeclarator declarator, Node anchor) {
        var statement = declarator.findAncestor(Statement.class).orElse(null);
        if (statement == null) {
            return false;
        }
        Node visibilityRoot = statement instanceof ExpressionStmt
                ? statement.getParentNode().orElse(null)
                : statement;
        return visibilityRoot != null && (visibilityRoot == anchor || visibilityRoot.isAncestorOf(anchor));
    }

    /**
     * {@code use} を囲む method（なければ constructor）の中で、同名の変数への代入がなく、
     * {@code use} より前で始まり、直近の block が {@code use} を含む宣言が 1 つだけあるなら返す。
     */
    static VariableDeclarator uniqueUnreassignedBefore(Node use, String name) {
        Node enclosing = nearestAncestor(use, MethodDeclaration.class::isInstance);
        if (enclosing == null) {
            enclosing = nearestAncestor(use, ConstructorDeclaration.class::isInstance);
        }
        if (enclosing == null) {
            return null;
        }
        boolean reassigned = enclosing.findAll(AssignExpr.class).stream()
                .anyMatch(assign -> assign.getTarget() instanceof NameExpr target
                        && target.getNameAsString().equals(name));
        if (reassigned) {
            return null;
        }
        // 複数が当てはまるときは、推測せずには 1 つに絞れないので採らない。
        List<VariableDeclarator> candidates = declaratorsNamed(enclosing, name).stream()
                .filter(declarator -> beginsBefore(declarator, use) && enclosingBlockContains(declarator, use))
                .toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean beginsBefore(Node first, Node second) {
        return first.getBegin().isPresent() && second.getBegin().isPresent()
                && first.getBegin().get().isBefore(second.getBegin().get());
    }

    /** declarator の直近の block が use を字句的に含むか。 */
    private static boolean enclosingBlockContains(Node declarator, Node use) {
        Node block = declarator.findAncestor(BlockStmt.class).orElse(null);
        if (block == null) {
            return false;
        }
        for (Node ancestor = use; ancestor != null; ancestor = ancestor.getParentNode().orElse(null)) {
            if (ancestor == block) {
                return true;
            }
        }
        return false;
    }

    /** 宣言の initializer。宣言がないか、initializer がなければ null。 */
    static Expression initializerOf(VariableDeclarator declarator) {
        return declarator != null ? declarator.getInitializer().orElse(null) : null;
    }

    /** {@code node} の親から外側へ辿り、{@code boundary} に当てはまる最初の祖先。なければ null。 */
    private static Node nearestAncestor(Node node, Predicate<Node> boundary) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null && !boundary.test(parent)) {
            parent = parent.getParentNode().orElse(null);
        }
        return parent;
    }

    private static List<VariableDeclarator> declaratorsNamed(Node root, String name) {
        return root.findAll(VariableDeclarator.class).stream()
                .filter(declarator -> declarator.getNameAsString().equals(name))
                .toList();
    }
}
