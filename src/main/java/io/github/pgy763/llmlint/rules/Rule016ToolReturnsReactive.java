package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** @Tool 方法返回 Mono/Flux：框架不会替你订阅，模型拿到的是个对象。 */
public final class Rule016ToolReturnsReactive extends AbstractRule {

    private static final Set<String> REACTIVE =
            Set.of("Mono", "Flux", "CompletableFuture", "CompletionStage");

    @Override
    public String id() {
        return "LLM016";
    }

    @Override
    public String title() {
        return Messages.t("工具方法返回响应式类型", "Tool method returns a reactive type");
    }

    @Override
    public String summary() {
        return Messages.t("`@Tool` 方法返回 `Mono` / `Flux`", "@Tool method returning Mono / Flux");
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm016";
    }

    @Override
    public String why() {
        return Messages.t(
                "模型调用工具时，框架要的是一段能直接塞回上下文的结果。返回 Mono/Flux 时没人替你订阅：轻则模型拿到对象的 toString，重则序列化失败、整轮对话 500。",
                "When the model calls a tool, the framework wants a result it can put straight back"
                        + " into the context. Return Mono/Flux and nobody subscribes for you: at best"
                        + " the model gets the object's toString, at worst serialization fails and"
                        + " the whole turn returns 500.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (!Ast.hasAnnotation(m, "Tool")) {
                continue;
            }
            String returnType = Ast.simpleTypeName(m.getType());
            if (!REACTIVE.contains(returnType)) {
                continue;
            }
            out.add(at(file, m, Messages.f(
                    "`%s` 是 `@Tool`，却返回 `%s`。"
                            + "工具调用要的是结果本身，框架不会订阅这个流：轻则模型收到 `"
                            + "%s@1a2b` 这样的字符串，重则序列化失败、整轮对话 500。"
                            + "工具方法改成同步返回，里面要调远程就先算完再 return。",
                    "`%s` is a `@Tool` but returns `%s`. A tool call needs the result itself and"
                            + " the framework will not subscribe to the stream: at best the model"
                            + " receives a string like `%s@1a2b`, at worst serialization fails and"
                            + " the whole turn returns 500. Make the tool method return"
                            + " synchronously; if it calls something remote, finish that call before"
                            + " returning.",
                    m.getNameAsString(), returnType, returnType)));
        }
        return out;
    }
}