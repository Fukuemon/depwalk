package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * resolver とは独立した AST 走査で、解析対象 call kind の全 lexical site を
 * semantic caller ごとに登録する inventory
 * (java-analyzer feature doc「Parse・resolution・call 完全性」)。
 * caller 導出は {@link CallerIdentities} を介して CallGraphBuilder と同じ規則を
 * 共有する。callee の型解決は一切行わない。
 */
public final class CallSiteInventory {

    private final Path workspaceRoot;
    private final Set<CallSiteId> ids = new LinkedHashSet<>();

    /**
     * @param workspaceRoot 絶対・正規化済み workspace root
     */
    public CallSiteInventory(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * parse 済み CU の call site を登録する (solver 処理前に呼ぶ)。
     *
     * @param cu storage path を持つ parse 済み compilation unit
     */
    public void accept(CompilationUnit cu) {
        String path = cu.getStorage()
                .map(storage -> RelativePaths.toRecordPath(
                        workspaceRoot.relativize(storage.getPath().toAbsolutePath().normalize()).toString()))
                .orElseThrow(() -> new IllegalStateException("compilation unit without storage path"));
        walk(cu, path, null, List.of());
    }

    private void walk(Node node, String path, Node enclosingType, List<String> callers) {
        switch (node) {
            case TypeDeclaration<?> td -> recurse(node, path, td, List.of());
            case MethodDeclaration md ->
                    recurse(node, path, enclosingType, List.of(CallerIdentities.methodCallerId(enclosingType, md, path)));
            case ConstructorDeclaration cd ->
                    recurse(node, path, enclosingType,
                            List.of(CallerIdentities.constructorCallerId(enclosingType, cd, path)));
            case CompactConstructorDeclaration ccd ->
                    recurse(node, path, enclosingType,
                            List.of(CallerIdentities.compactConstructorCallerId(enclosingType, ccd, path)));
            case InitializerDeclaration id ->
                    recurse(node, path, enclosingType, id.isStatic()
                            ? List.of(CallerIdentities.staticInitializerId(enclosingType))
                            : CallerIdentities.instanceInitializerCallerIds(enclosingType, path));
            case FieldDeclaration fd ->
                    recurse(node, path, enclosingType, fd.isStatic()
                            ? List.of(CallerIdentities.staticInitializerId(enclosingType))
                            : CallerIdentities.instanceInitializerCallerIds(enclosingType, path));
            // enum constant の引数評価は <clinit> 意味論。
            case EnumConstantDeclaration enumConstant ->
                    recurse(node, path, enclosingType, List.of(CallerIdentities.staticInitializerId(enclosingType)));
            case LambdaExpr lambda -> recurse(node, path, enclosingType, callers);
            case MethodCallExpr mce -> {
                register(mce, path, CallSiteId.CallKind.METHOD_CALL, enclosingType, callers);
                recurse(node, path, enclosingType, callers);
            }
            case MethodReferenceExpr mre -> {
                register(mre, path, CallSiteId.CallKind.METHOD_REFERENCE, enclosingType, callers);
                recurse(node, path, enclosingType, callers);
            }
            case ObjectCreationExpr oce -> {
                register(oce, path, CallSiteId.CallKind.OBJECT_CREATION, enclosingType, callers);
                for (Node argument : oce.getArguments()) {
                    walk(argument, path, enclosingType, callers);
                }
                oce.getScope().ifPresent(scope -> walk(scope, path, enclosingType, callers));
                if (oce.getAnonymousClassBody().isPresent()) {
                    for (BodyDeclaration<?> member : oce.getAnonymousClassBody().get()) {
                        walk(member, path, oce, List.of());
                    }
                }
            }
            case ExplicitConstructorInvocationStmt ecis -> {
                register(ecis, path, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, enclosingType, callers);
                for (Node argument : ecis.getArguments()) {
                    walk(argument, path, enclosingType, callers);
                }
                // qualified super (`expr.super(...)`) の outer 式内の call も登録する。
                ecis.getExpression().ifPresent(expression -> walk(expression, path, enclosingType, callers));
            }
            default -> recurse(node, path, enclosingType, callers);
        }
    }

    private void recurse(Node node, String path, Node enclosingType, List<String> callers) {
        for (Node child : node.getChildNodes()) {
            walk(child, path, enclosingType, callers);
        }
    }

    private void register(Node callNode, String path, CallSiteId.CallKind kind, Node enclosingType, List<String> callers) {
        for (String caller : CallerIdentities.effectiveCallers(callers, enclosingType, callNode, path)) {
            CallSiteId id = of(callNode, path, kind, caller);
            if (!ids.add(id)) {
                throw new IllegalStateException("duplicate call site inventory entry: " + id);
            }
        }
    }

    /**
     * lexical site + semantic caller から決定的な {@link CallSiteId} を作る。
     *
     * @param callNode source range を持つ call 表現の AST node
     * @param path workspace 相対 path
     * @param callerMethodId 呼び出し元の method id (未解決なら placeholder id)
     * @throws IllegalStateException {@code callNode} が source range を持たない場合
     */
    public static CallSiteId of(Node callNode, String path, CallSiteId.CallKind kind, String callerMethodId) {
        Range range = callNode.getRange()
                .orElseThrow(() -> new IllegalStateException("call site without a source range: " + kind));
        return new CallSiteId(
                path,
                range.begin.line,
                range.begin.column,
                range.end.line,
                range.end.column,
                kind,
                callerMethodId);
    }

    public boolean contains(CallSiteId id) {
        return ids.contains(id);
    }

    /** 登録順を保った変更不可 view。 */
    public Set<CallSiteId> ids() {
        return Collections.unmodifiableSet(ids);
    }

    /**
     * CallGraphBuilder と inventory が共有する semantic caller 導出規則。
     * decl が resolve できない場合は決定的な placeholder id を使い、その配下の
     * call site は最終的に primary diagnostic として完全性 gate に残る。
     */
    public static final class CallerIdentities {

        private static final String PLACEHOLDER_PREFIX = "unresolved-caller:";

        private CallerIdentities() {
        }

        /** placeholder caller かどうか (edge 出力には使えない)。 */
        public static boolean isPlaceholder(String callerId) {
            return callerId.startsWith(PLACEHOLDER_PREFIX);
        }

        static String placeholder(Node declarationNode, String path) {
            int line = declarationNode.getRange().map(r -> r.begin.line).orElse(0);
            return PLACEHOLDER_PREFIX + path + ":" + line;
        }

        /**
         * method 宣言の caller id を返す。解決できない場合は placeholder id を返す。
         *
         * @param enclosingType 宣言を囲む型 (type 宣言または anonymous class の生成式)
         * @param path placeholder 生成に使う workspace 相対 path
         * @return 正規化 signature 由来の method id、または placeholder id
         */
        public static String methodCallerId(Node enclosingType, MethodDeclaration md, String path) {
            try {
                ResolvedMethodDeclaration resolved = md.resolve();
                return MethodIds.methodId(MethodIds.signature(
                        BinaryNames.forTypeLikeNode(enclosingType), resolved.getName(), paramTypes(resolved)));
            } catch (RuntimeException | LinkageError e) {
                return placeholder(md, path);
            }
        }

        /**
         * constructor 宣言の caller id を返す。解決できない場合は placeholder id を返す。
         *
         * @param enclosingType 宣言を囲む型 (type 宣言または anonymous class の生成式)
         * @param path placeholder 生成に使う workspace 相対 path
         * @return {@code <init>} の method id、または placeholder id
         */
        public static String constructorCallerId(Node enclosingType, ConstructorDeclaration cd, String path) {
            try {
                ResolvedConstructorDeclaration resolved = cd.resolve();
                return MethodIds.methodId(MethodIds.signature(
                        BinaryNames.forTypeLikeNode(enclosingType), MethodIds.CONSTRUCTOR_TOKEN, paramTypes(resolved)));
            } catch (RuntimeException | LinkageError e) {
                return placeholder(cd, path);
            }
        }

        /**
         * record の compact constructor の caller id を、record 成分から
         * 引数型を復元して返す。enclosing が record 宣言でない場合や解決に
         * 失敗した場合は placeholder id を返す。
         *
         * @param enclosingType 宣言を囲む型 (record 宣言を期待する)
         * @param path placeholder 生成に使う workspace 相対 path
         * @return {@code <init>} の method id、または placeholder id
         */
        public static String compactConstructorCallerId(Node enclosingType, CompactConstructorDeclaration ccd, String path) {
            try {
                if (!(enclosingType instanceof RecordDeclaration rd)) {
                    return placeholder(ccd, path);
                }
                List<String> paramTypes = new ArrayList<>();
                for (Parameter component : rd.getParameters()) {
                    paramTypes.add(BinaryNames.erasureOf(component.resolve().getType()));
                }
                return MethodIds.methodId(MethodIds.signature(
                        BinaryNames.forTypeLikeNode(enclosingType), MethodIds.CONSTRUCTOR_TOKEN, paramTypes));
            } catch (RuntimeException | LinkageError e) {
                return placeholder(ccd, path);
            }
        }

        /** {@code <clinit>()} の method id。 */
        public static String staticInitializerId(Node enclosingType) {
            return MethodIds.methodId(MethodIds.signature(
                    BinaryNames.forTypeLikeNode(enclosingType), MethodIds.STATIC_INITIALIZER_TOKEN, List.of()));
        }

        /** instance initializer / field initializer の caller = 宣言済み全 constructor (無ければ default)。 */
        public static List<String> instanceInitializerCallerIds(Node enclosingType, String path) {
            List<ConstructorDeclaration> constructors = new ArrayList<>();
            if (enclosingType instanceof TypeDeclaration<?> td) {
                for (BodyDeclaration<?> member : td.getMembers()) {
                    if (member instanceof ConstructorDeclaration cd) {
                        constructors.add(cd);
                    }
                }
            } else if (enclosingType instanceof ObjectCreationExpr oce && oce.getAnonymousClassBody().isPresent()) {
                for (BodyDeclaration<?> member : oce.getAnonymousClassBody().get()) {
                    if (member instanceof ConstructorDeclaration cd) {
                        constructors.add(cd);
                    }
                }
            }
            if (constructors.isEmpty()) {
                return List.of(defaultConstructorId(enclosingType));
            }
            List<String> ids = new ArrayList<>();
            for (ConstructorDeclaration cd : constructors) {
                ids.add(constructorCallerId(enclosingType, cd, path));
            }
            return ids;
        }

        /** 合成 default constructor {@code <init>()} の method id。 */
        public static String defaultConstructorId(Node enclosingType) {
            return MethodIds.methodId(MethodIds.signature(
                    BinaryNames.forTypeLikeNode(enclosingType), MethodIds.CONSTRUCTOR_TOKEN, List.of()));
        }

        private static List<String> paramTypes(ResolvedMethodLikeDeclaration resolved) {
            List<String> types = new ArrayList<>();
            for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                types.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
            }
            return types;
        }

        /**
         * ledger 用の実効 caller。member 外 (enum constant 引数等) で caller が
         * 空の場合は enclosing type の {@code <clinit>} へ、それも無ければ
         * 決定的 placeholder へ帰着させ、黙示の未分類を残さない。
         */
        public static List<String> effectiveCallers(List<String> callers, Node enclosingType, Node callNode, String path) {
            if (!callers.isEmpty()) {
                return callers;
            }
            if (enclosingType != null) {
                return List.of(staticInitializerId(enclosingType));
            }
            return List.of(placeholder(callNode, path));
        }
    }
}
