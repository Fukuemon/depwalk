package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringAnnotationsTest {

    @Test
    void resolvesSupportedAnnotationsFromWildcardImportsWithoutExternalClasses() {
        CompilationUnit unit = StaticJavaParser.parse("""
                package com.example;

                import org.apache.ibatis.annotations.*;
                import org.springframework.boot.autoconfigure.condition.*;
                import org.springframework.stereotype.*;

                @Service
                @Mapper
                @ConditionalOnProperty(name = "feature.enabled")
                class WildcardAnnotatedType {
                }
                """);
        ClassOrInterfaceDeclaration type = unit.getClassByName("WildcardAnnotatedType").orElseThrow();

        assertEquals("org.springframework.stereotype.Service", annotationFqn(type, "Service"));
        assertEquals(SpringAnnotations.MAPPER, annotationFqn(type, "Mapper"));
        assertEquals(
                "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty",
                annotationFqn(type, "ConditionalOnProperty"));
    }

    private static String annotationFqn(ClassOrInterfaceDeclaration type, String simpleName) {
        return type.getAnnotationByName(simpleName)
                .map(SpringAnnotations::fqn)
                .orElseThrow();
    }
}
