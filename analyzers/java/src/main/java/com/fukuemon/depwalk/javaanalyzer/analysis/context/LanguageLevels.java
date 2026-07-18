package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.github.javaparser.ParserConfiguration;

import java.util.Optional;

/**
 * source language level 文字列を {@link ParserConfiguration.LanguageLevel} へ
 * 解決する。値は canonical な 10 進 major version ("8", "17", "25" 等) だけを
 * 受理し、"1.8" などの legacy 表記、空、非数値は解決しない。
 * toolchain 4 軸分離 (context/toolchain.md) に従い、他の軸からの推測や
 * 別 level への fallback は行わない。
 */
public final class LanguageLevels {

    private LanguageLevels() {
    }

    /**
     * canonical major version 文字列を language level へ解決する。
     *
     * @param canonicalMajor "8".."25" 等の 10 進 major version
     * @param preview preview 機能を有効にするか
     * @return 対応する level。表記が canonical でない、または JavaParser が
     *     その level / preview を持たない場合は empty
     */
    public static Optional<ParserConfiguration.LanguageLevel> resolve(String canonicalMajor, boolean preview) {
        if (canonicalMajor == null || canonicalMajor.isEmpty()
                || !canonicalMajor.chars().allMatch(Character::isDigit)
                || (canonicalMajor.length() > 1 && canonicalMajor.startsWith("0"))) {
            return Optional.empty();
        }
        String constantName = "JAVA_" + canonicalMajor + (preview ? "_PREVIEW" : "");
        try {
            return Optional.of(ParserConfiguration.LanguageLevel.valueOf(constantName));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
