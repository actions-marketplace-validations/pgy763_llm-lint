package io.github.pgy763.llmlint.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** 找 .java、解析、跑规则、过滤抑制注释。解析失败只提示，不中断。 */
public final class LintEngine {

    private final List<Rule> rules;
    private final List<String> excludePatterns;
    private final JavaParser parser;

    public LintEngine(List<Rule> rules, List<String> excludePatterns) {
        this.rules = List.copyOf(rules);
        this.excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setAttributeComments(false);
        this.parser = new JavaParser(config);
    }

    public LintResult run(List<Path> roots) throws IOException {
        List<Path> files = collect(roots);
        SourceCache sources = new SourceCache();
        List<Finding> findings = new ArrayList<>();
        int scanned = 0;
        int skipped = 0;
        int suppressed = 0;

        for (Path file : files) {
            ParseResult<CompilationUnit> parsed = parser.parse(file);
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                System.err.println("跳过（解析失败）: " + file);
                skipped++;
                continue;
            }
            scanned++;
            CompilationUnit cu = parsed.getResult().get();
            List<String> lines = sources.lines(file);
            for (Rule rule : rules) {
                if (Suppressions.fileSuppressed(lines, rule.id())) {
                    suppressed++;
                    continue;
                }
                for (Finding f : rule.check(file, cu)) {
                    if (Suppressions.suppressedAt(lines, f.line(), f.ruleId())) {
                        suppressed++;
                        continue;
                    }
                    findings.add(f);
                }
            }
        }

        findings.sort(Comparator
                .comparing(Finding::file)
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::ruleId));
        return new LintResult(findings, scanned, skipped, suppressed);
    }

    public List<Path> collect(List<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                if (root.toString().endsWith(".java") && !excluded(root)) {
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
                        .filter(p -> !excluded(p))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }

    /** `**&#47;generated&#47;**` 这样的通配；不含 * 的当子串排除。 */
    public boolean excluded(Path path) {
        String p = path.toString().replace('\\', '/');
        for (String raw : excludePatterns) {
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.contains("*") || pattern.contains("?")) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                if (matcher.matches(path) || matcher.matches(Path.of(p))) {
                    return true;
                }
                // 只写文件名（如 `*Test.java`、`LLM012_*`）也算
                Path fileName = path.getFileName();
                if (fileName != null && matcher.matches(fileName)) {
                    return true;
                }
            } else if (p.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}