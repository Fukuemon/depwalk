package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spring event listener methods indexed by their (raw) event parameter type.
 *
 * <p>Feeds the publish-to-listener edge generation (broadcast semantics, ADR-0012):
 * every matching listener is a certain callee, ambiguity applies only to conditional
 * listeners. Only single-parameter methods directly annotated with a listener
 * annotation are indexed; the {@code classes} attribute form and composed listener
 * annotations are out of scope.
 */
public final class EventListenerIndex {

    private static final Set<String> LISTENER_ANNOTATIONS = Set.of(
            "org.springframework.context.event.EventListener",
            "org.springframework.transaction.event.TransactionalEventListener");

    /** One indexed listener method. {@code parameterTypes} always has one element. */
    public record Listener(
            String declaringType,
            String methodName,
            List<String> parameterTypes,
            List<String> conditionTypes) {
    }

    private final Map<String, List<Listener>> listenersByEventType = new LinkedHashMap<>();

    /**
     * First pass: index resolvable listener declarations. Resolution failures are
     * skipped here; the second pass diagnoses the declaration through the normal path.
     */
    public void accept(CompilationUnit unit) {
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            if (method.getParameters().size() != 1 || !hasListenerAnnotation(method)) {
                continue;
            }
            try {
                ResolvedMethodDeclaration resolved = method.resolve();
                String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
                String eventType = BinaryNames.erasureOf(resolved.getParam(0).getType());
                Set<String> conditions = new TreeSet<>(SpringAnnotations.conditionTypes(method));
                method.findAncestor(TypeDeclaration.class)
                        .ifPresent(type -> conditions.addAll(SpringAnnotations.conditionTypes(type)));
                listenersByEventType
                        .computeIfAbsent(eventType, key -> new ArrayList<>())
                        .add(new Listener(
                                declaringType,
                                resolved.getName(),
                                List.of(eventType),
                                List.copyOf(conditions)));
            } catch (RuntimeException | LinkageError ignored) {
                // The declaration walk in the second pass reports unresolved declarations.
            }
        }
    }

    /** Listeners whose parameter type equals the given raw binary name. */
    public List<Listener> listenersFor(String eventTypeBinaryName) {
        return listenersByEventType.getOrDefault(eventTypeBinaryName, List.of());
    }

    public boolean isEmpty() {
        return listenersByEventType.isEmpty();
    }

    private static boolean hasListenerAnnotation(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String fqn = SpringAnnotations.fqn(annotation);
            if (fqn != null && LISTENER_ANNOTATIONS.contains(fqn)) {
                return true;
            }
        }
        return false;
    }
}
