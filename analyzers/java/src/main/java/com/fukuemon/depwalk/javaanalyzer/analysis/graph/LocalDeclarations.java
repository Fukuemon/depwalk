package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;

import java.util.List;

/**
 * 型解決を使わずに、local 変数の名前から AST 上の宣言を探す。
 *
 * <p>{@link #visibleAt} は、使う位置から外側へスコープを辿り、最初に見える宣言を採る。
 * 宣言の順序とスコープの入れ子だけで決めるので、兄弟の block の宣言と、使う位置より後ろの宣言は採らない。
 * parameter とパターン変数は宣言として返さないので、field で補う前に {@link #sameNameDeclaredInMember} で
 * 同名の local がないことを確かめる。{@link #reassigned} は、{@link #visibleAt} が返した宣言のスコープで
 * 再代入を判定する。2 つが同じスコープの模型を使うように、同じ class に置く。
 */
final class LocalDeclarations {

    private LocalDeclarations() {
    }

    /**
     * {@code use} から外側へスコープを辿り、{@code use} から見える同名の宣言のうち、最も内側のものを返す。
     * lambda か catch 節の parameter が同じ名前なら、宣言は parameter なので null を返す。
     * method、constructor、initializer、field などの member の境界で探すのをやめる。
     * 匿名 class の member も境界になるので、匿名 class の中から外側の local は探さない。
     */
    static VariableDeclarator visibleAt(Node use, String name) {
        Node child = use;
        Node parent = use.getParentNode().orElse(null);
        while (parent != null && !(parent instanceof BodyDeclaration<?>)) {
            if (declaresParameter(parent, child, name)) {
                return null;
            }
            VariableDeclarator declarator = declaredBefore(parent, child, name);
            if (declarator != null) {
                return declarator;
            }
            child = parent;
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    private static boolean declaresParameter(Node parent, Node child, String name) {
        if (parent instanceof LambdaExpr lambda) {
            return lambda.getParameters().stream().anyMatch(parameter -> parameter.getNameAsString().equals(name));
        }
        return parent instanceof CatchClause clause
                && clause.getBody() == child
                && clause.getParameter().getNameAsString().equals(name);
    }

    /**
     * {@code parent} が持つスコープのうち、{@code child} から見える同名の宣言。
     * Node の equals は構造の比較なので、位置は参照の同一性で比べる。
     * switch の selector や case のラベルのように、{@code child} が宣言の並びの外にあるときは、
     * 並びの宣言はどれも {@code child} より後ろにあるので採らない。
     */
    private static VariableDeclarator declaredBefore(Node parent, Node child, String name) {
        if (parent instanceof BlockStmt block) {
            return declaredInStatementsBefore(block.getStatements(), child, name);
        }
        if (parent instanceof SwitchEntry entry) {
            return contains(entry.getStatements(), child)
                    ? declaredInStatementsBefore(entry.getStatements(), child, name)
                    : null;
        }
        if (parent instanceof SwitchNode switchNode) {
            if (!contains(switchNode.getEntries(), child)) {
                return null;
            }
            // 文の group の switch では、前の case で宣言した local が後ろの case から見える。
            VariableDeclarator found = null;
            for (SwitchEntry entry : switchNode.getEntries()) {
                if (entry == child) {
                    break;
                }
                if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
                    VariableDeclarator declarator = declaredInStatementsBefore(entry.getStatements(), null, name);
                    found = declarator != null ? declarator : found;
                }
            }
            return found;
        }
        if (parent instanceof VariableDeclarationExpr declaration) {
            return contains(declaration.getVariables(), child)
                    ? namedBefore(declaration.getVariables(), child, name)
                    : null;
        }
        if (parent instanceof ForStmt loop) {
            if (contains(loop.getInitialization(), child)) {
                return null;
            }
            return declaredInExpressions(loop.getInitialization(), null, name);
        }
        if (parent instanceof ForEachStmt loop) {
            return loop.getBody() == child ? namedBefore(loop.getVariable().getVariables(), null, name) : null;
        }
        if (parent instanceof TryStmt tryStmt) {
            if (tryStmt.getTryBlock() == child) {
                return declaredInExpressions(tryStmt.getResources(), null, name);
            }
            if (contains(tryStmt.getResources(), child)) {
                return declaredInExpressions(tryStmt.getResources(), child, name);
            }
        }
        return null;
    }

    private static boolean contains(NodeList<? extends Node> nodes, Node child) {
        return nodes.stream().anyMatch(node -> node == child);
    }

    /** {@code end} より前の文の、変数の宣言のうち同名で最も後ろのもの。{@code end} が null なら全部の文を見る。 */
    private static VariableDeclarator declaredInStatementsBefore(
            NodeList<Statement> statements, Node end, String name) {
        VariableDeclarator found = null;
        for (Statement statement : statements) {
            if (statement == end) {
                break;
            }
            if (statement instanceof ExpressionStmt exprStmt
                    && exprStmt.getExpression() instanceof VariableDeclarationExpr declaration) {
                VariableDeclarator declarator = namedBefore(declaration.getVariables(), null, name);
                found = declarator != null ? declarator : found;
            }
        }
        return found;
    }

    private static VariableDeclarator declaredInExpressions(NodeList<Expression> expressions, Node end, String name) {
        VariableDeclarator found = null;
        for (Expression expression : expressions) {
            if (expression == end) {
                break;
            }
            if (expression instanceof VariableDeclarationExpr declaration) {
                VariableDeclarator declarator = namedBefore(declaration.getVariables(), null, name);
                found = declarator != null ? declarator : found;
            }
        }
        return found;
    }

    private static VariableDeclarator namedBefore(NodeList<VariableDeclarator> declarators, Node end, String name) {
        VariableDeclarator found = null;
        for (VariableDeclarator declarator : declarators) {
            if (declarator == end) {
                break;
            }
            if (declarator.getNameAsString().equals(name)) {
                found = declarator;
            }
        }
        return found;
    }

    /**
     * 宣言のスコープの中で、宣言より後ろに同名の変数への代入があるか。
     * 宣言より前の代入は、兄弟の block の別の変数への代入なので数えない。
     * 宣言より後ろの local class と匿名 class の member が同じ名前を宣言して代入していても数え、
     * 多く見積もる側に倒す。スコープを決められないときも true を返す。
     */
    static boolean reassigned(VariableDeclarator declarator) {
        Node scope = scopeOf(declarator);
        String name = declarator.getNameAsString();
        return scope == null || scope.findAll(AssignExpr.class).stream()
                .anyMatch(assign -> assign.getTarget() instanceof NameExpr target
                        && target.getNameAsString().equals(name)
                        && !beginsBefore(assign, declarator));
    }

    private static boolean beginsBefore(Node first, Node second) {
        return first.getBegin().isPresent() && second.getBegin().isPresent()
                && first.getBegin().get().isBefore(second.getBegin().get());
    }

    /** 宣言が見える範囲を持つ node。block の文の宣言は block、for 系と try の宣言は文そのもの。 */
    private static Node scopeOf(VariableDeclarator declarator) {
        Node declaration = declarator.getParentNode().orElse(null);
        Node owner = declaration != null ? declaration.getParentNode().orElse(null) : null;
        if (owner instanceof ExpressionStmt) {
            owner = owner.getParentNode().orElse(null);
        }
        if (owner instanceof SwitchEntry entry) {
            owner = entry.getParentNode().orElse(null);
        }
        return owner;
    }

    /**
     * {@code use} を囲む member の中に、同名の local の宣言（変数の宣言、for のヘッダ、try-resource、
     * catch 節、パターン変数、parameter）があるか。
     * 使う位置で実際に shadowing しているかまでは判定しない。多く見積もっても diagnostic が残るだけで済む。
     * 匿名 class は TypeDeclaration ではないので、匿名 class の外側の member まで見る。
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

    /** 宣言の initializer。宣言がないか、initializer がなければ null。 */
    static Expression initializerOf(VariableDeclarator declarator) {
        return declarator != null ? declarator.getInitializer().orElse(null) : null;
    }

    private static List<VariableDeclarator> declaratorsNamed(Node root, String name) {
        return root.findAll(VariableDeclarator.class).stream()
                .filter(declarator -> declarator.getNameAsString().equals(name))
                .toList();
    }
}
