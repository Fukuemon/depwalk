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
 * Detects framework entry point annotations without putting Spring on the classpath.
 *
 * <p>An entry point is a method the framework may invoke directly; the marker is
 * independent of whether the method also has caller edges (see ADR-0012). The first
 * pass records workspace annotation declarations that carry a known entry point
 * annotation, so user-defined composed annotations are detected one meta level deep.
 * Deeper nesting is undetectable by design and produces no diagnostic.
 */
public final class EntryPointIndex {

    private static final Set<String> ENTRY_POINT_ANNOTATIONS = SpringAnnotations.ENTRY_POINT_ANNOTATIONS;

    private final Map<String, Set<String>> composedToEntryPoints = new LinkedHashMap<>();

    /**
     * First pass: record user-defined annotations that directly carry an entry point
     * annotation (one meta level only). Duplicated declarations keep the first entry
     * (first-wins, consistent with the other first-pass indexes). Local annotation
     * declarations without a resolvable qualified name are skipped.
     */
    public void accept(CompilationUnit unit) {
        for (AnnotationDeclaration declaration : unit.findAll(AnnotationDeclaration.class)) {
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
     * Entry point annotation FQNs detected on the node, sorted and deduplicated.
     * Composed annotations contribute the carried entry point FQN, not their own name,
     * so the marker always names a known framework annotation. Only method-level
     * annotations are inspected by callers; type-level mappings are out of scope.
     */
    public List<String> entryPointsOf(NodeWithAnnotations<?> node) {
        return entryPointsOfAnnotationFqns(annotationFqnsOf(node));
    }

    /**
     * Raw annotation FQNs of the node, for callers that must resolve annotations
     * eagerly but map them to entry points later (the composed-annotation map may
     * not be complete until every compilation unit has been accepted).
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

    /** Maps already-resolved annotation FQNs to entry point FQNs (sorted, deduplicated). */
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
