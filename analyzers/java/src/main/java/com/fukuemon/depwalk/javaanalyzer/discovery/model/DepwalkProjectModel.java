package com.fukuemon.depwalk.javaanalyzer.discovery.model;

import java.io.File;
import java.util.List;

/**
 * 1 つの in-scope Gradle project の {@code main} source set model。
 * provider 側 {@code DefaultDepwalkProjectModel} の getter と一致させる。
 */
public interface DepwalkProjectModel {

    /** Gradle project path (例: {@code :app})。 */
    String getProjectPath();

    /** project の directory。 */
    File getProjectDirectory();

    /** {@code main} source set の Java source directory 一覧。 */
    List<File> getMainJavaSourceDirectories();

    /** {@code main.compileClasspath} を解決した file 一覧。 */
    List<File> getMainCompileClasspath();

    /** {@code main} source set の classes output directory 一覧。 */
    List<File> getMainClassesOutputDirectories();

    /** {@code main.compileClasspath} 上の project 依存の Gradle project path。 */
    List<String> getProjectDependencyPaths();

    /** {@code release} 優先、なければ実効 {@code sourceCompatibility}。 */
    String getSourceLanguageLevel();

    /** {@code compileJava} の compiler 引数に {@code --enable-preview} を含むか。 */
    boolean isPreviewEnabled();
}
