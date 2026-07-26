package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;

/**
 * call site ノード (method call / method reference) から receiver 式を取り出す。
 */
final class CallScopes {

    private CallScopes() {
    }

    static Expression scopeOf(Node callNode) {
        if (callNode instanceof MethodCallExpr methodCall && methodCall.getScope().isPresent()) {
            return methodCall.getScope().get();
        }
        if (callNode instanceof MethodReferenceExpr methodReference) {
            return methodReference.getScope();
        }
        return null;
    }

    /** call scope から括弧 ({@code (foo).bar()}) を剥がした receiver 式。scope が無ければ {@code null}。 */
    static Expression unwrappedScopeOf(Node callNode) {
        Expression scope = scopeOf(callNode);
        while (scope != null && scope.isEnclosedExpr()) {
            scope = scope.asEnclosedExpr().getInner();
        }
        return scope;
    }
}
