package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-hop mapping from a workspace method parameter to the callables passed to it.
 *
 * <p>Feeds the callable invocation edges (ADR-0012): when a lambda or method
 * reference is passed as an argument to a workspace method, invoking the matching
 * parameter inside that method links back to the callable body. Only the direct
 * argument-passing hop is indexed; field stores and multi-hop flows stay out of
 * scope by design. Resolution failures are skipped silently — the passing call
 * site itself is classified by the normal second-pass processing.
 */
public final class CallablePassIndex {

    /**
     * The callable body an invocation should link to: the referenced method for a
     * method (or constructor) reference, the lexically enclosing method for a lambda
     * (the lambda body belongs to that node; the marker keeps the approximation
     * observable).
     */
    public record CallableTarget(String declaringType, String methodName, List<String> parameterTypes) {
    }

    private final Map<String, List<CallableTarget>> targetsByParameter = new LinkedHashMap<>();

    /** First pass: record lambda / method reference arguments to workspace methods. */
    public void accept(CompilationUnit unit) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            for (int i = 0; i < call.getArguments().size(); i++) {
                Expression argument = call.getArgument(i);
                if (!(argument instanceof LambdaExpr) && !(argument instanceof MethodReferenceExpr)) {
                    continue;
                }
                try {
                    ResolvedMethodDeclaration target = call.resolve();
                    String targetId = methodIdOf(target);
                    CallableTarget callable = targetOf(argument);
                    if (callable != null) {
                        targetsByParameter
                                .computeIfAbsent(parameterKey(targetId, i), key -> new ArrayList<>())
                                .add(callable);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // The passing call site is diagnosed by the normal path if needed.
                }
            }
        }
    }

    /** Callables passed to the given parameter of the given method. Empty when none. */
    public List<CallableTarget> callablesFor(String methodId, int parameterIndex) {
        return targetsByParameter.getOrDefault(parameterKey(methodId, parameterIndex), List.of());
    }

    public boolean isEmpty() {
        return targetsByParameter.isEmpty();
    }

    private static String parameterKey(String methodId, int parameterIndex) {
        return methodId + "@" + parameterIndex;
    }

    /**
     * The callable body a lambda / method reference expression stands for, or null
     * when it cannot be determined (unsupported shape or resolution failure is
     * propagated to the caller as an exception).
     */
    static CallableTarget targetOf(Expression callableExpression) {
        if (callableExpression instanceof MethodReferenceExpr reference) {
            return referenceTarget(reference);
        }
        if (callableExpression instanceof LambdaExpr) {
            return enclosingCallableOf(callableExpression);
        }
        return null;
    }

    private static String methodIdOf(ResolvedMethodDeclaration resolved) {
        String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
        List<String> parameterTypes = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            parameterTypes.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
        }
        return MethodIds.methodId(MethodIds.signature(declaringType, resolved.getName(), parameterTypes));
    }

    private static CallableTarget referenceTarget(MethodReferenceExpr reference) {
        Object resolved = reference.resolve();
        if (resolved instanceof ResolvedMethodDeclaration method) {
            List<String> parameterTypes = new ArrayList<>();
            for (int i = 0; i < method.getNumberOfParams(); i++) {
                parameterTypes.add(BinaryNames.erasureOf(method.getParam(i).getType()));
            }
            return new CallableTarget(
                    BinaryNames.forResolvedDeclaration(method.declaringType()), method.getName(),
                    List.copyOf(parameterTypes));
        }
        if (resolved instanceof ResolvedConstructorDeclaration constructor) {
            List<String> parameterTypes = new ArrayList<>();
            for (int i = 0; i < constructor.getNumberOfParams(); i++) {
                parameterTypes.add(BinaryNames.erasureOf(constructor.getParam(i).getType()));
            }
            return new CallableTarget(
                    BinaryNames.forResolvedDeclaration(constructor.declaringType()),
                    MethodIds.CONSTRUCTOR_TOKEN,
                    List.copyOf(parameterTypes));
        }
        return null;
    }

    /** The method or constructor lexically enclosing the lambda expression. */
    private static CallableTarget enclosingCallableOf(Node node) {
        MethodDeclaration method = node.findAncestor(MethodDeclaration.class).orElse(null);
        if (method != null) {
            ResolvedMethodDeclaration resolved = method.resolve();
            List<String> parameterTypes = new ArrayList<>();
            for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                parameterTypes.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
            }
            return new CallableTarget(
                    BinaryNames.forResolvedDeclaration(resolved.declaringType()), resolved.getName(),
                    List.copyOf(parameterTypes));
        }
        ConstructorDeclaration constructor = node.findAncestor(ConstructorDeclaration.class).orElse(null);
        if (constructor != null) {
            ResolvedConstructorDeclaration resolved = constructor.resolve();
            List<String> parameterTypes = new ArrayList<>();
            for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                parameterTypes.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
            }
            return new CallableTarget(
                    BinaryNames.forResolvedDeclaration(resolved.declaringType()),
                    MethodIds.CONSTRUCTOR_TOKEN,
                    List.copyOf(parameterTypes));
        }
        // Lambdas in initializers are out of scope (their calls are folded elsewhere).
        return null;
    }
}
