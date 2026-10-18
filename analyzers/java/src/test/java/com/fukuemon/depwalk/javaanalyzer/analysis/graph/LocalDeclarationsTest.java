package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 各メソッドで最初の {@code use(x)} の引数の {@code x} から、見える宣言を探す。 */
@DisplayName("local 変数の名前から、使う位置で見える宣言を探す")
class LocalDeclarationsTest {

    private static final CompilationUnit SOURCE = StaticJavaParser.parse("""
            class Sample {
                String x = "field";

                void forHeader() {
                    for (int x = 1; x < 3; x++) {
                        use(x);
                    }
                }

                void forEachVariable(java.util.List<String> list) {
                    for (String x : list) {
                        use(x);
                    }
                }

                void forEachIterable() {
                    for (String x : use(x)) {
                    }
                }

                void tryResource() throws Exception {
                    try (AutoCloseable x = open()) {
                        use(x);
                    }
                }

                void tryResourceFromCatch() {
                    try (AutoCloseable x = open()) {
                    } catch (Exception e) {
                        use(x);
                    }
                }

                void catchParameter() {
                    try {
                    } catch (RuntimeException x) {
                        use(x);
                    }
                }

                void switchGroup(int k) {
                    switch (k) {
                        case 1:
                            String x = "one";
                            break;
                        case 2:
                            x = "two";
                            use(x);
                    }
                }

                void switchSelector() {
                    switch (use(x)) {
                        case "a":
                            String x = "case";
                            break;
                    }
                }

                void sameStatement() {
                    String x = "first", y = use(x);
                }

                void laterInTheSameBlock() {
                    use(x);
                    String x = "later";
                }

                void lambdaParameter() {
                    java.util.function.Consumer<String> c = x -> use(x);
                }

                void anonymousClassMember() {
                    String x = "outer";
                    new Object() {
                        void inner() {
                            use(x);
                        }
                    };
                }

                void reassigned(Runnable a, Runnable b) {
                    Runnable x = a;
                    x = b;
                    use(x);
                }

                void reassignedOnlyInSiblingBlock(Runnable a, Runnable b) {
                    {
                        Runnable x = a;
                        x = b;
                    }
                    Runnable x = b;
                    use(x);
                }
            }
            """);

    @Test
    @DisplayName("for のヘッダで宣言した名前を本体で使うとき、ヘッダの宣言を採る")
    void forHeaderDeclarationIsAdoptedInTheBody() {
        assertEquals("1", initializerAt("forHeader"));
    }

    @Test
    @DisplayName("for-each の変数を本体で使うとき、その変数の宣言を採る")
    void forEachVariableIsAdoptedInTheBody() {
        VariableDeclarator declarator = visibleAt("forEachVariable");
        assertNotNull(declarator);
        assertEquals("String", declarator.getTypeAsString());
    }

    @Test
    @DisplayName("for-each の変数と同じ名前を iterable の式で使うとき、変数の宣言を採らない")
    void forEachVariableIsNotAdoptedInTheIterable() {
        assertNull(visibleAt("forEachIterable"));
    }

    @Test
    @DisplayName("try の resource を try の block で使うとき、resource の宣言を採る")
    void tryResourceIsAdoptedInTheTryBlock() {
        assertEquals("open()", initializerAt("tryResource"));
    }

    @Test
    @DisplayName("try の resource と同じ名前を catch 節で使うとき、resource の宣言を採らない")
    void tryResourceIsNotAdoptedInACatchClause() {
        assertNull(visibleAt("tryResourceFromCatch"));
    }

    @Test
    @DisplayName("catch 節の parameter を使うとき、宣言を採らない")
    void catchParameterIsNotADeclarator() {
        assertNull(visibleAt("catchParameter"));
    }

    @Test
    @DisplayName("文の group の switch で、前の case の宣言を後ろの case で使うとき、前の case の宣言を採る")
    void earlierCaseDeclarationIsAdoptedInALaterCase() {
        assertEquals("\"one\"", initializerAt("switchGroup"));
    }

    @Test
    @DisplayName("switch の selector で使うとき、case の中の同じ名前の宣言を採らない")
    void caseDeclarationIsNotAdoptedInTheSelector() {
        assertNull(visibleAt("switchSelector"));
    }

    @Test
    @DisplayName("同じ文の前の declarator を後ろの declarator の initializer で使うとき、前の declarator を採る")
    void earlierDeclaratorInTheSameStatementIsAdopted() {
        assertEquals("\"first\"", initializerAt("sameStatement"));
    }

    @Test
    @DisplayName("宣言より前で同じ名前を使うとき、後ろの宣言を採らない")
    void laterDeclarationIsNotAdopted() {
        assertNull(visibleAt("laterInTheSameBlock"));
    }

    @Test
    @DisplayName("lambda の parameter を使うとき、宣言を採らない")
    void lambdaParameterIsNotADeclarator() {
        assertNull(visibleAt("lambdaParameter"));
    }

    @Test
    @DisplayName("匿名 class の member の中で外側の local を使うとき、member の境界で探すのをやめて宣言を採らない")
    void searchStopsAtAnAnonymousClassMember() {
        assertNull(visibleAt("anonymousClassMember"));
    }

    @Test
    @DisplayName("見える宣言のスコープの中で、宣言より後ろに代入があるとき、再代入とみなす")
    void assignmentAfterTheDeclarationIsAReassignment() {
        assertTrue(LocalDeclarations.reassigned(visibleAt("reassigned")));
    }

    @Test
    @DisplayName("兄弟の block の同じ名前の変数に代入しているとき、見える宣言の再代入とみなさない")
    void assignmentInASiblingBlockIsNotAReassignment() {
        assertFalse(LocalDeclarations.reassigned(visibleAt("reassignedOnlyInSiblingBlock")));
    }

    private static String initializerAt(String method) {
        VariableDeclarator declarator = visibleAt(method);
        assertNotNull(declarator, () -> "no declaration is visible in " + method);
        return LocalDeclarations.initializerOf(declarator).toString();
    }

    private static VariableDeclarator visibleAt(String method) {
        MethodDeclaration declaration = SOURCE.findFirst(
                        MethodDeclaration.class, candidate -> candidate.getNameAsString().equals(method))
                .orElseThrow();
        NameExpr use = declaration.findFirst(
                        MethodCallExpr.class, call -> call.getNameAsString().equals("use"))
                .orElseThrow()
                .getArgument(0)
                .asNameExpr();
        return LocalDeclarations.visibleAt(use, use.getNameAsString());
    }
}
