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

/**
 * {@code @Transactional} 方法返回 {@code SseEmitter} / {@code ResponseBodyEmitter}：
 * 事务会活到整个流结束。LLM015 管 {@code Flux} / {@code Mono}，这条补上 SSE 那一半。
 */
public final class Rule021TransactionalEmitter extends AbstractRule {

    private static final Set<String> EMITTER_TYPES = Set.of("SseEmitter", "ResponseBodyEmitter");

    @Override
    public String id() {
        return "LLM021";
    }

    @Override
    public String title() {
        return Messages.t("事务方法返回长连接", "Transactional method returns a streaming emitter");
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm021";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return Messages.t(
                "返回 SseEmitter 的方法，事务要等整个流结束才提交——生成多久，连接就攥多久。"
                        + "一个长回答几十秒，几个并发就能把连接池占满；看起来是「流式接口一多就卡」，"
                        + "实际是事务一直没结束。把发数据挪到事务外，事务里只管写库。",
                "A method that returns an SseEmitter keeps its transaction open until the whole"
                        + " stream finishes — the connection is held for as long as generation takes."
                        + " A few slow answers can exhaust the pool; it looks like \"streaming endpoints"
                        + " get stuck\", but the transaction simply never ends. Send from outside the"
                        + " transaction and keep the transaction around the writes only.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            String returnType = Ast.simpleTypeName(m.getType());
            if (!EMITTER_TYPES.contains(returnType)) {
                continue;
            }
            boolean transactional = Ast.hasAnnotation(m, "Transactional")
                    || Ast.enclosingClassHas(m, "Transactional");
            if (!transactional) {
                continue;
            }
            out.add(at(file, m, Messages.f(
                    "`%s` 标了 `@Transactional` 却返回 `%s`：事务要等整个流结束才提交，"
                            + "生成多久连接就攥多久。一个长回答几十秒，几个并发就把连接池占满。"
                            + "把发数据挪到事务外——事务里只做写库那几行，流的组装放到事务之后。",
                    "`%s` is annotated `@Transactional` but returns `%s`: the transaction commits only"
                            + " when the stream ends, so the connection is held for the whole"
                            + " generation. A few slow answers exhaust the pool. Send from outside the"
                            + " transaction — keep the transaction around the writes and assemble the"
                            + " stream afterwards.",
                    m.getNameAsString(), m.getTypeAsString())));
        }
        return out;
    }
}