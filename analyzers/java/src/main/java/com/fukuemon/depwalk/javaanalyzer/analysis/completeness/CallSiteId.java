package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import java.util.Comparator;

/**
 * 解析対象 call site の決定的な内部識別子
 * (java-analyzer feature doc「Parse・resolution・call 完全性」)。
 * lexical site (workspace 相対 path + source range + AST call kind) と
 * semantic caller method ID の組で一意になる。initializer 内の 1 lexical call は
 * caller (constructor) ごとに別 ID へ展開される。Protocol へは出力しない。
 *
 * @param path workspace 相対 path ({@code /} 区切り)
 * @param beginLine 開始行 (1-based)
 * @param beginColumn 開始 column (1-based)
 * @param endLine 終了行
 * @param endColumn 終了 column
 * @param callKind AST call kind
 * @param callerMethodId semantic caller の method ID
 */
public record CallSiteId(
        String path,
        int beginLine,
        int beginColumn,
        int endLine,
        int endColumn,
        CallKind callKind,
        String callerMethodId) implements Comparable<CallSiteId> {

    /** 解析対象の AST call kind。 */
    public enum CallKind {
        METHOD_CALL("method-call"),
        OBJECT_CREATION("object-creation"),
        EXPLICIT_CONSTRUCTOR_INVOCATION("explicit-constructor-invocation"),
        METHOD_REFERENCE("method-reference");

        private final String label;

        CallKind(String label) {
            this.label = label;
        }

        /** detail metadata へ出力する安定 label。 */
        public String label() {
            return label;
        }
    }

    private static final Comparator<CallSiteId> ORDER = Comparator.comparing(CallSiteId::path)
            .thenComparingInt(CallSiteId::beginLine)
            .thenComparingInt(CallSiteId::beginColumn)
            .thenComparingInt(CallSiteId::endLine)
            .thenComparingInt(CallSiteId::endColumn)
            .thenComparing(CallSiteId::callKind)
            .thenComparing(CallSiteId::callerMethodId);

    /** inventory 登録順と details 出力順を安定させる決定的順序。 */
    @Override
    public int compareTo(CallSiteId other) {
        return ORDER.compare(this, other);
    }
}
