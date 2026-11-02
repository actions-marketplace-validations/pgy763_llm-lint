package io.github.pgy763.llmlint;

import com.github.javaparser.ast.Node;

import java.nio.file.Path;
import java.util.List;

/** 规则的公共部分：拼 docUrl、把 AST 节点变成带行号的 Finding。 */
public abstract class AbstractRule implements Rule {

    private static final String SITE = "https://pgy763.github.io/llm-dev-pitfalls";

    protected static String doc(String path) {
        return SITE + path;
    }

    protected Finding at(Path file, Node node, String message) {
        int line = node.getBegin().map(p -> p.line).orElse(0);
        return new Finding(
                id(),
                severity(),
                file.toString().replace('\\', '/'),
                line,
                title(),
                message,
                docUrl());
    }

    protected static List<Finding> none() {
        return List.of();
    }
}