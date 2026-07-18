package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import java.util.List;
import java.util.Optional;

/**
 * scope 内 source type の bytecode-only callable member を、source call site
 * からの照会に対して generator 非依存に解決する索引 (spec #24 D18)。
 * 所有 context の {@link SootUpTypeHierarchyIndex} (lazy) へ委譲し、annotation
 * 名や generator 名では一切分岐しない。JVM 内部 member (bridge / synthetic /
 * {@code lambda$...} / {@code access$...} / {@code <clinit>}) は候補にしない。
 */
public final class ProjectBytecodeMemberIndex {

    private final SootUpTypeHierarchyIndex sootUpIndex;

    public ProjectBytecodeMemberIndex(SootUpTypeHierarchyIndex sootUpIndex) {
        this.sootUpIndex = sootUpIndex;
    }

    /**
     * 所有 class に同名・同 arity の callable method が一意に存在する場合だけ
     * その candidate を返す (曖昧なら救済しない)。
     */
    public Optional<SootUpTypeHierarchyIndex.MethodCandidate> uniqueMethod(
            String ownerBinaryName, String methodName, int arity) {
        if (isJvmInternalName(methodName)) {
            return Optional.empty();
        }
        SootUpTypeHierarchyIndex.Resolution resolution =
                sootUpIndex.resolveDeclaredCallableMethods(ownerBinaryName, methodName);
        return uniqueByArity(resolution, arity);
    }

    /** 所有 class に同 arity の constructor が一意に存在する場合だけ返す。 */
    public Optional<SootUpTypeHierarchyIndex.MethodCandidate> uniqueConstructor(
            String ownerBinaryName, int arity) {
        SootUpTypeHierarchyIndex.Resolution resolution = sootUpIndex.resolveConstructors(ownerBinaryName);
        return uniqueByArity(resolution, arity);
    }

    /** bytecode-only field の型を receiver 解決の補完としてだけ返す (node 化しない)。 */
    public Optional<String> fieldType(String ownerBinaryName, String fieldName) {
        return sootUpIndex.resolveDeclaredFieldType(ownerBinaryName, fieldName);
    }

    private static Optional<SootUpTypeHierarchyIndex.MethodCandidate> uniqueByArity(
            SootUpTypeHierarchyIndex.Resolution resolution, int arity) {
        if (!resolution.isAvailable()) {
            return Optional.empty();
        }
        List<SootUpTypeHierarchyIndex.MethodCandidate> matches = resolution.candidates().stream()
                .filter(candidate -> candidate.parameterTypes().size() == arity)
                .filter(candidate -> !isJvmInternalName(candidate.methodName()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static boolean isJvmInternalName(String methodName) {
        return methodName.startsWith("lambda$")
                || methodName.startsWith("access$")
                || methodName.equals("<clinit>");
    }
}
