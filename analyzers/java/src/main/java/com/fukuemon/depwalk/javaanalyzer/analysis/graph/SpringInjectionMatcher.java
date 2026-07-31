package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiIndex;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * call site の receiver を Spring DI の注入点へ突き合わせ、その解決結果を返す。
 * 注入点は所有型 + receiver 名で索引し、同名の候補が複数ある場合は宣言そのものと対応する 1 件に絞る。
 */
final class SpringInjectionMatcher {

    private final Map<String, List<SpringDiIndex.InjectionResolution>> resolutionsByReceiver = new LinkedHashMap<>();

    SpringInjectionMatcher(SpringDiIndex.Result springResult) {
        for (SpringDiIndex.InjectionResolution resolution : springResult.resolutions()) {
            SpringDiIndex.InjectionPoint injection = resolution.injectionPoint();
            for (String receiverName : injection.receiverNames()) {
                resolutionsByReceiver
                        .computeIfAbsent(
                                receiverKey(injection.ownerType(), receiverName),
                                ignored -> new ArrayList<>())
                        .add(resolution);
            }
        }
    }

    /**
     * call site の receiver が指す注入点の解決結果。receiver 名で注入点を引き当ててから、
     * その receiver 宣言に対応する 1 件を {@link #selectResolution} で選ぶ。
     *
     * <p>receiver 宣言の {@code resolve()} は symbol solver の実コストがかかるため 1 回だけ行う。
     * {@code this.field} 形式は名前だけで注入点を引けるので、該当する注入点が無い場合は
     * {@code resolve()} 自体を行わない (従来の評価順を維持する)。
     */
    SpringDiIndex.InjectionResolution resolutionFor(Node callNode, Node enclosingTypeNode) {
        if (enclosingTypeNode == null) {
            return null;
        }
        Expression scope = CallScopes.unwrappedScopeOf(callNode);
        ResolvedValueDeclaration declaration = null;
        String receiverName;
        if (scope instanceof NameExpr name) {
            try {
                declaration = name.resolve();
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
            if (!declaration.isField() && !declaration.isParameter()) {
                return null;
            }
            receiverName = name.getNameAsString();
        } else if (scope instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
            receiverName = field.getNameAsString();
        } else {
            return null;
        }
        String ownerType = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        List<SpringDiIndex.InjectionResolution> resolutions =
                resolutionsByReceiver.get(receiverKey(ownerType, receiverName));
        if (resolutions == null || resolutions.isEmpty()) {
            return null;
        }
        if (declaration == null) {
            try {
                declaration = ((FieldAccessExpr) scope).resolve();
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return selectResolution(declaration, receiverName, resolutions);
    }

    /**
     * 同じownerとreceiver名を共有する注入点から、call siteが参照する宣言に対応する1件を選ぶ。
     * parameterはsource上の宣言行で区別し、fieldは直接field injectionをconstructor／setter経由の
     * aliasより優先する。1件に決められない場合は誤ったDI候補を使わず、型階層解決へ委ねる。
     */
    private static SpringDiIndex.InjectionResolution selectResolution(
            ResolvedValueDeclaration declaration,
            String receiverName,
            List<SpringDiIndex.InjectionResolution> resolutions) {
        if (declaration.isParameter()) {
            Node ast = declaration.toAst().orElse(null);
            int declarationLine = ast != null && ast.getBegin().isPresent() ? ast.getBegin().get().line : -1;
            return uniqueResolution(resolutions.stream()
                    .filter(resolution -> resolution.injectionPoint().sourceLine() == declarationLine)
                    .toList());
        }
        if (!declaration.isField()) {
            return null;
        }
        List<SpringDiIndex.InjectionResolution> directFields = resolutions.stream()
                .filter(resolution -> resolution.injectionPoint().kind() == SpringDiIndex.InjectionKind.FIELD)
                .filter(resolution -> receiverName.equals(resolution.injectionPoint().targetName()))
                .toList();
        if (!directFields.isEmpty()) {
            return uniqueResolution(directFields);
        }
        return uniqueResolution(resolutions);
    }

    private static SpringDiIndex.InjectionResolution uniqueResolution(
            List<SpringDiIndex.InjectionResolution> resolutions) {
        return resolutions.size() == 1 ? resolutions.get(0) : null;
    }

    private static String receiverKey(String ownerType, String targetName) {
        return ownerType + "\u0000" + targetName;
    }
}
