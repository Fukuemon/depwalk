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

    static final Set<String> ENTRY_POINT_ANNOTATIONS = Set.of(
            "org.springframework.scheduling.annotation.Scheduled",
            "javax.annotation.PostConstruct",
            "jakarta.annotation.PostConstruct",
            "javax.annotation.PreDestroy",
            "jakarta.annotation.PreDestroy",
            "org.springframework.context.event.EventListener",
            "org.springframework.transaction.event.TransactionalEventListener",
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.ExceptionHandler",
            "org.springframework.web.bind.annotation.ModelAttribute");

    private final Map<String, Set<String>> composedToEntryPoints = new LinkedHashMap<>();

    /**
     * First pass: record user-defined annotations that directly carry an entry point
     * annotation (one meta level only).
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
                        .ifPresent(fqn -> composedToEntryPoints.put(fqn, Set.copyOf(carried)));
            }
        }
    }

    /**
     * Entry point annotation FQNs detected on the node, sorted and deduplicated.
     * Composed annotations contribute the carried entry point FQN, not their own name,
     * so the marker always names a known framework annotation.
     */
    public List<String> entryPointsOf(NodeWithAnnotations<?> node) {
        Set<String> found = new TreeSet<>();
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String fqn = SpringAnnotations.fqn(annotation);
            if (fqn == null) {
                continue;
            }
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
