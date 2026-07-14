package com.fukuemon.depwalk.javaanalyzer.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.protocol.ProtocolRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * {@code methodSymbol} / {@code callEdge} / {@code diagnostic} / {@code error} を JSONL 1 行 1 record
 * で stdout へ書き出す writer。streaming flush を前提にし、グラフ全体をメモリ保持しない。
 */
public final class RecordWriter implements AutoCloseable {

    private final ObjectMapper mapper;
    private final Writer out;

    /**
     * UTF-8 JSONL writer を生成する。
     *
     * @param out protocol record の出力先
     * @param mapper record の serialize に使う mapper
     */
    public RecordWriter(OutputStream out, ObjectMapper mapper) {
        this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.mapper = mapper;
    }

    /**
     * record を1行の JSON として書き出し、即座に flush する。
     *
     * @param record 出力する protocol record
     * @throws IOException serialize または出力に失敗した場合
     */
    public void write(ProtocolRecord record) throws IOException {
        out.write(mapper.writeValueAsString(record));
        out.write("\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
