package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spring を classpath に置かずに framework entry point アノテーションを検出する。
 *
 * <p>entry point は framework が直接起動しうる method であり、標識は caller edge の有無と
 * 独立している (adr/0012-implicit-call-resolution-and-type-propagation-rescue.md)。
 * first pass で、既知の entry point アノテーションを担持する workspace のアノテーション宣言を
 * 記録し、ユーザー定義の合成アノテーションをメタ 1 段まで検出する。より深い入れ子は設計上
 * 検出対象外であり、診断も出さない。
 */
public final class EntryPointIndex {

    private static final Set<String> ENTRY_POINT_ANNOTATIONS = SpringAnnotations.ENTRY_POINT_ANNOTATIONS;

    private final Map<String, Set<String>> composedToEntryPoints = new LinkedHashMap<>();

    /**
     * first pass: entry point アノテーションを直接 (メタ 1 段のみ) 担持するユーザー定義
     * アノテーションを記録する。重複宣言は最初の entry を保持する (他の first-pass 索引と
     * 揃えた first-wins)。nested / local に宣言した合成アノテーションと、修飾名を解決
     * できない宣言は対象外として読み飛ばす (design/features/java-analyzer/analysis.md
     * の検出制約)。
     */
    public void accept(CompilationUnit unit) {
        for (AnnotationDeclaration declaration : unit.findAll(AnnotationDeclaration.class)) {
            if (!declaration.isTopLevelType()) {
                // nested / local に宣言した合成アノテーションは検出対象外
                // (design/features/java-analyzer/analysis.md の制約)。
                continue;
            }
            Set<String> carried = new TreeSet<>();
            for (AnnotationExpr annotation : declaration.getAnnotations()) {
                String fqn = SpringAnnotations.fqn(annotation);
                if (fqn != null && ENTRY_POINT_ANNOTATIONS.contains(fqn)) {
                    carried.add(fqn);
                }
            }
            if (!carried.isEmpty()) {
                declaration.getFullyQualifiedName()
                        .ifPresent(fqn -> composedToEntryPoints.putIfAbsent(fqn, Set.copyOf(carried)));
            }
        }
    }

    /**
     * node 上で検出した entry point アノテーション FQN (辞書順・重複なし)。合成アノテーションは
     * 自身の名前でなく担持している entry point FQN で数えるため、標識は常に既知の framework
     * アノテーション名になる。呼び出し側が検査するのは method レベルのアノテーションのみ
     * (型レベルの対応付けは対象外)。
     */
    // 実経路は annotationFqnsOf + entryPointsOfAnnotationFqns の 2 段。この合成形は
    // 同 package の unit test だけが使う。
    List<String> entryPointsOf(NodeWithAnnotations<?> node) {
        return entryPointsOfAnnotationFqns(annotationFqnsOf(node));
    }

    /**
     * node の生のアノテーション FQN 列。アノテーション解決は即時に行い、entry point への
     * 対応付けは後で行う呼び出し側のための形 (合成アノテーションの対応表は全 compilation
     * unit の accept が終わるまで完全にならない)。
     */
    public List<String> annotationFqnsOf(NodeWithAnnotations<?> node) {
        List<String> fqns = new java.util.ArrayList<>();
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String fqn = SpringAnnotations.fqn(annotation);
            if (fqn != null) {
                fqns.add(fqn);
            }
        }
        return List.copyOf(fqns);
    }

    /** 解決済みアノテーション FQN 列を entry point FQN (辞書順・重複なし) へ対応付ける。 */
    public List<String> entryPointsOfAnnotationFqns(List<String> annotationFqns) {
        Set<String> found = new TreeSet<>();
        for (String fqn : annotationFqns) {
            if (ENTRY_POINT_ANNOTATIONS.contains(fqn)) {
                found.add(fqn);
                continue;
            }
            Set<String> composed = composedToEntryPoints.get(fqn);
            if (composed != null) {
                found.addAll(composed);
            }
        }
        return List.copyOf(found);
    }
}
