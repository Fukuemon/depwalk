package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.GenericSignatureReader;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JavaParser の generic 推論が失敗した式の型を、確定した根拠だけで前進導出する。
 * 根拠は次の 3 つに限定し、推測による型付けは行わない。
 *
 * <ul>
 * <li>AST に書かれた宣言型 (local / parameter の明示型、`var` は initializer を辿る)</li>
 * <li>project classes output の classfile Signature (生成 member の generic 戻り値)</li>
 * <li>JDK コレクション / Stream / Optional / Map の宣言済み generic 意味論
 *     (classfile Signature と等価な情報の固定表)</li>
 * </ul>
 *
 * <p>lambda parameter の型は、lambda を受ける呼び出しの receiver 要素型から導出する。
 * 導出できない式は null を返し、呼び出し側が従来の分類 (diagnostic) に残す。
 */
final class GenericChainTypes {

    /** erasure binary name と型引数の入れ子。raw / 不明の型引数は持たない。 */
    record Model(String binaryName, List<Model> args) {
        static final Model OBJECT = new Model("java.lang.Object", List.of());

        Model arg(int index) {
            return index < args.size() ? args.get(index) : OBJECT;
        }
    }

    private static final int MAX_DEPTH = 24;

    private static final String COLLECTORS = "java.util.stream.Collectors";

    private static final Set<String> COLLECTION_LIKE = Set.of(
            "java.lang.Iterable", "java.util.Collection", "java.util.List", "java.util.Set",
            "java.util.SortedSet", "java.util.NavigableSet", "java.util.Queue", "java.util.Deque",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet",
            "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.ArrayDeque");

