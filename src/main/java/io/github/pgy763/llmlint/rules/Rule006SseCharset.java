package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** produces 写了 text/event-stream 却没写 charset：中文变乱码。 */
public final class Rule006SseCharset extends AbstractRule {

    @Override
    public String id() {
        return "LLM006";
    }

    @Override
    public String title() {
        return Messages.t("produces 少了 charset", "produces is missing charset");
    }

    @Override
    public String summary() {
        return Messages.t("`produces = \"text/event-stream\"` 少了 charset", "produces = \"text/event-stream\" without a charset");
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String docUrl() {
        return doc("/java/SSE响应中文乱码");
    }

    @Override
    public String why() {
        return Messages.t(
                "text/event-stream 不带 charset，Spring 按默认编码把 String 写出去，中文在浏览器里是问号，接口却是 200。",
                "text/event-stream without charset: Spring writes the String with the default"
                        + " encoding, so Chinese shows up as question marks in the browser while the"
                        + " endpoint still returns 200.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (MemberValuePair pair : cu.findAll(MemberValuePair.class)) {
            if (!pair.getValue().isStringLiteralExpr()) {
                continue;
            }
            StringLiteralExpr literal = pair.getValue().asStringLiteralExpr();
            String value = literal.asString();
            if (!value.contains("text/event-stream") || value.contains("charset")) {
                continue;
            }
            out.add(at(file, pair,
                    Messages.f(
                            "`%s` 声明了 `text/event-stream`，但没带 `charset`。"
                                    + "Spring 的 String 转换器默认 ISO-8859-1，中文会变成问号或乱码——"
                                    + "而且前端看起来「接口通了」，只是字不对。"
                                    + "写成 `MediaType.TEXT_EVENT_STREAM_VALUE + \";charset=UTF-8\"`。",
                            "`%s` declares `text/event-stream` but carries no `charset`. Spring's"
                                    + " String converter defaults to ISO-8859-1, so Chinese becomes"
                                    + " question marks or mojibake — and the front end sees \"the"
                                    + " endpoint works\", just with wrong characters. Write"
                                    + " `MediaType.TEXT_EVENT_STREAM_VALUE + \";charset=UTF-8\"`.",
                            pair.getNameAsString()),
                    fix(literal, "\"" + escape(value) + ";charset=UTF-8\"")));
        }
        return out;
    }

    /** 把字符串里的反斜杠和引号转回去，补丁贴进源码才是合法的 Java 字面量。 */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}