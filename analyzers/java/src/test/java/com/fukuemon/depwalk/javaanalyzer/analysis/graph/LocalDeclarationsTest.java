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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 各メソッドの {@code use(x)} の引数の {@code x} から、見える宣言を探す。 */
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

                void tryResource() {
                    try (AutoCloseable x = open()) {
                        use(x);
                    }
                }

                void switchGroup(int k) {
                    switch (k) {
                        case 1:
                            String x = "one";
                            break;
                        case 2:
                            use(x);
                    }
                }

                void sameStatement() {
                    String x = "first", y = use(x);
                }

                void lambdaParameter() {
                    java.util.function.Consumer<String> c = x -> use(x);
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
    @DisplayName("for のヘッダの宣言は、本体から見える")
    void forHeaderDeclarationIsVisibleInTheBody() {
        assertEquals("1", initializerAt("forHeader"));
    }

    @Test
    @DisplayName("for-each の変数は、本体から見える")
    void forEachVariableIsVisibleInTheBody() {
        VariableDeclarator declarator = visibleAt("forEachVariable");
        assertEquals("String", declarator.getTypeAsString());
    }

    @Test
    @DisplayName("try の resource は、try の block から見える")
    void tryResourceIsVisibleInTheTryBlock() {
        assertEquals("open()", initializerAt("tryResource"));
    }

    @Test
    @DisplayName("文の group の switch では、前の case の宣言が後ろの case から見える")
    void earlierCaseDeclarationIsVisibleInALaterCase() {
        assertEquals("\"one\"", initializerAt("switchGroup"));
    }

    @Test
    @DisplayName("同じ文の前の declarator は、後ろの declarator の initializer から見える")
    void earlierDeclaratorInTheSameStatementIsVisible() {
        assertEquals("\"first\"", initializerAt("sameStatement"));
    }

    @Test
    @DisplayName("lambda の parameter が同じ名前なら、宣言を採らない")
    void lambdaParameterIsNotADeclarator() {
        assertNull(visibleAt("lambdaParameter"));
    }

    @Test
    @DisplayName("見える宣言のスコープの中で代入していれば、再代入とみなす")
    void assignmentInTheScopeIsAReassignment() {
        assertTrue(LocalDeclarations.reassigned(visibleAt("reassigned")));
    }

    @Test
    @DisplayName("兄弟の block の同名の変数への代入は、見える宣言の再代入とみなさない")
    void assignmentInASiblingBlockIsNotAReassignment() {
        assertFalse(LocalDeclarations.reassigned(visibleAt("reassignedOnlyInSiblingBlock")));
    }

    private static String initializerAt(String method) {
        return LocalDeclarations.initializerOf(visibleAt(method)).toString();
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
