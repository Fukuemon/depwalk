package com.fukuemon.depwalk.javaanalyzer.discovery.model;

import java.io.File;
import java.util.List;

/**
 * 1 つの in-scope Gradle project の {@code main} source set model。
 * provider 側 {@code DefaultDepwalkProjectModel} の getter と一致させる。
 */
public interface DepwalkProjectModel {

    String getProjectPath();

    File getProjectDirectory();

    List<File> getMainJavaSourceDirectories();

    List<File> getMainCompileClasspath();

    List<File> getMainClassesOutputDirectories();

    /** {@code main.compileClasspath} 上の project 依存の Gradle project path。 */
    List<String> getProjectDependencyPaths();

    /** {@code release} 優先、なければ実効 {@code sourceCompatibility}。 */
    String getSourceLanguageLevel();

    boolean isPreviewEnabled();
}
