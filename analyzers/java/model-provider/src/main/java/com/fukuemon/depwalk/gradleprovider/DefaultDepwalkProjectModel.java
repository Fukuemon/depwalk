package com.fukuemon.depwalk.gradleprovider;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * 1 つの in-scope Gradle project の {@code main} source set model。
 * getter 名は Analyzer 側の {@code DepwalkProjectModel} interface と一致させる。
 */
public class DefaultDepwalkProjectModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String projectPath;
    private final File projectDirectory;
    private final List<File> mainJavaSourceDirectories;
    private final List<File> mainCompileClasspath;
    private final List<File> mainClassesOutputDirectories;
    private final List<String> projectDependencyPaths;
    private final String sourceLanguageLevel;
    private final boolean previewEnabled;

    public DefaultDepwalkProjectModel(
            String projectPath,
            File projectDirectory,
            List<File> mainJavaSourceDirectories,
            List<File> mainCompileClasspath,
            List<File> mainClassesOutputDirectories,
            List<String> projectDependencyPaths,
            String sourceLanguageLevel,
            boolean previewEnabled) {
        this.projectPath = projectPath;
        this.projectDirectory = projectDirectory;
        this.mainJavaSourceDirectories = mainJavaSourceDirectories;
        this.mainCompileClasspath = mainCompileClasspath;
        this.mainClassesOutputDirectories = mainClassesOutputDirectories;
        this.projectDependencyPaths = projectDependencyPaths;
        this.sourceLanguageLevel = sourceLanguageLevel;
        this.previewEnabled = previewEnabled;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public List<File> getMainJavaSourceDirectories() {
        return mainJavaSourceDirectories;
    }

    public List<File> getMainCompileClasspath() {
        return mainCompileClasspath;
    }

    public List<File> getMainClassesOutputDirectories() {
        return mainClassesOutputDirectories;
    }

    public List<String> getProjectDependencyPaths() {
        return projectDependencyPaths;
    }

    public String getSourceLanguageLevel() {
        return sourceLanguageLevel;
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }
}
