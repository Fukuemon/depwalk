package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * call site に属さない解析の欠け (resolve できない宣言、索引を見送った file) を記録する内部台帳。
 * 欠けが 1 件でもあれば、primary diagnostic と同じく完全性 gate の対象になる。
 *
 * <p>同じ欠けを複数の経路から記録しても 1 件にまとめる。宣言は file の path と開始の行と列で
 * 識別する。開始の位置を持たない宣言は識別できないため、まとめずに記録ごとに 1 件とする。
 */
public final class AnalysisGapLedger {

    /** method / constructor の宣言を resolve できず、その宣言の node を出せなかった場合の reason。 */
    public static final String REASON_UNRESOLVED_DECLARATION = "unresolved-declaration";
    /** Spring DI の索引で例外が起き、その file の索引を見送った場合の reason。 */
    public static final String REASON_SPRING_DI_INDEX_FAILED = "spring-di-index-failed";

    /**
     * 1 件の欠け。
     *
     * @param code 元 diagnostic code
     * @param reason {@link #REASON_UNRESOLVED_DECLARATION} または {@link #REASON_SPRING_DI_INDEX_FAILED}
     * @param sourceLocation 位置が判明している場合のみ: 欠けた箇所
     * @param target 判明している場合のみ: 欠けた宣言の名前
     * @param metadata sanitize 済み診断項目 (例外のクラス名など)
     */
    public record AnalysisGap(
            String code,
            String reason,
            SourceLocation sourceLocation,
            String target,
            Map<String, Object> metadata) {
    }

    /**
     * 欠けの識別子。{@code sequence} は開始の位置を持たない宣言にだけ振る通し番号で、
     * 位置で識別できる欠けでは 0 にする。
     */
    private record GapKey(String path, int line, int column, String reason, String target, long sequence)
            implements Comparable<GapKey> {

        private static final Comparator<GapKey> ORDER = Comparator.comparing(GapKey::path)
                .thenComparingInt(GapKey::line)
                .thenComparingInt(GapKey::column)
                .thenComparing(GapKey::reason)
                .thenComparing(GapKey::target)
                .thenComparingLong(GapKey::sequence);

        @Override
        public int compareTo(GapKey other) {
            return ORDER.compare(this, other);
        }
    }

    private final Map<GapKey, AnalysisGap> gaps = new TreeMap<>();
    private long unpositionedCount;

    /**
     * method / constructor の宣言の resolve の失敗を記録する。
     *
     * @param code 元 diagnostic code
     * @param path 宣言を含む file の workspace 相対 path
     * @param declaration resolve できなかった宣言の node。開始の位置で宣言を識別する
     * @param declarationName 宣言の名前
     * @param failure resolve が投げた例外。クラス名だけを記録し、message は含めない
     */
    public void recordUnresolvedDeclaration(
            String code, String path, Node declaration, String declarationName, Throwable failure) {
        Optional<Position> begin = declaration.getBegin();
        GapKey key = begin
                .map(p -> new GapKey(path, p.line, p.column, REASON_UNRESOLVED_DECLARATION, declarationName, 0))
                .orElseGet(() -> new GapKey(
                        path, 0, 0, REASON_UNRESOLVED_DECLARATION, declarationName, ++unpositionedCount));
        // sourceLocation の startLine は 1 以上を要するため、位置が不明なら sourceLocation を省く。
        SourceLocation location = begin.map(p -> SourceLocation.of(path, p.line)).orElse(null);
        gaps.putIfAbsent(key, new AnalysisGap(
                code, REASON_UNRESOLVED_DECLARATION, location, declarationName, metadataOf(failure)));
    }

    /**
     * Spring DI の索引の失敗を記録する。
     *
     * @param code 元 diagnostic code
     * @param path 索引を見送った file の workspace 相対 path
     * @param failure 索引が投げた例外。クラス名だけを記録し、message は含めない
     */
    public void recordSpringDiIndexFailure(String code, String path, Throwable failure) {
        gaps.putIfAbsent(
                new GapKey(path, 1, 0, REASON_SPRING_DI_INDEX_FAILED, "", 0),
                new AnalysisGap(code, REASON_SPRING_DI_INDEX_FAILED, SourceLocation.of(path, 1), null,
                        metadataOf(failure)));
    }

    private static Map<String, Object> metadataOf(Throwable failure) {
        return Map.of("exceptionClass", failure.getClass().getName());
    }

    /** 記録した欠けを、path、開始の行、開始の列、reason、target の順で返す。 */
    public List<AnalysisGap> gaps() {
        return List.copyOf(gaps.values());
    }

    /**
     * production stderr 用の、code と reason ごとの件数。{@code gap[<code>:<reason>]=<件数>} を
     * 空白で区切って並べ、欠けが無ければ空文字を返す。
     */
    public String summary() {
        Map<String, Long> countByReason = new TreeMap<>();
        for (AnalysisGap gap : gaps.values()) {
            countByReason.merge(gap.code() + ":" + gap.reason(), 1L, Long::sum);
        }
        StringBuilder text = new StringBuilder();
        countByReason.forEach((reason, count) -> {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append("gap[").append(reason).append("]=").append(count);
        });
        return text.toString();
    }
}
