package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@code sourceRoots} 省略時だけ実行される Gradle build model の自動 discovery。
 * 1 件以上の明示 root がある request では呼び出し側が本 class へ一切入らない
 * (完全 bypass、ADR-0006)。
 */
public final class GradleModelDiscovery {

    /**
     * discovery 開始前に必ず stderr へ出す固定安全通知。build logic 評価と
     * network / credential / cache 副作用、明示 override による回避を明示する。
     */
    public static final String SAFETY_NOTICE =
            "depwalk: notice build-model discovery evaluates the workspace build logic with your user"
                    + " permissions and may use configured artifact repositories, existing credential"
                    + " resolution, the network, and the build tool user cache."
                    + " Pass explicit --source-root values to bypass discovery.";

    private final ToolingClient client;
    private final PrintStream stderr;

    public GradleModelDiscovery(ToolingClient client, PrintStream stderr) {
        this.client = client;
        this.stderr = stderr;
    }

    /**
     * 明示 override の有無を返す。1 件以上の明示 root を持つ request は
     * Tooling API runtime を完全に bypass する。
     *
     * @param sourceRoots analysisRequest.sourceRoots (省略時 null)
     * @return 明示 override なら true
     */
    public static boolean isExplicitOverride(List<String> sourceRoots) {
        return sourceRoots != null;
    }

    /**
     * build model を取得する。失敗時は filesystem 走査や不完全 model へ
     * fallback せず {@link DiscoveryFailure} で fatal にする。
     *
     * @param workspaceRoot 対象 build の root directory
     * @return provider が返した build model
     * @throws DiscoveryFailure 対応範囲外 version、daemon JVM 非互換、
     *     provider 非互換、model 取得失敗
     */
    public DepwalkGradleModel discover(Path workspaceRoot) throws DiscoveryFailure {
        stderr.println(SAFETY_NOTICE);
        stderr.println("depwalk: discovery phase=start");

        ToolingClient.BuildEnvironmentInfo environment;
        try {
            environment = client.buildEnvironment(workspaceRoot);
        } catch (ToolingClient.ToolingRequestException e) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.MODEL_REQUEST_FAILED, e.phase(), e.getMessage());
        }

        checkCompatibility(environment);

        DepwalkGradleModel model = fetchModel(workspaceRoot);
        validateModel(model);

        int sourceRootCount = model.getProjects().stream()
                .mapToInt(project -> project.getMainJavaSourceDirectories().size())
                .sum();
        stderr.println("depwalk: discovery phase=end gradleVersion=" + environment.gradleVersion()
                + " projects=" + model.getProjects().size()
                + " sourceRoots=" + sourceRootCount
                + " excludedSourceSets=" + model.getExcludedSourceSetCount());
        return model;
    }

    private void checkCompatibility(ToolingClient.BuildEnvironmentInfo environment) throws DiscoveryFailure {
        Optional<Boolean> supported = GradleVersionSupport.isSupportedGradleVersion(environment.gradleVersion());
        if (supported.isEmpty() || !supported.get()) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.UNSUPPORTED_GRADLE_VERSION,
                    DiscoveryFailure.Phase.VERSION_CHECK,
                    "target Gradle version is outside the supported range 7.6.5 <= version < 9.7.0"
                            + " or could not be determined");
        }
        Optional<Integer> daemonJavaMajor = environment.daemonJavaMajor();
        if (daemonJavaMajor.isEmpty()) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.DAEMON_JVM_INCOMPATIBLE,
                    DiscoveryFailure.Phase.VERSION_CHECK,
                    "the daemon JVM version could not be determined for the official compatibility check");
        }
        Optional<Boolean> compatible = GradleVersionSupport.isDaemonJvmCompatible(
                environment.gradleVersion(), daemonJavaMajor.get());
        if (compatible.isEmpty() || !compatible.get()) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.DAEMON_JVM_INCOMPATIBLE,
                    DiscoveryFailure.Phase.VERSION_CHECK,
                    "the selected daemon JVM is outside the official Gradle Java compatibility range");
        }
    }

    private DepwalkGradleModel fetchModel(Path workspaceRoot) throws DiscoveryFailure {
        try (ProviderWorkspace provider = ProviderWorkspace.create(stderr)) {
            return client.model(workspaceRoot, provider.initScript());
        } catch (IOException e) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.PROVIDER_INCOMPATIBLE,
                    DiscoveryFailure.Phase.MODEL_REQUEST,
                    "the bundled model provider could not be prepared in a temporary directory");
        } catch (ToolingClient.ToolingRequestException e) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.MODEL_REQUEST_FAILED, e.phase(), e.getMessage());
        }
    }

    private void validateModel(DepwalkGradleModel model) throws DiscoveryFailure {
        if (model == null || model.getBuildRootDirectory() == null || model.getProjects() == null) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.PROVIDER_INCOMPATIBLE,
                    DiscoveryFailure.Phase.MODEL_REQUEST,
                    "the model provider returned an incomplete build model");
        }
        for (DepwalkProjectModel project : model.getProjects()) {
            if (project.getProjectPath() == null
                    || project.getProjectDirectory() == null
                    || project.getMainJavaSourceDirectories() == null
                    || project.getMainCompileClasspath() == null
                    || project.getMainClassesOutputDirectories() == null
                    || project.getProjectDependencyPaths() == null
                    || project.getSourceLanguageLevel() == null) {
                throw new DiscoveryFailure(
                        DiscoveryFailure.Category.PROVIDER_INCOMPATIBLE,
                        DiscoveryFailure.Phase.MODEL_REQUEST,
                        "the model provider returned an incomplete project model");
            }
        }
        boolean hasSourceRoot = model.getProjects().stream()
                .anyMatch(project -> !project.getMainJavaSourceDirectories().isEmpty());
        if (!hasSourceRoot) {
            throw new DiscoveryFailure(
                    DiscoveryFailure.Category.NO_JAVA_SOURCE_ROOTS,
                    DiscoveryFailure.Phase.MODEL_REQUEST,
                    "build-model discovery found no main Java source root in the workspace");
        }
    }
}
