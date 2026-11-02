package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 无参 SseEmitter：容器默认 30 秒，一次长回答必断。 */
public final class Rule004SseEmitterDefaultTimeout extends AbstractRule {

    @Override
    public String id() {
        return "LLM004";
    }

    @Override
    public String title() {
        return "SseEmitter 没设超时";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/java/SSE连接30秒断开");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (ObjectCreationExpr e : cu.findAll(ObjectCreationExpr.class)) {
            if (!"SseEmitter".equals(e.getTypeAsString())) {
                continue;
            }
            if (!e.getArguments().isEmpty()) {
                continue;
            }
            out.add(at(file, e,
                    "`new SseEmitter()` 用的是容器默认超时（Tomcat 30 秒）。"
                            + "模型那边还在生成，连接已经断了，前端表现是「回答到一半停住」。"
                            + "按业务给个上限：`new SseEmitter(180_000L)`；要长连接就传 0，"
                            + "但别忘了在 `onTimeout` 里收尾。"));
        }
        return out;
    }
}