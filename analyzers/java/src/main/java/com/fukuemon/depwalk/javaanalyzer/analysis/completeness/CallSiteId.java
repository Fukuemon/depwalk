package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

/**
 * 解析対象 call site の決定的な内部識別子 (spec #24 D17 / D28)。
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

    /** inventory 登録順と details 出力順を安定させる決定的順序。 */
    @Override
    public int compareTo(CallSiteId other) {
        int c = path.compareTo(other.path);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(beginLine, other.beginLine);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(beginColumn, other.beginColumn);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(endLine, other.endLine);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(endColumn, other.endColumn);
        if (c != 0) {
            return c;
        }
        c = callKind.compareTo(other.callKind);
        if (c != 0) {
            return c;
        }
        return callerMethodId.compareTo(other.callerMethodId);
    }
}
