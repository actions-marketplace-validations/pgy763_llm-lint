package io.github.pgy763.llmlint.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 找 .java、解析、跑规则。解析失败只提示，不中断。 */
public final class LintEngine {

    private final List<Rule> rules;
    private final JavaParser parser;

    public LintEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setAttributeComments(false);
        this.parser = new JavaParser(config);
    }

    public List<Finding> run(List<Path> roots) throws IOException {
        List<Path> files = collect(roots);
        List<Finding> findings = new ArrayList<>();
        for (Path file : files) {
            ParseResult<CompilationUnit> parsed = parser.parse(file);
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                System.err.println("跳过（解析失败）: " + file);
                continue;
            }
            CompilationUnit cu = parsed.getResult().get();
            for (Rule rule : rules) {
                findings.addAll(rule.check(file, cu));
            }
        }
        findings.sort(Comparator
                .comparing(Finding::file)
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::ruleId));
        return findings;
    }

    public static List<Path> collect(List<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                if (root.toString().endsWith(".java")) {
                    files.add(root);
                }
                continue;
            }
            if (!Files.isDirectory(root)) {
                System.err.println("找不到: " + root);
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("/target/"))
                        .filter(p -> !p.toString().contains("/build/"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }
}