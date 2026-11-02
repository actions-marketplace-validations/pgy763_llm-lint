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

/**
 * LangChain4j：{@code chatMemoryProvider.get("default")}——给所有人发同一份记忆。
 *
 * <p>LLM002 管的是字段级单例；这条管的是「看起来按会话取、其实 id 写死了」。
 */
public final class Rule014ChatMemoryConstantId extends AbstractRule {

    @Override
    public String id() {
        return "LLM014";
    }

    @Override
    public String title() {
        return "ChatMemoryProvider 用了写死的 id";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/java/多用户共用一份ChatMemory");
    }

    @Override
    public String framework() {
        return "langchain4j";
    }

    @Override
    public String why() {
        return "ChatMemoryProvider 是按 id 发记忆的，id 写死就等于全站一个会话："
                + "A 说过的名字、地址会出现在 B 的上下文里。这类问题在压测里看不出来，"
                + "上线后第一个投诉的是用户。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        Map<String, String> types = Ast.declaredTypes(cu);
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"get".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }
            String receiver = Ast.receiverType(call, types);
            if (receiver == null || !receiver.endsWith("ChatMemoryProvider")) {
                continue;
            }
            if (!Ast.isConstantId(call.getArgument(0))) {
                continue;
            }
            out.add(at(file, call,
                    "这里按固定 id 取记忆：所有用户拿到的是同一份，"
                            + "A 说过的话会出现在 B 的上下文里。"
                            + "id 要从请求里来（会话 id / userId），"
                            + "每个会话一条，用完记得按 TTL 清。"));
        }
        return out;
    }
}