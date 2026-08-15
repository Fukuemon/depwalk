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
 * Spring event listener method を (raw の) event parameter 型で索引する。
 *
 * <p>publish → listener の edge 生成 (broadcast 意味論) に使う。合致した
 * listener はいずれも callee になり、実行時条件を持つ listener と raw type 近似が
 * 過剰一致しうる listener が ambiguous になる。listener アノテーションを直接付けた
 * 単一 parameter の method だけを索引し、{@code classes} 属性形と合成 listener
 * アノテーションは対象外。
 */
public final class EventListenerIndex {

    private static final Set<String> LISTENER_ANNOTATIONS = Set.of(
            "org.springframework.context.event.EventListener",
            "org.springframework.transaction.event.TransactionalEventListener");

    /**
     * 索引した listener method 1 件。{@code parameterTypes} は常に 1 要素。
     * {@code conditionTypes} は条件の根拠となったアノテーション FQN を持つ
     * (条件アノテーションの FQN と、空文字列と証明できない {@code condition} 属性や transactional
     * phase 依存の根拠として listener アノテーション自身の FQN が混在する)。空でなければ
     * edge は ambiguous になる。{@code rawApproximation} は parameter 型が型変数または
     * 型引数付きで、raw type 突合が過剰一致しうる場合に true。
     */
    public record Listener(
            String declaringType,
            String methodName,
            List<String> parameterTypes,
            List<String> conditionTypes,
            boolean rawApproximation) {
    }

    private final Map<String, List<Listener>> listenersByEventType = new LinkedHashMap<>();

    /**
     * first pass: 型解決できた listener 宣言を索引する。解決に失敗した宣言は
     * 索引せず読み飛ばす。
     */
    public void accept(CompilationUnit unit) {
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            if (method.getParameters().size() != 1 || !hasListenerAnnotation(method)) {
                continue;
            }
            try {
                ResolvedMethodDeclaration resolved = method.resolve();
                String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
                var paramType = resolved.getParam(0).getType();
                String eventType = BinaryNames.erasureOf(paramType);
                Set<String> conditions = new TreeSet<>(SpringAnnotations.conditionTypes(method));
                method.findAncestor(TypeDeclaration.class)
                        .ifPresent(type -> conditions.addAll(SpringAnnotations.conditionTypes(type)));
                conditions.addAll(runtimeConditionsOf(method));
                boolean rawApproximation = paramType.isTypeVariable()
                        || (paramType.isReferenceType()
                                && !paramType.asReferenceType().typeParametersValues().isEmpty());
                listenersByEventType
                        .computeIfAbsent(eventType, key -> new ArrayList<>())
                        .add(new Listener(
                                declaringType,
                                resolved.getName(),
                                List.of(eventType),
                                List.copyOf(conditions),
                                rawApproximation));
            } catch (RuntimeException | LinkageError ignored) {
                // 解決できない listener はここで診断せず索引から漏らす。listener 宣言
                // 自身の解決失敗は second pass の通常診断 (JAVA_UNRESOLVED_SYMBOL)
                // として現れる (JAVA_EVENT_UNRESOLVED は publish 引数型の解決失敗専用)。
            }
        }
    }

    /** parameter 型が指定の raw binary name と一致する listener。 */
    public List<Listener> listenersFor(String eventTypeBinaryName) {
        return listenersByEventType.getOrDefault(eventTypeBinaryName, List.of());
    }

    public boolean isEmpty() {
        return listenersByEventType.isEmpty();
    }

    /**
     * listener アノテーション自身が運ぶ実行時条件: 空文字列と証明できない {@code condition}
     * 属性 (定数参照や連結など値を確定できない式を含む) と、
     * {@code @TransactionalEventListener} の transaction phase 依存 (囲む transaction が
     * 設定 phase に到達したときだけ発火する)。いずれも実行を条件付きにするため broadcast の
     * 確実性が成り立たず、edge は ambiguous にする。返すのは条件の根拠となった listener
     * アノテーション自身の FQN で、conditionTypes には条件アノテーションの FQN と種別が
     * 混在して入る。
     */
    private static java.util.List<String> runtimeConditionsOf(MethodDeclaration method) {
        java.util.List<String> conditions = new ArrayList<>();
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String fqn = SpringAnnotations.fqn(annotation);
            if (fqn == null || !LISTENER_ANNOTATIONS.contains(fqn)) {
                continue;
            }
            if ("org.springframework.transaction.event.TransactionalEventListener".equals(fqn)
                    || SpringAnnotations.hasNonEmptyAttribute(annotation, "condition")) {
                conditions.add(fqn);
            }
        }
        return conditions;
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
