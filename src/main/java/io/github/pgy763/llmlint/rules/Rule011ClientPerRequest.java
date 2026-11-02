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
import java.util.Set;

/**
 * 每次请求都现建一个 ChatClient / ChatModel：把「建连接」当成免费的了。
 *
 * <p>单例 Bean 里的 {@code chatClient.prompt()} 是复用的；而
 * {@code ChatClient.create(model)}、{@code OpenAiChatModel.builder().build()}
 * 写在业务方法里，就是每个请求新建一次 HTTP 客户端（连接池、TLS、序列化器全部重来）。
 */
public final class Rule011ClientPerRequest extends AbstractRule {

    private static final Set<String> FACTORY_CALLS = Set.of("create", "builder");

    @Override
    public String id() {
        return "LLM011";
    }

    @Override
    public String title() {
        return "每次请求都新建 ChatClient/ChatModel";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm011";
    }

    @Override
    public String framework() {
        return "both";
    }

    @Override
    public String why() {
        return "Spring AI 的 ChatClient/ChatModel 与 LangChain4j 的 ChatLanguageModel 都是"
                + "「建一次、随便用」的东西：内部带连接池和重试器。在业务方法里建，等于每个请求"
                + "重开连接池、重做 TLS 握手，并发一高线程和端口先耗尽。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (Ast.hasAnnotation(m, "Bean")) {
                continue;
            }
            for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                if (!FACTORY_CALLS.contains(call.getNameAsString())) {
                    continue;
                }
                String root = Ast.rootName(call);
                if (root == null || !Ast.isClientType(root) || root.endsWith("AiServices")) {
                    continue;
                }
                out.add(at(file, call,
                        "`" + m.getNameAsString() + "` 里现建 `" + root + "`。"
                                + "每来一个请求就新建一次客户端：连接池、TLS 握手、序列化器全部重来，"
                                + "高峰期线程和端口会先于模型耗尽。"
                                + "在 `@Bean` 方法里建一次，业务类注入复用即可。"));
                break;
            }
        }
        return out;
    }
}