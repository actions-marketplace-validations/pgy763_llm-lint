package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code @Scheduled} / {@code @Async} 方法里直接调模型：没有请求上下文，
 * 会话记忆要么拿到别人的，要么每次新建一份。
 */
public final class Rule022ScheduledModelCall extends AbstractRule {

    @Override
    public String id() {
        return "LLM022";
    }

    @Override
    public String title() {
        return Messages.t("定时任务里直接调模型", "Model call inside a scheduled or async method");
    }

    @Override
    public String summary() {
        return Messages.t("`@Scheduled` / `@Async` 方法里直接调模型", "model call directly inside @Scheduled / @Async");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm022";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return Messages.t(
                "@Scheduled / @Async 里没有请求，也就没有会话边界：用 ChatMemory 时要么拿到别人的会话，"
                        + "要么每次都新建一份，上下文永远对不上。定时任务该做的是「算出要处理什么」，"
                        + "模型调用留给有请求上下文的路径。",
                "There is no request inside @Scheduled / @Async, so there is no conversation boundary:"
                        + " with ChatMemory you either read someone else's session or create a new one"
                        + " every time, and the context never lines up. A scheduled job should decide"
                        + " what to process and leave the model call to a path that has request"
                        + " context.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        Map<String, String> types = Ast.declaredTypes(cu);
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (!Ast.hasAnnotation(m, "Scheduled", "Async")) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                if (Ast.enclosingMethod(call).orElse(null) != m) {
                    continue;
                }
                String receiver = Ast.receiverType(call, types);
                if (!Ast.isModelType(receiver)) {
                    continue;
                }
                out.add(at(file, call, Messages.f(
                        "`%s` 是定时任务 / 异步方法，里面直接调了模型（`%s`）。"
                                + "这里没有请求上下文：用 ChatMemory 时要么串到别人的会话，要么每次新建一份。"
                                + "定时任务只决定「要处理什么」，模型调用交给有请求上下文的路径。",
                        "`%s` runs on a scheduler or an async thread and calls the model directly"
                                + " (`%s`). There is no request context here: with ChatMemory you either"
                                + " join someone else's session or start a new one every time. Let the"
                                + " job decide what to process and keep the model call on a path that"
                                + " has request context.",
                        m.getNameAsString(), receiver)));
                break;
            }
        }
        return out;
    }
}