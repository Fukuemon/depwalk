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
 * workspace method の parameter から、そこへ渡された callable への 1 hop の対応表。
 *
 * <p>callable invocation edge の生成
 * (adr/0012-implicit-call-resolution-and-type-propagation-rescue.md) に使う: lambda /
 * method reference を workspace method の引数に渡した場合、その method 内で該当 parameter を
 * invoke する箇所を callable 本体へ結び付ける。索引するのは直接の引数渡し 1 hop のみで、
 * field への格納と多段の流れは設計上対象外。解決失敗は黙って読み飛ばす (渡している call
 * site 自体の終端は通常の second pass 処理が分類する)。
 *
 * <p>複数の call site から渡された callable は同一の parameter キーへ集約される。これは
 * invocation 側で候補を全列挙する設計 (design/features/java-analyzer/analysis.md の
 * callable 追跡) のためであり、call site ごとに分離しない。
 */
public final class CallablePassIndex {

    /**
     * invocation の結び付け先となる callable 本体。method (constructor) reference は
     * 参照先、lambda は字句的に囲む method (lambda body はその node に属する近似で、
     * 近似であることは edge の標識で観測できる)。
     */
    public record CallableTarget(String declaringType, String methodName, List<String> parameterTypes) {
    }

    private final Map<String, List<CallableTarget>> targetsByParameter = new LinkedHashMap<>();

    /**
     * first pass: lambda / method reference 引数を、静的に解決した渡し先宣言で索引する。
     * 所有 context を問わず索引し、edge 出力側で callee を到達可能な workspace method へ
     * 制限する。宣言 parameter 数を超える可変長引数位置は索引されるが参照されない
     * (invocation 側は宣言 parameter の index しか引かない) ため、可変長へ渡した callable は
     * 設計上未追跡のまま。
     */
    public void accept(CompilationUnit unit) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String targetId = null;
            for (int i = 0; i < call.getArguments().size(); i++) {
                Expression argument = call.getArgument(i);
                if (!(argument instanceof LambdaExpr) && !(argument instanceof MethodReferenceExpr)) {
                    continue;
                }
                try {
                    if (targetId == null) {
                        targetId = methodIdOf(call.resolve());
                    }
                    CallableTarget callable = targetOf(argument);
                    if (callable != null) {
                        targetsByParameter
                                .computeIfAbsent(parameterKey(targetId, i), key -> new ArrayList<>())
                                .add(callable);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // 渡している call site 自体は必要なら通常経路が診断する。
                }
            }
        }
    }

    /** 指定 method の指定 parameter へ渡された callable。無ければ空。 */
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
     * lambda / method reference 式が表す callable 本体。未対応の形なら null。
     * 解決失敗は例外のまま呼び出し側へ伝播する。
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

    /** lambda 式を字句的に囲む method または constructor。 */
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
        // initializer 内の lambda は対象外 (その呼び出しは別経路で畳み込まれる)。
        return null;
    }
}
