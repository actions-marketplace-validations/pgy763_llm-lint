package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code @Transactional} 方法返回 Flux/Mono：事务在方法返回时就结束了，流不在事务里。
 *
 * <p>只有换成 R2DBC 这类响应式事务管理器才成立，而这个前提很少有人确认过。
 */
public final class Rule015ReactiveInTransaction extends AbstractRule {

    private static final Set<String> REACTIVE_TYPES = Set.of("Flux", "Mono");

    @Override
    public String id() {
        return "LLM015";
    }

    @Override
    public String title() {
        return "事务方法返回 Flux/Mono";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm015";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return "阻塞式事务管理器在方法返回的那一刻提交。返回 Flux/Mono 的方法里，"
                + "真正干活的是订阅之后——那时代码已经跑在事务外面了。"
                + "看起来有 @Transactional，回滚却不会发生。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            String returnType = Ast.simpleTypeName(m.getType());
            if (!REACTIVE_TYPES.contains(returnType)) {
                continue;
            }
            boolean transactional = Ast.hasAnnotation(m, "Transactional")
                    || Ast.enclosingClassHas(m, "Transactional");
            if (!transactional) {
                continue;
            }
            out.add(at(file, m,
                    "`" + m.getNameAsString() + "` 标了 `@Transactional` 但返回 `" + returnType + "`。"
                            + "阻塞式事务管理器在方法返回时就提交了，订阅之后发出的数据不在事务里——"
                            + "看着有注解，回滚不会发生。"
                            + "拆成「事务里只做同步的库操作 + 事务外组装流」，"
                            + "或者确认你确实用的是响应式事务管理器。"));
        }
        return out;
    }
}