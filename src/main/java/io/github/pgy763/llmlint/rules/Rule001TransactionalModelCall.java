package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 事务里调模型：数据库连接被模型那几秒一直占着。 */
public final class Rule001TransactionalModelCall extends AbstractRule {

    @Override
    public String id() {
        return "LLM001";
    }

    @Override
    public String title() {
        return "@Transactional 里调模型";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/java/事务里调模型拖垮连接池");
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
                if (Ast.isModelType(receiver)) {
                    out.add(at(file, call,
                            "`" + m.getNameAsString() + "` 开着事务，里面调了 `" + receiver + "`。"
                                    + "模型这一次要几秒，这几秒数据库连接一直没还回去——并发一上来，"
                                    + "报的是「拿不到连接」，不是「模型慢」，排查方向很容易被带偏。"
                                    + "把模型调用挪到事务外面，或者先查好数据再开事务。"));
                    break;
                }
            }
        }
        return out;
    }
}