package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
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
        return "流式接口没声明 produces";
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
        return "返回 Flux 但没写 produces，Spring 会按 Jackson 的 JSON 走：先收完整个流再一次性写出去，代理也更容易把响应攒着——流式就白开了。";
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
                    "`" + m.getNameAsString() + "` 返回 `" + returnType + "`，但 `"
                            + mapping.getNameAsString() + "` 没写 `produces`。"
                            + "Spring 会按 JSON 的转换器走：先把整个流收完，再一次性写出去，"
                            + "中间件也更容易把响应攒着——客户端看到的还是「等很久，然后全出来」。"
                            + "写成 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`。"));
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
}