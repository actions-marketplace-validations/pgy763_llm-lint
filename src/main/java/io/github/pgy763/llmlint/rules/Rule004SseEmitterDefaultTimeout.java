package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;

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
        return Messages.t("SseEmitter 没设超时", "SseEmitter without a timeout");
    }

    @Override
    public String summary() {
        return Messages.t("`new SseEmitter()` 用容器默认超时，30 秒断流", "new SseEmitter() without a timeout, dies at the 30s container default");
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
    public String why() {
        return Messages.t(
                "new SseEmitter() 用的是容器默认超时（Tomcat 30 秒）。模型还没生成完，连接先被掐了，前端看到的是「回答到一半停住」。",
                "new SseEmitter() uses the container's default timeout (30 seconds on Tomcat)."
                        + " The connection is cut before the model finishes generating, and the"
                        + " front end sees an answer that stops halfway.");
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
            out.add(at(file, e, Messages.t(
                    "`new SseEmitter()` 用的是容器默认超时（Tomcat 30 秒）。"
                            + "模型那边还在生成，连接已经断了，前端表现是「回答到一半停住」。"
                            + "按业务给个上限：`new SseEmitter(180_000L)`；要长连接就传 0，"
                            + "但别忘了在 `onTimeout` 里收尾。",
                    "`new SseEmitter()` uses the container's default timeout (30 seconds on"
                            + " Tomcat). The model is still generating and the connection is already"
                            + " gone; the front end shows an answer that stops halfway. Set a limit"
                            + " per use case: `new SseEmitter(180_000L)`; for long-lived connections"
                            + " pass 0, but remember to clean up in `onTimeout`.")));
        }
        return out;
    }
}