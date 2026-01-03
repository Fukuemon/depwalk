package com.fukuemon.depwalk;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HelperMain {
    private final Gson gson = new Gson();
    private final JavaParser parser;
    private final Map<String, CompilationUnit> cuCache = new HashMap<>();
    private final List<String> sourceRoots;

    public HelperMain(String classpath, List<String> sourceRoots) {
        this.sourceRoots = sourceRoots;
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        // Add source roots
        for (String root : sourceRoots) {
            File dir = new File(root);
            if (dir.exists() && dir.isDirectory()) {
                typeSolver.add(new JavaParserTypeSolver(dir));
            }
        }

        // Add jars from classpath
        if (classpath != null && !classpath.isEmpty()) {
            for (String path : classpath.split(File.pathSeparator)) {
                File f = new File(path);
                if (f.exists() && f.getName().endsWith(".jar")) {
                    try {
                        typeSolver.add(new JarTypeSolver(f));
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to add jar: " + path);
                    }
                }
            }
        }

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        this.parser = new JavaParser(config);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: HelperMain <classpath> <sourceRoot1> [sourceRoot2 ...]");
            System.exit(1);
        }

        String classpath = args[0];
        List<String> sourceRoots = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));

        HelperMain helper = new HelperMain(classpath, sourceRoots);
        helper.run();
    }

    private void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                JsonObject request = gson.fromJson(line, JsonObject.class);
                JsonObject response = processRequest(request);
                writer.println(gson.toJson(response));
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        }
    }

    private JsonObject processRequest(JsonObject request) {
        String op = request.get("op").getAsString();

        try {
            switch (op) {
                case "resolveDecl":
                    return resolveDecl(request);
                case "resolveCalls":
                    return resolveCalls(request);
                case "shutdown":
                    System.exit(0);
                    return null;
                default:
                    return errorResponse("Unknown operation: " + op);
            }
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    private JsonObject resolveDecl(JsonObject request) {
        String file = request.get("file").getAsString();
        int startByte = request.get("startByte").getAsInt();
        int endByte = request.get("endByte").getAsInt();

        try {
            CompilationUnit cu = getOrParse(file);
            if (cu == null) {
                return errorResponse("Failed to parse file: " + file);
            }

            // Find the node at the given byte range
            Optional<Node> nodeOpt = findNodeByByteRange(cu, file, startByte, endByte);
            if (nodeOpt.isEmpty()) {
                return errorResponse("No node found at byte range [" + startByte + ":" + endByte + "]");
            }

            Node node = nodeOpt.get();
            String methodId = resolveMethodId(node);

            if (methodId == null) {
                return errorResponse("Could not resolve method declaration");
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("methodId", methodId);
            return result;
        } catch (Exception e) {
            return errorResponse("resolveDecl failed: " + e.getMessage());
        }
    }

    private JsonObject resolveCalls(JsonObject request) {
        JsonArray calls = request.getAsJsonArray("calls");
        JsonArray results = new JsonArray();

        for (int i = 0; i < calls.size(); i++) {
            JsonObject call = calls.get(i).getAsJsonObject();
            String file = call.get("file").getAsString();
            int startByte = call.get("startByte").getAsInt();
            int endByte = call.get("endByte").getAsInt();

            JsonObject enclosing = call.getAsJsonObject("enclosingMethodDeclRange");
            int enclosingStart = enclosing.get("startByte").getAsInt();
            int enclosingEnd = enclosing.get("endByte").getAsInt();

            JsonObject result = new JsonObject();
            result.addProperty("file", file);
            result.addProperty("startByte", startByte);
            result.addProperty("endByte", endByte);

            try {
                CompilationUnit cu = getOrParse(file);
                if (cu == null) {
                    result.addProperty("calleeMethodId", "(unresolved)");
                    result.addProperty("callerMethodId", "(unresolved)");
                    results.add(result);
                    continue;
                }

                // Resolve caller (enclosing method)
                Optional<Node> callerNodeOpt = findNodeByByteRange(cu, file, enclosingStart, enclosingEnd);
                String callerMethodId = "(unresolved)";
                if (callerNodeOpt.isPresent()) {
                    String resolved = resolveMethodId(callerNodeOpt.get());
                    if (resolved != null) {
                        callerMethodId = resolved;
                    }
                }

                // Resolve callee (call expression)
                Optional<Node> callNodeOpt = findNodeByByteRange(cu, file, startByte, endByte);
                String calleeMethodId = "(unresolved)";
                if (callNodeOpt.isPresent()) {
                    Node callNode = callNodeOpt.get();
                    calleeMethodId = resolveCallTarget(callNode);
                }

                result.addProperty("calleeMethodId", calleeMethodId);
                result.addProperty("callerMethodId", callerMethodId);
            } catch (Exception e) {
                result.addProperty("calleeMethodId", "(unresolved)");
                result.addProperty("callerMethodId", "(unresolved)");
            }

            results.add(result);
        }

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.add("results", results);
        return response;
    }

    private CompilationUnit getOrParse(String file) throws IOException {
        if (cuCache.containsKey(file)) {
            return cuCache.get(file);
        }

        Path path = Path.of(file);
        if (!Files.exists(path)) {
            return null;
        }

        ParseResult<CompilationUnit> result = parser.parse(path);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return null;
        }

        CompilationUnit cu = result.getResult().get();
        cuCache.put(file, cu);
        return cu;
    }

    private Optional<Node> findNodeByByteRange(CompilationUnit cu, String file, int startByte, int endByte) throws IOException {
        // Convert byte offsets to line/column using the source content
        String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        int[] startPos = byteOffsetToLineCol(content, startByte);
        int[] endPos = byteOffsetToLineCol(content, endByte);

        // Find nodes at the position
        return cu.findAll(Node.class).stream()
                .filter(n -> n.getRange().isPresent())
                .filter(n -> {
                    var range = n.getRange().get();
                    return range.begin.line == startPos[0] && range.begin.column == startPos[1];
                })
                .filter(n -> n instanceof MethodDeclaration || n instanceof ConstructorDeclaration ||
                        n instanceof MethodCallExpr || n instanceof ObjectCreationExpr)
                .findFirst();
    }

    private int[] byteOffsetToLineCol(String content, int byteOffset) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int line = 1;
        int col = 1;
        int currentByte = 0;

        for (int i = 0; i < content.length() && currentByte < byteOffset; i++) {
            char c = content.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;

            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            currentByte += charBytes;
        }

        return new int[]{line, col};
    }

    private String resolveMethodId(Node node) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration md = (MethodDeclaration) node;
            try {
                ResolvedMethodDeclaration resolved = md.resolve();
                return formatMethodId(resolved);
            } catch (Exception e) {
                return null;
            }
        } else if (node instanceof ConstructorDeclaration) {
            ConstructorDeclaration cd = (ConstructorDeclaration) node;
            try {
                ResolvedConstructorDeclaration resolved = cd.resolve();
                return formatConstructorId(resolved);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String resolveCallTarget(Node node) {
        if (node instanceof MethodCallExpr) {
            MethodCallExpr mce = (MethodCallExpr) node;
            try {
                ResolvedMethodDeclaration resolved = mce.resolve();
                return formatMethodId(resolved);
            } catch (Exception e) {
                return "(unresolved)";
            }
        } else if (node instanceof ObjectCreationExpr) {
            ObjectCreationExpr oce = (ObjectCreationExpr) node;
            try {
                ResolvedConstructorDeclaration resolved = oce.resolve();
                return formatConstructorId(resolved);
            } catch (Exception e) {
                return "(unresolved)";
            }
        }
        return "(unresolved)";
    }

    private String formatMethodId(ResolvedMethodDeclaration resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolved.declaringType().getQualifiedName());
        sb.append("#");
        sb.append(resolved.getName());
        sb.append("(");

        int paramCount = resolved.getNumberOfParams();
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) sb.append(",");
            try {
                sb.append(resolved.getParam(i).getType().describe());
            } catch (Exception e) {
                sb.append("?");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    private String formatConstructorId(ResolvedConstructorDeclaration resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolved.declaringType().getQualifiedName());
        sb.append("#<init>(");

        int paramCount = resolved.getNumberOfParams();
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) sb.append(",");
            try {
                sb.append(resolved.getParam(i).getType().describe());
            } catch (Exception e) {
                sb.append("?");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    private JsonObject errorResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message);
        return response;
    }
}
