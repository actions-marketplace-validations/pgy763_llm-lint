package io.github.pgy763.llmlint.support;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}