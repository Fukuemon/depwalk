package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.protocol.FailureDetail;

import java.util.List;
import java.util.Map;

/**
 * 全 resolver と bytecode 救済の完了後も primary diagnostic が残った request の
 * fatal 化 (spec #24 D20 / D22)。全未解決 call の自己完結な detail と
 * 集計 metadata を保持し、{@code JAVA_INCOMPLETE_ANALYSIS} の error record へ
 * 変換される。
 */
public class IncompleteAnalysisException extends Exception {

    private final transient List<FailureDetail> details;
    private final transient Map<String, Object> metadata;

    public IncompleteAnalysisException(String message, List<FailureDetail> details, Map<String, Object> metadata) {
        super(message);
        if (details.isEmpty()) {
            throw new IllegalStateException("JAVA_INCOMPLETE_ANALYSIS requires at least one failure detail");
        }
        this.details = List.copyOf(details);
        this.metadata = Map.copyOf(metadata);
    }

    public List<FailureDetail> details() {
        return details;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
