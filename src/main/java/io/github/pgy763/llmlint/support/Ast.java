package io.github.pgy763.llmlint.support;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;


import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 没有 symbol solver 也能做的那部分类型推断。
 *
 * <p>只认编译单元里自己声明过的类型（字段、参数、局部变量）。这样做的代价是：
 * 通过 {@code getChatClient()} 返回的、或者来自泛型容器的调用认不出来。
 * 换来的是：不要求调用者给出 classpath，装个 jar 就能跑，且**认不出来时不报**。
 */
public final class Ast {

    private Ast() {
    }

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, String... simpleNames) {
        List<String> wanted = Arrays.asList(simpleNames);
        return node.getAnnotations().stream()
                .map(a -> a.getName().getIdentifier())
                .anyMatch(wanted::contains);
    }

    /** 当前节点所在的类（或上个类）上有没有这个注解。 */
    public static boolean enclosingClassHas(Node node, String... simpleNames) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(c -> hasAnnotation(c, simpleNames))
                .orElse(false);
    }

    public static String simpleTypeName(Type type) {
        if (type instanceof ClassOrInterfaceType c) {
            return c.getNameAsString();
        }
        return type.asString();
    }

    /** 变量名 → 声明时的简单类型名。局部变量后写，所以同名时以最近的作用域为准。 */
    public static Map<String, String> declaredTypes(CompilationUnit cu) {
        Map<String, String> types = new HashMap<>();
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator v : f.getVariables()) {
                types.put(v.getNameAsString(), simpleTypeName(v.getType()));
            }
        }
        for (Parameter p : cu.findAll(Parameter.class)) {
            types.put(p.getNameAsString(), simpleTypeName(p.getType()));
        }
        for (VariableDeclarationExpr v : cu.findAll(VariableDeclarationExpr.class)) {
            for (VariableDeclarator d : v.getVariables()) {
                types.put(d.getNameAsString(), simpleTypeName(d.getType()));
            }
        }
        return types;
    }

    /** 调用的接收者是什么类型：`chatClient.prompt()` → ChatClient；链式调用（`x.y().z()`）返回 null。 */
    public static String receiverType(MethodCallExpr call, Map<String, String> types) {
        Optional<com.github.javaparser.ast.expr.Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return null;
        }
        var s = scope.get();
        if (s instanceof NameExpr n) {
            return types.get(n.getNameAsString());
        }
        if (s instanceof FieldAccessExpr f) {
            return types.get(f.getNameAsString());
        }
        return null;
    }

    public static boolean isModelType(String type) {
        if (type == null) {
            return false;
        }
        return type.endsWith("ChatClient")
                || type.endsWith("ChatModel")
                || type.endsWith("ChatLanguageModel")
                || type.endsWith("StreamingChatModel")
                || type.endsWith("EmbeddingModel")
                || type.endsWith("EmbeddingClient");
    }

    public static boolean isMemoryType(String type) {
        if (type == null) {
            return false;
        }
        return type.endsWith("ChatMemory") || type.endsWith("ChatMemoryStore");
    }

    /** 向量库 / 向量模型：这两种调用的成本都在网络往返上。 */
    public static boolean isVectorType(String type) {
        if (type == null) {
            return false;
        }
        return type.endsWith("VectorStore") || type.endsWith("EmbeddingStore")
                || type.endsWith("EmbeddingModel") || type.endsWith("EmbeddingClient");
    }

    /** 能「流出多个值」的返回类型。 */
    public static boolean isStreamingType(String type) {
        if (type == null) {
            return false;
        }
        return Set.of("Flux", "Mono", "SseEmitter", "StreamingResponseBody", "ResponseBodyEmitter")
                .contains(type);
    }

    /**
     * 调用链最左边那个名字：`ChatClient.builder(m).build()` → ChatClient；
     * `x.getClient().prompt()` → null（认不出来就不报）。
     */
    public static String rootName(Expression expr) {
        Expression cur = expr;
        while (cur instanceof MethodCallExpr m) {
            Optional<Expression> scope = m.getScope();
            if (scope.isEmpty()) {
                return null;
            }
            cur = scope.get();
        }
        if (cur instanceof NameExpr n) {
            return n.getNameAsString();
        }
        if (cur instanceof FieldAccessExpr f) {
            return f.getNameAsString();
        }
        if (cur instanceof ObjectCreationExpr o) {
            return simpleTypeName(o.getType());
        }
        return null;
    }

    /** 由 `builder()/create()/build()` 这类工厂方法产出的类型（Spring AI 与 LangChain4j 都有）。 */
    public static boolean isClientType(String type) {
        if (type == null) {
            return false;
        }
        return type.endsWith("ChatClient") || type.endsWith("ChatModel")
                || type.endsWith("ChatLanguageModel") || type.endsWith("EmbeddingModel")
                || type.endsWith("EmbeddingClient") || type.endsWith("AiServices");
    }

    /** 是不是写在 for/while/forEach 里面：这些地方的每次调用都是一次独立的网络往返。 */
    private static final Set<String> STREAM_OPERATORS =
            Set.of("forEach", "forEachOrdered", "map", "flatMap", "concatMap", "stream", "parallelStream");

    public static boolean insideLoop(Node node) {
        Node cur = node.getParentNode().orElse(null);
        while (cur != null) {
            if (cur instanceof ForStmt || cur instanceof ForEachStmt
                    || cur instanceof WhileStmt || cur instanceof DoStmt) {
                return true;
            }
            if (cur instanceof LambdaExpr lambda) {
                Optional<MethodCallExpr> call = lambda.getParentNode()
                        .filter(MethodCallExpr.class::isInstance)
                        .map(MethodCallExpr.class::cast);
                if (call.isPresent() && STREAM_OPERATORS.contains(call.get().getNameAsString())) {
                    return true;
                }
            }
            if (cur instanceof MethodDeclaration || cur instanceof ClassOrInterfaceDeclaration) {
                return false;
            }
            cur = cur.getParentNode().orElse(null);
        }
        return false;
    }

    public static Optional<MethodDeclaration> enclosingMethod(Node node) {
        return node.findAncestor(MethodDeclaration.class);
    }

    /** 字面量或常量名（`"default"`、`1L`、`DEFAULT_ID`）：说明所有会话共用同一个 id。 */
    public static boolean isConstantId(Expression expr) {
        if (expr.isStringLiteralExpr() || expr.isIntegerLiteralExpr() || expr.isLongLiteralExpr()) {
            return true;
        }
        if (expr instanceof NameExpr n) {
            String name = n.getNameAsString();
            return name.equals(name.toUpperCase(Locale.ROOT)) && name.length() > 1;
        }
        return false;
    }
}