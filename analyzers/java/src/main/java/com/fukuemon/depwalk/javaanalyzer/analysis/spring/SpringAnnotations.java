package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Spring を classpath に追加せず、annotation の FQN と文字列属性だけを読む。 */
final class SpringAnnotations {

    static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    static final String BEAN = "org.springframework.context.annotation.Bean";
    static final String CONFIGURATION = "org.springframework.context.annotation.Configuration";
    static final String MAPPER = "org.apache.ibatis.annotations.Mapper";
    static final String PRIMARY = "org.springframework.context.annotation.Primary";
    static final String PROFILE = "org.springframework.context.annotation.Profile";
    static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";
    static final String SPRING_CONDITIONAL = "org.springframework.context.annotation.Conditional";

    static final Set<String> STEREOTYPES = Set.of(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            CONFIGURATION);

    private SpringAnnotations() {
    }

    static boolean has(NodeWithAnnotations<?> node, String fqn) {
        return find(node, fqn) != null;
    }

    static AnnotationExpr find(NodeWithAnnotations<?> node, String fqn) {
        return node.getAnnotations().stream()
                .filter(annotation -> fqn.equals(fqn(annotation)))
                .findFirst()
                .orElse(null);
    }

    static AnnotationExpr findAny(NodeWithAnnotations<?> node, Set<String> fqns) {
        return node.getAnnotations().stream()
                .filter(annotation -> fqns.contains(fqn(annotation)))
                .findFirst()
                .orElse(null);
    }

    static String qualifier(NodeWithAnnotations<?> node) {
        AnnotationExpr qualifier = find(node, QUALIFIER);
        if (qualifier == null) {
            return null;
        }
        List<String> values = stringValues(qualifier, "value");
        return values.isEmpty() ? null : values.get(0);
    }

    static List<String> conditionTypes(NodeWithAnnotations<?> node) {
        Set<String> conditions = new LinkedHashSet<>();
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String fqn = fqn(annotation);
            if (PROFILE.equals(fqn)
                    || SPRING_CONDITIONAL.equals(fqn)
                    || (fqn != null && fqn.startsWith("org.springframework.boot.autoconfigure.condition.Conditional"))) {
                conditions.add(fqn);
            }
        }
        return conditions.stream().sorted().toList();
    }

    static List<String> stringValues(AnnotationExpr annotation, String... attributeNames) {
        List<Expression> expressions = new ArrayList<>();
        if (annotation.isSingleMemberAnnotationExpr()) {
            for (String attributeName : attributeNames) {
                if ("value".equals(attributeName)) {
                    expressions.add(annotation.asSingleMemberAnnotationExpr().getMemberValue());
                    break;
                }
            }
        } else if (annotation.isNormalAnnotationExpr()) {
            for (String attributeName : attributeNames) {
                annotation.asNormalAnnotationExpr().getPairs().stream()
                        .filter(pair -> attributeName.equals(pair.getNameAsString()))
                        .map(pair -> pair.getValue())
                        .forEach(expressions::add);
            }
        }

        List<String> values = new ArrayList<>();
        for (Expression expression : expressions) {
            addStrings(expression, values);
        }
        return values.stream().filter(value -> !value.isEmpty()).distinct().toList();
    }

    static String fqn(AnnotationExpr annotation) {
        try {
            return annotation.resolve().getQualifiedName();
        } catch (RuntimeException ignored) {
            String writtenName = annotation.getNameAsString();
            if (writtenName.contains(".")) {
                return writtenName;
            }
            return annotation.findCompilationUnit()
                    .flatMap(cu -> cu.getImports().stream()
                            .filter(importDeclaration -> matches(importDeclaration, writtenName))
                            .map(ImportDeclaration::getNameAsString)
                            .findFirst())
                    .orElse(null);
        }
    }

    private static boolean matches(ImportDeclaration importDeclaration, String simpleName) {
        return !importDeclaration.isAsterisk()
                && !importDeclaration.isStatic()
                && importDeclaration.getName().getIdentifier().equals(simpleName);
    }

    private static void addStrings(Expression expression, List<String> values) {
        if (expression instanceof StringLiteralExpr stringLiteral) {
            values.add(stringLiteral.asString());
            return;
        }
        if (expression instanceof ArrayInitializerExpr array) {
            for (Expression value : array.getValues()) {
                addStrings(value, values);
            }
        }
    }
}
