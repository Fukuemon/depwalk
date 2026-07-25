package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSourceDeclarationIndexTest {

    @TempDir
    Path workspace;

    @Test
    void rejectsCrossContextDuplicatesByParsedBinaryName() throws Exception {
        // 配置 (root 相対 path) は異なるが package 宣言が同じ 2 file。
        // ContextScope の path 近似では検出できず、実 binary name で検出する。
        CompilationUnit first = parse(write("module-a/src/main/java/com/example/Dup.java",
                "package com.example; public class Dup {}"));
        CompilationUnit second = parse(write("module-b/src/main/java/misplaced/Dup.java",
                "package com.example; public class Dup {}"));

        WorkspaceSourceDeclarationIndex index = new WorkspaceSourceDeclarationIndex(workspace);
        index.accept(first, "a|main");
        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> index.accept(second, "b|main"));
        assertEquals(JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS, e.errorCode());
    }

    @Test
    void keepsTheFirstDeclarationWithinTheSameContext() throws Exception {
        CompilationUnit first = parse(write("src/main/java/com/example/Same.java",
                "package com.example; public class Same {}"));

        WorkspaceSourceDeclarationIndex index = new WorkspaceSourceDeclarationIndex(workspace);
        index.accept(first, "a|main");
        index.accept(first, "a|main");
        assertTrue(index.find("com.example.Same").isPresent());
        assertEquals("a|main", index.find("com.example.Same").get().contextId());
    }

    private Path write(String relative, String source) throws Exception {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private static CompilationUnit parse(Path file) throws Exception {
        return new JavaParser().parse(file).getResult().orElseThrow();
    }
}
