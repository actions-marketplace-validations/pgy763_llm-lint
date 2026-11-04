package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 空的 catch(IOException) 包着写流：客户端早断开了，你还在写，日志里什么都没有。 */
public final class Rule008SwallowedIoException extends AbstractRule {

    private static final Set<String> WRITE_CALLS = Set.of("write", "send", "flush", "flushBuffer", "writeAndFlush");

    @Override
    public String id() {
        return "LLM008";
    }

    @Override
    public String title() {
        return Messages.t("空的 catch 吞掉 IOException", "Empty catch swallows IOException");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return doc("/java/客户端断开后还在写SSE");
    }

    @Override
    public String why() {
        return Messages.t(
                "catch 里什么都不做，等于把「写失败」当成「写成功」。连接断了也看不出来，日志干净，用户那头停在半句。",
                "Doing nothing in the catch treats a failed write as a successful one. A dropped"
                        + " connection is invisible, the log stays clean, and the user is left with"
                        + " half a sentence.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (CatchClause c : cu.findAll(CatchClause.class)) {
            String caught = Ast.simpleTypeName(c.getParameter().getType());
            if (!Set.of("IOException", "Exception", "Throwable").contains(caught)) {
                continue;
            }
            if (!c.getBody().getStatements().isEmpty()) {
                continue;
            }
            if (!(c.getParentNode().orElse(null) instanceof TryStmt tryStmt)) {
                continue;
            }
            boolean writes = tryStmt.getTryBlock().findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> WRITE_CALLS.contains(call.getNameAsString()));
            if (!writes) {
                continue;
            }
            out.add(at(file, c, Messages.f(
                    "`catch (%s ignored)` 是空的，而 try 里在往连接上写。"
                            + "客户端断开时 `write` 会抛 IOException——被吞掉之后循环继续跑到结束，"
                            + "看起来一切正常，日志里一条都没有。"
                            + "至少 `break` 出去；要排查就把断连和真正的写失败分开记。",
                    "`catch (%s ignored)` is empty, and the try block writes to the connection."
                            + " When the client disconnects, `write` throws IOException — swallowed,"
                            + " the loop keeps running to the end, everything looks fine and nothing"
                            + " lands in the log. At least `break` out; to diagnose properly, log"
                            + " disconnects and real write failures separately.",
                    caught)));
        }
        return out;
    }
}