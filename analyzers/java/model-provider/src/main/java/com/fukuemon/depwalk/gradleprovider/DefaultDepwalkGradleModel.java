package com.fukuemon.depwalk.gradleprovider;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Tooling API が Analyzer 側の model interface へ構造的に adapt する
 * serializable な build 全体 model。getter 名は
 * {@code com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel}
 * と一致させる。
 */
public class DefaultDepwalkGradleModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final File buildRootDirectory;
    private final List<DefaultDepwalkProjectModel> projects;
    private final List<String> excludedSourceSetNames;
    private final int excludedSourceSetCount;

    public DefaultDepwalkGradleModel(
            File buildRootDirectory,
            List<DefaultDepwalkProjectModel> projects,
            List<String> excludedSourceSetNames,
            int excludedSourceSetCount) {
        this.buildRootDirectory = buildRootDirectory;
        this.projects = projects;
        this.excludedSourceSetNames = excludedSourceSetNames;
        this.excludedSourceSetCount = excludedSourceSetCount;
    }

    public File getBuildRootDirectory() {
        return buildRootDirectory;
    }

    public List<DefaultDepwalkProjectModel> getProjects() {
        return projects;
    }

    public List<String> getExcludedSourceSetNames() {
        return excludedSourceSetNames;
    }

    public int getExcludedSourceSetCount() {
        return excludedSourceSetCount;
    }
}
