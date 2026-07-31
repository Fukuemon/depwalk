package com.fukuemon.depwalk.javaanalyzer.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Core から Analyzer process の stdin へ 1 件だけ送信される解析要求。
 * protocol の正本は analyzer-protocol feature doc / ADR-0001 であり、本 record はそれに準拠する
 * 受信側 DTO である (schema はここで定義・変更しない)。
 *
 * @param schemaVersion Analyzer Protocol の schema version。現在は {@code "1"}
 * @param recordType    {@code analysisRequest}
 * @param requestId     解析要求を識別する ID
 * @param workspaceRoot 解析対象 repository root
 * @param sourceRoots   workspaceRoot 相対の source root 配列 (任意)。Protocol 契約上、省略は
 *                      Analyzer の build model discovery への委譲、1 件以上は明示 override を
 *                      意味し、空配列は invalid である (受信側検査は
 *                      {@code AnalysisContextFactory} が明示 root 解決時に行い、空配列は
 *                      {@code JAVA_INVALID_REQUEST} で fatal になる)
 * @param language      対象言語。Java Analyzer では {@code java}
 * @param include       workspaceRoot からの相対 path glob 配列 (任意)
 * @param exclude       workspaceRoot からの相対除外 path glob 配列 (任意)
 * @param entrypoints   起点 method selector 配列 (任意)
 * @param analysisMode  {@code fullGraph} または {@code reachableFromEntrypoints} (任意)
 * @param metadata      言語固有 / Analyzer 固有の hint (任意)。Java 固有 key は
 *                      {@code classpath} (必須 key) / {@code liftExcludePackages} (任意)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisRequest(
        String schemaVersion,
        String recordType,
        String requestId,
        String workspaceRoot,
        List<String> sourceRoots,
        String language,
        List<String> include,
        List<String> exclude,
        List<MethodSelector> entrypoints,
        String analysisMode,
        Map<String, Object> metadata) {
}
