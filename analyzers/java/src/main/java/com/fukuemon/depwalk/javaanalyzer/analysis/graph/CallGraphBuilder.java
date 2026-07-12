package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AST を 1 ファイルずつ走査し、declared method / constructor / static initializer の
 * {@code methodSymbol} と、呼び出し式ごとの帰属型決定 + {@code callEdge} を {@link GraphAccumulator}
 * へ積み上げる。呼び出し先の型解決に AST ノードの identity が必要な場面
 * ({@link BinaryNames#forTypeLikeNode(Node)}) は、ファイルの visit 順序に依存しない純粋な構造計算で
 * 実装しているため、ファイル単位で 1 パス (declare + call-edge を同時に処理) で完結できる。
 */
public final class CallGraphBuilder {

    private final Path workspaceRoot;
    private final AttributionResolver attributionResolver;
    private final GraphAccumulator accumulator;

    public CallGraphBuilder(Path workspaceRoot, AttributionResolver attributionResolver, GraphAccumulator accumulator) {
        this.workspaceRoot = workspaceRoot;
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
    }

    public void process(CompilationUnit cu) {
        walk(cu, new WalkContext(null, List.of(), false));
    }

    private record WalkContext(Node enclosingTypeNode, List<String> callerMethodIds, boolean viaLambda) {
        WalkContext withCaller(List<String> callerIds) {
            return new WalkContext(enclosingTypeNode, callerIds, viaLambda);
        }

        WalkContext withEnclosingType(Node typeNode) {
            return new WalkContext(typeNode, List.of(), viaLambda);
        }

        WalkContext withViaLambda() {
            return new WalkContext(enclosingTypeNode, callerMethodIds, true);
        }
    }

    private void walk(Node node, WalkContext ctx) {
        if (node instanceof TypeDeclaration<?> td) {
            recurseChildren(node, ctx.withEnclosingType(td));
            return;
        }
        if (node instanceof MethodDeclaration md) {
            MethodSymbol symbol;
            try {
                ResolvedMethodDeclaration resolved = md.resolve();
                symbol = buildMethodSymbol(AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())), resolved);
            } catch (RuntimeException e) {
                reportUnresolvedDeclaration(md, "failed to resolve method declaration: " + md.getNameAsString());
                recurseChildren(node, ctx.withCaller(List.of()));
                return;
            }
            accumulator.addNode(symbol);
            recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
            return;
        }
        if (node instanceof ConstructorDeclaration cd) {
            MethodSymbol symbol;
            try {
                ResolvedConstructorDeclaration resolved = cd.resolve();
                symbol = buildConstructorSymbol(AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())), resolved);
            } catch (RuntimeException e) {
                reportUnresolvedDeclaration(cd, "failed to resolve constructor declaration: " + cd.getNameAsString());
                recurseChildren(node, ctx.withCaller(List.of()));
                return;
            }
            accumulator.addNode(symbol);
            recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
            return;
        }
        if (node instanceof InitializerDeclaration id) {
            if (!containsAnyCall(id)) {
                recurseChildren(node, ctx);
                return;
            }
            if (id.isStatic()) {
                String clinitId = ensureStaticInitializerNode(ctx.enclosingTypeNode());
                recurseChildren(node, ctx.withCaller(List.of(clinitId)));
            } else {
                recurseChildren(node, ctx.withCaller(constructorCallerIdsFor(ctx.enclosingTypeNode())));
            }
            return;
        }
        if (node instanceof FieldDeclaration fd) {
            if (!containsAnyCall(fd)) {
                recurseChildren(node, ctx);
                return;
            }
            if (fd.isStatic()) {
                String clinitId = ensureStaticInitializerNode(ctx.enclosingTypeNode());
                recurseChildren(node, ctx.withCaller(List.of(clinitId)));
            } else {
                recurseChildren(node, ctx.withCaller(constructorCallerIdsFor(ctx.enclosingTypeNode())));
            }
            return;
        }
        if (node instanceof LambdaExpr) {
            recurseChildren(node, ctx.withViaLambda());
            return;
        }
        if (node instanceof MethodCallExpr mce) {
            processMethodCall(mce, ctx);
            recurseChildren(node, ctx);
            return;
        }
        if (node instanceof ObjectCreationExpr oce) {
            processObjectCreation(oce, ctx);
            for (Node argument : oce.getArguments()) {
                walk(argument, ctx);
            }
            oce.getScope().ifPresent(scope -> walk(scope, ctx));
            if (oce.getAnonymousClassBody().isPresent()) {
                WalkContext bodyCtx = ctx.withEnclosingType(oce);
                for (BodyDeclaration<?> member : oce.getAnonymousClassBody().get()) {
                    walk(member, bodyCtx);
                }
            }
            return;
        }
        if (node instanceof ExplicitConstructorInvocationStmt ecis) {
            processExplicitConstructorInvocation(ecis, ctx);
            for (Node argument : ecis.getArguments()) {
                walk(argument, ctx);
            }
            return;
        }
        recurseChildren(node, ctx);
    }

    private void recurseChildren(Node node, WalkContext ctx) {
        for (Node child : node.getChildNodes()) {
            walk(child, ctx);
        }
    }

    private boolean containsAnyCall(Node node) {
        return !node.findAll(MethodCallExpr.class).isEmpty()
                || !node.findAll(ObjectCreationExpr.class).isEmpty()
                || !node.findAll(ExplicitConstructorInvocationStmt.class).isEmpty();
    }

    // ------------------------------------------------------------------
    // method call processing
    // ------------------------------------------------------------------

    private void processMethodCall(MethodCallExpr mce, WalkContext ctx) {
        ResolvedMethodDeclaration resolved;
        try {
            resolved = mce.resolve();
        } catch (RuntimeException e) {
            reportUnresolved(mce, ctx);
            return;
        }

        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = receiverSiteOf(mce, ctx);
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            return;
        }

        MethodSymbol calleeSymbol = buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = dispatchOf(resolved);
        Map<String, Object> metadata = edgeMetadata(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mce);
        for (String callerId : ctx.callerMethodIds()) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
    }

    private void processObjectCreation(ObjectCreationExpr oce, WalkContext ctx) {
        ResolvedConstructorDeclaration resolved;
        try {
            resolved = oce.resolve();
        } catch (RuntimeException e) {
            reportUnresolved(oce, ctx);
            return;
        }
        emitConstructorCall(resolved, oce, ctx);
    }

    private void processExplicitConstructorInvocation(ExplicitConstructorInvocationStmt ecis, WalkContext ctx) {
        ResolvedConstructorDeclaration resolved;
        try {
            resolved = ecis.resolve();
        } catch (RuntimeException e) {
            reportUnresolved(ecis, ctx);
            return;
        }
        emitConstructorCall(resolved, ecis, ctx);
    }

    private void emitConstructorCall(ResolvedConstructorDeclaration resolved, Node callNode, WalkContext ctx) {
        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            return;
        }
        MethodSymbol calleeSymbol = buildConstructorSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = edgeMetadata(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(callNode);
        for (String callerId : ctx.callerMethodIds()) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
    }

    private void reportUnresolved(Node callNode, WalkContext ctx) {
        accumulator.incrementUnresolved();
        String relatedMethodId = ctx.callerMethodIds().isEmpty() ? null : ctx.callerMethodIds().get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                "failed to resolve call: " + callNode,
                sourceLocationOf(callNode),
                relatedMethodId,
                null));
    }

    /**
     * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}) の解決失敗 (H2)。呼び出し式側の
     * {@link #reportUnresolved(Node, WalkContext)} と異なり、宣言そのものが対象のため
     * {@code relatedMethodId} は付けない。その宣言だけ skip し、解析全体は継続する。
     */
    private void reportUnresolvedDeclaration(Node declNode, String message) {
        accumulator.incrementUnresolved();
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                message,
                sourceLocationOf(declNode),
                null,
                null));
    }

    // ------------------------------------------------------------------
    // node construction
    // ------------------------------------------------------------------

    private MethodSymbol buildMethodSymbol(AttributionResult attribution, ResolvedMethodDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, resolved.getName(), paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + resolved.getName();

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            Node ast = resolved.toAst().orElse(null);
            sourceLocation = ast != null ? sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "method", qualifiedName, signature, sourceLocation, metadata);
    }

    private MethodSymbol buildConstructorSymbol(AttributionResult attribution, ResolvedConstructorDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            // synthetic default constructor は自身の AST を持たない ({@code toAst()} が空) ため、
            // 宣言型の AST 位置へフォールバックする。これにより ensureDefaultConstructorNode と
            // 同一内容になり、同一 methodId の node がどの経路から生成されても内容が一致する
            // (GraphAccumulator の first-wins 重複排除で情報が失われない)。
            Node ast = resolved.toAst().orElse(null);
            if (ast == null) {
                ast = resolved.declaringType().toAst().orElse(null);
            }
            sourceLocation = ast != null ? sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, metadata);
    }

    private String ensureStaticInitializerNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.STATIC_INITIALIZER_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.STATIC_INITIALIZER_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "initializer", qualifiedName, signature, sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    private List<String> constructorCallerIdsFor(Node enclosingType) {
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        for (Node member : membersOf(enclosingType)) {
            if (member instanceof ConstructorDeclaration cd) {
                constructors.add(cd);
            }
        }
        if (constructors.isEmpty()) {
            return List.of(ensureDefaultConstructorNode(enclosingType));
        }
        List<String> ids = new ArrayList<>();
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        for (ConstructorDeclaration cd : constructors) {
            try {
                List<String> paramTypes = paramBinaryNames(cd.resolve());
                String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
                ids.add(MethodIds.methodId(signature));
            } catch (RuntimeException e) {
                reportUnresolvedDeclaration(cd, "failed to resolve constructor declaration: " + cd.getNameAsString());
            }
        }
        return ids;
    }

    private String ensureDefaultConstructorNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "constructor", qualifiedName, signature, sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    private List<Node> membersOf(Node typeLikeNode) {
        List<Node> members = new ArrayList<>();
        if (typeLikeNode instanceof TypeDeclaration<?> td) {
            for (BodyDeclaration<?> member : td.getMembers()) {
                members.add(member);
            }
        } else if (typeLikeNode instanceof ObjectCreationExpr oce) {
            NodeList<BodyDeclaration<?>> body = oce.getAnonymousClassBody().orElseGet(NodeList::new);
            for (BodyDeclaration<?> member : body) {
                members.add(member);
            }
        }
        return members;
    }

    // ------------------------------------------------------------------
    // attribution helpers
    // ------------------------------------------------------------------

    private TypeSite typeSiteOf(ResolvedReferenceTypeDeclaration decl) {
        Node ast = decl.toAst().orElse(null);
        String binaryName = BinaryNames.forResolvedDeclaration(decl);
        Path filePath = ast != null ? filePathOf(ast) : null;
        return new TypeSite(binaryName, filePath);
    }

    private TypeSite receiverSiteOf(MethodCallExpr mce, WalkContext ctx) {
        if (mce.getScope().isEmpty()) {
            Node enclosing = ctx.enclosingTypeNode();
            if (enclosing == null) {
                return null;
            }
            return new TypeSite(BinaryNames.forTypeLikeNode(enclosing), filePathOf(enclosing));
        }
        try {
            ResolvedType receiverType = mce.getScope().get().calculateResolvedType();
            if (!receiverType.isReferenceType()) {
                return null;
            }
            ResolvedReferenceTypeDeclaration decl = receiverType.asReferenceType().getTypeDeclaration().orElse(null);
            if (decl == null) {
                return null;
            }
            return typeSiteOf(decl);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String dispatchOf(ResolvedMethodDeclaration resolved) {
        if (resolved.isStatic()) {
            return "static";
        }
        if (resolved.declaringType().isInterface()) {
            return "interface";
        }
        if (resolved.isAbstract()) {
            return "abstract";
        }
        return "virtual";
    }

    private Map<String, Object> edgeMetadata(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private List<String> paramBinaryNames(ResolvedMethodLikeDeclaration resolved) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            names.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
        }
        return names;
    }

    private Path filePathOf(Node node) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toAbsolutePath().normalize())
                .orElse(null);
    }

    /**
     * node の source 位置。schema は {@code startLine} を 1-based で要求するため、位置が不明な場合
     * ({@code node.getBegin()} が空) は {@code startLine: 0} を出さず sourceLocation 自体を省略する
     * (null を返す)。
     */
    private SourceLocation sourceLocationOf(Node node) {
        return node.getBegin().map(p -> {
            Path filePath = filePathOf(node);
            String relativePath = filePath != null ? workspaceRoot.relativize(filePath).toString() : "";
            return SourceLocation.of(relativePath, p.line);
        }).orElse(null);
    }
}
