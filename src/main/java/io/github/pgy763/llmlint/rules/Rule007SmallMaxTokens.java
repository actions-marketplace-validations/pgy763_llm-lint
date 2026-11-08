package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** maxTokens 给得太小：工具参数一长就被截断，客户端拿到半截 JSON。 */
public final class Rule007SmallMaxTokens extends AbstractRule {

    private static final Set<String> CALLS = Set.of("maxTokens", "setMaxTokens", "maxCompletionTokens");

    private static final int TOO_SMALL = 256;

    @Override
    public String id() {
        return "LLM007";
    }

    @Override
    public String title() {
        return Messages.t("maxTokens 太小", "maxTokens too small");
    }

    @Override
    public String summary() {
        return Messages.t("`maxTokens` 太小，工具参数被截断", "maxTokens too small, tool arguments get cut off");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm007";
    }

    @Override
    public String why() {
        return Messages.t(
                "maxTokens 是上限不是目标。给 128 时模型想调工具，参数 JSON 写到一半就被截断，客户端看到 Unexpected end-of-input，看着像解析 bug。",
                "maxTokens is a ceiling, not a target. At 128, when the model wants to call a tool"
                        + " the argument JSON is cut off mid-write; the client sees Unexpected"
                        + " end-of-input, which looks like a parsing bug.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!CALLS.contains(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            if (!call.getArgument(0).isIntegerLiteralExpr()) {
                continue;
            }
            IntegerLiteralExpr literal = call.getArgument(0).asIntegerLiteralExpr();
            int value;
            try {
                value = Integer.parseInt(literal.getValue().replace("_", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (value > 0 && value <= TOO_SMALL) {
                out.add(at(file, call, Messages.f(
                        "`%s(%d)` 只留了 %d 个 token。"
                                + "只要模型要调工具，参数就是一段 JSON——写到这里被截断，客户端看到的是"
                                + " `Unexpected end-of-input`，看着像解析 bug，其实是预算不够。"
                                + "工具场景留 1024 以上，并且把「是否被截断」当错误处理。",
                        "`%s(%d)` leaves only %d tokens. As soon as the model calls a tool the"
                                + " arguments are a JSON blob — truncated here, the client sees"
                                + " `Unexpected end-of-input`; it looks like a parsing bug but is"
                                + " really a budget problem. Leave 1024+ for tool use, and treat"
                                + " \"was it truncated\" as an error.",
                        call.getNameAsString(), value, value)));
            }
        }
        return out;
    }
}