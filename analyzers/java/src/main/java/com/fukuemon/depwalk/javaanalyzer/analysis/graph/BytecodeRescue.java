package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * source で解決できなかった call site を、到達可能な scope 内 source 型の project bytecode 上の
 * 一意 member へ救済できるかを判定し、採用する member を返す。あわせて receiver の owner 型復元と、
 * 救済不能な site を external-target と分類できるかの根拠付き判定を担う。
 *
 * <p>本クラスの契約の正本は java-analyzer feature doc「solver 層の bytecode member 合成」。
 * 判定はすべて classfile 上の根拠に基づき、根拠のない型推測は行わない。
 */
final class BytecodeRescue {

    /** SootUp の descriptor erasure で type variable が落ちる先 (JLS 4.6 の既定境界)。 */
    private static final String ERASED_TYPE_VARIABLE_BOUND = "java.lang.Object";

    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final WorkspaceSourceDeclarationIndex declIndex;
    private final ProjectBytecodeMemberIndex bytecodeIndex;
    private final ReachableOwners reachableOwners;

    /**
     * @param sootUpIndex full classpath 視点の型階層・宣言 member 索引 (external chain の前進検証に使う)
     * @param declIndex workspace の source 宣言から型の所有 context と source location を引く索引
     * @param bytecodeIndex 呼び出し元 context の classpath 視点で bytecode member
     *     (method / constructor / field 型 / generic 戻り型) を引く索引
     */
    BytecodeRescue(
            SootUpTypeHierarchyIndex sootUpIndex,
            WorkspaceSourceDeclarationIndex declIndex,
            ProjectBytecodeMemberIndex bytecodeIndex,
            ReachableOwners reachableOwners) {
        this.sootUpIndex = sootUpIndex;
        this.declIndex = declIndex;
        this.bytecodeIndex = bytecodeIndex;
        this.reachableOwners = reachableOwners;
    }

    /**
     * 救済で採用した bytecode-only member。emit は呼び出し側 ({@code emitBytecodeOnlyCall}) が行う。
     *
     * @param owner symbol の source anchor にする所有型の所在
     */
    record Rescue(
            WorkspaceSourceDeclarationIndex.TypeLocation owner,
            String declaringType,
            String methodNameToken,
            List<String> parameterTypes,
            String symbolKind) {
    }

    /**
     * solver が合成した bytecode-only member の owner。合成は到達可能な scope 内 owner を
     * 前提に行われるため、ここで引けない場合は analyzer 側の不変条件違反として failfast する。
     */
    WorkspaceSourceDeclarationIndex.TypeLocation requireReachableOwner(
            SynthesizedBytecodeMethodDeclaration synthesized) {
        return reachableOwners.find(synthesized.candidate().declaringType())
                .orElseThrow(() -> new IllegalStateException(
                        "synthesized bytecode member without a reachable in-scope owner: "
                                + synthesized.candidate().declaringType() + "#" + synthesized.getName()));
    }

    /**
     * 解決失敗した method call を、scope 内 source type の到達可能な project
     * bytecode の一意 member へ generator 非依存で救済する (ADR-0005)。
     */
    Rescue methodRescue(MethodCallExpr mce, Node enclosingTypeNode) {
        String ownerBinaryName = bytecodeRescueOwner(mce, enclosingTypeNode);
        return ownerBinaryName != null ? methodRescueWithOwner(mce, ownerBinaryName) : null;
    }

