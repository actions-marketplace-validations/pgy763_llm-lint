package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LangChain4j 的 {@code AiServices} 每个请求建一个：记忆、检索器、工具全跟着新建。
 *
 * <p>正确做法是建一个单例，记忆通过 {@code ChatMemoryProvider} 按会话 id 取。
 */
public final class Rule012AiServicesPerRequest extends AbstractRule {

    private static final Set<String> FACTORY_CALLS = Set.of("create", "builder");

    @Override
    public String id() {
        return "LLM012";
    }

    @Override
    public String title() {
        return "每次请求都新建 AiServices";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm012";
    }

    @Override
    public String framework() {
        return "langchain4j";
    }

    @Override
    public String why() {
        return "AiServices.create(...) 会把 memory、ContentRetriever、tools 绑到生成的代理上。"
                + "放在业务方法里，等于每个请求一份新记忆——上一句说的话下一句就忘，"
                + "检索器和工具也要重新装配。代理本身线程安全，建一次就够。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (Ast.hasAnnotation(m, "Bean")) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                if (!FACTORY_CALLS.contains(call.getNameAsString())) {
                    continue;
                }
                String root = Ast.rootName(call);
                if (root == null || !root.endsWith("AiServices")) {
                    continue;
                }
                out.add(at(file, call,
                        "`" + m.getNameAsString() + "` 里现建 `" + root + "`。"
                                + "AiServices 会把 memory / retriever / tools 一起绑到新代理上："
                                + "每个请求一份新记忆，多轮对话直接断片；工具和检索器也要重新装配。"
                                + "在 `@Bean` 里建一个单例，记忆按会话 id 从 `ChatMemoryProvider` 取。"));
                break;
            }
        }
        return out;
    }
}