    private static final Set<String> LIST_LIKE = Set.of(
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList");

    private static final Set<String> DEQUE_LIKE = Set.of(
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
            "java.util.Deque", "java.util.ArrayDeque");

    // 固定表の意味論を適用する Map 型の明示列挙。名前 pattern (java.util.* かつ *Map) の
    // 判定は、意味論を確認していない型まで意図せず対象へ含めるため採らない。
    private static final Set<String> MAP_LIKE = Set.of(
            "java.util.Map", "java.util.SortedMap", "java.util.NavigableMap",
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
            "java.util.EnumMap", "java.util.WeakHashMap", "java.util.IdentityHashMap",
            "java.util.Hashtable", "java.util.concurrent.ConcurrentMap",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentNavigableMap",
            "java.util.concurrent.ConcurrentSkipListMap");

    private static final Set<String> STREAM_ELEMENT_PRESERVING = Set.of(
            "filter", "peek", "distinct", "sorted", "limit", "skip", "takeWhile", "dropWhile",
            "sequential", "parallel", "unordered");

    // 単一引数 functional の入力が receiver の要素型である JDK member (toArray 等の
    // 要素型でない functional を誤って含めないための白リスト)。
    private static final Set<String> ELEMENT_INPUT_FUNCTIONALS = Set.of(
            "map", "flatMap", "filter", "peek", "forEach", "forEachOrdered", "anyMatch",
            "allMatch", "noneMatch", "removeIf", "takeWhile", "dropWhile", "ifPresent");

    private final ProjectBytecodeMemberIndex bytecodeIndex;

    GenericChainTypes(ProjectBytecodeMemberIndex bytecodeIndex) {
        this.bytecodeIndex = bytecodeIndex;
    }

    /** 式の型 model。導出できなければ null。 */
    Model typeOf(Expression expression) {
        return typeOf(expression, new HashMap<>(), 0);
    }

    private Model typeOf(Expression expression, Map<String, Model> lambdaBindings, int depth) {
        if (expression == null || depth > MAX_DEPTH) {
            return null;
        }
        // JavaParser は推論が壊れた chain でも「型引数を Object へ落とした」結果を
        // 返すことがある (部分成功)。Object へ落ちた型引数を持つ場合だけ自前導出と
        // マージして補う。raw (型引数 0 個) は劣化と扱わずそのまま採用する。全ての
        // 型引数なし型で導出を走らせるコストを避けるため。
        Model solved = trySolvedModel(expression);
        if (solved != null && solved.args().stream().noneMatch(Model.OBJECT::equals)) {
            return solved;
        }
        Model derived = derive(expression, lambdaBindings, depth);
        return merge(solved, derived);
    }

    private Model derive(Expression expression, Map<String, Model> lambdaBindings, int depth) {
        if (expression instanceof EnclosedExpr enclosed) {
            return typeOf(enclosed.getInner(), lambdaBindings, depth + 1);
        }
        if (expression instanceof NameExpr name) {
            return nameModel(name, lambdaBindings, depth);
        }
        if (expression instanceof MethodCallExpr call) {
            return callModel(call, lambdaBindings, depth);
        }
        return null;
    }

    /** solved の劣化した型引数 (Object) を derived の同位置の型引数で補う。 */
    private static Model merge(Model solved, Model derived) {
        if (solved == null) {
            return derived;
        }
        if (derived == null || !derived.binaryName().equals(solved.binaryName())) {
            return solved;
        }
        List<Model> args = new ArrayList<>();
        for (int i = 0; i < solved.args().size(); i++) {
            Model arg = solved.args().get(i);
            args.add(Model.OBJECT.equals(arg) ? derived.arg(i) : arg);
        }
        return new Model(solved.binaryName(), List.copyOf(args));
    }

    /** JavaParser 自身が解決できる式はその結果を正とする (前進導出は fallback)。 */
    private static Model trySolvedModel(Expression expression) {
        try {
            return toModel(expression.calculateResolvedType(), 0);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Model toModel(ResolvedType type, int depth) {
        if (type == null || depth > MAX_DEPTH || !type.isReferenceType()) {
            return null;
        }
        ResolvedReferenceType reference = type.asReferenceType();
        List<Model> args = new ArrayList<>();
        try {
            for (ResolvedType argument : reference.typeParametersValues()) {
                Model model;
                if (argument.isWildcard()) {
                    // wildcard は境界の型へ丸める (要素型としての member 解決には十分)。
                    model = argument.asWildcard().isBounded()
                            ? toModel(argument.asWildcard().getBoundedType(), depth + 1)
                            : Model.OBJECT;
                } else if (argument.isTypeVariable()) {
                    model = Model.OBJECT;
                } else {
                    model = toModel(argument, depth + 1);
                }
                args.add(model != null ? model : Model.OBJECT);
            }
            return new Model(BinaryNames.erasureOf(reference.erasure()), List.copyOf(args));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private Model nameModel(NameExpr name, Map<String, Model> lambdaBindings, int depth) {
        Model bound = lambdaBindings.get(name.getNameAsString());
        if (bound != null) {
            return bound;
        }
        LambdaExpr lambda = declaringLambda(name);
        if (lambda != null) {
            return lambdaParamModel(lambda, name.getNameAsString(), lambdaBindings, depth);
        }
        VariableDeclarator declarator = uniqueLocalDeclarator(name, name.getNameAsString());
        if (declarator != null) {
            Model declared = declaredTypeModel(declarator.getType());
            if (declared != null) {
                return declared;
            }
            return typeOf(declarator.getInitializer().orElse(null), lambdaBindings, depth + 1);
        }
        Parameter parameter = enclosingCallableParameter(name, name.getNameAsString());
        if (parameter != null) {
            return declaredTypeModel(parameter.getType());
        }
        return null;
    }

    /** 明示宣言型 (var 以外) の model。解決できなければ null。 */
    private static Model declaredTypeModel(Type type) {
        if (type.isVarType()) {
            return null;
        }
        try {
            return toModel(type.resolve(), 0);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private Model callModel(MethodCallExpr call, Map<String, Model> lambdaBindings, int depth) {
        Expression scope = call.getScope().orElse(null);
        if (scope == null) {
            return null;
        }
        Model receiver = typeOf(scope, lambdaBindings, depth + 1);
        if (receiver == null) {
            return null;
        }
        return memberResultModel(receiver, call, lambdaBindings, depth);
    }

    /** receiver model 上の member 呼び出しの戻り値 model。 */
    private Model memberResultModel(
            Model receiver, MethodCallExpr call, Map<String, Model> lambdaBindings, int depth) {
        Model jdk = jdkResultModel(receiver, call, lambdaBindings, depth);
        if (jdk != null) {
            return jdk;
        }
        return bytecodeReturnModel(receiver.binaryName(), call.getNameAsString(), call.getArguments().size());
    }

    /** project bytecode の一意 instance member に限る戻り値 model (method reference 用)。 */
    private Model instanceBytecodeReturnModel(String owner, String methodName, int arity) {
        var candidate = bytecodeIndex.uniqueMethod(owner, methodName, arity).orElse(null);
        return candidate != null && !candidate.isStatic() ? candidateReturnModel(candidate) : null;
    }

    /** project bytecode の一意 member の generic 戻り値 (Signature が無ければ erasure)。 */
    private Model bytecodeReturnModel(String owner, String methodName, int arity) {
        var candidate = bytecodeIndex.uniqueMethod(owner, methodName, arity).orElse(null);
        return candidate != null ? candidateReturnModel(candidate) : null;
    }

    private Model candidateReturnModel(
            com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        var generic = bytecodeIndex.genericReturnType(candidate).orElse(null);
        if (generic != null) {
            Model model = toModel(generic);
            if (model != null) {
                return model;
            }
        }
        String erasure = candidate.returnType();
        if (erasure == null || erasure.endsWith("[]") || isPrimitiveOrVoid(erasure)) {
            return null;
        }
        return new Model(erasure, List.of());
    }

    private static Model toModel(GenericSignatureReader.BytecodeType model) {
        if (model.typeVariable() || model.arrayDims() > 0) {
            return null;
        }
        if (model.wildcard()) {
            // 変位のある型引数は要素型の根拠にしない (`? super X` の要素は X とは限らない)。
            return Model.OBJECT;
        }
        List<Model> args = new ArrayList<>();
        for (var argument : model.typeArguments()) {
            Model arg = toModel(argument);
            args.add(arg != null ? arg : Model.OBJECT);
        }
        return new Model(model.binaryName(), List.copyOf(args));
    }

    // ------------------------------------------------------------------
    // JDK コレクション / Stream / Optional / Map の宣言済み generic 意味論
    // ------------------------------------------------------------------

    private Model jdkResultModel(
            Model receiver, MethodCallExpr call, Map<String, Model> lambdaBindings, int depth) {
        String owner = receiver.binaryName();
        String method = call.getNameAsString();
        int arity = call.getArguments().size();
        if (COLLECTION_LIKE.contains(owner)) {
            return switch (method) {
                case "stream", "parallelStream" ->
                        arity == 0 ? new Model("java.util.stream.Stream", List.of(receiver.arg(0))) : null;
                case "iterator" ->
                        arity == 0 ? new Model("java.util.Iterator", List.of(receiver.arg(0))) : null;
                case "get" -> arity == 1 && LIST_LIKE.contains(owner) ? receiver.arg(0) : null;
                case "getFirst", "getLast", "removeFirst", "removeLast" ->
                        arity == 0 && DEQUE_LIKE.contains(owner) ? receiver.arg(0) : null;
                default -> null;
            };
        }
        if ("java.util.stream.Stream".equals(owner)) {
            Model element = receiver.arg(0);
            if (STREAM_ELEMENT_PRESERVING.contains(method)) {
                return new Model(owner, List.of(element));
            }
            return switch (method) {
                case "map" -> arity == 1
                        ? wrapIfPresent(owner, functionResultModel(call.getArgument(0), element, lambdaBindings, depth))
                        : null;
                case "flatMap" -> {
                    Model mapped = functionResultModel(call.getArgument(0), element, lambdaBindings, depth);
                    yield mapped != null && "java.util.stream.Stream".equals(mapped.binaryName())
                            ? mapped
                            : null;
                }
                case "toList" -> arity == 0 ? new Model("java.util.List", List.of(element)) : null;
                case "findFirst", "findAny" -> arity == 0 ? new Model("java.util.Optional", List.of(element)) : null;
                case "min", "max" -> arity == 1 ? new Model("java.util.Optional", List.of(element)) : null;
                case "collect" -> arity == 1
                        ? collectorResultModel(call.getArgument(0), element, lambdaBindings, depth)
                        : null;
                default -> null;
            };
        }
        if ("java.util.Optional".equals(owner)) {
            Model element = receiver.arg(0);
            return switch (method) {
                case "get" -> arity == 0 ? element : null;
                case "orElseThrow" -> arity <= 1 ? element : null;
                case "orElse", "orElseGet" -> arity == 1 ? element : null;
                case "filter" -> arity == 1 ? new Model(owner, List.of(element)) : null;
                case "map" -> arity == 1
                        ? wrapIfPresent(owner, functionResultModel(call.getArgument(0), element, lambdaBindings, depth))
                        : null;
                case "stream" -> new Model("java.util.stream.Stream", List.of(element));
                default -> null;
            };
        }
        if (MAP_LIKE.contains(owner)) {
            Model key = receiver.arg(0);
            Model value = receiver.arg(1);
            return switch (method) {
                case "get", "remove" -> arity == 1 ? value : null;
                case "put", "getOrDefault", "putIfAbsent", "computeIfAbsent",
                        "computeIfPresent", "compute", "merge" -> arity == 2 ? value : null;
                case "keySet" -> arity == 0 ? new Model("java.util.Set", List.of(key)) : null;
                case "values" -> arity == 0 ? new Model("java.util.Collection", List.of(value)) : null;
                case "entrySet" -> arity == 0
                        ? new Model("java.util.Set", List.of(new Model("java.util.Map$Entry", List.of(key, value))))
                        : null;
                default -> null;
            };
        }
        if ("java.util.Map$Entry".equals(owner)) {
            return switch (method) {
                case "getKey" -> receiver.arg(0);
                case "getValue" -> receiver.arg(1);
                default -> null;
            };
        }
        if ("java.util.Iterator".equals(owner)) {
            return "next".equals(method) && arity == 0 ? receiver.arg(0) : null;
        }
        return null;
    }

    private static Model wrapIfPresent(String container, Model element) {
        return element != null ? new Model(container, List.of(element)) : null;
    }

    /** Collectors.toList / toSet / toMap / groupingBy の結果 model。 */
    private Model collectorResultModel(
            Expression collector, Model element, Map<String, Model> lambdaBindings, int depth) {
        if (!(collector instanceof MethodCallExpr factory)) {
            return null;
        }
        // 固定表は java.util.stream.Collectors の意味論なので、宣言型を解決して
        // 出所を確かめる。名前が一致するだけの自作 factory へ適用すると、戻り値を
        // 誤った型として扱い誤った edge を作る。解決できない場合は適用しない。
        if (!isJdkCollectorsFactory(factory)) {
            return null;
        }
        String name = factory.getNameAsString();
        return switch (name) {
            case "toList", "toUnmodifiableList" -> new Model("java.util.List", List.of(element));
            case "toSet", "toUnmodifiableSet" -> new Model("java.util.Set", List.of(element));
            case "toMap", "toUnmodifiableMap" -> {
                if (factory.getArguments().size() < 2) {
                    yield null;
                }
                Model key = functionResultModel(factory.getArgument(0), element, lambdaBindings, depth);
                Model value = functionResultModel(factory.getArgument(1), element, lambdaBindings, depth);
                yield key != null && value != null
                        ? new Model("java.util.Map", List.of(key, value))
                        : null;
            }
            case "groupingBy" -> {
                // downstream collector 付き (2/3 引数) の値型は downstream 依存のため
                // 導出しない (classifier 単独の 1 引数形だけ Map<K, List<E>> と確定できる)。
                if (factory.getArguments().size() != 1) {
                    yield null;
                }
                Model key = functionResultModel(factory.getArgument(0), element, lambdaBindings, depth);
                yield key != null
                        ? new Model("java.util.Map",
                                List.of(key, new Model("java.util.List", List.of(element))))
                        : null;
            }
            default -> null;
        };
    }

    /** factory 呼び出しが java.util.stream.Collectors の member かを宣言型で確かめる。 */
    private static boolean isJdkCollectorsFactory(MethodCallExpr factory) {
        try {
            return COLLECTORS.equals(BinaryNames.forResolvedDeclaration(factory.resolve().declaringType()));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * functional 引数 (lambda / method reference) を入力 model へ適用した結果の model。
     * lambda は式 body のみを対象とし、parameter を入力 model に束縛して評価する。
     */
    private Model functionResultModel(
            Expression function, Model input, Map<String, Model> lambdaBindings, int depth) {
        if (depth > MAX_DEPTH || input == null) {
            return null;
        }
        if (function instanceof LambdaExpr lambda) {
            if (lambda.getParameters().size() != 1) {
                return null;
            }
            Expression body = lambda.getBody() instanceof ExpressionStmt statement
                    ? statement.getExpression()
                    : lambda.getExpressionBody().orElse(null);
            if (body == null) {
                return null;
            }
            Map<String, Model> nested = new HashMap<>(lambdaBindings);
            nested.put(lambda.getParameter(0).getNameAsString(), input);
            return typeOf(body, nested, depth + 1);
        }
        if (function instanceof MethodReferenceExpr reference) {
            Expression scope = reference.getScope();
            if (scope instanceof TypeExpr typeExpr) {
                // JavaParser は `mapVar::get` の単純名 scope も TypeExpr として parse
                // するため、構文では bound / unbound を区別できない。JLS の名前解決と
                // 同じく値 (local / parameter) を先に探し、見つかれば bound として
                // receiver model に入力 1 個を適用する。
                Model receiver = valueScopeModel(reference, typeExpr, lambdaBindings, depth);
                if (receiver != null) {
                    return boundReferenceResult(receiver, reference, lambdaBindings, depth);
                }
                // unbound instance method reference (`X::getY`): 要素型上の 0 引数
                // instance member。scope の型が解決できなければ導出しない
                // (推測による owner の当てはめはしない)。
                String owner;
                try {
                    owner = BinaryNames.erasureOf(typeExpr.getType().resolve());
                } catch (RuntimeException | LinkageError e) {
                    return null;
                }
                return instanceBytecodeReturnModel(owner, reference.getIdentifier(), 0);
            }
            // bound method reference (式 scope): receiver 式の model に入力 1 個を適用する。
            Model receiver = typeOf(scope, lambdaBindings, depth + 1);
            if (receiver == null) {
                return null;
            }
            return boundReferenceResult(receiver, reference, lambdaBindings, depth);
        }
        return null;
    }

    /** 単純名 scope を値 (lambda 束縛 / local / parameter) として解決した model。 */
    private Model valueScopeModel(
            MethodReferenceExpr reference, TypeExpr typeExpr, Map<String, Model> lambdaBindings, int depth) {
        String name = typeExpr.getType().toString();
        if (name.indexOf('.') >= 0 || name.indexOf('<') >= 0) {
            return null;
        }
        Model bound = lambdaBindings.get(name);
        if (bound != null) {
            return bound;
        }
        VariableDeclarator declarator = uniqueLocalDeclarator(reference, name);
        if (declarator != null) {
            Model declared = declaredTypeModel(declarator.getType());
            if (declared != null) {
                return declared;
            }
            return typeOf(declarator.getInitializer().orElse(null), lambdaBindings, depth + 1);
        }
        Parameter parameter = enclosingCallableParameter(reference, name);
        return parameter != null ? declaredTypeModel(parameter.getType()) : null;
    }

    private Model boundReferenceResult(
            Model receiver, MethodReferenceExpr reference, Map<String, Model> lambdaBindings, int depth) {
        // この関数は Function 適用 (入力 1 個) の文脈からしか呼ばれないため、probe は常に 1 引数で足りる。
        MethodCallExpr probe = new MethodCallExpr(reference.getIdentifier(), new NameExpr("arg0"));
        return memberResultModel(receiver, probe, lambdaBindings, depth + 1);
    }

    // ------------------------------------------------------------------
    // AST 上の宣言の探索 (型解決を伴わない)
    // ------------------------------------------------------------------

    /** 名前を parameter として宣言している最内の lambda。無ければ null。 */
    private static LambdaExpr declaringLambda(NameExpr name) {
        Node node = name;
        while ((node = node.getParentNode().orElse(null)) != null) {
            if (node instanceof LambdaExpr lambda && lambda.getParameters().stream()
                    .anyMatch(parameter -> parameter.getNameAsString().equals(name.getNameAsString()))) {
                return lambda;
            }
        }
        return null;
    }

    /** lambda parameter の型を、lambda を受ける呼び出しの receiver 要素型から導出する。 */
    private Model lambdaParamModel(
            LambdaExpr lambda, String paramName, Map<String, Model> lambdaBindings, int depth) {
        if (lambda.getParameters().size() != 1
                || !lambda.getParameter(0).getNameAsString().equals(paramName)) {
            return null;
        }
        Node parent = lambda.getParentNode().orElse(null);
        if (!(parent instanceof MethodCallExpr target) || !target.getArguments().contains(lambda)) {
            return null;
        }
        Expression scope = target.getScope().orElse(null);
        if (scope == null) {
            return null;
        }
        Model receiver = typeOf(scope, lambdaBindings, depth + 1);
        if (receiver == null) {
            return null;
        }
        String owner = receiver.binaryName();
        // 白リストの member に限り、単一引数 functional の入力 = receiver の要素型
        // (`toArray(n -> ...)` のような要素型でない functional を含めない)。
        if (!ELEMENT_INPUT_FUNCTIONALS.contains(target.getNameAsString())) {
            return null;
        }
        if ("java.util.stream.Stream".equals(owner)
                || "java.util.Optional".equals(owner)
                || COLLECTION_LIKE.contains(owner)) {
            return receiver.arg(0);
        }
        return null;
    }

    /** 囲み callable 内で同名の local 宣言が一意かつ anchor から可視ならその declarator。 */
    private static VariableDeclarator uniqueLocalDeclarator(Node anchor, String name) {
        Node scope = anchor;
        Node parent = scope.getParentNode().orElse(null);
        while (parent != null && !(parent instanceof com.github.javaparser.ast.body.CallableDeclaration<?>)
                && !(parent instanceof LambdaExpr)) {
            scope = parent;
            parent = scope.getParentNode().orElse(null);
        }
        Node root = parent != null ? parent : scope;
        List<VariableDeclarator> matches = root.findAll(VariableDeclarator.class).stream()
                .filter(declarator -> declarator.getNameAsString().equals(name))
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        return visibleFrom(matches.get(0), anchor) ? matches.get(0) : null;
    }

    /**
     * 宣言の可視範囲 (宣言文を含む block、for 系は statement 自体) が anchor を
     * 含むかを検査する。兄弟 block や内側の匿名 class の同名宣言を、名前だけの
     * 一致で採用すると誤った型 → 誤 owner に繋がるため。
     */
    private static boolean visibleFrom(VariableDeclarator declarator, Node anchor) {
        var statement = declarator.findAncestor(com.github.javaparser.ast.stmt.Statement.class).orElse(null);
        if (statement == null) {
            return false;
        }
        Node visibilityRoot = statement instanceof ExpressionStmt
                ? statement.getParentNode().orElse(null)
                : statement;
        return visibilityRoot != null && (visibilityRoot == anchor || visibilityRoot.isAncestorOf(anchor));
    }

    /** 囲み callable の parameter で同名のもの (lambda parameter を除く)。 */
    private static Parameter enclosingCallableParameter(Node anchor, String name) {
        Node node = anchor;
        while ((node = node.getParentNode().orElse(null)) != null) {
            if (node instanceof com.github.javaparser.ast.body.CallableDeclaration<?> callable) {
                return callable.getParameters().stream()
                        .filter(parameter -> parameter.getNameAsString().equals(name))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private static boolean isPrimitiveOrVoid(String name) {
        return switch (name) {
            case "void", "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
            default -> false;
        };
    }
}
