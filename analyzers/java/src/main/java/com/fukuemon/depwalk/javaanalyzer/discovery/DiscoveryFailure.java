package com.fukuemon.depwalk.javaanalyzer.discovery;

/**
 * 自動 discovery の fatal failure。Gradle 由来の raw message / stack trace /
 * repository URL を保持せず、Analyzer が定義した安定 category・失敗 phase・
 * 固定 message だけを持つ ({@code JAVA_GRADLE_MODEL_ERROR} へ変換される)。
 */
public class DiscoveryFailure extends Exception {

    /** 安定 failure category。文字列は Protocol 観測面の契約 (toolchain.md)。 */
    public enum Category {
        /** target Gradle version が対応範囲外、または安定判定できない。 */
        UNSUPPORTED_GRADLE_VERSION("unsupported-gradle-version"),
        /** 同梱 provider を一時 directory へ準備できない、または不完全な model が返った。 */
        PROVIDER_INCOMPATIBLE("provider-incompatible"),
        /** daemon JVM の major version が Gradle 公式互換範囲外、または判定不能。 */
        DAEMON_JVM_INCOMPATIBLE("daemon-jvm-incompatible"),
        /** connect phase の Tooling API 呼び出しに失敗した。 */
        CONNECTION_FAILED("connection-failed"),
        /** model 取得の Tooling API 呼び出しに失敗した。 */
        MODEL_REQUEST_FAILED("model-request-failed"),
        /** 全 project を通じて main Java source root が 1 件も無い。 */
        NO_JAVA_SOURCE_ROOTS("no-java-source-roots");

        private final String reason;

        Category(String reason) {
            this.reason = reason;
        }

        /** Protocol 観測面へ出す kebab-case の安定 reason 文字列。 */
        public String reason() {
            return reason;
        }
    }

    /** discovery のどの段階で失敗したか (観測用の安定値)。 */
    public enum Phase {
        /** 対象 build への接続と build environment 取得。 */
        CONNECT("connect"),
        /** Gradle version と daemon JVM の互換性判定。 */
        VERSION_CHECK("version-check"),
        /** provider 注入による custom model の取得。 */
        MODEL_REQUEST("model-request");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        /** 観測面へ出す kebab-case の安定 phase 文字列。 */
        public String label() {
            return label;
        }
    }

    private final Category category;
    private final Phase phase;

    /**
     * @param fixedMessage Analyzer が定義した固定 message (Gradle 由来の raw message を渡さない)
     */
    public DiscoveryFailure(Category category, Phase phase, String fixedMessage) {
        super(fixedMessage);
        this.category = category;
        this.phase = phase;
    }

    public Category category() {
        return category;
    }

    public Phase phase() {
        return phase;
    }

    /** 明示 override の案内を含む利用者向け固定 message。 */
    public String userMessage() {
        return getMessage()
                + " (category=" + category.reason()
                + ", phase=" + phase.label()
                + "). Pass explicit --source-root values (and metadata.classpath) to bypass build-model discovery.";
    }
}
