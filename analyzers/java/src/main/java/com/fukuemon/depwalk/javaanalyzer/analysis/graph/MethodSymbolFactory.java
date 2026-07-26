package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 宣言・dispatch 候補から {@code methodSymbol} を組み立てる。暗黙の member
 * (static initializer / default constructor) は同一 methodId の node が未登録のときだけ
 * {@link GraphAccumulator} へ登録する。
 */
final class MethodSymbolFactory {

    private final GraphAccumulator accumulator;
    private final SourceLocations sourceLocations;
    private final SourceMethodIndex sourceMethodIndex;
    private final ReachableOwners reachableOwners;

    MethodSymbolFactory(
            GraphAccumulator accumulator,
            SourceLocations sourceLocations,
            SourceMethodIndex sourceMethodIndex,
            ReachableOwners reachableOwners) {
        this.accumulator = accumulator;
        this.sourceLocations = sourceLocations;
        this.sourceMethodIndex = sourceMethodIndex;
        this.reachableOwners = reachableOwners;
    }

    MethodSymbol buildMethodSymbol(AttributionResult attribution, ResolvedMethodDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = AttributionSites.paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, resolved.getName(), paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + resolved.getName();

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            Node ast = resolved.toAst().orElse(null);
            sourceLocation = ast != null ? sourceLocations.sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "method", qualifiedName, signature, sourceLocation, metadata);
    }

    MethodSymbol buildConstructorSymbol(AttributionResult attribution, ResolvedConstructorDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = AttributionSites.paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            // synthetic default constructor / record canonical constructor は自身の AST を持たない
            // ({@code toAst()} が空、record の場合は compact constructor の有無によらず常に空) ため、
            // 宣言型の AST 位置へフォールバックする。これにより ensureDefaultConstructorNode /
            // buildCompactConstructorSymbol と同一内容になり、同一 methodId の node がどの経路から
            // 生成されても内容が一致する (GraphAccumulator の first-wins 重複排除で情報が失われない)。
            // record に compact constructor があれば、宣言型そのものより精度の高い compact constructor
            // の位置を使う (buildCompactConstructorSymbol と揃える)。
            Node ast = resolved.toAst().orElse(null);
            if (ast == null) {
                ast = preferCompactConstructorLocation(resolved.declaringType().toAst().orElse(null));
            }
            sourceLocation = ast != null ? sourceLocations.sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, metadata);
    }

    /**
     * {@code typeAst} が record で compact constructor を持つ場合、その位置を優先して返す
     * (宣言型全体より精度の高い sourceLocation にする)。該当しなければ {@code typeAst} をそのまま返す。
     */
    private static Node preferCompactConstructorLocation(Node typeAst) {
        if (typeAst instanceof RecordDeclaration rd) {
            for (BodyDeclaration<?> member : rd.getMembers()) {
                if (member instanceof CompactConstructorDeclaration ccd) {
                    return ccd;
                }
            }
        }
        return typeAst;
    }

    /**
     * record の compact constructor を canonical constructor として扱い、その {@link MethodSymbol}
     * を作る。signature は record component の erasure 型列 (宣言順)。JavaParser の
     * {@code CompactConstructorDeclaration#resolve()} は未実装のため、record の component 一覧
     * ({@link RecordDeclaration#getParameters()}) から自前で param 型を求める。
     */
    MethodSymbol buildCompactConstructorSymbol(Node enclosingTypeNode, CompactConstructorDeclaration ccd) {
        if (!(enclosingTypeNode instanceof RecordDeclaration rd)) {
            throw new IllegalStateException("compact constructor outside of a record declaration: " + ccd);
        }
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        List<String> paramTypes = new ArrayList<>();
        for (Parameter component : rd.getParameters()) {
            paramTypes.add(BinaryNames.erasureOf(component.resolve().getType()));
        }
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
        SourceLocation sourceLocation = sourceLocations.sourceLocationOf(ccd);
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, null);
    }

    String ensureStaticInitializerNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.STATIC_INITIALIZER_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.STATIC_INITIALIZER_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "initializer", qualifiedName, signature,
                    sourceLocations.sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    String ensureDefaultConstructorNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "constructor", qualifiedName, signature,
                    sourceLocations.sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    MethodSymbol buildCandidateMethodSymbol(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        // bytecode 候補を source 宣言へ再対応付けするのは、宣言型が scope 内 source に
        // 存在し、呼出元 context から依存到達可能な場合だけ。external /
        // JDK / 非依存 context を workspace 全体の名前一致で source へ戻さない。
        boolean remappable = reachableOwners.find(candidate.declaringType()).isPresent();
        return (remappable ? sourceMethodIndex.find(candidate) : Optional.<MethodSymbol>empty()).orElseGet(() -> {
            String signature = MethodIds.signature(
                    candidate.declaringType(),
                    candidate.methodName(),
                    candidate.parameterTypes());
            return MethodSymbol.of(
                    MethodIds.methodId(signature),
                    "java",
                    "method",
                    candidate.declaringType().replace('$', '.') + "." + candidate.methodName(),
                    signature,
                    null,
                    null);
        });
    }
}
