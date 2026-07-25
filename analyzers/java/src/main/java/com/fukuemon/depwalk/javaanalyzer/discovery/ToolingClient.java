package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Tooling API 呼び出しの seam。実装は Gradle 由来の raw message / stack trace
 * を境界の外へ出さず、{@link ToolingRequestException} の固定情報だけを返す。
 */
public interface ToolingClient {

    /**
     * 対象 build の environment (Gradle version と daemon JVM major) を返す。
     *
     * @param workspaceRoot 対象 build の root directory
     * @return build environment の安定情報
     * @throws ToolingRequestException 接続または取得に失敗した場合
     */
    BuildEnvironmentInfo buildEnvironment(Path workspaceRoot) throws ToolingRequestException;

    /**
     * 一時 init script で provider を注入し custom model を取得する。
     *
     * @param workspaceRoot 対象 build の root directory
     * @param initScript provider を登録する一時 init script
     * @return provider が返した build model
     * @throws ToolingRequestException model 取得に失敗した場合
     */
    DepwalkGradleModel model(Path workspaceRoot, Path initScript) throws ToolingRequestException;

    /**
     * build environment の安定情報。
     *
     * @param gradleVersion BuildEnvironment が報告した Gradle version 文字列
     * @param daemonJavaMajor daemon JVM の Java major version。判定不能なら empty
     */
    record BuildEnvironmentInfo(String gradleVersion, Optional<Integer> daemonJavaMajor) {
    }

    /** Tooling API 呼び出し失敗。固定 message と失敗 phase だけを持つ。 */
    class ToolingRequestException extends Exception {

        private final DiscoveryFailure.Phase phase;

        public ToolingRequestException(DiscoveryFailure.Phase phase, String fixedMessage) {
            super(fixedMessage);
            this.phase = phase;
        }

        public DiscoveryFailure.Phase phase() {
            return phase;
        }
    }
}
