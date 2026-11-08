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
import java.util.Set;

/**
 * {@code @Transactional} 方法里等异步结果：连接从方法开始一直被攥到方法结束。
 *
 * <p>LLM001 管的是「事务里等模型」，这条管的是「事务里等任何东西」。
 */
public final class Rule020TransactionBlocks extends AbstractRule {

    private static final Set<String> BLOCKING = Set.of("block", "blockFirst", "blockLast");

    @Override
    public String id() {
        return "LLM020";
    }

    @Override
    public String title() {
        return Messages.t("事务方法里等异步结果", "Waiting on async work inside a transaction");
    }

    @Override
    public String summary() {
        return Messages.t("`@Transactional` 方法里 `.block()` / `Future.get()` / `Thread.sleep`", ".block() / Future.get() / Thread.sleep inside @Transactional");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm020";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return Messages.t(
                "事务的边界是方法，不是那段异步代码。方法里 block 一下、睡一会儿，"
                        + "数据库连接就从方法开始一直攥到方法结束——等的是模型还是别的线程并不重要，"
                        + "池子一样会被占满。把等待挪到事务外面，或者让事务只包住真正写库的那几行。",
                "A transaction's boundary is the method, not the async snippet inside it. A block"
                        + " or a sleep keeps the database connection checked out from the start of the"
                        + " method to its end — it does not matter whether you wait for a model or for"
                        + " another thread, the pool is held either way. Move the wait outside the"
                        + " transaction, or let the transaction cover only the rows you actually write.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        Map<String, String> types = Ast.declaredTypes(cu);
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            boolean transactional = Ast.hasAnnotation(m, "Transactional")
                    || Ast.enclosingClassHas(m, "Transactional");
            if (!transactional) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                if (Ast.enclosingMethod(call).orElse(null) != m) {
                    continue;
                }
                String waited = waitedOn(call, types);
                if (waited == null) {
                    continue;
                }
                out.add(at(file, call, Messages.f(
                        "`%s` 标了 `@Transactional`，方法里却在 `%s` 等异步结果。"
                                + "等的时候数据库连接一直攥在这个线程上没还回去；并发一上来先报"
                                + "「拿不到连接」，看着像数据库慢，其实是事务在等。"
                                + "把等待挪到事务外，或者让事务只包住写库那几行。",
                        "`%s` is annotated `@Transactional` but waits inside it with `%s`. While"
                                + " waiting, the database connection stays checked out; under load the"
                                + " first symptom is \"connection is not available\", which looks like a"
                                + " slow database but is the transaction waiting. Move the wait outside"
                                + " the transaction, or keep the transaction around the writes only.",
                        m.getNameAsString(), waited)));
                break;
            }
        }
        return out;
    }

    /** 命中的写法，没命中返回 null。 */
    private static String waitedOn(MethodCallExpr call, Map<String, String> types) {
        String name = call.getNameAsString();
        if (BLOCKING.contains(name)) {
            return "." + name + "()";
        }
        String receiver = Ast.receiverType(call, types);
        if ("get".equals(name) && receiver != null && receiver.endsWith("Future")) {
            return "Future.get()";
        }
        if ("sleep".equals(name) && call.getScope()
                .filter(scope -> "Thread".equals(scope.toString()))
                .isPresent()) {
            return "Thread.sleep()";
        }
        return null;
    }
}