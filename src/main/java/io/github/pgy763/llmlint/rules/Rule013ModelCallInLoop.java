package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型 / 向量库调用写在循环里：N 条数据就是 N 次网络往返。
 *
 * <p>这正是 RAG 里最常见的慢：先拿 10 个问题，然后一人一次 embedding、一人一次检索。
 */
public final class Rule013ModelCallInLoop extends AbstractRule {

    private static final Set<String> EXPENSIVE_CALLS = Set.of(
            "call", "chat", "generate", "generateStream", "stream",
            "similaritySearch", "search", "embed", "embedAll", "embedForResponse", "retrieve");

    @Override
    public String id() {
        return "LLM013";
    }

    @Override
    public String title() {
        return "循环里逐个调模型/向量库";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm013";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return "模型和向量库的耗时几乎全在网络往返上。循环里一次一条，10 条就是 10 次 RTT，"
                + "延迟线性叠加，而批量接口往往能一次带走。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        Map<String, String> types = Ast.declaredTypes(cu);
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!EXPENSIVE_CALLS.contains(call.getNameAsString())) {
                continue;
            }
            // 链式调用（chatClient.prompt().user(q).call()）的 scope 不是简单名字，
            // 沿 scope 链找到根变量再查类型；认不出就不报。
            String receiver = Ast.rootName(call.getScope().orElse(null));
            if (receiver != null) {
                receiver = types.get(receiver);
            }
            if (receiver == null) {
                receiver = Ast.receiverType(call, types);
            }
            if (!Ast.isModelType(receiver) && !Ast.isVectorType(receiver)) {
                continue;
            }
            if (!Ast.insideLoop(call)) {
                continue;
            }
            out.add(at(file, call,
                    "`" + call.getNameAsString() + "()` 写在循环里：每条一次往返，"
                            + "N 条数据的耗时就是 N 倍的网络延迟。"
                            + "能批量的一次带走（`embedAll`、一次查多个 id），"
                            + "实在要逐条的，至少并发发出去再一起等。"));
        }
        return out;
    }
}