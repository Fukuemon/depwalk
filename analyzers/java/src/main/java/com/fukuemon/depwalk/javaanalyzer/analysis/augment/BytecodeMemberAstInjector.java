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
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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

    /** unit 内の class / enum 宣言へ、source に無い bytecode-only member を注入する。 */
    public void inject(CompilationUnit cu) {
        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (decl.isInterface()) {
                // interface に生成 member は付かない (Lombok 等の対象は class / enum)。
                // record は accessor が言語仕様で暗黙宣言されるため対象外。
                continue;
            }
            injectInto(decl);
            injectConstructorsInto(decl);
        }
        for (EnumDeclaration decl : cu.findAll(EnumDeclaration.class)) {
            // enum 定数の getter (@Getter 付き enum) を救済する。constructor は enum
            // 内部からしか呼べず call site 解決に寄与しないため注入しない。
            injectInto(decl);
        }
    }

    private void injectInto(TypeDeclaration<?> decl) {
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
        if (decl instanceof EnumDeclaration) {
            // values() / valueOf(String) は言語仕様の暗黙宣言で、注入すると JavaParser の
            // 暗黙解決と二重になる。
            sourceKeys.add("values/0");
            sourceKeys.add("valueOf/1");
        }
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
            TypeDeclaration<?> decl, SootUpTypeHierarchyIndex.MethodCandidate candidate) {
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
     * source に無い bytecode-only constructor (@AllArgsConstructor 等の生成
     * constructor) を注入する。source に constructor が 1 つでも書かれると暗黙
     * default constructor が消える言語規則と同じく、bytecode の constructor 集合を
     * 正として同 arity の source 宣言が無いものだけを足す。同 arity が bytecode 上に
     * 複数ある形は曖昧として注入しない (member と同じ一意性規則)。
     */
    private void injectConstructorsInto(ClassOrInterfaceDeclaration decl) {
        String binaryName = BinaryNames.forTypeLikeNode(decl);
        if (!speakable(binaryName)) {
            return;
        }
        List<SootUpTypeHierarchyIndex.MethodCandidate> candidates =
                bytecodeIndex.declaredConstructors(binaryName);
        if (candidates.isEmpty()) {
            return;
        }
        Set<Integer> sourceArities = new HashSet<>();
        for (ConstructorDeclaration constructor : decl.getConstructors()) {
            sourceArities.add(constructor.getParameters().size());
        }
        Map<Integer, Integer> arityCounts = new HashMap<>();
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            arityCounts.merge(candidate.parameterTypes().size(), 1, Integer::sum);
        }
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            int arity = candidate.parameterTypes().size();
            if (arityCounts.get(arity) != 1 || !sourceArities.add(arity)) {
                continue;
            }
            try {
                injectConstructor(decl, candidate);
            } catch (RuntimeException e) {
                // member 注入と同じ best-effort。失敗した candidate は既存の
                // constructor 救済経路に委ねる。
            }
        }
    }

    private void injectConstructor(
            ClassOrInterfaceDeclaration decl, SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        List<Optional<Type>> parameterTypes = candidate.parameterTypes().stream()
                .map(this::renderType)
                .toList();
        if (parameterTypes.stream().anyMatch(Optional::isEmpty)) {
            return;
        }
        ConstructorDeclaration constructor = new ConstructorDeclaration();
        constructor.setName(decl.getNameAsString());
        constructor.addModifier(Modifier.Keyword.PUBLIC);
        for (int i = 0; i < parameterTypes.size(); i++) {
            constructor.addParameter(parameterTypes.get(i).get(), "arg" + i);
        }
        constructor.setData(INJECTED, candidate);
        decl.addMember(constructor);
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
