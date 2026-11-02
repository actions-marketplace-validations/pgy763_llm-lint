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

/** @Bean 里造 HTTP 客户端却没有超时：默认是「一直等」，线程跟着挂住。 */
public final class Rule009HttpClientWithoutTimeout extends AbstractRule {

    private static final Set<String> CLIENT_TYPES =
            Set.of("WebClient", "RestClient", "RestTemplate", "HttpClient", "OkHttpClient");

    private static final Set<String> TIMEOUT_CALLS = Set.of(
            "timeout", "readTimeout", "setReadTimeout", "setReadTimeoutMillis",
            "connectTimeout", "setConnectTimeout", "setConnectTimeoutMillis",
            "callTimeout", "setCallTimeout", "responseTimeout", "setResponseTimeout");

    @Override
    public String id() {
        return "LLM009";
    }

    @Override
    public String title() {
        return "HTTP 客户端没配超时";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return doc("/java/Reactor超时截断流式");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (!Ast.hasAnnotation(m, "Bean")) {
                continue;
            }
            String returnType = Ast.simpleTypeName(m.getType());
            if (!CLIENT_TYPES.contains(returnType)) {
                continue;
            }
            boolean configured = m.findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> TIMEOUT_CALLS.contains(call.getNameAsString()));
            if (configured) {
                continue;
            }
            out.add(at(file, m,
                    "`@Bean " + returnType + " " + m.getNameAsString() + "(...)` 里没有一处超时设置。"
                            + "这类客户端的读超时默认是「不限」——上游不回，你的线程就一直等，"
                            + "既不报错也不释放。在这里补 `connectTimeout` + `readTimeout`；"
                            + "如果别处有统一配置（比如全局 HttpClient 定制），加 `--disable LLM009`。"));
        }
        return out;
    }
}