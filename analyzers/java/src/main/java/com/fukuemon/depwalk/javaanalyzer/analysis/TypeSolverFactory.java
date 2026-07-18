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
 * classpath は解析開始前に検証済みであり、jar / classes dir の存在・読み取り可否はここでは再検査
 * しない。metadata 契約上、classpath の各 entry は「依存 jar」または「classes dir (コンパイル済み
 * .class ファイルの directory)」のいずれも許容する。jar は {@link JarTypeSolver} で直接読み、
 * classes directory は全 classpath entry を共有する {@link URLClassLoader} 経由の
 * {@link ClassLoaderTypeSolver} で読む。共有 classloader により、ある module の class が別 module
 * または依存 jar の型を参照していても JVM の class loading が途中で失敗しない。
 */
public final class TypeSolverFactory {

    private TypeSolverFactory() {
    }

    /**
     * workspace source と検証済み classpath を検索する合成 TypeSolver を生成する。
     *
     * @param workspaceRoot 対象プロジェクトの source root ({@link JavaParserTypeSolver} に渡す)
     * @param classpathJars {@code analysisRequest.metadata.classpath} の jar / classes dir path 一覧
     * @param languageLevel {@link JavaParserTypeSolver} が workspaceRoot 配下の依存ソース (record 等)
     *                      を読み直す際に使う {@link ParserConfiguration.LanguageLevel} (呼び出し側の
     *                      メインパーサ設定と一致させる。既定 {@code POPULAR} は record を構文サポート
     *                      しないため、呼び出し側で明示的に渡す)
     * @return reflection、source、jar、classes directory を合成した TypeSolver
     * @throws IOException jar / classes dir の読み込みに失敗した場合 (pre-flight で存在確認済みのため通常は起きない)
     */
    public static CombinedTypeSolver create(
            Path workspaceRoot, List<String> classpathJars, ParserConfiguration.LanguageLevel languageLevel) throws IOException {
        return createForRoots(
                List.of(workspaceRoot),
                classpathJars.stream().map(Path::of).toList(),
                languageLevel);
    }

    /**
     * 複数 source root と classpath entry から合成 TypeSolver を生成する。
     * source root は classpath (project classes output を含む) より先に登録し、
     * source 宣言を bytecode より優先する (spec #24 D6)。
     *
     * @param sourceRoots {@link JavaParserTypeSolver} へ登録する package hierarchy 起点 (各 1 回)
     * @param classpathEntries 検証済み jar / classes dir
     * @param languageLevel 内部 parser の language level (メインパーサと一致させる)
     * @return 合成 TypeSolver
     * @throws IOException jar / classes dir の読み込みに失敗した場合
     */
    public static CombinedTypeSolver createForRoots(
            List<Path> sourceRoots, List<Path> classpathEntries, ParserConfiguration.LanguageLevel languageLevel) throws IOException {
        return createForRoots(sourceRoots, classpathEntries, languageLevel, null);
    }

    /**
     * bytecode member 合成付きの合成 TypeSolver を生成する (spec #24 D31)。
     * {@code bytecodeIndex} が非 null のとき、source root の解決結果の class 宣言へ
     * 同一 context classes output の bytecode-only member を fallback 合成する。
     */
    public static CombinedTypeSolver createForRoots(
            List<Path> sourceRoots,
            List<Path> classpathEntries,
            ParserConfiguration.LanguageLevel languageLevel,
            com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex bytecodeIndex)
            throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        ParserConfiguration typeSolverConfig = new ParserConfiguration().setLanguageLevel(languageLevel);
        for (Path root : sourceRoots) {
            JavaParserTypeSolver sourceSolver = new JavaParserTypeSolver(root, typeSolverConfig);
            typeSolver.add(bytecodeIndex != null
                    ? new com.fukuemon.depwalk.javaanalyzer.analysis.augment.MemberAugmentingTypeSolver(sourceSolver, bytecodeIndex)
                    : sourceSolver);
        }
        // JavaParserTypeSolver が内部 parse した AST にも resolver を注入する。
        // record の canonical constructor 解決 (JavaParserRecordDeclaration) は
        // solver 内部 CU の Parameter#resolve() を呼ぶため、resolver 不在だと
        // "Symbol resolution not configured" の IllegalStateException になる
        // (#24 実プロジェクト検証で検出)。config は parse 時に参照されるため
        // 循環参照でも post-hoc 設定で機能する。
        typeSolverConfig.setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(typeSolver));
        List<Path> entries = classpathEntries.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        for (Path entry : entries) {
            if (!Files.isDirectory(entry)) {
                typeSolver.add(new JarTypeSolver(entry));
            }
        }
        if (entries.stream().anyMatch(Files::isDirectory)) {
            typeSolver.add(classpathTypeSolver(entries));
        }
        return typeSolver;
    }

    private static ClassLoaderTypeSolver classpathTypeSolver(List<Path> entries) throws IOException {
        URL[] urls = new URL[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            try {
                urls[i] = entries.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IOException("failed to resolve classpath entry URL: " + entries.get(i), e);
            }
        }
        URLClassLoader classLoader = new URLClassLoader(urls, TypeSolverFactory.class.getClassLoader());
        return new ClassLoaderTypeSolver(classLoader);
    }
}
