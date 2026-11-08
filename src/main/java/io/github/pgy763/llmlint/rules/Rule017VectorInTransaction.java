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

/** 事务里查/写向量库：和「事务里调模型」同一类问题，只是看着更无害。 */
public final class Rule017VectorInTransaction extends AbstractRule {

    @Override
    public String id() {
        return "LLM017";
    }

    @Override
    public String title() {
        return Messages.t("事务里查/写向量库", "Vector store access inside a transaction");
    }

    @Override
    public String summary() {
        return Messages.t("事务里查 / 写向量库", "vector store read / write inside a transaction");
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm017";
    }

    @Override
    public String why() {
        return Messages.t(
                "向量的写入和检索都要先算嵌入、再走一次网络：事务里等这几百毫秒，数据库连接一直被这个线程占着，并发一上来先报「拿不到连接」。",
                "Vector writes and searches both compute an embedding and then make a network"
                        + " call: waiting those hundreds of milliseconds inside a transaction keeps a"
                        + " database connection checked out by this thread, and under load the first"
                        + " error is \"cannot get a connection\".");
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
                String receiver = Ast.receiverType(call, types);
                if (!isVectorStore(receiver)) {
                    continue;
                }
                out.add(at(file, call, Messages.f(
                        "`%s` 开着事务，里面调了 `%s`。"
                                + "向量检索、写入都要先算嵌入再走网络，这几百毫秒里数据库连接一直没还回去——"
                                + "和事务里调模型是同一类问题，只是看起来更无害。"
                                + "把向量操作挪到事务外面，事务只留数据库那一段。",
                        "`%s` runs inside a transaction and calls `%s` in it. Vector search and"
                                + " write both compute an embedding and then hit the network; for"
                                + " those hundreds of milliseconds the database connection is never"
                                + " returned — the same problem as calling the model inside a"
                                + " transaction, only it looks more harmless. Move vector operations"
                                + " outside the transaction and keep only the database part inside.",
                        m.getNameAsString(), receiver)));
                break;
            }
        }
        return out;
    }

    /** 只认「库」，不认「模型」：EmbeddingModel 的调用已经由 LLM001 报过了。 */
    private static boolean isVectorStore(String type) {
        return type != null && (type.endsWith("VectorStore") || type.endsWith("EmbeddingStore"));
    }
}