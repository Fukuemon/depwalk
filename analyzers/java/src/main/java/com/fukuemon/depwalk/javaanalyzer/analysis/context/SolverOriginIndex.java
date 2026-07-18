package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 1 つの解析 context の solver 構成 entry (source root / project classes /
 * external artifact) と {@link ResolvedDeclarationOrigin} の対応。P3 では
 * solver 構成時に登録だけを行い、P4 の source 再対応付けが参照する境界となる。
 */
public final class SolverOriginIndex {

    private final Map<Path, ResolvedDeclarationOrigin> originsByEntry = new LinkedHashMap<>();

    /** solver へ登録した entry の origin を記録する。 */
    public void register(Path entry, ResolvedDeclarationOrigin origin) {
        originsByEntry.putIfAbsent(entry.toAbsolutePath().normalize(), origin);
    }

    /** entry (source root / jar / classes dir) の origin を返す。 */
    public Optional<ResolvedDeclarationOrigin> originOf(Path entry) {
        return Optional.ofNullable(originsByEntry.get(entry.toAbsolutePath().normalize()));
    }

    /** 登録済み entry 数 (test 用)。 */
    public int size() {
        return originsByEntry.size();
    }
}
