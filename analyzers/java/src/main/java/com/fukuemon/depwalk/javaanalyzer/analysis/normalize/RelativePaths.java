package com.fukuemon.depwalk.javaanalyzer.analysis.normalize;

/**
 * protocol record ({@code sourceLocation.path} / {@code callSite.path}) に載せる相対 path 文字列の
 * 区切り文字正規化。Core の protocol validate は {@code \} を含む path を拒否するため、
 * {@link java.nio.file.Path#toString()} の結果 (Windows では {@code \} 区切り) をそのまま record に
 * 載せてはならない。{@link com.fukuemon.depwalk.javaanalyzer.analysis.scope.ScopeFiles#toMatchablePath}
 * と同種の正規化 (glob 照合用は {@link java.nio.file.Path} を返すのに対し、こちらは record 出力用に
 * {@link String} を返す)。
 */
public final class RelativePaths {

    private RelativePaths() {
    }

    /**
     * 相対 path 文字列を {@code /} 区切りへ正規化する (Windows の {@code \} 区切りを吸収する。
     * {@code /} 区切り入力はそのまま)。
     */
    public static String toRecordPath(String rawRelativePath) {
        return rawRelativePath.replace('\\', '/');
    }
}
