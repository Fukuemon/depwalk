package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;

import java.util.function.IntSupplier;

/**
 * solver が resolve できなかった call site を、bytecode の member へ救済できるか、scope の外の
 * callee と根拠を持って言えるか、どちらでもないかに分類する。
 *
 * <p>個々の判定は {@link BytecodeRescue} が持ち、このクラスは呼び出しの種類ごとに判定を試す順を持つ。
 * 先に試した判定が結果を決めるので、順を入れ替えると edge と diagnostic の分類が変わる。
 */
final class UnresolvedCallClassifier {

    /** 分類の結果。 */
    sealed interface Outcome permits Rescued, External, Unresolved {
    }

    /** bytecode 上の一意な member へ救済できた。 */
    record Rescued(BytecodeRescue.Rescue rescue) implements Outcome {
    }

    /** callee が scope の外にあると classfile の根拠で言える。 */
    record External() implements Outcome {
    }

    /** 救済も external の分類もできず、diagnostic に残す。 */
    record Unresolved() implements Outcome {
    }

    private static final Outcome EXTERNAL = new External();
    private static final Outcome UNRESOLVED = new Unresolved();

    private final BytecodeRescue bytecodeRescue;
    private final WorkspaceSourceDeclarationIndex declIndex;

    /**
     * @param declIndex workspace の source 宣言の索引。owner の型がここに無ければ scope の外とみなす
     */
    UnresolvedCallClassifier(BytecodeRescue bytecodeRescue, WorkspaceSourceDeclarationIndex declIndex) {
        this.bytecodeRescue = bytecodeRescue;
        this.declIndex = declIndex;
    }

    Outcome classifyMethodCall(MethodCallExpr mce, Node enclosingTypeNode) {
        BytecodeRescue.Rescue rescue = bytecodeRescue.methodRescue(mce, enclosingTypeNode);
        if (rescue != null) {
            return new Rescued(rescue);
        }
        // receiver の型を、bytecode の field の型で補っても特定でき、その型が scope の中の source に
        // 無ければ、callee は scope の外にある。例: Lombok の @Slf4j が足す log field の Logger#info。
        String receiverOwner = bytecodeRescue.bytecodeRescueOwner(mce, enclosingTypeNode);
        if (receiverOwner != null && isOutsideScope(receiverOwner)) {
            return EXTERNAL;
        }
        if (receiverOwner == null && mce.getScope().isPresent()) {
            // 解決できなかった receiver の chain を、classfile にある戻り値の型で前に辿って owner を
            // 復元する。根拠のない型の推測はしない。
            String forwardOwner = bytecodeRescue.chainForwardOwner(mce.getScope().get(), enclosingTypeNode);
            if (forwardOwner == null) {
                // erasure だけでは辿れない JDK の stream と collection の連鎖と lambda の parameter は、
                // generic の型を前に導いて owner を復元する。
                forwardOwner = bytecodeRescue.genericChainOwner(mce.getScope().get());
            }
            if (forwardOwner != null) {
                if (isOutsideScope(forwardOwner)) {
                    return EXTERNAL;
                }
                BytecodeRescue.Rescue forwardRescue = bytecodeRescue.methodRescueWithOwner(mce, forwardOwner);
                if (forwardRescue != null) {
                    return new Rescued(forwardRescue);
                }
            }
        }
        // receiver の型が取れなくても、chain の起点の静的型が scope の外にあるか、lambda の parameter が
        // 受ける functional interface の型が scope の外にあれば external に分類する。根拠に scope の中の
        // 型が現れるときは、偽の除外を避けて diagnostic に残す。
        if (receiverOwner == null
                && (bytecodeRescue.chainRootIsExternal(mce, enclosingTypeNode)
                        || bytecodeRescue.lambdaParamReceiverIsExternal(mce))) {
            return EXTERNAL;
        }
        return UNRESOLVED;
    }

    Outcome classifyObjectCreation(ObjectCreationExpr oce) {
        BytecodeRescue.Rescue rescue = bytecodeRescue.constructorRescue(oce);
        return rescue != null ? new Rescued(rescue) : UNRESOLVED;
    }

    /** {@code super(...)} は親の型、{@code this(...)} は囲みの型の constructor を探す。 */
    Outcome classifyExplicitConstructorInvocation(ExplicitConstructorInvocationStmt ecis, Node enclosingTypeNode) {
        BytecodeRescue.Rescue rescue = bytecodeRescue.explicitCtorRescue(ecis, enclosingTypeNode);
        if (rescue != null) {
            return new Rescued(rescue);
        }
        String ctorOwner = bytecodeRescue.explicitCtorOwner(ecis, enclosingTypeNode);
        if (ctorOwner != null && isOutsideScope(ctorOwner)) {
            return EXTERNAL;
        }
        return UNRESOLVED;
    }

    /**
     * @param samArity 参照先の functional interface の SAM の引数の数。救済の候補を選ぶときだけ評価する
     */
    Outcome classifyMethodReference(MethodReferenceExpr mre, IntSupplier samArity) {
        BytecodeRescue.Rescue rescue = bytecodeRescue.methodReferenceRescue(mre, samArity);
        if (rescue != null) {
            return new Rescued(rescue);
        }
        String referenceOwner = bytecodeRescue.methodReferenceOwner(mre);
        if (referenceOwner != null && isOutsideScope(referenceOwner)) {
            return EXTERNAL;
        }
        return UNRESOLVED;
    }

    private boolean isOutsideScope(String ownerBinaryName) {
        return declIndex.find(ownerBinaryName).isEmpty();
    }
}
