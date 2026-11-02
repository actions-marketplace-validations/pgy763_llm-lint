package io.github.pgy763.llmlint;

import com.github.javaparser.ast.Node;

import java.nio.file.Path;
import java.util.List;

/** 规则的公共部分：拼 docUrl、把 AST 节点变成带行列号的 Finding。 */
public abstract class AbstractRule implements Rule {

    private static final String SITE = "https://pgy763.github.io/llm-dev-pitfalls";

    protected static String doc(String path) {
        return SITE + path;
    }

    /** 自己仓库里的规则说明页；手册里还没有对应条目时用它。 */
    protected static String docInRepo() {
        return "https://github.com/pgy763/llm-lint/blob/main/docs/rules.md";
    }

    protected Finding at(Path file, Node node, String message) {
        return at(file, node, message, null);
    }

    /** 带补丁的结果：告诉调用者这段源码可以安全地换成 {@code fix} 里的写法。 */
    protected Finding at(Path file, Node node, String message, Fix fix) {
        int line = node.getBegin().map(p -> p.line).orElse(0);
        int column = node.getBegin().map(p -> p.column).orElse(0);
        return new Finding(
                id(),
                severity(),
                file.toString().replace('\\', '/'),
                line,
                column,
                title(),
                message,
                docUrl(),
                fix);
    }

    /** 把 AST 节点的源码范围包成 Fix；节点位置认不出来时返回 null（宁可不给补丁）。 */
    protected static Fix fix(Node node, String replacement) {
        return node.getRange()
                .map(r -> new Fix(r.begin.line, r.begin.column, r.end.line, r.end.column, replacement))
                .orElse(null);
    }

    protected static List<Finding> none() {
        return List.of();
    }
}