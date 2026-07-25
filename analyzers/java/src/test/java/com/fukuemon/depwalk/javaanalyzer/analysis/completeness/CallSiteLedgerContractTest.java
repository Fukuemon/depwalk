package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallSiteLedgerContractTest {

    @TempDir
    Path workspace;

    private CallSiteInventory inventoryOf(String relativePath, String source) throws Exception {
        Path file = workspace.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(workspace.toRealPath()));
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25));
        CompilationUnit cu = parser.parse(file.toRealPath()).getResult().orElseThrow();
        CallSiteInventory inventory = new CallSiteInventory(workspace.toRealPath());
        inventory.accept(cu);
        return inventory;
    }

    @Test
    void registersAllCallKindsWithDeterministicIds() throws Exception {
        CallSiteInventory inventory = inventoryOf("com/example/App.java", """
                package com.example;
                public class App {
                    void run() {
                        toString();
                        new App();
                        Runnable r = this::run;
                    }
                    App() { super(); }
                }
                """);

        List<CallSiteId.CallKind> kinds = inventory.ids().stream().map(CallSiteId::callKind).sorted().toList();
        assertEquals(List.of(
                CallSiteId.CallKind.METHOD_CALL,
                CallSiteId.CallKind.OBJECT_CREATION,
                CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION,
                CallSiteId.CallKind.METHOD_REFERENCE).stream().sorted().toList(), kinds);
        assertTrue(inventory.ids().stream()
                .allMatch(id -> "com/example/App.java".equals(id.path()) && id.beginLine() > 0));
        assertTrue(inventory.ids().stream()
                .filter(id -> id.callKind() != CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION)
                .allMatch(id -> "java:com.example.App#run()".equals(id.callerMethodId())));
    }

    @Test
    void registersCallsInsideQualifiedSuperOuterExpression() throws Exception {
        // qualified super (`expr.super(...)`) の outer 式内の method call も
        // inventory へ登録される (walk が式を辿らないと黙示の脱落になる)。
        CallSiteInventory inventory = inventoryOf("com/example/Qualified.java", """
                package com.example;
                public class Qualified {
                    static class Outer {
                        class Inner {}
                    }
                    static class Sub extends Outer.Inner {
                        Sub(Outer outer) {
                            pick(outer).super();
                        }
                        static Outer pick(Outer outer) { return outer; }
                    }
                }
                """);

        assertTrue(inventory.ids().stream().anyMatch(id ->
                        id.callKind() == CallSiteId.CallKind.METHOD_CALL
                                && id.callerMethodId().contains("Sub#<init>")),
                () -> "outer expression call missing from the inventory: " + inventory.ids());
    }

    @Test
    void expandsInstanceFieldInitializerToEachConstructor() throws Exception {
        CallSiteInventory inventory = inventoryOf("com/example/Init.java", """
                package com.example;
                public class Init {
                    private final String value = String.valueOf(1);
                    Init() {}
                    Init(int x) {}
                }
                """);

        List<CallSiteId> fieldInitEntries = inventory.ids().stream()
                .filter(id -> id.callKind() == CallSiteId.CallKind.METHOD_CALL)
                .sorted()
                .toList();
        assertEquals(2, fieldInitEntries.size(), "1 lexical call must expand to each constructor caller");
        assertEquals("java:com.example.Init#<init>()", fieldInitEntries.get(0).callerMethodId());
        assertEquals("java:com.example.Init#<init>(int)", fieldInitEntries.get(1).callerMethodId());
        assertEquals(fieldInitEntries.get(0).beginLine(), fieldInitEntries.get(1).beginLine(),
                "both entries share the same lexical site");
    }

    @Test
    void staticFieldInitializerUsesClinitCaller() throws Exception {
        CallSiteInventory inventory = inventoryOf("com/example/S.java", """
                package com.example;
                public class S {
                    static final String V = String.valueOf(2);
                }
                """);
        assertEquals(1, inventory.ids().size());
        assertEquals("java:com.example.S#<clinit>()", inventory.ids().iterator().next().callerMethodId());
    }

    @Test
    void ledgerEnforcesOneOutcomePerEntry() throws Exception {
        CallSiteInventory inventory = inventoryOf("com/example/L.java", """
                package com.example;
                public class L { void go() { toString(); } }
                """);
        CallSiteId id = inventory.ids().iterator().next();
        CallSiteOutcomeLedger ledger = new CallSiteOutcomeLedger(inventory);

        ledger.commitDiagnostic(id, "JAVA_UNRESOLVED_SYMBOL", "unresolved-method-call", "toString", null);
        // 補助 diagnostic の後に edge が出た entry は primary EMITTED へ昇格する。
        ledger.commitEmitted(id);
        ledger.validateComplete();
        assertTrue(ledger.primaryDiagnostics().isEmpty());
        assertTrue(ledger.summary().contains("silentOmission=0"));

        assertThrows(IllegalStateException.class, () -> ledger.commitExcluded(id, "external-target"));
        CallSiteId unknown = new CallSiteId("x/Y.java", 1, 1, 1, 2, CallSiteId.CallKind.METHOD_CALL, "java:x.Y#z()");
        assertThrows(IllegalStateException.class, () -> ledger.commitEmitted(unknown));
    }

    @Test
    void unclassifiedEntriesFailValidation() throws Exception {
        CallSiteInventory inventory = inventoryOf("com/example/U.java", """
                package com.example;
                public class U { void go() { toString(); } }
                """);
        CallSiteOutcomeLedger ledger = new CallSiteOutcomeLedger(inventory);
        assertThrows(IllegalStateException.class, ledger::validateComplete);
    }
}
