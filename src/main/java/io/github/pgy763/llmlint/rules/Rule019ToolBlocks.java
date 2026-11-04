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

/** @Tool 方法里做阻塞调用：模型在等工具返回，工具在等数据库。 */
public final class Rule019ToolBlocks extends AbstractRule {

    private static final Set<String> BLOCKING = Set.of("block", "blockFirst", "blockLast", "join");

    @Override
    public String id() {
        return "LLM019";
    }

    @Override
    public String title() {
        return Messages.t("工具方法里做阻塞调用", "Blocking call inside a @Tool method");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm019";
    }

    @Override
    public String why() {
        return Messages.t(
                "工具调用跑在请求线程上。方法里查一次库、调一次同步接口、甚至睡一会儿，"
                        + "模型那边都在等：看起来是「模型变慢了」，其实是它在等你的工具。"
                        + "该查的东西先查好再交给工具，或者让工具返回一句「正在处理」由后台去做。",
                "Tool calls run on the request thread. A database lookup, a synchronous HTTP call or"
                        + " even a sleep inside the tool means the model sits there waiting: it looks"
                        + " like a slow model, but it is waiting for your tool. Fetch what the tool"
                        + " needs beforehand, or have the tool hand off the work and return right away.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        Map<String, String> types = Ast.declaredTypes(cu);
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (!Ast.hasAnnotation(m, "Tool")) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                String what = blockingWhat(call, types);
                if (what == null) {
                    continue;
                }
                out.add(at(file, call, Messages.f(
                        "`%s` 是给模型用的工具方法，里面调了 `%s`：模型调用工具时是在请求线程上等结果的，"
                                + "这一等就把整轮响应拖住了。"
                                + "把查询结果提前准备好传进来，或者让工具先返回、把慢活丢给后台。",
                        "`%s` is a tool the model can call, and it runs `%s` in the request thread:"
                                + " while the model waits for the tool, the whole response waits too."
                                + " Prepare the data before the call, or return early and let a"
                                + " background job do the slow part.",
                        m.getNameAsString(), what)));
                break;
            }
        }
        return out;
    }

    /** 命中就返回一句「调用什么」，用于消息里；没命中返回 null。 */
    private static String blockingWhat(MethodCallExpr call, Map<String, String> types) {
        String name = call.getNameAsString();
        String receiver = Ast.receiverType(call, types);

        if (BLOCKING.contains(name)) {
            if ("join".equals(name) && "String".equals(receiver)) {
                return null;
            }
            return receiver == null ? name + "()" : receiver + "." + name + "()";
        }
        if ("get".equals(name) && receiver != null && receiver.endsWith("Future")) {
            return receiver + ".get()";
        }
        if ("sleep".equals(name) && call.getScope().map(s -> s.toString().equals("Thread")).orElse(false)) {
            return "Thread.sleep()";
        }
        if (receiver != null && isRepository(receiver)) {
            return receiver + "." + name + "()";
        }
        return null;
    }

    /** 只认「去外面拿数据」的那几类；带缓存、静态工具类不算。 */
    private static boolean isRepository(String type) {
        return type.endsWith("Repository")
                || type.endsWith("Mapper")
                || type.endsWith("JdbcTemplate")
                || type.endsWith("RestTemplate")
                || type.endsWith("WebClient");
    }
}