package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Processor;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Injects bytecode-only callable members of a class into parsed ASTs before
 * resolution. References that resolve inside an AST (implicit/this-scoped
 * calls, same-file locals, switch selectors) and declarations JavaParser
 * builds directly from a compilation unit never consult the type solver, so
 * {@link MemberAugmentingTypeSolver} cannot reach them; injecting the member
 * declarations into every parsed unit makes JavaParser's own context
 * resolution see them everywhere (argument positions, chained receivers,
 * switch selectors, method-reference targets).
 *
 * <p>Injected declarations are markers for resolution only: they carry the
 * originating bytecode candidate in {@link #INJECTED} so the graph builder can
 * emit calls to them under the bytecode-only member output contract
 * (adr/0005-adopt-sootup-and-spring-di-resolution.md) instead of treating them
 * as source declarations, and skips walking them as callers.
 *
 * <p>The injector performs no symbol resolution itself (types are rendered
 * textually from classfile names), so it is safe to run as a parse
 * post-processor of the solver-internal parser without re-entering the solver.
 */
public final class BytecodeMemberAstInjector {

    /** Marks an injected declaration and carries its bytecode candidate. */
    public static final DataKey<SootUpTypeHierarchyIndex.MethodCandidate> INJECTED = new DataKey<>() {
    };

    // Anonymous / local class parts ($1, $2Local) cannot be spelled in source.
    private static final Pattern UNSPEAKABLE_NAME_PART = Pattern.compile("\\$\\d");

    private final ProjectBytecodeMemberIndex bytecodeIndex;
    // Plain parser (no symbol resolver, no processors) used only to build type ASTs.
    private final JavaParser typeParser = new JavaParser();

    /** @param bytecodeIndex member index over the same analysis context's classes output */
    public BytecodeMemberAstInjector(ProjectBytecodeMemberIndex bytecodeIndex) {
        this.bytecodeIndex = bytecodeIndex;
    }

    /** Registers injection as a parse post-processor of the given configuration. */
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

    /** Injects missing bytecode-only members into every class declared in the unit. */
    public void inject(CompilationUnit cu) {
        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (decl.isInterface()) {
                // Generated members attach to classes (Lombok and friends); interface
                // declarations keep their source members only, matching the solver-side
                // augmentation scope.
                continue;
            }
            try {
                injectInto(decl);
            } catch (RuntimeException e) {
                // Injection is a best-effort resolution aid; a type we cannot process
                // keeps its current behavior (bytecode rescue on the call sites).
            }
        }
    }

    private void injectInto(ClassOrInterfaceDeclaration decl) {
        String binaryName = BinaryNames.forTypeLikeNode(decl);
        List<SootUpTypeHierarchyIndex.MethodCandidate> candidates =
                bytecodeIndex.declaredCallableMethods(binaryName);
        if (candidates.isEmpty()) {
            return;
        }
        Set<String> sourceKeys = new HashSet<>();
        for (MethodDeclaration method : decl.getMethods()) {
            sourceKeys.add(method.getNameAsString() + "/" + method.getParameters().size());
        }
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            if (!sourceKeys.add(candidate.methodName() + "/" + candidate.parameterTypes().size())) {
                continue;
            }
            Optional<Type> returnType = renderReturnType(candidate);
            if (returnType.isEmpty()) {
                continue;
            }
            List<Optional<Type>> parameterTypes = candidate.parameterTypes().stream()
                    .map(this::renderType)
                    .toList();
            if (parameterTypes.stream().anyMatch(Optional::isEmpty)) {
                continue;
            }
            MethodDeclaration method = candidate.isStatic()
                    ? decl.addMethod(candidate.methodName(), Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                    : decl.addMethod(candidate.methodName(), Modifier.Keyword.PUBLIC);
            method.setType(returnType.get());
            for (int i = 0; i < parameterTypes.size(); i++) {
                method.addParameter(parameterTypes.get(i).get(), "arg" + i);
            }
            method.setData(INJECTED, candidate);
        }
    }

    /** Prefers the generic Signature model for the return type, falling back to erasure. */
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
            // Type variables map to their erasure, matching the solver-side synthesis.
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
     * Every type in a compiled classfile's descriptors is on the compile
     * classpath by construction, so no solver lookup is needed here; the only
     * names that cannot materialize in source text are anonymous/local parts.
     */
    private static boolean speakable(String binaryName) {
        return !UNSPEAKABLE_NAME_PART.matcher(binaryName).find();
    }

    private static String sourceName(String binaryName) {
        return binaryName.replace('$', '.');
    }
}
