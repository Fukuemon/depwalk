package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P3 の候補 edge が宣言ファイルより先に現れても sourceLocation を欠落させないための compact index。
 * first pass で AST から {@link MethodSymbol} だけを保持し、AST 自体は保持しない。
 */
public final class SourceMethodIndex {

    private final Path workspaceRoot;
    private final Map<String, MethodSymbol> symbolsByMethodId = new LinkedHashMap<>();

    public SourceMethodIndex(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public void accept(CompilationUnit unit) {
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            try {
                ResolvedMethodDeclaration resolved = method.resolve();
                String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
                List<String> parameterTypes = new ArrayList<>();
                for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                    parameterTypes.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
                }
                String signature = MethodIds.signature(declaringType, resolved.getName(), parameterTypes);
                String methodId = MethodIds.methodId(signature);
                String qualifiedName = declaringType.replace('$', '.') + "." + resolved.getName();
                SourceLocation sourceLocation = method.getBegin().flatMap(position -> unit.getStorage().map(storage -> {
                    Path path = storage.getPath().toAbsolutePath().normalize();
                    return SourceLocation.of(
                            RelativePaths.toRecordPath(workspaceRoot.relativize(path).toString()),
                            position.line);
                })).orElse(null);
                symbolsByMethodId.putIfAbsent(methodId, MethodSymbol.of(
                        methodId,
                        "java",
                        "method",
                        qualifiedName,
                        signature,
                        sourceLocation,
                        null));
            } catch (RuntimeException ignored) {
                // CallGraphBuilder の既存 unresolved declaration 経路が second pass で診断する。
            }
        }
    }

    public Optional<MethodSymbol> find(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        String signature = MethodIds.signature(
                candidate.declaringType(),
                candidate.methodName(),
                candidate.parameterTypes());
        return Optional.ofNullable(symbolsByMethodId.get(MethodIds.methodId(signature)));
    }
}
