package com.fukuemon.depwalk.javaanalyzer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 外部ライブラリ隔離 (ADR-0007 / DesignDoc_java-analyzer.md 「内部 package 構成と依存境界」) の機械検査。
 *
 * <p>隔離は 3 段階で、適用レベルはライブラリごとに異なる。SootUp は adapter package へ完全封じ込め、
 * Gradle Tooling API は discovery へ完全隔離、JavaParser / SymbolSolver は解析エンジンの中核として
 * {@code analysis} 配下では自由に使ってよく、禁止するのは {@code analysis} の外への漏れのみ。
 *
 * <p>ArchUnit の JUnit5 TestEngine ではなく core API を Jupiter の {@code @Test} から呼ぶ。
 * 本 project は JUnit Platform 6 系を使うため、engine のバージョン整合を持ち込まない。
 */
class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.fukuemon.depwalk.javaanalyzer";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    @Test
    void sootUpIsConfinedToTheSootUpAdapterPackage() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..javaanalyzer.analysis.sootup..")
                .should().dependOnClassesThat().resideInAPackage("sootup..")
                .because("SootUp は analysis/sootup の facade に封じ込め、自前型で公開する (ADR-0007)");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void gradleApiIsConfinedToDiscovery() {
        // gradle-tooling-api の jar は org.gradle.tooling 以外に org.gradle.api / util /
        // internal も同梱するため、tooling 配下だけでなく org.gradle 全体を禁止する。
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..javaanalyzer.discovery..")
                .should().dependOnClassesThat().resideInAPackage("org.gradle..")
                .because("Gradle Tooling API による project 構造取得は discovery に隔離する (ADR-0007)");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void javaParserDoesNotLeakOutsideAnalysis() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..javaanalyzer.analysis..")
                .should().dependOnClassesThat().resideInAPackage("com.github.javaparser..")
                .because("JavaParser / SymbolSolver は analysis 配下の解析エンジン内部に留め、"
                        + "io / protocol / preflight / discovery / Main へ漏らさない (ADR-0007)");

        rule.check(PRODUCTION_CLASSES);
    }
}
