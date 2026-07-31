package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;

import java.util.Optional;
import java.util.Set;

/**
 * 型が workspace source 宣言を持ち、かつ呼び出し元 context から依存到達可能かを判定する。
 * bytecode 救済と候補の source 再対応付けはすべてこの判定を通す (ADR-0005)。
 */
final class ReachableOwners {

    private final WorkspaceSourceDeclarationIndex declIndex;
    private final Set<String> reachableContextIds;

    /**
     * @param declIndex workspace の source 宣言から型の所有 context と source location を引く索引
     * @param reachableContextIds 自 context と Gradle project 依存で推移的に到達可能な context id の集合
     */
    ReachableOwners(WorkspaceSourceDeclarationIndex declIndex, Set<String> reachableContextIds) {
        this.declIndex = declIndex;
        this.reachableContextIds = reachableContextIds;
    }

    /** 到達可能な場合だけ {@code binaryName} の source 上の所在を返す。 */
    Optional<WorkspaceSourceDeclarationIndex.TypeLocation> find(String binaryName) {
        return declIndex.find(binaryName).filter(owner -> reachableContextIds.contains(owner.contextId()));
    }
}