    /**
     * 確定済み owner を使う method call の bytecode 救済。receiver の静的型を owner とする
     * 通常経路 ({@link #methodRescue}) と、chain 前進解決で復元した owner を使う経路の共通実体。
     */
    Rescue methodRescueWithOwner(MethodCallExpr mce, String ownerBinaryName) {
        WorkspaceSourceDeclarationIndex.TypeLocation owner = reachableOwners.find(ownerBinaryName).orElse(null);
        if (owner == null) {
            return null;
        }
        var candidate = bytecodeIndex.uniqueMethod(ownerBinaryName, mce.getNameAsString(), mce.getArguments().size())
                .orElse(null);
        if (candidate == null) {
            return null;
        }
        // 型名 scope の static call を instance member で救済しない (偽 edge 防止)。
        // forward 経路の owner は式評価由来で型名 scope になり得ないが、guard の非対称を残さない。
        if (!candidate.isStatic() && mce.getScope().isPresent() && isTypeNameScope(mce.getScope().get())) {
            return null;
        }
        return new Rescue(owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method");
    }

    /**
     * scope が値でなく型名 (static call の receiver) かを判定する。型として
     * 解決できる scope のうち、値 (field / 変数) として解決できない単純名 /
     * qualified name だけを型名とみなす。型が取れない scope は bytecode-only
     * field 補完経路の instance receiver であり型名扱いしない。
     */
    static boolean isTypeNameScope(Expression scope) {
        try {
            scope.calculateResolvedType();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        try {
            if (scope instanceof NameExpr nameExpr) {
                nameExpr.resolve();
                return false;
            }
            if (scope instanceof FieldAccessExpr fieldAccess) {
                fieldAccess.resolve();
                return false;
            }
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
        return false;
    }

    /**
     * 解決失敗した method reference の bytecode-only member 救済。
     * 参照先型が scope 内で到達可能な場合に、JLS 15.13.1 に沿った候補選択
     * ({@link #selectMethodReferenceCandidate}) で救済する。
     *
     * @param samArity 参照先 functional interface の SAM 引数数。owner の到達可能性を
     *     確認できた場合のみ評価する
     */
    Rescue methodReferenceRescue(MethodReferenceExpr mre, IntSupplier samArity) {
        String ownerBinaryName = methodReferenceOwner(mre);
        if (ownerBinaryName == null) {
            return null;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = reachableOwners.find(ownerBinaryName).orElse(null);
        if (owner == null) {
            return null;
        }
        boolean typeNameScope = mre.getScope() instanceof TypeExpr;
        var candidate = selectMethodReferenceCandidate(
                ownerBinaryName, mre.getIdentifier(), typeNameScope, samArity.getAsInt());
        if (candidate == null) {
            return null;
        }
        return new Rescue(owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method");
    }

    /**
     * method reference の候補選択。JLS 15.13.1 の 2 つの解釈だけを候補にする:
     * <ul>
     * <li>{@code Type::m} ({@code typeNameScope=true}): static なら arity=samArity、
     *     instance (unbound reference) なら arity=samArity-1 のみが有効。両方に
     *     候補があれば曖昧として不採用。</li>
     * <li>{@code expr::m} ({@code typeNameScope=false}、bound reference): instance
     *     の arity=samArity のみが有効 (static、または samArity-1 は無効)。</li>
     * </ul>
     * SAM arity を推論できない場合は救済しない。候補列挙は owner classfile の
     * 宣言 member に限られ継承 overload が見えず、宣言上の名前一意を参照先の
     * 一意の根拠にできないため、曖昧として diagnostic に残す。
     */
    private SootUpTypeHierarchyIndex.MethodCandidate selectMethodReferenceCandidate(
            String ownerBinaryName, String methodName, boolean typeNameScope, int samArity) {
        if (samArity < 0) {
            return null;
        }
        if (typeNameScope) {
            var staticCandidate = uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity, true);
            var instanceCandidate = samArity >= 1
                    ? uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity - 1, false)
                    : null;
            if (staticCandidate != null && instanceCandidate != null) {
                return null;
            }
            return staticCandidate != null ? staticCandidate : instanceCandidate;
        }
        return uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity, false);
    }

    /** 名前・arity・static 性が一致する owner classfile 上の一意 member。 */
    private SootUpTypeHierarchyIndex.MethodCandidate uniqueMethodByArityAndStatic(
            String ownerBinaryName, String methodName, int arity, boolean wantStatic) {
        var matches = bytecodeIndex.declaredCallableMethods(ownerBinaryName).stream()
                .filter(method -> method.methodName().equals(methodName))
                .filter(method -> method.parameterTypes().size() == arity)
                .filter(method -> method.isStatic() == wantStatic)
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * constructor reference (`Foo::new`) の候補選択が決まらない場合の
     * bytecode-only constructor 救済。SAM arity の一意 constructor
     * だけを採用する。
     */
    Rescue constructorReferenceRescue(ResolvedReferenceTypeDeclaration scopeDecl, IntSupplier samArity) {
        return constructorRescueWithOwner(BinaryNames.forResolvedDeclaration(scopeDecl), samArity);
    }

    /**
     * 確定済み owner の一意 bytecode constructor による救済 (object creation / 明示 constructor
     * invocation / constructor reference の共通実体)。owner が到達可能な scope 内 source 型で、
     * かつ {@code arity} の constructor が classfile 上で一意なときだけ採用する。
     *
     * @param ownerBinaryName 救済先 owner。特定できていない場合は {@code null}
     * @param arity 引数個数。owner の到達可能性を確認できた場合のみ評価する。負値は「推論不能」を表し
     *     救済しない
     */
    private Rescue constructorRescueWithOwner(String ownerBinaryName, IntSupplier arity) {
        if (ownerBinaryName == null) {
            return null;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = reachableOwners.find(ownerBinaryName).orElse(null);
        if (owner == null) {
            return null;
        }
        int argumentCount = arity.getAsInt();
        if (argumentCount < 0) {
            return null;
        }
        var candidate = bytecodeIndex.uniqueConstructor(ownerBinaryName, argumentCount).orElse(null);
        if (candidate == null) {
            return null;
        }
        return new Rescue(owner,
                candidate.declaringType(), MethodIds.CONSTRUCTOR_TOKEN, candidate.parameterTypes(), "constructor");
    }

    /** method reference の参照先 owner (scope 式の静的型 erasure)。 */
    String methodReferenceOwner(MethodReferenceExpr mre) {
        try {
            ResolvedType scopeType = mre.getScope().calculateResolvedType().erasure();
            if (!scopeType.isReferenceType()) {
                return null;
            }
            return BinaryNames.erasureOf(scopeType);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * 解決失敗した明示 constructor invocation の bytecode-only constructor 救済。
     * this(...) は囲み型、super(...) は extends 節を resolve した
     * 親型を owner とする。
     */
    Rescue explicitCtorRescue(ExplicitConstructorInvocationStmt ecis, Node enclosingTypeNode) {
        return constructorRescueWithOwner(
                explicitCtorOwner(ecis, enclosingTypeNode), () -> ecis.getArguments().size());
    }

    /** 明示 constructor invocation の解決先 owner 型 (this は囲み型、super は親型)。 */
    String explicitCtorOwner(ExplicitConstructorInvocationStmt ecis, Node enclosingTypeNode) {
        if (enclosingTypeNode == null) {
            return null;
        }
        if (ecis.isThis()) {
            try {
                return BinaryNames.forTypeLikeNode(enclosingTypeNode);
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        if (enclosingTypeNode instanceof ClassOrInterfaceDeclaration cid
                && !cid.getExtendedTypes().isEmpty()) {
            try {
                return BinaryNames.erasureOf(cid.getExtendedTypes().get(0).resolve());
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return null;
    }

    /** 解決失敗した object creation の bytecode-only constructor 救済。 */
    Rescue constructorRescue(ObjectCreationExpr oce) {
        String ownerBinaryName;
        try {
            ownerBinaryName = BinaryNames.erasureOf(oce.getType().resolve());
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return constructorRescueWithOwner(ownerBinaryName, () -> oce.getArguments().size());
    }

    /**
     * chain の前進解決。receiver 式の静的型が取れない場合、
     * chain を再帰的に遡り、各 link を bytecode candidate の戻り値型
     * (classfile の descriptor / generic Signature 由来) で前進解決して現在の
     * call の owner 型を復元する。候補が一意でない・classfile に根拠が無い
     * link があれば null (推測しない)。
     */
    String chainForwardOwner(Expression expr, Node enclosingTypeNode) {
        return chainForwardOwner(expr, enclosingTypeNode, 0);
    }

    private String chainForwardOwner(Expression expr, Node enclosingTypeNode, int depth) {
        if (expr == null || depth > 16) {
            return null;
        }
        String direct = tryTypeErasureOf(expr);
        if (direct != null) {
            return direct;
        }
        if (expr instanceof EnclosedExpr enclosed) {
            return chainForwardOwner(enclosed.getInner(), enclosingTypeNode, depth + 1);
        }
        if (expr instanceof NameExpr nameExpr) {
            // 型が取れない local 変数は、宣言の initializer 式を同じ規則で前進
            // 解決する (var / 失敗 chain 由来の変数への波及を classfile 根拠で辿る)。
            try {
                ResolvedValueDeclaration value = nameExpr.resolve();
                Node ast = value.toAst().orElse(null);
                if (ast instanceof VariableDeclarator declarator) {
                    Expression initializer = declarator.getInitializer().orElse(null);
                    if (initializer != null) {
                        return chainForwardOwner(initializer, enclosingTypeNode, depth + 1);
                    }
                }
                return null;
            } catch (RuntimeException | LinkageError e) {
                // `var` の型推論が壊れていると resolve() 自体が失敗する。囲み
                // callable 内で同名宣言が一意なら、その initializer を確定 AST
                // として前進解決する (一意でなければ shadowing の誤追跡を避けて
                // 不採用)。local に該当が無ければ囲み型の bytecode field 型で
                // 補完する (既存の receiver 補完経路と同じ classfile 根拠)。
                Expression initializer = uniqueLocalInitializer(nameExpr);
                if (initializer != null) {
                    return chainForwardOwner(initializer, enclosingTypeNode, depth + 1);
                }
                if (sameNameLocalDeclared(nameExpr)) {
                    return null;
                }
                return enclosingBytecodeFieldType(nameExpr.getNameAsString(), enclosingTypeNode);
            }
        }
        if (expr instanceof FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof ThisExpr) {
            return enclosingBytecodeFieldType(fieldAccess.getNameAsString(), enclosingTypeNode);
        }
        if (!(expr instanceof MethodCallExpr link)) {
            return null;
        }
        Expression scope = link.getScope().orElse(null);
        String receiverOwner;
        if (scope == null) {
            // 暗黙 this の link は囲み型の classfile candidate で前進する
            // (継承 member は declared methods に現れないため、その場合は null)。
            receiverOwner = enclosingTypeNode != null ? tryBinaryNameOfEnclosing(enclosingTypeNode) : null;
        } else {
            receiverOwner = chainForwardOwner(scope, enclosingTypeNode, depth + 1);
        }
        if (receiverOwner == null) {
            return null;
        }
        var candidate = bytecodeIndex
                .uniqueMethod(receiverOwner, link.getNameAsString(), link.getArguments().size())
                .orElse(null);
        if (candidate == null) {
            return null;
        }
        var generic = bytecodeIndex.genericReturnType(candidate).orElse(null);
        String returnType = generic != null && !generic.typeVariable() && generic.arrayDims() == 0
                ? generic.binaryName()
                : candidate.returnType();
        if (returnType == null || returnType.endsWith("[]") || isPrimitiveOrVoid(returnType)) {
            return null;
        }
        return returnType;
    }

    /**
     * 囲み callable (method / constructor / initializer / lambda body を含む
     * 最内の宣言) の中で同名の local 宣言が一意なら、その initializer を返す。
     */
    private static Expression uniqueLocalInitializer(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        Node child = nameExpr;
        Node parent = child.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof BlockStmt block) {
                Expression initializer = declaratorBeforeInBlock(block, child, name);
                if (initializer != null) {
                    return initializer;
                }
            }
            if (parent instanceof MethodDeclaration
                    || parent instanceof ConstructorDeclaration
                    || parent instanceof InitializerDeclaration
                    || parent instanceof LambdaExpr) {
                // callable / lambda 境界。ここまでで見つからなければ不採用
                // (字句スコープ外の同名宣言を誤って結びつけない、保守側)。
                return null;
            }
            child = parent;
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    /**
     * {@code block} 直下の文のうち、{@code child} (を祖先に持つ文) より前にある
     * 同名 {@code VariableDeclarator} の initializer (前方参照は無効、かつ use を
     * 含まない兄弟文の宣言は対象にしない)。
     */
    private static Expression declaratorBeforeInBlock(BlockStmt block, Node child, String name) {
        for (Statement statement : block.getStatements()) {
            if (statement == child || statement.isAncestorOf(child)) {
                break;
            }
            if (statement instanceof ExpressionStmt exprStmt
                    && exprStmt.getExpression()
                            instanceof VariableDeclarationExpr varDecl) {
                for (VariableDeclarator declarator : varDecl.getVariables()) {
                    if (declarator.getNameAsString().equals(name)) {
                        return declarator.getInitializer().orElse(null);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 囲み member 内に同名の local 宣言 (変数宣言・for ヘッダ・try-resource・
     * catch 節・パターン変数・parameter) が存在するか。field フォールバックの
     * 前提検査に使う: {@link #uniqueLocalInitializer} は block 直下の宣言文しか
     * 見ないため、それ以外の位置で宣言された同名 local が field を shadowing
     * していると、local への呼び出しを field 型で誤判定して false exclusion に
     * 倒れる余地がある。use 位置で実際に shadowing しているかまでは判定しない
     * (過剰検出は diagnostic 残留にしか倒れない保守側)。
     */
    private static boolean sameNameLocalDeclared(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        Node member = nameExpr;
        Node parent = nameExpr.getParentNode().orElse(null);
        while (parent != null && !(parent instanceof TypeDeclaration)) {
            member = parent;
            parent = parent.getParentNode().orElse(null);
        }
        // FieldDeclaration 直下の declarator は field であり local ではない
        // (field initializer 内からの遡上や、member 内の local class の field を
        // 同名 local として誤検出しない)。
        return member.findAll(VariableDeclarator.class).stream()
                        .filter(declarator ->
                                !(declarator.getParentNode().orElse(null) instanceof FieldDeclaration))
                        .anyMatch(declarator -> declarator.getNameAsString().equals(name))
                || member.findAll(Parameter.class).stream()
                        .anyMatch(parameter -> parameter.getNameAsString().equals(name))
                || member.findAll(TypePatternExpr.class).stream()
                        .anyMatch(pattern -> pattern.getNameAsString().equals(name));
    }

    /** 囲み型の bytecode field 型 (classfile 根拠の receiver 補完)。 */
    private String enclosingBytecodeFieldType(String fieldName, Node enclosingTypeNode) {
        if (enclosingTypeNode == null) {
            return null;
        }
        String ownerType = tryBinaryNameOfEnclosing(enclosingTypeNode);
        if (ownerType == null) {
            return null;
        }
        if (reachableOwners.find(ownerType).isEmpty()) {
            return null;
        }
        return bytecodeIndex.fieldType(ownerType, fieldName).orElse(null);
    }

    private static String tryBinaryNameOfEnclosing(Node enclosingTypeNode) {
        try {
            return BinaryNames.forTypeLikeNode(enclosingTypeNode);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean isPrimitiveOrVoid(String binaryName) {
        return switch (binaryName) {
            case "void", "boolean", "byte", "short", "char", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * chain 起点遡及 (external 分類規則 (i))。receiver が method call chain の
     * とき、chain を遡って最初に静的型が取れる式を探し、その型が scope 外
     * (source 宣言索引に無い) なら、root から現在の call までの中間 link を
     * {@link #forwardVerifyExternalChain} で classfile 根拠 (project 限定でない
     * full classpath) により前進検証できた場合だけ true。scope 内型が現れた場合、
     * 起点の型も取れない場合、または中間 link を検証できない場合は false
     * (diagnostic に残す、保守側)。
     */
    boolean chainRootIsExternal(MethodCallExpr mce, Node enclosingTypeNode) {
        return rootIsExternal(mce.getScope().orElse(null), enclosingTypeNode);
    }

    /**
     * 式の起点型を遡及し、根拠を伴って scope 外と判定できる場合だけ true
     * (規則 (i) の実体)。
     */
    private boolean rootIsExternal(Expression start, Node enclosingTypeNode) {
        List<MethodCallExpr> links = new ArrayList<>();
        Expression cursor = start;
        int guard = 0;
        while (cursor != null && guard++ < 64) {
            String erasure = tryTypeErasureOf(cursor);
            if (erasure != null) {
                if (declIndex.find(erasure).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(erasure, links);
            }
            if (cursor instanceof EnclosedExpr enclosed) {
                cursor = enclosed.getInner();
                continue;
            }
            if (cursor instanceof MethodCallExpr link) {
                Expression inner = link.getScope().orElse(null);
                if (inner == null) {
                    // 暗黙 this 起点 = 囲み型 (scope 内) → 保守的に diagnostic。
                    return false;
                }
                // root 側 (先頭) が先に来るよう、遡る順とは逆に挿入する。
                links.add(0, link);
                cursor = inner;
                continue;
            }
            if (cursor instanceof NameExpr nameExpr) {
                // var 等の型が取れない変数は、確定 AST の initializer を起点として
                // 遡及を続ける (規則 (i) の「chain 起点」を代入 chain へ拡張)。
                Expression initializer = declaredInitializerOf(nameExpr);
                if (initializer != null) {
                    cursor = initializer;
                    continue;
                }
                // local に該当が無ければ囲み型の bytecode field 型で判定する。
                if (sameNameLocalDeclared(nameExpr)) {
                    return false;
                }
                String fieldType = enclosingBytecodeFieldType(nameExpr.getNameAsString(), enclosingTypeNode);
                if (fieldType == null || declIndex.find(fieldType).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(fieldType, links);
            }
            if (cursor instanceof FieldAccessExpr fieldAccess
                    && fieldAccess.getScope() instanceof ThisExpr) {
                String fieldType = enclosingBytecodeFieldType(fieldAccess.getNameAsString(), enclosingTypeNode);
                if (fieldType == null || declIndex.find(fieldType).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(fieldType, links);
            }
            return false;
        }
        return false;
    }

    /**
     * root 型 (scope 外と確定済み) から、root→現在の call の順に並んだ中間
     * {@code links} を full classpath (project 限定でない) の classfile 情報で
     * 前進検証する。各 link について owner 上の名前・arity が一意な宣言 method を
     * 求め、その戻り値型を次の owner とする。いずれかの link で候補が一意に
     * 求まらない、戻り値型が判明しない、または戻り値型が in-scope と判明した
     * 場合は前進を打ち切り false (診断維持) を返す。全 link を通過できた場合の
     * み true (root が external で、かつ中間区間もすべて external と確認できた)。
     */
    private boolean forwardVerifyExternalChain(String rootType, List<MethodCallExpr> links) {
        String currentOwner = rootType;
        for (MethodCallExpr link : links) {
            var candidate = uniqueDeclaredMethodOnClasspath(
                    currentOwner, link.getNameAsString(), link.getArguments().size());
            if (candidate == null) {
                return false;
            }
            String returnType = candidate.returnType();
            if (returnType == null || isPrimitiveOrVoid(returnType) || returnType.endsWith("[]")) {
                return false;
            }
            // full classpath 経路 (uniqueDeclaredMethodOnClasspath)
            // には project 限定の bytecodeIndex.genericReturnType 相当の generic Signature
            // 読み取りが無く、境界なし type variable の戻り値は descriptor erasure で
            // Object になる。in-scope な実際の型引数を見失ったまま前進すると false
            // exclusion を再発するため、Object を「型変数の疑いあり、根拠不足」として
            // 前進を打ち切る (保守側)。
            if (ERASED_TYPE_VARIABLE_BOUND.equals(returnType)) {
                return false;
            }
            if (declIndex.find(returnType).isPresent()) {
                return false;
            }
            currentOwner = returnType;
        }
        return true;
    }

    /**
     * owner の classfile 上で名前・arity が一意な宣言 method (project 限定でない
     * full classpath、{@code sootUpIndex} 直接参照。継承 member はここでは
     * 対象外 — 見つからなければ前進を打ち切る保守側)。
     */
    private SootUpTypeHierarchyIndex.MethodCandidate uniqueDeclaredMethodOnClasspath(
            String ownerBinaryName, String methodName, int arity) {
        var resolution = sootUpIndex.resolveDeclaredCallableMethods(ownerBinaryName, methodName);
        if (!resolution.isAvailable()) {
            return null;
        }
        var matches = resolution.candidates().stream()
                .filter(candidate -> candidate.parameterTypes().size() == arity)
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** NameExpr の宣言 (resolve または囲み callable 内の一意宣言) の initializer。 */
    private static Expression declaredInitializerOf(NameExpr nameExpr) {
        try {
            ResolvedValueDeclaration value = nameExpr.resolve();
            Node ast = value.toAst().orElse(null);
            if (ast instanceof VariableDeclarator declarator) {
                return declarator.getInitializer().orElse(null);
            }
            return null;
        } catch (RuntimeException | LinkageError e) {
            return uniqueLocalInitializer(nameExpr);
        }
    }

    /**
     * lambda parameter 起点の external 判定 (external 分類規則 (ii))。
     * receiver が lambda parameter で、
     * lambda 自体が代入される変数の宣言型 (= functional interface 型そのもの)
     * が scope 外なら true。scope 内 functional interface / 判定不能は false。
     *
     * <p>lambda を直接 method の引数として渡す形 (受け手 method call の
     * receiver 型を根拠にする案) は撤回した: 受け手 method の receiver 型
     * (例 {@code externalApi.each(...)} の {@code externalApi}) と、その
     * method の functional interface parameter が実際に instantiate される型
     * (lambda parameter の型) は独立した情報であり、前者を後者の根拠にできない
     * (external な receiver を持つ method が in-scope 型を引数に取り得るため、
     * false exclusion の原因になる)。unqualified static import 経由で lambda を
     * 直接引数に渡す形も同じ理由で対象外とする。
     */
    boolean lambdaParamReceiverIsExternal(MethodCallExpr mce) {
        if (!(mce.getScope().orElse(null) instanceof NameExpr name)) {
            return false;
        }
        Node node = mce;
        while ((node = node.getParentNode().orElse(null)) != null) {
            if (!(node instanceof LambdaExpr lambda)) {
                continue;
            }
            boolean declaresReceiver = lambda.getParameters().stream()
                    .anyMatch(parameter -> parameter.getNameAsString().equals(name.getNameAsString()));
            if (!declaresReceiver) {
                continue; // 外側の lambda が宣言している可能性があるため遡上を続ける
            }
            Node parent = lambda.getParentNode().orElse(null);
            if (parent instanceof VariableDeclarator declarator) {
                try {
                    String owner = BinaryNames.erasureOf(declarator.getType().resolve());
                    return declIndex.find(owner).isEmpty();
                } catch (RuntimeException | LinkageError e) {
                    return false;
                }
            }
            return false;
        }
        return false;
    }

    /** 式の静的型 erasure。reference type として解決できなければ null。 */
    private static String tryTypeErasureOf(Expression expression) {
        try {
            ResolvedType type = expression.calculateResolvedType().erasure();
            if (!type.isReferenceType()) {
                return null;
            }
            return BinaryNames.erasureOf(type);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** 救済対象 method call の owner (receiver の static type、暗黙 this は囲み型)。 */
    String bytecodeRescueOwner(MethodCallExpr mce, Node enclosingTypeNode) {
        try {
            if (mce.getScope().isPresent()) {
                ResolvedType scopeType = mce.getScope().get().calculateResolvedType();
                if (!scopeType.isReferenceType()) {
                    return null;
                }
                return BinaryNames.erasureOf(scopeType);
            }
            return enclosingTypeNode != null ? BinaryNames.forTypeLikeNode(enclosingTypeNode) : null;
        } catch (RuntimeException | LinkageError e) {
            // receiver が source に無い bytecode-only field (Lombok logging field 等)
            // の場合、囲み型の bytecode field 型で receiver を補完する。
            return bytecodeFieldReceiverType(mce, enclosingTypeNode);
        }
    }

    /**
     * scope が単純名 / this.field で、囲み型の bytecode-only field なら field 型を返す。
     * 単純名は {@link #sameNameLocalDeclared} が偽の場合だけ field とみなす。
     * この経路は救済 owner の復元に使われ、誤認は偽 edge に倒れうる。
     * this.field 形は shadowing され得ないため検査しない。
     */
    private String bytecodeFieldReceiverType(MethodCallExpr mce, Node enclosingTypeNode) {
        if (mce.getScope().isEmpty() || enclosingTypeNode == null) {
            return null;
        }
        String fieldName = null;
        var scope = mce.getScope().get();
        if (scope instanceof NameExpr nameExpr) {
            if (sameNameLocalDeclared(nameExpr)) {
                return null;
            }
            fieldName = nameExpr.getNameAsString();
        } else if (scope instanceof FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof ThisExpr) {
            fieldName = fieldAccess.getNameAsString();
        }
        if (fieldName == null) {
            return null;
        }
        try {
            String ownerType = BinaryNames.forTypeLikeNode(enclosingTypeNode);
            if (reachableOwners.find(ownerType).isEmpty()) {
                return null;
            }
            return bytecodeIndex.fieldType(ownerType, fieldName).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
