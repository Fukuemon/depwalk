package com.fukuemon.depwalk.javaanalyzer.analysis.completeness;

import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import java.util.List;
import java.util.Optional;

/**
 * scope 内 source type の bytecode-only callable member を、source call site
 * からの照会に対して generator 非依存に解決する索引
 * (adr/0005-adopt-sootup-and-spring-di-resolution.md)。
 * 所有 context の {@link SootUpTypeHierarchyIndex} (lazy) へ委譲し、annotation
 * 名や generator 名では一切分岐しない。JVM 内部 member (bridge / synthetic /
 * {@code lambda$...} / {@code access$...} / {@code <clinit>}) は候補にしない。
 */
public final class ProjectBytecodeMemberIndex {

    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final com.fukuemon.depwalk.javaanalyzer.analysis.augment.GenericSignatureReader signatureReader;
    private final List<java.nio.file.Path> projectOutputDirs;
    private final java.util.Map<String, Boolean> projectOutputCache = new java.util.HashMap<>();
    private final java.util.Map<String, List<SootUpTypeHierarchyIndex.MethodCandidate>> declaredCache =
            new java.util.HashMap<>();

    /**
     * project 所有の classes output を持たない索引を作る (origin 検証で
     * 常に救済対象外となる)。
     *
     * @param sootUpIndex 所有 context の型階層索引
     */
    public ProjectBytecodeMemberIndex(SootUpTypeHierarchyIndex sootUpIndex) {
        this(sootUpIndex, List.of());
    }

    /**
     * 型階層索引と project 所有の classes output を与えて索引を作る。
     *
     * @param sootUpIndex 所有 context の型階層索引
     * @param classesOutputDirs この context から見える project 所有の classes
     *     output。member 救済の origin 検証と generic Signature 属性の読み取り
     *     (java-analyzer feature doc「solver 層の bytecode member 合成」) に使う
     */
    public ProjectBytecodeMemberIndex(
            SootUpTypeHierarchyIndex sootUpIndex, List<java.nio.file.Path> classesOutputDirs) {
        this.sootUpIndex = sootUpIndex;
        this.projectOutputDirs = List.copyOf(classesOutputDirs);
        this.signatureReader =
                new com.fukuemon.depwalk.javaanalyzer.analysis.augment.GenericSignatureReader(classesOutputDirs);
    }

    /**
     * owner class の classfile が project 所有の classes output に存在するかを
     * 検証する (feature doc「solver 層の bytecode member 合成」)。
     * external artifact だけに存在する同名 class の member を
     * project bytecode として救済しない。
     */
    private boolean inProjectOutput(String ownerBinaryName) {
        return projectOutputCache.computeIfAbsent(ownerBinaryName, name -> {
            String relative = name.replace('.', '/') + ".class";
            for (java.nio.file.Path dir : projectOutputDirs) {
                if (java.nio.file.Files.isRegularFile(dir.resolve(relative))) {
                    return true;
                }
            }
            return false;
        });
    }

    /** 合成 member の generic 戻り値型 (Signature 属性が無ければ empty)。 */
    public java.util.Optional<com.fukuemon.depwalk.javaanalyzer.analysis.augment.GenericSignatureReader.BytecodeType>
            genericReturnType(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        return signatureReader.genericReturnType(
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes());
    }

    /**
     * 所有 class に同名・同 arity の callable method が一意に存在する場合だけ
     * その candidate を返す (曖昧なら救済しない)。
     */
    public Optional<SootUpTypeHierarchyIndex.MethodCandidate> uniqueMethod(
            String ownerBinaryName, String methodName, int arity) {
        if (isJvmInternalName(methodName) || !inProjectOutput(ownerBinaryName)) {
            return Optional.empty();
        }
        SootUpTypeHierarchyIndex.Resolution resolution =
                sootUpIndex.resolveDeclaredCallableMethods(ownerBinaryName, methodName);
        return uniqueByArity(resolution, arity);
    }

    /** 所有 class に同 arity の constructor が一意に存在する場合だけ返す。 */
    public Optional<SootUpTypeHierarchyIndex.MethodCandidate> uniqueConstructor(
            String ownerBinaryName, int arity) {
        if (!inProjectOutput(ownerBinaryName)) {
            return Optional.empty();
        }
        SootUpTypeHierarchyIndex.Resolution resolution = sootUpIndex.resolveConstructors(ownerBinaryName);
        return uniqueByArity(resolution, arity);
    }

    /** 所有 class 自身の全 callable method (JVM 内部 member 除外)。合成候補の列挙用。 */
    public List<SootUpTypeHierarchyIndex.MethodCandidate> declaredCallableMethods(String ownerBinaryName) {
        return declaredCache.computeIfAbsent(ownerBinaryName, this::declaredCallableMethodsUncached);
    }

    private List<SootUpTypeHierarchyIndex.MethodCandidate> declaredCallableMethodsUncached(String ownerBinaryName) {
        if (!inProjectOutput(ownerBinaryName)) {
            return List.of();
        }
        SootUpTypeHierarchyIndex.Resolution resolution = sootUpIndex.resolveDeclaredCallableMethods(ownerBinaryName);
        if (!resolution.isAvailable()) {
            return List.of();
        }
        return resolution.candidates().stream()
                .filter(candidate -> !isJvmInternalName(candidate.methodName()))
                .filter(candidate -> !"<init>".equals(candidate.methodName()))
                .toList();
    }

    /** bytecode-only field の型を receiver 解決の補完としてだけ返す (node 化しない)。 */
    public Optional<String> fieldType(String ownerBinaryName, String fieldName) {
        if (!inProjectOutput(ownerBinaryName)) {
            return Optional.empty();
        }
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
