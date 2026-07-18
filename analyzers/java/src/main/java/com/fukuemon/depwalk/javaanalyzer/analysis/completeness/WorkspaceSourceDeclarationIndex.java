package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * include / exclude 後の全 source から作る軽量な型宣言索引 (spec #24 D16 / D18)。
 * binary name → (所有 context, workspace 相対 location) を保持し、AST は保持しない。
 * 正規化 method signature の索引は既存 {@code SourceMethodIndex} (methodId =
 * 正規化 signature) が正本で、本 index は型の所有 context / 到達性の制約と
 * owner source location の構築を担う (両者で spec step 1.2 の索引を構成する)。
 */
public final class WorkspaceSourceDeclarationIndex {

    /**
     * 1 つの scope 内 source type 宣言。
     *
     * @param contextId 所有解析 context
     * @param path workspace 相対 path
     * @param beginLine 宣言の開始行 (1-based)
     */
    public record TypeLocation(String contextId, String path, int beginLine) {
    }

    private final Path workspaceRoot;
    private final Map<String, TypeLocation> typesByBinaryName = new LinkedHashMap<>();

    public WorkspaceSourceDeclarationIndex(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /** first pass で CU ごとに呼び、型宣言を索引へ登録する。 */
    public void accept(CompilationUnit cu, String contextId) {
        String path = cu.getStorage()
                .map(storage -> RelativePaths.toRecordPath(
                        workspaceRoot.relativize(storage.getPath().toAbsolutePath().normalize()).toString()))
                .orElse(null);
        if (path == null) {
            return;
        }
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            String binaryName;
            try {
                binaryName = BinaryNames.forTypeLikeNode(type);
            } catch (RuntimeException e) {
                continue;
            }
            int line = type.getRange().map(r -> r.begin.line).orElse(1);
            typesByBinaryName.putIfAbsent(binaryName, new TypeLocation(contextId, path, line));
        }
    }

    /** binary name の scope 内 source 宣言を返す。 */
    public Optional<TypeLocation> find(String binaryName) {
        return Optional.ofNullable(typesByBinaryName.get(binaryName));
    }
}
