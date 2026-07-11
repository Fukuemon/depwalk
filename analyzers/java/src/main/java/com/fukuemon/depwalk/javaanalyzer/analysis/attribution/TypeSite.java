package com.fukuemon.depwalk.javaanalyzer.analysis.attribution;

import java.nio.file.Path;

/**
 * 型の所在情報。{@code sourceFilePath} が {@code null} の場合、AST を持たない
 * (JDK reflection / jar 由来) 型であり、常に scope 外として扱う。
 *
 * @param binaryName     JVM binary name
 * @param sourceFilePath 宣言元ソースファイルの絶対 path (AST を持たない場合は {@code null})
 */
public record TypeSite(String binaryName, Path sourceFilePath) {
}
