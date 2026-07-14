package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
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
    private final SootUpTypeHierarchyIndex sootUpIndex;

    public CallGraphBuilder(
            Path workspaceRoot,
            AttributionResolver attributionResolver,
            GraphAccumulator accumulator,
            SootUpTypeHierarchyIndex sootUpIndex) {
        this.workspaceRoot = workspaceRoot;
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
        this.sootUpIndex = sootUpIndex;
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
        if (node instanceof CompactConstructorDeclaration ccd) {
            // record の compact constructor (`record User(String name) { User { validate(); } }`)。
            // canonical constructor 扱いとし、signature は record component の erasure 型列にする。
            // JavaParser の CompactConstructorDeclaration#resolve() は未実装
            // (UnsupportedOperationException) なため、record の component 列から自前で計算する。
            MethodSymbol symbol;
            try {
                symbol = buildCompactConstructorSymbol(ctx.enclosingTypeNode(), ccd);
            } catch (RuntimeException e) {
                reportUnresolvedDeclaration(ccd, "failed to resolve compact constructor declaration: " + ccd.getNameAsString());
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
        if (node instanceof MethodReferenceExpr mre) {
            processMethodReference(mre, ctx);
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
        TypeSite receiverSite = receiverSiteOf(mce, ctx, resolved, declaringSite);
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            return;
        }

        MethodSymbol calleeSymbol = buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = dispatchOf(resolved);
        indexDispatchCandidates(resolved, dispatch, mce, ctx);
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

    /** {@code Foo::new} の source 上の識別子 ({@code getIdentifier()} が返す値)。 */
    private static final String METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER = "new";

    /**
     * D6 の lambda 既定 (囲みメソッドへ帰属 + {@code viaLambda: true}) と同じ原則を method reference
     * ({@code this::toDto} / {@code Foo::bar} / {@code Foo::new}) に適用する。囲みメソッドを caller、
     * 参照先メソッド (D11 の帰属規則適用) を callee とする {@code callEdge} を出力し、
     * {@code metadata.viaMethodReference: true} で標識する (Core は metadata を解釈しないため契約変更なし)。
     */
    private void processMethodReference(MethodReferenceExpr mre, WalkContext ctx) {
        if (METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER.equals(mre.getIdentifier())) {
            processConstructorReference(mre, ctx);
            return;
        }

        ResolvedMethodDeclaration resolved;
        try {
            resolved = mre.resolve();
        } catch (RuntimeException e) {
            reportUnresolved(mre, ctx);
            return;
        }

        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = typeSiteOfExpression(mre.getScope());
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            return;
        }

        MethodSymbol calleeSymbol = buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = dispatchOf(resolved);
        indexDispatchCandidates(resolved, dispatch, mre, ctx);
        Map<String, Object> metadata = methodReferenceEdgeMetadata(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mre);
        for (String callerId : ctx.callerMethodIds()) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
    }

    /**
     * constructor reference ({@code Foo::new}) の扱い。D11 の {@code new} 規則 (constructor は継承され
     * ないため引き上げは発生しない) をそのまま適用し、scope 外なら出力しない。{@code JavaParser} は
     * constructor reference の {@code resolve()} を未実装 ({@code UnsupportedOperationException}) と
     * しているため、参照先型の constructor 一覧から候補を自前で選ぶ (単一候補ならそれを使い、複数候補
     * のときは呼び出し先の関数型インタフェースの SAM 引数数で絞り込む)。
     */
    private void processConstructorReference(MethodReferenceExpr mre, WalkContext ctx) {
        ResolvedReferenceTypeDeclaration scopeDecl;
        try {
            ResolvedType scopeType = mre.getScope().calculateResolvedType();
            if (!scopeType.isReferenceType()) {
                reportUnresolved(mre, ctx);
                return;
            }
            scopeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
            if (scopeDecl == null) {
                reportUnresolved(mre, ctx);
                return;
            }
        } catch (RuntimeException e) {
            reportUnresolved(mre, ctx);
            return;
        }

        TypeSite declaringSite = typeSiteOf(scopeDecl);
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            return;
        }

        ResolvedConstructorDeclaration resolvedCtor = selectConstructor(scopeDecl.getConstructors(), mre);
        if (resolvedCtor == null) {
            reportUnresolved(mre, ctx);
            return;
        }

        MethodSymbol calleeSymbol = buildConstructorSymbol(attribution, resolvedCtor);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = methodReferenceEdgeMetadata(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mre);
        for (String callerId : ctx.callerMethodIds()) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
    }

    /**
     * constructor reference の候補選択。候補が 1 つならそれを使う。複数の overload があるときは、
     * {@code mre} が代入される関数型インタフェースの SAM (single abstract method) の引数数に一致する
     * ものを選ぶ。一致がゼロ / 複数 (曖昧) / SAM 引数数が推論できない場合は {@code null} (呼び出し側で
     * {@code JAVA_UNRESOLVED_SYMBOL} diagnostic にする)。
     */
    private ResolvedConstructorDeclaration selectConstructor(List<ResolvedConstructorDeclaration> ctors, MethodReferenceExpr mre) {
        if (ctors.isEmpty()) {
            return null;
        }
        if (ctors.size() == 1) {
            return ctors.get(0);
        }
        int arity = inferFunctionalInterfaceArity(mre);
        if (arity < 0) {
            return null;
        }
        ResolvedConstructorDeclaration match = null;
        for (ResolvedConstructorDeclaration ctor : ctors) {
            if (ctor.getNumberOfParams() == arity) {
                if (match != null) {
                    return null;
                }
                match = ctor;
            }
        }
        return match;
    }

    /** {@code mre} が代入される関数型インタフェースの SAM の引数数。推論できなければ {@code -1}。 */
    private int inferFunctionalInterfaceArity(MethodReferenceExpr mre) {
        try {
            ResolvedType functionalType = mre.calculateResolvedType();
            if (!functionalType.isReferenceType()) {
                return -1;
            }
            ResolvedReferenceTypeDeclaration decl = functionalType.asReferenceType().getTypeDeclaration().orElse(null);
            if (decl == null) {
                return -1;
            }
            for (MethodUsage methodUsage : decl.getAllMethods()) {
                if (methodUsage.getDeclaration().isAbstract()) {
                    return methodUsage.getNoParams();
                }
            }
        } catch (RuntimeException e) {
            return -1;
        }
        return -1;
    }

    private Map<String, Object> methodReferenceEdgeMetadata(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        metadata.put("viaMethodReference", true);
        return metadata;
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
            // synthetic default constructor / record canonical constructor は自身の AST を持たない
            // ({@code toAst()} が空、record の場合は compact constructor の有無によらず常に空) ため、
            // 宣言型の AST 位置へフォールバックする。これにより ensureDefaultConstructorNode /
            // buildCompactConstructorSymbol と同一内容になり、同一 methodId の node がどの経路から
            // 生成されても内容が一致する (GraphAccumulator の first-wins 重複排除で情報が失われない)。
            // record に compact constructor があれば、宣言型そのものより精度の高い compact constructor
            // の位置を使う (buildCompactConstructorSymbol と揃える)。
            Node ast = resolved.toAst().orElse(null);
            if (ast == null) {
                ast = preferCompactConstructorLocation(resolved.declaringType().toAst().orElse(null));
            }
            sourceLocation = ast != null ? sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, metadata);
    }

    /**
     * {@code typeAst} が record で compact constructor を持つ場合、その位置を優先して返す
     * (宣言型全体より精度の高い sourceLocation にする)。該当しなければ {@code typeAst} をそのまま返す。
     */
    private Node preferCompactConstructorLocation(Node typeAst) {
        if (typeAst instanceof RecordDeclaration rd) {
            for (BodyDeclaration<?> member : rd.getMembers()) {
                if (member instanceof CompactConstructorDeclaration ccd) {
                    return ccd;
                }
            }
        }
        return typeAst;
    }

    /**
     * record の compact constructor (D11: record の canonical constructor 扱い) の {@link MethodSymbol}
     * を作る。signature は record component の erasure 型列 (宣言順)。JavaParser の
     * {@code CompactConstructorDeclaration#resolve()} は未実装のため、record の component 一覧
     * ({@link RecordDeclaration#getParameters()}) から自前で param 型を求める。
     */
    private MethodSymbol buildCompactConstructorSymbol(Node enclosingTypeNode, CompactConstructorDeclaration ccd) {
        if (!(enclosingTypeNode instanceof RecordDeclaration rd)) {
            throw new IllegalStateException("compact constructor outside of a record declaration: " + ccd);
        }
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        List<String> paramTypes = new ArrayList<>();
        for (Parameter component : rd.getParameters()) {
            paramTypes.add(BinaryNames.erasureOf(component.resolve().getType()));
        }
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
        SourceLocation sourceLocation = sourceLocationOf(ccd);
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, null);
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

    /**
     * mce の scope (レシーバ式) から帰属型決定に使う {@link TypeSite} を決める。
     *
     * <p>D11: scope が空 (無修飾呼び出し) のとき、通常は enclosing class を「参照した型」とみなす
     * (自クラス呼び出し / 継承 static・instance メソッドの暗黙 this 呼び出し)。ただし static かつ
     * 宣言型が enclosing class の継承階層に含まれない場合は、無修飾 static import
     * ({@code import static pkg.Type.member;}) 由来の呼び出しであり、「参照した型」は enclosing class
     * ではなく宣言型そのものである。そのため receiverSite = declaringSite として扱い、宣言型が
     * scope 外なら enclosing への誤った引き上げを起こさず出力を省略する。
     */
    private TypeSite receiverSiteOf(MethodCallExpr mce, WalkContext ctx, ResolvedMethodDeclaration resolved, TypeSite declaringSite) {
        if (mce.getScope().isEmpty()) {
            Node enclosing = ctx.enclosingTypeNode();
            if (enclosing == null) {
                return null;
            }
            if (resolved.isStatic() && !declaringTypeInEnclosingHierarchy(enclosing, resolved.declaringType())) {
                return declaringSite;
            }
            return new TypeSite(BinaryNames.forTypeLikeNode(enclosing), filePathOf(enclosing));
        }
        return typeSiteOfExpression(mce.getScope().get());
    }

    /** 式を評価した静的型の {@link TypeSite}。解決できなければ {@code null}。 */
    private TypeSite typeSiteOfExpression(Expression expr) {
        try {
            ResolvedType receiverType = expr.calculateResolvedType();
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

    /**
     * declaringType が enclosingTypeNode 自身または、その継承階層 (supertype / interface) に含まれるか
     * を判定する。判定不能な場合 (enclosing の型解決失敗 / ancestor 列挙が unresolved symbol で失敗) は、
     * 従来の「enclosing への引き上げ」挙動を壊さないよう保守的に {@code true} を返す。
     */
    private boolean declaringTypeInEnclosingHierarchy(Node enclosingTypeNode, ResolvedReferenceTypeDeclaration declaringType) {
        String declaringBinaryName = BinaryNames.forResolvedDeclaration(declaringType);
        String enclosingBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        if (declaringBinaryName.equals(enclosingBinaryName)) {
            return true;
        }
        ResolvedReferenceTypeDeclaration enclosingDecl = resolveTypeLikeNode(enclosingTypeNode);
        if (enclosingDecl == null) {
            return true;
        }
        try {
            for (ResolvedReferenceType ancestor : enclosingDecl.getAllAncestors()) {
                ResolvedReferenceTypeDeclaration ancestorDecl = ancestor.getTypeDeclaration().orElse(null);
                if (ancestorDecl != null && declaringBinaryName.equals(BinaryNames.forResolvedDeclaration(ancestorDecl))) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return true;
        }
        return false;
    }

    private ResolvedReferenceTypeDeclaration resolveTypeLikeNode(Node typeLikeNode) {
        try {
            if (typeLikeNode instanceof TypeDeclaration<?> td) {
                return td.resolve();
            }
            if (typeLikeNode instanceof ObjectCreationExpr oce) {
                ResolvedType type = oce.calculateResolvedType();
                return type.isReferenceType() ? type.asReferenceType().getTypeDeclaration().orElse(null) : null;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
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

    /**
     * P1 では SootUp の候補を run-local cache に索引するだけで edge 化しない。候補を使った edge の
     * 統合は P3 の責務とし、ここでは E3 のみを既存 JavaParser edge と併記する。
     */
    private void indexDispatchCandidates(
            ResolvedMethodDeclaration resolved,
            String dispatch,
            Node callNode,
            WalkContext ctx) {
        if ("static".equals(dispatch)) {
            return;
        }
        String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
        SootUpTypeHierarchyIndex.Resolution resolution = sootUpIndex.resolveMethod(
                declaringType,
                resolved.getName(),
                paramBinaryNames(resolved));
        if (resolution.isAvailable()) {
            return;
        }
        String relatedMethodId = ctx.callerMethodIds().isEmpty() ? null : ctx.callerMethodIds().get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.severity(),
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.code(),
                resolution.unavailableReason(),
                sourceLocationOf(callNode),
                relatedMethodId,
                Map.of("targetType", declaringType)));
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
            String relativePath = filePath != null
                    ? RelativePaths.toRecordPath(workspaceRoot.relativize(filePath).toString())
                    : "";
            return SourceLocation.of(relativePath, p.line);
        }).orElse(null);
    }
}
