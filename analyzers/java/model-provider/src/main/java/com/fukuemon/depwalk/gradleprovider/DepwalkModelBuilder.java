package com.fukuemon.depwalk.gradleprovider;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 各 in-scope project の {@code main} source set model を収集する builder。
 *
 * <p>model 取得は task を実行しない。ただし {@code main.compileClasspath} の
 * file 解決は Gradle の dependency resolution として実行され、network /
 * cache 副作用が発生し得る (ADR-0006)。
 */
public class DepwalkModelBuilder implements ToolingModelBuilder {

    /** 要求 model 名。Analyzer 側 interface の FQN と一致させる。 */
    static final String MODEL_NAME = "com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel";

    private static final String MAIN_SOURCE_SET = "main";

    @Override
    public boolean canBuild(String modelName) {
        return MODEL_NAME.equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project rootProject) {
        List<DefaultDepwalkProjectModel> projects = new ArrayList<DefaultDepwalkProjectModel>();
        Set<String> excludedSourceSetNames = new TreeSet<String>();
        int excludedSourceSetCount = 0;

        for (Project project : rootProject.getAllprojects()) {
            JavaPluginExtension javaExtension =
                    project.getExtensions().findByType(JavaPluginExtension.class);
            if (javaExtension == null) {
                continue;
            }
            SourceSetContainer sourceSets = javaExtension.getSourceSets();
            SourceSet main = sourceSets.findByName(MAIN_SOURCE_SET);
            if (main == null) {
                continue;
            }
            // names は project 横断で dedup、count は出現ごとの総数。
            for (SourceSet sourceSet : sourceSets) {
                String name = sourceSet.getName();
                if (!MAIN_SOURCE_SET.equals(name)) {
                    excludedSourceSetNames.add(name);
                    excludedSourceSetCount++;
                }
            }
            projects.add(buildProjectModel(project, main));
        }

        return new DefaultDepwalkGradleModel(
                rootProject.getProjectDir(),
                projects,
                new ArrayList<String>(excludedSourceSetNames),
                excludedSourceSetCount);
    }

    private DefaultDepwalkProjectModel buildProjectModel(Project project, SourceSet main) {
        SourceDirectorySet javaSource = main.getJava();
        List<File> sourceDirectories = new ArrayList<File>(javaSource.getSrcDirs());
        List<File> compileClasspath = new ArrayList<File>(main.getCompileClasspath().getFiles());
        List<File> classesOutputDirectories =
                new ArrayList<File>(main.getOutput().getClassesDirs().getFiles());

        // ProjectDependency.getDependencyProject() は Gradle 9.0 で削除された
        // ため、7.6〜9.6 の全対象で安定する resolution result の
        // ProjectComponentIdentifier から project 依存を収集する。
        List<String> projectDependencyPaths = new ArrayList<String>();
        Configuration configuration =
                project.getConfigurations().findByName(main.getCompileClasspathConfigurationName());
        if (configuration != null && configuration.isCanBeResolved()) {
            Set<String> seen = new TreeSet<String>();
            for (ResolvedComponentResult component
                    : configuration.getIncoming().getResolutionResult().getAllComponents()) {
                if (component.getId() instanceof ProjectComponentIdentifier) {
                    String path = ((ProjectComponentIdentifier) component.getId()).getProjectPath();
                    if (!path.equals(project.getPath())) {
                        seen.add(path);
                    }
                }
            }
            projectDependencyPaths.addAll(seen);
        }

        return new DefaultDepwalkProjectModel(
                project.getPath(),
                project.getProjectDir(),
                sourceDirectories,
                compileClasspath,
                classesOutputDirectories,
                projectDependencyPaths,
                sourceLanguageLevel(project, main),
                previewEnabled(project, main));
    }

    /**
     * source language level は {@code compileJava.options.release} を優先し、
     * 未指定なら実効 {@code sourceCompatibility} を使用する
     * (context/toolchain.md の4軸分離)。
     */
    private String sourceLanguageLevel(Project project, SourceSet main) {
        JavaCompile compileTask = compileJavaTask(project, main);
        if (compileTask != null) {
            Integer release = compileTask.getOptions().getRelease().getOrNull();
            if (release != null) {
                return release.toString();
            }
            String sourceCompatibility = compileTask.getSourceCompatibility();
            if (sourceCompatibility != null) {
                return canonicalMajor(sourceCompatibility);
            }
        }
        JavaPluginExtension javaExtension =
                project.getExtensions().getByType(JavaPluginExtension.class);
        return canonicalMajor(javaExtension.getSourceCompatibility().toString());
    }

    /**
     * {@code sourceCompatibility} の "1.8" 等の legacy 表記を Analyzer 側
     * {@code LanguageLevels} が受理する canonical major ("8") へ正規化する。
     * 解釈できない表記はそのまま返し、Analyzer 側の validation に委ねる。
     */
    private static String canonicalMajor(String version) {
        try {
            return JavaVersion.toVersion(version).getMajorVersion();
        } catch (IllegalArgumentException e) {
            return version;
        }
    }

    private boolean previewEnabled(Project project, SourceSet main) {
        JavaCompile compileTask = compileJavaTask(project, main);
        return compileTask != null
                && compileTask.getOptions().getCompilerArgs().contains("--enable-preview");
    }

    private JavaCompile compileJavaTask(Project project, SourceSet main) {
        Object task = project.getTasks().findByName(main.getCompileJavaTaskName());
        if (task instanceof JavaCompile) {
            return (JavaCompile) task;
        }
        return null;
    }
}
