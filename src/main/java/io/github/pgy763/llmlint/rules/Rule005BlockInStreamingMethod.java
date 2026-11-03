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
import java.util.Set;

/** 返回流式、里面却把它收完了：前端只会「等很久，然后一次性全出来」。 */
public final class Rule005BlockInStreamingMethod extends AbstractRule {

    private static final Set<String> STREAM_TYPES =
            Set.of("Flux", "Mono", "SseEmitter", "StreamingResponseBody", "ResponseBodyEmitter");

    private static final Set<String> COLLECT_CALLS =
            Set.of("block", "blockFirst", "blockLast", "collectList", "collectMap", "collect");

    @Override
    public String id() {
        return "LLM005";
    }

    @Override
    public String title() {
        return Messages.t("流式方法里 block/collect", "block/collect in a streaming method");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return doc("/java/WebFlux里block流式结果");
    }

    @Override
    public String why() {
        return Messages.t(
                "响应式流要在事件循环里跑。block() 把线程占住，Netty 的少量线程很快耗尽；流已经被订阅，再 block 也拿不到完整结果。",
                "Reactive streams must run on the event loop. block() pins the thread and Netty's"
                        + " few threads run out fast; the stream is already subscribed, so blocking"
                        + " will not give you the full result either.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            String returnType = Ast.simpleTypeName(m.getType());
            if (!STREAM_TYPES.contains(returnType)) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                String name = call.getNameAsString();
                if (!COLLECT_CALLS.contains(name)) {
                    continue;
                }
                out.add(at(file, call, Messages.f(
                        "`%s` 声明返回 `%s`，"
                                + "里面却 `%s()` 把结果收完了。"
                                + "调用方看到的效果是：先安静等好几秒，然后一次全出来——"
                                + "和不开流式没区别，WebFlux 那层还白占线程。"
                                + "返回 `Flux<String>` 就一路 `flatMap` 下去，别在中途收口。",
                        "`%s` declares it returns `%s`, but calls `%s()` to drain the result."
                                + " Callers see: several seconds of silence, then everything at"
                                + " once — no different from no streaming, and the WebFlux thread is"
                                + " held for nothing. If you return `Flux<String>`, keep `flatMap`ing"
                                + " all the way; don't collect halfway.",
                        m.getNameAsString(), returnType, name)));
                break;
            }
        }
        return out;
    }
}