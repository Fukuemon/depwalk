package com.fukuemon.depwalk.javaanalyzer.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * stdin から {@code analysisRequest} JSONL 1 件を読み取る。
 * process contract (analyzer-protocol 正本): stdin は {@code analysisRequest} 1 件を受けて close される。
 */
public final class RequestReader {

    private final ObjectMapper mapper;

    public RequestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * stdin の先頭 1 行を {@link AnalysisRequest} として deserialize する。未知 field は無視する。
     *
     * @param in UTF-8 JSONL の入力
     * @throws IOException stdin が空、または JSON として不正な場合
     */
    public AnalysisRequest read(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            throw new IOException("no analysisRequest received on stdin");
        }
        return mapper.readValue(line, AnalysisRequest.class);
    }
}
