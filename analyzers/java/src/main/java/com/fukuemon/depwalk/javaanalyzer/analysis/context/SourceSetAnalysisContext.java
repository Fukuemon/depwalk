package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.github.javaparser.ParserConfiguration;

import java.nio.file.Path;
import java.util.List;

/**
 * 1 つの解析 context (spec #24 D6)。discovery 経路では Gradle project /
 * {@code main} source set ごとに 1 つ、明示 root 経路では全 root を共有する
 * 1 つの synthetic context になる。識別子は Analyzer 内部専用で Protocol
 * record へは出力しない。
 *
 * @param id context 識別子 (discovery: {@code <projectPath>|main}、明示: {@code explicit})
 * @param sourceRoots 絶対・正規化済み source root。列挙と TypeSolver 登録の起点
 * @param classpath 外部 jar / classes directory (検証済み)
 * @param classesOutputs 自 project の classes output directory。空なら source-only
 * @param dependencyContextIds compile classpath 上の project 依存で到達可能な context id
 * @param languageLevel main parser と TypeSolver parser の両方へ設定する level
 * @param previewEnabled preview 機能の有効性
 */
public record SourceSetAnalysisContext(
        String id,
        List<Path> sourceRoots,
        List<Path> classpath,
        List<Path> classesOutputs,
        List<String> dependencyContextIds,
        ParserConfiguration.LanguageLevel languageLevel,
        boolean previewEnabled) {

    /** 明示 root 経路の synthetic context id。 */
    public static final String EXPLICIT_CONTEXT_ID = "explicit";

    /** 自 project の classes output を持たず SootUp 補完を無効化するか。 */
    public boolean sootUpUnavailable() {
        return classesOutputs.isEmpty();
    }
}
