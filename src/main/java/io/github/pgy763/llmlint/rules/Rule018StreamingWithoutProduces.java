package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 返回 Flux 却没写 produces：Spring 按 JSON 收完再发，前端看到的还是「等很久、然后全出来」。 */
public final class Rule018StreamingWithoutProduces extends AbstractRule {

    private static final Set<String> MAPPINGS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping");

    private static final Set<String> STREAMING = Set.of("Flux", "StreamingResponseBody");

    @Override
    public String id() {
        return "LLM018";
    }

    @Override
    public String title() {
        return Messages.t("流式接口没声明 produces", "Streaming endpoint without produces");
    }

    @Override
    public String summary() {
        return Messages.t("流式接口没声明 `produces`", "streaming endpoint without produces");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return docInRepo() + "#llm018";
    }

    @Override
    public String why() {
        return Messages.t(
                "返回 Flux 但没写 produces，Spring 会按 Jackson 的 JSON 走：先收完整个流再一次性写出去，代理也更容易把响应攒着——流式就白开了。",
                "Returning Flux without produces makes Spring go through the Jackson JSON"
                        + " converter: it drains the whole stream and writes it out at once, and"
                        + " proxies are more likely to buffer the response — streaming was enabled"
                        + " for nothing.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            String returnType = Ast.simpleTypeName(m.getType());
            if (!STREAMING.contains(returnType)) {
                continue;
            }
            AnnotationExpr mapping = m.getAnnotations().stream()
                    .filter(a -> MAPPINGS.contains(a.getName().getIdentifier()))
                    .findFirst()
                    .orElse(null);
            if (mapping == null || declaresProduces(mapping)) {
                continue;
            }
            out.add(at(file, m,
                    Messages.f(
                            "`%s` 返回 `%s`，但 `%s` 没写 `produces`。"
                                    + "Spring 会按 JSON 的转换器走：先把整个流收完，再一次性写出去，"
                                    + "中间件也更容易把响应攒着——客户端看到的还是「等很久，然后全出来」。"
                                    + "写成 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`。",
                            "`%s` returns `%s`, but `%s` has no `produces`. Spring goes through the"
                                    + " JSON converter: it drains the whole stream and writes it out"
                                    + " in one go, and middleware is more likely to buffer the"
                                    + " response — the client still sees \"a long wait, then"
                                    + " everything at once\". Write"
                                    + " `produces = MediaType.TEXT_EVENT_STREAM_VALUE`.",
                            m.getNameAsString(), returnType, mapping.getNameAsString()),
                    fix(mapping, withProduces(mapping))));
        }
        return out;
    }

    private static boolean declaresProduces(AnnotationExpr annotation) {
        if (!annotation.isNormalAnnotationExpr()) {
            return false;
        }
        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
            if (pair.getNameAsString().equals("produces")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把注解改写成带 produces 的样子。
     *
     * <p>用字符串字面量而不是 {@code MediaType.TEXT_EVENT_STREAM_VALUE}：不用额外加 import，
     * 补丁贴上去就能编译。charset 一起写上，免得改完又踩 LLM006。
     */
    private static String withProduces(AnnotationExpr annotation) {
        String name = "@" + annotation.getNameAsString();
        String produces = "produces = \"text/event-stream;charset=UTF-8\"";
        if (annotation.isMarkerAnnotationExpr()) {
            return name + "(" + produces + ")";
        }
        if (annotation.isSingleMemberAnnotationExpr()) {
            String value = annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
            return name + "(value = " + value + ", " + produces + ")";
        }
        List<MemberValuePair> pairs = annotation.asNormalAnnotationExpr().getPairs();
        StringBuilder sb = new StringBuilder(name).append("(");
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pairs.get(i));
        }
        if (!pairs.isEmpty()) {
            sb.append(", ");
        }
        return sb.append(produces).append(")").toString();
    }
}