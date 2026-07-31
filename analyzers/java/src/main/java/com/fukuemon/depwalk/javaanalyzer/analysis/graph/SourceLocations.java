package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.nio.file.Path;

/**
 * AST ノードの所在を workspace root 基準の record path / {@link SourceLocation} へ正規化する。
 */
final class SourceLocations {

    private final Path workspaceRoot;

    /**
     * @param workspaceRoot source location を相対化する workspace root (絶対・正規化済み)
     */
    SourceLocations(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    static Path filePathOf(Node node) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toAbsolutePath().normalize())
                .orElse(null);
    }

    /** 絶対 path を record 出力用の workspace 相対 path へ正規化する。 */
    String recordPathOf(Path absolutePath) {
        return RelativePaths.toRecordPath(workspaceRoot.relativize(absolutePath).toString());
    }

    /**
     * node の source 位置。schema は {@code startLine} を 1-based で要求するため、位置が不明な場合
     * ({@code node.getBegin()} が空) は {@code startLine: 0} を出さず sourceLocation 自体を省略する
     * (null を返す)。
     */
    SourceLocation sourceLocationOf(Node node) {
        return node.getBegin().map(p -> {
            Path filePath = filePathOf(node);
            String relativePath = filePath != null ? recordPathOf(filePath) : "";
            return SourceLocation.of(relativePath, p.line);
        }).orElse(null);
    }
}
