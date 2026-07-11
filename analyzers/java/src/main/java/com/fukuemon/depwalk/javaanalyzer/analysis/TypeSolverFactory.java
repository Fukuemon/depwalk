package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 型解決 (design/features/java-analyzer/DesignDoc_java-analyzer.md 「型解決」) の 3 TypeSolver を構成する。
 * classpath は pre-flight 済み (P1_02) であり、jar の存在・読み取り可否はここでは再検査しない。
 */
public final class TypeSolverFactory {

    private TypeSolverFactory() {
    }

    /**
     * @param workspaceRoot 対象プロジェクトの source root ({@link JavaParserTypeSolver} に渡す)
     * @param classpathJars {@code analysisRequest.metadata.classpath} の jar path 一覧
     * @throws IOException jar の読み込みに失敗した場合 (pre-flight で存在確認済みのため通常は起きない)
     */
    public static CombinedTypeSolver create(Path workspaceRoot, List<String> classpathJars) throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(workspaceRoot));
        for (String jar : classpathJars) {
            typeSolver.add(new JarTypeSolver(jar));
        }
        return typeSolver;
    }
}
