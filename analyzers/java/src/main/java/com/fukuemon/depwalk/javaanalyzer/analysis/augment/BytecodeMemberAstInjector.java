package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Processor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * class の bytecode-only callable member を、parse 済み AST へ解決前に注入する。
 * AST 内で完結する参照 (this / 暗黙 scope の呼び出し、同一 file の local receiver、
 * switch selector) と、solver 内部で parse した compilation unit から直接構築される
 * 宣言は TypeSolver を経由しないため、{@link MemberAugmentingTypeSolver} の合成が
 * 届かない。member 宣言を AST 自体へ注入すると、JavaParser の context 解決が
 * どの位置 (引数・chain receiver・switch selector・method reference 先) でも
 * 生成 member を見られる。契約の正本は java-analyzer feature doc
 * 「solver 層の bytecode member 合成」の AST 注入節。
 *
 * <p>注入宣言は解決専用の標識であり source 宣言として扱わない: 由来の bytecode
 * candidate を {@link #INJECTED} で保持し、graph builder は注入 member への呼び出しを
 * bytecode-only member の出力契約 (adr/0005-adopt-sootup-and-spring-di-resolution.md)
 * で emit し、caller としては walk しない。
 *
 * <p>注入時に型解決は行わない (型は classfile 上の名前をテキストとして書き下す)。
 * solver 内部 parser の post-processor として動かしても solver に再入しないための
 * 制約であり、resolvability の検査を持たないのはこのためである。
 */
public final class BytecodeMemberAstInjector {

    /** 注入宣言の標識。由来の bytecode candidate を保持する。 */
    public static final DataKey<SootUpTypeHierarchyIndex.MethodCandidate> INJECTED = new DataKey<>() {
    };

    // 匿名・local class の名前部 ($ + 数字) は source に書けない。
    private static final Pattern UNSPEAKABLE_NAME_PART = Pattern.compile("\\$\\d");

    private final ProjectBytecodeMemberIndex bytecodeIndex;
    // 型 AST の構築専用 parser。symbol resolver / processor を持たせない。
    private final JavaParser typeParser = new JavaParser();

    /** @param bytecodeIndex 同一解析 context の classes output を引く member 索引 */
    public BytecodeMemberAstInjector(ProjectBytecodeMemberIndex bytecodeIndex) {
        this.bytecodeIndex = Objects.requireNonNull(bytecodeIndex, "bytecodeIndex");
    }

    /** 注入を parser 構成の parse post-processor として登録する。 */
    public void installInto(ParserConfiguration configuration) {
        configuration.getProcessors().add(() -> new Processor() {
            @Override
            public void postProcess(ParseResult<? extends Node> result, ParserConfiguration config) {
                result.getResult().ifPresent(node -> {
                    if (node instanceof CompilationUnit cu) {
                        inject(cu);
                    }
                });
            }
        });
    }

    /** unit 内の全 class 宣言へ、source に無い bytecode-only member を注入する。 */
    public void inject(CompilationUnit cu) {
        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (decl.isInterface()) {
                // 生成 member が付くのは class (Lombok 等)。interface / enum / record は
                // solver 側合成 (JavaParserClassDeclaration 限定) と同じ範囲で対象外とし、
                // source 宣言のままにする。
                continue;
            }
            injectInto(decl);
        }
    }

    private void injectInto(ClassOrInterfaceDeclaration decl) {
        String binaryName = BinaryNames.forTypeLikeNode(decl);
        if (!speakable(binaryName)) {
            // 匿名・local class は member を注入しても source から参照できる名前を持たない。
            return;
        }
        List<SootUpTypeHierarchyIndex.MethodCandidate> candidates =
                bytecodeIndex.declaredCallableMethods(binaryName);
        if (candidates.isEmpty()) {
            return;
        }
        Set<String> sourceKeys = new HashSet<>();
        for (MethodDeclaration method : decl.getMethods()) {
            sourceKeys.add(method.getNameAsString() + "/" + method.getParameters().size());
        }
        // 同名・同 arity が bytecode 上に複数ある member は注入しない (曖昧なら
        // 救済しない一意性規則。adr/0005-adopt-sootup-and-spring-di-resolution.md)。
        Map<String, Integer> arityCounts = new HashMap<>();
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            arityCounts.merge(candidateKey(candidate), 1, Integer::sum);
        }
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            if (arityCounts.get(candidateKey(candidate)) != 1 || !sourceKeys.add(candidateKey(candidate))) {
                continue;
            }
            try {
                injectCandidate(decl, candidate);
            } catch (RuntimeException e) {
                // 注入は best-effort の解決補助であり、扱えない candidate は従来の
                // 救済経路 (call site 側の bytecode rescue) に委ねる。candidate 単位で
                // 握るのは、1 件の失敗で class の他 member の注入まで落とさないため。
            }
        }
    }

    private void injectCandidate(
            ClassOrInterfaceDeclaration decl, SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        Optional<Type> returnType = renderReturnType(candidate);
        if (returnType.isEmpty()) {
            return;
        }
        List<Optional<Type>> parameterTypes = candidate.parameterTypes().stream()
                .map(this::renderType)
                .toList();
        if (parameterTypes.stream().anyMatch(Optional::isEmpty)) {
            return;
        }
        // 可視性は bytecode candidate に載らないため public へ寄せる (解決専用の標識
        // であり、可視性違反の source は元々コンパイルできないので偽 edge は作らない)。
        MethodDeclaration method = candidate.isStatic()
                ? decl.addMethod(candidate.methodName(), Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                : decl.addMethod(candidate.methodName(), Modifier.Keyword.PUBLIC);
        method.setType(returnType.get());
        for (int i = 0; i < parameterTypes.size(); i++) {
            method.addParameter(parameterTypes.get(i).get(), "arg" + i);
        }
        method.setData(INJECTED, candidate);
    }

    private static String candidateKey(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        return candidate.methodName() + "/" + candidate.parameterTypes().size();
    }

    /**
     * 戻り値は generic Signature 由来を優先し、無ければ erasure へ degrade する
     * (solver 側合成と同じ規則)。Signature 上の wildcard は境界の型として書き
     * 下される (overload 選択に使われない戻り値位置に限るため許容する)。
     */
    private Optional<Type> renderReturnType(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        Optional<GenericSignatureReader.BytecodeType> generic = bytecodeIndex.genericReturnType(candidate);
        if (generic.isPresent()) {
            Optional<Type> rendered = genericModelText(generic.get()).flatMap(this::parseType);
            if (rendered.isPresent()) {
                return rendered;
            }
        }
        return renderType(candidate.returnType());
    }

    private Optional<String> genericModelText(GenericSignatureReader.BytecodeType model) {
        if (model.typeVariable()) {
            // 型変数は erasure へ写像する (solver 側合成と同じ)。
            return Optional.of("java.lang.Object");
        }
        if (!speakable(model.binaryName())) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder(sourceName(model.binaryName()));
        if (!model.typeArguments().isEmpty()) {
            text.append('<');
            for (int i = 0; i < model.typeArguments().size(); i++) {
                Optional<String> argument = genericModelText(model.typeArguments().get(i));
                if (argument.isEmpty()) {
                    return Optional.empty();
                }
                if (i > 0) {
                    text.append(", ");
                }
                text.append(argument.get());
            }
            text.append('>');
        }
        text.append("[]".repeat(model.arrayDims()));
        return Optional.of(text.toString());
    }

    private Optional<Type> renderType(String erasureBinaryName) {
        if (!speakable(erasureBinaryName)) {
            return Optional.empty();
        }
        return parseType(sourceName(erasureBinaryName));
    }

    private Optional<Type> parseType(String text) {
        ParseResult<Type> result = typeParser.parseType(text);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return Optional.of(result.getResult().get());
        }
        return Optional.empty();
    }

    /**
     * classfile の descriptor / Signature に現れる型は compile classpath 上に
     * 存在するため、注入時の解決検査は要らない。source テキストに書けないのは
     * 匿名・local class の名前部だけであり、それだけを弾く。
     */
    private static boolean speakable(String binaryName) {
        return !UNSPEAKABLE_NAME_PART.matcher(binaryName).find();
    }

    private static String sourceName(String binaryName) {
        return binaryName.replace('$', '.');
    }
}
