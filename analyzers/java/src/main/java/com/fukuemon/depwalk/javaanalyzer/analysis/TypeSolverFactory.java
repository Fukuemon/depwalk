package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 型解決 (design/features/java-analyzer/DesignDoc_java-analyzer.md 「型解決」) の 3 TypeSolver を構成する。
 * classpath は pre-flight 済み (P1_02) であり、jar / classes dir の存在・読み取り可否はここでは再検査
 * しない。metadata 契約上、classpath の各 entry は「依存 jar」または「classes dir (コンパイル済み
 * .class ファイルの directory)」のいずれも許容するため (M6)、entry が directory の場合は
 * {@link ClassLoaderTypeSolver} ({@link URLClassLoader} 経由) を、jar (通常ファイル) の場合は
 * {@link JarTypeSolver} を使う。
 */
public final class TypeSolverFactory {

    private TypeSolverFactory() {
    }

    /**
     * @param workspaceRoot 対象プロジェクトの source root ({@link JavaParserTypeSolver} に渡す)
     * @param classpathJars {@code analysisRequest.metadata.classpath} の jar / classes dir path 一覧
     * @param languageLevel {@link JavaParserTypeSolver} が workspaceRoot 配下の依存ソース (record 等)
     *                      を読み直す際に使う {@link ParserConfiguration.LanguageLevel} (呼び出し側の
     *                      メインパーサ設定と一致させる。既定 {@code POPULAR} は record を構文サポート
     *                      しないため、呼び出し側で明示的に渡す)
     * @throws IOException jar / classes dir の読み込みに失敗した場合 (pre-flight で存在確認済みのため通常は起きない)
     */
    public static CombinedTypeSolver create(
            Path workspaceRoot, List<String> classpathJars, ParserConfiguration.LanguageLevel languageLevel) throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        ParserConfiguration typeSolverConfig = new ParserConfiguration().setLanguageLevel(languageLevel);
        typeSolver.add(new JavaParserTypeSolver(workspaceRoot, typeSolverConfig));
        for (String entry : classpathJars) {
            Path path = Path.of(entry);
            if (Files.isDirectory(path)) {
                typeSolver.add(classesDirTypeSolver(path));
            } else {
                typeSolver.add(new JarTypeSolver(entry));
            }
        }
        return typeSolver;
    }

    private static ClassLoaderTypeSolver classesDirTypeSolver(Path classesDir) throws IOException {
        URL url;
        try {
            url = classesDir.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IOException("failed to resolve classes directory URL: " + classesDir, e);
        }
        URLClassLoader classLoader = new URLClassLoader(new URL[] {url}, TypeSolverFactory.class.getClassLoader());
        return new ClassLoaderTypeSolver(classLoader);
    }
}
