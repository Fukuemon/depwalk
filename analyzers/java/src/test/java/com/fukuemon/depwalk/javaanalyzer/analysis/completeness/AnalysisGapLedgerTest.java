package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("call site に属さない解析の欠け (AnalysisGapLedger) の記録と集計")
class AnalysisGapLedgerTest {

    private static final String CODE = "JAVA_UNRESOLVED_SYMBOL";
    private static final RuntimeException FAILURE = new IllegalStateException("message must not be recorded");

    @Test
    @DisplayName("同じ宣言の resolve の失敗を 2 回記録したとき、欠けは 1 件になり、集計 (gap[...]) も 1 と数える")
    void sameDeclarationRecordedTwiceBecomesOneGap() {
        ConstructorDeclaration constructor = StaticJavaParser.parse("""
                class A {
                    A(Missing m) {}
                }
                """).findFirst(ConstructorDeclaration.class).orElseThrow();
        AnalysisGapLedger ledger = new AnalysisGapLedger();

        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", constructor, "A", FAILURE);
        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", constructor, "A", FAILURE);

        List<AnalysisGapLedger.AnalysisGap> gaps = ledger.gaps();
        assertEquals(1, gaps.size(), gaps::toString);
        AnalysisGapLedger.AnalysisGap gap = gaps.get(0);
        assertEquals(CODE, gap.code());
        assertEquals(AnalysisGapLedger.REASON_UNRESOLVED_DECLARATION, gap.reason());
        assertEquals(SourceLocation.of("com/example/A.java", 2), gap.sourceLocation());
        assertEquals("A", gap.target());
        assertEquals(Map.of("exceptionClass", "java.lang.IllegalStateException"), gap.metadata());
        assertEquals("gap[JAVA_UNRESOLVED_SYMBOL:unresolved-declaration]=1", ledger.summary());
    }

    @Test
    @DisplayName("同じ行に並ぶ overload の宣言を記録したとき、開始の列で別の欠けとして数える")
    void overloadsOnTheSameLineStaySeparate() {
        List<MethodDeclaration> methods = StaticJavaParser.parse("""
                class A {
                    void f(Missing m) {} void f(Other o) {}
                }
                """).findAll(MethodDeclaration.class);
        AnalysisGapLedger ledger = new AnalysisGapLedger();

        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", methods.get(1), "f", FAILURE);
        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", methods.get(0), "f", FAILURE);

        assertEquals(2, ledger.gaps().size(), () -> ledger.gaps().toString());
        assertEquals("gap[JAVA_UNRESOLVED_SYMBOL:unresolved-declaration]=2", ledger.summary());
    }

    @Test
    @DisplayName("開始の位置を持たない宣言を記録したとき、同じ名前でもまとめず、記録ごとに位置のない欠けを 1 件残す")
    void declarationsWithoutPositionAreNotMerged() {
        AnalysisGapLedger ledger = new AnalysisGapLedger();

        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", new MethodDeclaration(), "f", FAILURE);
        ledger.recordUnresolvedDeclaration(CODE, "com/example/A.java", new MethodDeclaration(), "f", FAILURE);

        List<AnalysisGapLedger.AnalysisGap> gaps = ledger.gaps();
        assertEquals(2, gaps.size(), gaps::toString);
        for (AnalysisGapLedger.AnalysisGap gap : gaps) {
            assertNull(gap.sourceLocation(), "startLine must not be 0, so the location is omitted");
        }
    }

    @Test
    @DisplayName("複数の file と理由の欠けを記録したとき、path、開始の行、開始の列、reason の順に並べ、集計は code と reason の辞書順に並べる")
    void gapsAreOrderedByPathThenPosition() {
        CompilationUnit unit = StaticJavaParser.parse("""
                class B {
                    void late(Missing m) {}
                    void early(Missing m) {}
                }
                """);
        List<MethodDeclaration> methods = unit.findAll(MethodDeclaration.class);
        AnalysisGapLedger ledger = new AnalysisGapLedger();

        ledger.recordUnresolvedDeclaration(CODE, "com/example/B.java", methods.get(1), "early", FAILURE);
        ledger.recordSpringDiIndexFailure(CODE, "com/example/B.java", FAILURE);
        ledger.recordUnresolvedDeclaration(CODE, "com/example/B.java", methods.get(0), "late", FAILURE);
        ledger.recordSpringDiIndexFailure(CODE, "com/example/A.java", FAILURE);

        List<AnalysisGapLedger.AnalysisGap> gaps = ledger.gaps();
        assertEquals(List.of(
                        "com/example/A.java:1:" + AnalysisGapLedger.REASON_SPRING_DI_INDEX_FAILED,
                        "com/example/B.java:1:" + AnalysisGapLedger.REASON_SPRING_DI_INDEX_FAILED,
                        "com/example/B.java:2:" + AnalysisGapLedger.REASON_UNRESOLVED_DECLARATION,
                        "com/example/B.java:3:" + AnalysisGapLedger.REASON_UNRESOLVED_DECLARATION),
                gaps.stream()
                        .map(g -> g.sourceLocation().path() + ":" + g.sourceLocation().startLine() + ":" + g.reason())
                        .toList());
        assertNull(gaps.get(0).target());
        assertEquals("gap[JAVA_UNRESOLVED_SYMBOL:spring-di-index-failed]=2"
                + " gap[JAVA_UNRESOLVED_SYMBOL:unresolved-declaration]=2", ledger.summary());
    }

    @Test
    @DisplayName("欠けを記録していないとき、集計は空文字になる")
    void emptyLedgerHasEmptySummary() {
        AnalysisGapLedger ledger = new AnalysisGapLedger();

        assertEquals(List.of(), ledger.gaps());
        assertEquals("", ledger.summary());
    }
}
