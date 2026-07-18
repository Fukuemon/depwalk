package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * graph record 出力前の全 file parse 検証 (spec #24 D15)。1 件でも設定済み
 * language level で parse できない file があれば request 全体を
 * {@code JAVA_PARSE_ERROR} の fatal にする。file skip / partial mode /
 * 別 level への fallback は行わない。AST は file ごとに破棄する。
 */
public final class ParsePreflight {

    private ParsePreflight() {
    }

    /**
     * 全 file を workspace 相対 path 順に parse 検証する。
     *
     * @param workspaceRoot record path の基準
     * @param allFiles workspace 相対 path 昇順の全対象 file
     * @param contextByFile file → 所有 context
     * @param parserByContext context id → 設定済み parser
     * @return pre-flight の所要時間 (millis)
     * @throws AnalyzerFatalException 最初の parse 失敗 (決定的順序)
     */
    public static long verify(
            Path workspaceRoot,
            List<Path> allFiles,
            Map<Path, SourceSetAnalysisContext> contextByFile,
            Map<String, JavaParser> parserByContext) throws AnalyzerFatalException {
        long start = System.nanoTime();
        for (Path file : allFiles) {
            SourceSetAnalysisContext context = contextByFile.get(file);
            JavaParser parser = parserByContext.get(context.id());
            String relative = ContextScope.workspaceRelative(workspaceRoot, file);
            try {
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    throw parseFailure(relative, context, firstProblem(result.getProblems()));
                }
                // AST はここでスコープを抜けて破棄される。全成功後の通常解析で再 parse する。
            } catch (IOException | ParseProblemException e) {
                throw parseFailure(relative, context, e.getMessage());
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static AnalyzerFatalException parseFailure(
            String relativePath, SourceSetAnalysisContext context, String parserMessage) {
        return new AnalyzerFatalException(
                JavaErrorCode.JAVA_PARSE_ERROR,
                "failed to parse " + relativePath
                        + " with language level " + context.languageLevel()
                        + ": " + (parserMessage == null ? "unknown parser failure" : parserMessage));
    }

    private static String firstProblem(List<Problem> problems) {
        if (problems.isEmpty()) {
            return "unknown parser failure";
        }
        Problem problem = problems.get(0);
        String location = problem.getLocation()
                .flatMap(range -> range.getBegin().getRange())
                .map(r -> r.begin)
                .map(Position::toString)
                .orElse("");
        return (location.isEmpty() ? "" : location + " ") + problem.getMessage();
    }
}
