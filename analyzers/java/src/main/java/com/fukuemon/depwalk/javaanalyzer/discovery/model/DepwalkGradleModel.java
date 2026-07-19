package com.fukuemon.depwalk.javaanalyzer.discovery.model;

import java.io.File;
import java.util.List;

/**
 * 自動 discovery が Gradle daemon 内の custom provider から取得する build 全体
 * model。Tooling API がこの interface へ構造的に adapt するため、getter は
 * provider 側 {@code DefaultDepwalkGradleModel} と一致させる。
 */
public interface DepwalkGradleModel {

    /** build root directory (build identifier)。 */
    File getBuildRootDirectory();

    /** Java plugin を持つ in-scope project の model 一覧。 */
    List<? extends DepwalkProjectModel> getProjects();

    /** 解析対象へ含めなかった source set 名 (summary 表示用)。 */
    List<String> getExcludedSourceSetNames();

    /** 解析対象へ含めなかった source set の総数 (project 横断)。 */
    int getExcludedSourceSetCount();

    /**
     * v1 scope 外として model へ含めなかった composite / included build の
     * root directory。Analyzer は黙って脱落させず、1 build につき 1 件の
     * warning と明示 override の案内を出す。旧 provider model との構造互換の
     * ため default を持つ (bundled provider は常に実値を返す)。
     */
    default List<File> getIncludedBuildRootDirectories() {
        return List.of();
    }
}
