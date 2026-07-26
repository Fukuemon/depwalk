package com.fukuemon.depwalk.javaanalyzer.analysis.augment;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * project classes output の class file から method の generic Signature 属性を
 * 読み取り、合成 member の実型引数を復元する
 * (java-analyzer feature doc「solver 層の bytecode member 合成」)。読み取りは
 * class 単位で lazy に行い、失敗した class は「generic 情報なし」として扱う
 * (erasure へ degrade し、解析を失敗させない)。
 */
public final class GenericSignatureReader {

    /**
     * bytecode 上の型表現。型変数は名前だけを保持し、解決側で erasure
     * (Object) へ写像する (型変数の自己写像による無限再帰を避ける)。
     *
     * @param binaryName 参照型 / primitive の binary name。型変数なら変数名
     * @param typeArguments 実型引数 (raw なら空)
     * @param arrayDims 配列次元
     * @param typeVariable 型変数かどうか
     */
    public record BytecodeType(
            String binaryName, List<BytecodeType> typeArguments, int arrayDims, boolean typeVariable) {

        static BytecodeType reference(String binaryName, List<BytecodeType> args, int dims) {
            return new BytecodeType(binaryName, List.copyOf(args), dims, false);
        }

        static BytecodeType variable(String name) {
            return new BytecodeType(name, List.of(), 0, true);
        }
    }

    private final List<Path> classesOutputDirs;
    private final Map<String, Map<String, BytecodeType>> cache = new HashMap<>();

    /** @param classesOutputDirs 探索順に並んだ classes output directory (先に一致した class file を使う) */
    public GenericSignatureReader(List<Path> classesOutputDirs) {
        this.classesOutputDirs = List.copyOf(classesOutputDirs);
    }

    /**
     * method の generic 戻り値型を返す。Signature 属性が無い・読めない場合は
     * empty (呼び出し側は erasure を使う)。
     *
     * @param erasedParameterTypes erasure 済み引数型 (overload 識別に使うため宣言順)
     */
    public Optional<BytecodeType> genericReturnType(
            String ownerBinaryName, String methodName, List<String> erasedParameterTypes) {
        Map<String, BytecodeType> methods =
                cache.computeIfAbsent(ownerBinaryName, this::readClassSignatures);
        return Optional.ofNullable(methods.get(methodKey(methodName, erasedParameterTypes)));
    }

    private static String methodKey(String name, List<String> erasedParameterTypes) {
        return name + "(" + String.join(",", erasedParameterTypes) + ")";
    }

    private Map<String, BytecodeType> readClassSignatures(String ownerBinaryName) {
        Path classFile = locateClassFile(ownerBinaryName);
        if (classFile == null) {
            return Map.of();
        }
        Map<String, BytecodeType> methods = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (signature == null) {
                        return null;
                    }
                    List<String> erasedParams = new ArrayList<>();
                    for (Type argument : Type.getArgumentTypes(descriptor)) {
                        erasedParams.add(argument.getClassName());
                    }
                    parseReturnType(signature)
                            .ifPresent(type -> methods.putIfAbsent(methodKey(name, erasedParams), type));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
        return methods;
    }

    private Path locateClassFile(String ownerBinaryName) {
        String relative = ownerBinaryName.replace('.', '/') + ".class";
        for (Path dir : classesOutputDirs) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** method signature の戻り値部分だけを {@link BytecodeType} へ変換する。 */
    static Optional<BytecodeType> parseReturnType(String methodSignature) {
        try {
            TypeBuilder builder = new TypeBuilder();
            new SignatureReader(methodSignature).accept(new SignatureVisitor(Opcodes.ASM9) {
                @Override
                public SignatureVisitor visitReturnType() {
                    return builder;
                }
            });
            return Optional.ofNullable(builder.build());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** SignatureVisitor で 1 つの型式を組み立てる (型引数は入れ子 builder)。 */
    private static final class TypeBuilder extends SignatureVisitor {

        private String binaryName;
        private boolean typeVariable;
        private int arrayDims;
        // 宣言順を保つため wildcard / bounded を同じ列で管理する。
        private final List<java.util.function.Supplier<BytecodeType>> argumentSources = new ArrayList<>();

        private TypeBuilder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitBaseType(char descriptor) {
            binaryName = Type.getType(String.valueOf(descriptor)).getClassName();
        }

        @Override
        public void visitTypeVariable(String name) {
            binaryName = name;
            typeVariable = true;
        }

        @Override
        public SignatureVisitor visitArrayType() {
            arrayDims++;
            return this;
        }

        @Override
        public void visitClassType(String name) {
            binaryName = name.replace('/', '.');
        }

        @Override
        public void visitInnerClassType(String name) {
            binaryName = binaryName + "$" + name;
            // outer 型の型引数を inner の引数と混合しない。outer 引数は破棄し、
            // 不足分は解決側の Object 補正へ委ねる (混合より erasure 側へ倒す)。
            argumentSources.clear();
        }

        @Override
        public void visitTypeArgument() {
            // unbounded wildcard (?): erasure と同じく Object へ写像する。
            argumentSources.add(() -> BytecodeType.reference("java.lang.Object", List.of(), 0));
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            TypeBuilder argument = new TypeBuilder();
            argumentSources.add(argument::build);
            return argument;
        }

        BytecodeType build() {
            if (binaryName == null) {
                return null;
            }
            if (typeVariable) {
                return BytecodeType.variable(binaryName);
            }
            List<BytecodeType> arguments = new ArrayList<>();
            for (var source : argumentSources) {
                BytecodeType built = source.get();
                arguments.add(built != null ? built : BytecodeType.reference("java.lang.Object", List.of(), 0));
            }
            return BytecodeType.reference(binaryName, arguments, arrayDims);
        }
    }
}
