package com.fukuemon.depwalk.javaanalyzer.discovery;

/**
 * 自動 discovery の fatal failure。Gradle 由来の raw message / stack trace /
 * repository URL を保持せず、Analyzer が定義した安定 category・失敗 phase・
 * 固定 message だけを持つ ({@code JAVA_GRADLE_MODEL_ERROR} へ変換される)。
 */
public class DiscoveryFailure extends Exception {

    /** 安定 failure category。文字列は Protocol 観測面の契約 (toolchain.md)。 */
    public enum Category {
        UNSUPPORTED_GRADLE_VERSION("unsupported-gradle-version"),
        PROVIDER_INCOMPATIBLE("provider-incompatible"),
        DAEMON_JVM_INCOMPATIBLE("daemon-jvm-incompatible"),
        CONNECTION_FAILED("connection-failed"),
        MODEL_REQUEST_FAILED("model-request-failed"),
        NO_JAVA_SOURCE_ROOTS("no-java-source-roots");

        private final String reason;

        Category(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /** discovery のどの段階で失敗したか (観測用の安定値)。 */
    public enum Phase {
        CONNECT("connect"),
        VERSION_CHECK("version-check"),
        MODEL_REQUEST("model-request");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Category category;
    private final Phase phase;

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
