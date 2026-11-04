package io.github.pgy763.llmlint.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/** 找 .java、解析、跑规则、过滤抑制注释。解析失败只提示，不中断。 */
public final class LintEngine {

    /** 结果顺序：文件、行、列、规则、消息。并行扫的时候靠它把顺序钉死。 */
    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::file)
            .thenComparingInt(Finding::line)
            .thenComparingInt(Finding::column)
            .thenComparing(Finding::ruleId)
            .thenComparing(Finding::message);

    private final List<Rule> rules;
    private final List<String> excludePatterns;
    private final int jobs;
    private final ParserConfiguration parserConfig;
    private final JavaParser parser;
    private final ThreadLocal<JavaParser> threadParsers;

    public LintEngine(List<Rule> rules, List<String> excludePatterns) {
        this(rules, excludePatterns, 1);
    }

    /**
     * @param jobs 同时扫几个文件；1 表示顺序扫。JavaParser 不是线程安全的，每个线程各拿一个。
     */
    public LintEngine(List<Rule> rules, List<String> excludePatterns, int jobs) {
        this.rules = List.copyOf(rules);
        this.excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
        this.jobs = Math.max(1, jobs);
        this.parserConfig = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setAttributeComments(false);
        this.parser = new JavaParser(parserConfig);
        this.threadParsers = ThreadLocal.withInitial(() -> new JavaParser(parserConfig));
    }

    public LintResult run(List<Path> roots) throws IOException {
        List<Path> files = collect(roots);
        SourceCache sources = new SourceCache();
        List<Partial> partials = scanAll(files, sources);

        List<Finding> findings = new ArrayList<>();
        int scanned = 0;
        int skipped = 0;
        int suppressed = 0;
        for (Partial partial : partials) {
            if (partial.parseFailed()) {
                System.err.println(Messages.f("跳过（解析失败）: %s", "skipped (parse failed): %s", partial.file()));
                skipped++;
                continue;
            }
            scanned++;
            findings.addAll(partial.findings());
            suppressed += partial.suppressed();
        }

        findings.sort(ORDER);
        return new LintResult(findings, scanned, skipped, suppressed);
    }

    private List<Partial> scanAll(List<Path> files, SourceCache sources) throws IOException {
        if (files.isEmpty()) {
            return List.of();
        }
        if (jobs == 1 || files.size() == 1) {
            List<Partial> out = new ArrayList<>(files.size());
            for (Path file : files) {
                out.add(scan(file, sources, parser));
            }
            return out;
        }

        int threads = Math.min(jobs, files.size());
        Lang lang = Messages.lang();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread thread = new Thread(r, "llm-lint-scan");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<Partial>> futures = new ArrayList<>(files.size());
            for (Path file : files) {
                futures.add(pool.submit(() -> {
                    Messages.use(lang);
                    return scan(file, sources, threadParsers.get());
                }));
            }
            // 按 files 的顺序收：日志里的「跳过（解析失败）」和最终排序都不受调度影响。
            List<Partial> out = new ArrayList<>(files.size());
            for (Future<Partial> future : futures) {
                try {
                    out.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(Messages.t("扫描被中断", "scan interrupted"), e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    throw new IOException(Messages.f("扫描失败：%s", "scan failed: %s", cause), cause);
                }
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Partial scan(Path file, SourceCache sources, JavaParser javaParser) throws IOException {
        ParseResult<CompilationUnit> parsed = javaParser.parse(file);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            return new Partial(file, true, List.of(), 0);
        }

        List<Finding> found = new ArrayList<>();
        int suppressed = 0;
        List<String> lines = sources.lines(file);
        CompilationUnit cu = parsed.getResult().get();
        for (Rule rule : rules) {
            if (Suppressions.fileSuppressed(lines, rule.id())) {
                suppressed++;
                continue;
            }
            for (Finding finding : rule.check(file, cu)) {
                if (Suppressions.suppressedAt(lines, finding.line(), finding.ruleId())) {
                    suppressed++;
                    continue;
                }
                found.add(finding);
            }
        }
        return new Partial(file, false, found, suppressed);
    }

    private record Partial(Path file, boolean parseFailed, List<Finding> findings, int suppressed) {
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
                System.err.println(Messages.f("找不到: %s", "not found: %s", root));
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

    /**
     * `**&#47;generated&#47;**` 这样的通配；不含 * 的当子串排除。
     *
     * <p>通配会拿完整路径和它的每一段后缀去试，所以 `src/generated/**` 这种「相对仓库根」
     * 的写法，不管是在仓库根扫还是 cd 进子目录扫，都能对上。
     */
    public boolean excluded(Path path) {
        String p = path.toString().replace('\\', '/');
        List<String> candidates = candidates(p, path.getFileName());
        for (String raw : excludePatterns) {
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.contains("*") || pattern.contains("?")) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                if (matcher.matches(path)) {
                    return true;
                }
                for (String candidate : candidates) {
                    if (matcher.matches(Path.of(candidate))) {
                        return true;
                    }
                }
            } else if (p.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 完整路径、文件名，以及每一段 / 之后的后缀（`src/generated/A.java` → `generated/A.java` …）。 */
    private static List<String> candidates(String normalized, Path fileName) {
        List<String> out = new ArrayList<>();
        out.add(normalized);
        if (fileName != null) {
            out.add(fileName.toString());
        }
        for (int i = normalized.indexOf('/'); i >= 0; i = normalized.indexOf('/', i + 1)) {
            out.add(normalized.substring(i + 1));
        }
        return out;
    }
}
