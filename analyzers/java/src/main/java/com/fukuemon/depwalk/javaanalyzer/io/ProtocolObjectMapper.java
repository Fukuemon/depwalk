package com.fukuemon.depwalk.javaanalyzer.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** protocol I/O 全体で共有する {@link ObjectMapper} の構成。 */
public final class ProtocolObjectMapper {

    private ProtocolObjectMapper() {
    }

    /**
     * 未知 field を無視する ({@code analysisRequest} の未知 field 無視は protocol の規則) よう構成し、
     * 値を持たない任意 field を出力から省く ({@code testdata/analyzer-protocol/} の fixture が
     * 任意 field 不在時に key 自体を省略している慣行に合わせる) {@link ObjectMapper} を返す。
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
        return mapper;
    }
}
