package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigException;
import io.github.pgy763.llmlint.config.ConfigLoader;
import io.github.pgy763.llmlint.config.RuleLevel;
import io.github.pgy763.llmlint.engine.Baseline;
import io.github.pgy763.llmlint.engine.ChangedFiles;
import io.github.pgy763.llmlint.engine.FileList;
import io.github.pgy763.llmlint.engine.Fixer;
import io.github.pgy763.llmlint.engine.LintEngine;
import io.github.pgy763.llmlint.engine.LintResult;
import io.github.pgy763.llmlint.engine.SourceCache;
import io.github.pgy763.llmlint.report.GitHubReport;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.report.HtmlReport;
import io.github.pgy763.llmlint.report.JsonReport;
import io.github.pgy763.llmlint.report.JUnitReport;
import io.github.pgy763.llmlint.report.MarkdownReport;
import io.github.pgy763.llmlint.report.RuleCatalog;
import io.github.pgy763.llmlint.report.SarifReport;
import io.github.pgy763.llmlint.report.SummaryReport;
import io.github.pgy763.llmlint.report.TextReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Parameters;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "llm-lint",
        mixinStandardHelpOptions = true,
        versionProvider = Main.Version.class,
        description = "Scans Spring AI / LangChain4j code and catches the expensive mistakes before they ship.")
public final class Main implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(arity = "0..*", paramLabel = "<path>",
            description = "Directories or .java files to scan")
    private List<Path> paths = new ArrayList<>();

    @Option(names = "--format", defaultValue = "text",
            description = "Output format: text (default), json, github (Actions annotations), sarif (Code Scanning), "
                    + "junit (JUnit XML for Jenkins/GitLab), html (standalone page)")
    private String format = "text";

    @Option(names = "--fail-on", defaultValue = "error",
            description = "Which level fails the run: error (default), warning, never; the command line wins over the config file")
    private String failOn = "error";

    @Option(names = "--min-severity", defaultValue = "info",
            description = "Findings below this level are neither reported nor counted: info (default), warning, error")
    private String minSeverity = "info";

    @Option(names = "--disable", split = ",",
            description = "Rules not to run, comma separated, e.g. LLM004,LLM009; prefer .llm-lint.yml for permanent switches")
    private List<String> disable = new ArrayList<>();

    @Option(names = "--exclude", split = ",",
            description = "Paths not to scan, comma separated, e.g. **/generated/**,**/test/java/**")
    private List<String> exclude = new ArrayList<>();

    @Option(names = "--jobs", paramLabel = "<n>",
            description = "How many files to scan at once: CPU count by default (max 8), 1 = one at a time")
    private int jobs;

    @Option(names = "--since", paramLabel = "<ref>",
            description = "Only scan .java files changed since this git ref, e.g. origin/main, HEAD~1")
    private String since;

    @Option(names = "--files-from", paramLabel = "<file>",
            description = "Only scan the .java files listed in this file, one path per line; - reads stdin")
    private Path filesFrom;

    @Option(names = "--config", paramLabel = "<file>",
            description = "Config file; by default .llm-lint.yml is discovered by walking up from the current directory")
    private Path configFile;

    @Option(names = "--no-config", description = "Don't read any config file, use command-line options only")
    private boolean noConfig;

    @Option(names = "--init", description = "Write a commented .llm-lint.yml in the current directory and exit")
    private boolean init;

    @Option(names = "--init-ci", arity = "0..1", paramLabel = "<dir>",
            description = "Write a GitHub Actions workflow under <dir>/.github/workflows/ (default: current directory)")
    private Path initCi;

    @Option(names = "--init-hook", arity = "0..1", paramLabel = "<dir>",
            description = "Write a pre-commit hook that lints staged .java files (default: current directory)")
    private Path initHook;

    @Option(names = "--print-config", description = "Print the effective config (with defaults) and exit")
    private boolean printConfig;

    @Option(names = "--baseline", paramLabel = "<file>",
            description = "Only report findings that are not in this baseline; known issues stay in the file and don't block CI (see docs/baseline.md)")
    private Path baselineFile;

    @Option(names = "--save-baseline", paramLabel = "<file>",
            description = "Merge this run's findings into the baseline file (existing entries are kept)")
    private Path saveBaseline;

    @Option(names = "--baseline-stats",
            description = "Show how many baseline entries remain and how many were fixed (needs --baseline or baseline: in config)")
    private boolean baselineStats;

    @Option(names = "--summary", paramLabel = "<file>",
            description = "Write a Markdown summary to a file (great for GitHub Actions step summaries)")
    private Path summary;

    @Option(names = "--markdown", paramLabel = "<file>",
            description = "Write one row per finding as Markdown (the Action posts it as a sticky PR comment)")
    private Path markdown;

    @Option(names = "--fix",
            description = "Don't print a report; print a unified diff instead (LLM006 / LLM018 have patches today)")
    private boolean fix;

    @Option(names = "--no-context", description = "Don't print the source line in text format")
    private boolean noContext;

    @Option(names = "--quiet", description = "Don't print the final summary line")
    private boolean quiet;

    @Option(names = "--lang", paramLabel = "<zh|en>",
            description = "Language for human-readable output: zh (default) or en; also LLM_LINT_LANG")
    private String lang;

    @Option(names = "--list-rules",
            description = "List every rule and exit; --format markdown gives a table, json a machine-readable catalog")
    private boolean listRules;

    @Option(names = "--explain", paramLabel = "<ID>",
            description = "Explain one rule, e.g. --explain LLM001")
    private String explain;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        String langValue = lang;
        if (langValue == null || langValue.isBlank()) {
            langValue = System.getenv("LLM_LINT_LANG");
        }
        try {
            Messages.use(Lang.of(langValue));
        } catch (IllegalArgumentException e) {
            err.println("llm-lint: unknown language \"" + lang + "\" (supported: zh, en)");
            return 2;
        }

        if (init) {
            return writeInit(out, err);
        }
        if (initCi != null) {
            return writeInitCi(out, err, initCi);
        }
        if (initHook != null) {
            return writeInitHook(out, err, initHook);
        }

        List<Rule> canonical = Rules.all();
        Config config;
        try {
            config = resolveConfig(canonical);
        } catch (ConfigException e) {
            err.println(e.getMessage());
            return 2;
        }

        if (explain != null) {
            return printExplanation(explain, config, canonical, out, err);
        }

        List<Rule> rules = config.apply(canonical);
        if (!disable.isEmpty()) {
            List<String> ids = disable.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).toList();
            rules = rules.stream().filter(r -> !ids.contains(r.id())).toList();
        }

        if (listRules) {
            if ("json".equalsIgnoreCase(format)) {
                out.print(RuleCatalog.format(canonical, config, new HashSet<>(disable)));
                out.flush();
                return 0;
            }
            Set<String> active = new HashSet<>();
            rules.forEach(r -> active.add(r.id()));
            boolean table = "markdown".equalsIgnoreCase(format);
            if (table) {
                out.printf("| %s | %s | %s | %s |%n", Messages.t("编号", "ID"),
                        Messages.t("级别", "Level"), Messages.t("框架", "Framework"),
                        Messages.t("拦的是什么", "What it catches"));
                out.println("| --- | --- | --- | --- |");
            }
            for (Rule r : canonical) {
                String level = active.contains(r.id())
                        ? config.levelOf(r.id()).map(RuleLevel::label).orElse(r.severity().label())
                        : "off";
                if (table) {
                    out.printf("| [%s](#%s) | %s | %s | %s |%n",
                            r.id(), r.id().toLowerCase(Locale.ROOT), level, framework(r), r.title());
                } else {
                    out.printf("%-7s %-8s %-13s %s%n", r.id(), level, framework(r), r.title());
                }
            }
            out.flush();
            return 0;
        }

        String failOnValue = cliGiven("--fail-on") ? failOn : config.failOnOrDefault();
        String minSeverityValue = cliGiven("--min-severity") ? minSeverity : config.minSeverityOrDefault();
        List<String> excludes = new ArrayList<>(config.exclude());
        exclude.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(excludes::add);

        if (printConfig) {
            out.print(config.effectiveYaml(canonical, excludes, failOnValue, minSeverityValue));
            out.flush();
            return 0;
        }

        if (paths.isEmpty() && since == null && filesFrom == null) {
            err.println(Messages.t("要给一个目录或 .java 文件，比如：llm-lint src/main/java",
                    "Give it a directory or a .java file, e.g. llm-lint src/main/java"));
            err.println(Messages.t("看全部规则：llm-lint --list-rules", "See all rules: llm-lint --list-rules"));
            return 2;
        }
        boolean explicitPaths = !paths.isEmpty();
        if (paths.isEmpty()) {
            // --since 不带路径时，默认看整个仓库的改动。
            paths = new ArrayList<>(List.of(Path.of(".")));
        }

        Path baselinePath = resolveBaseline(config);
        if (baselineStats && baselinePath == null) {
            err.println(Messages.t(
                    "llm-lint: --baseline-stats 得先有一份基线：加 --baseline <file>，"
                            + "或者在 .llm-lint.yml 里写 baseline:",
                    "llm-lint: --baseline-stats needs a baseline: pass --baseline <file> "
                            + "or set baseline: in .llm-lint.yml"));
            return 2;
        }
        Baseline baseline = Baseline.empty();
        if (baselinePath != null) {
            try {
                baseline = Baseline.load(baselinePath);
            } catch (IOException e) {
                err.println("llm-lint: " + e.getMessage());
                return 2;
            }
        }

        if (jobs < 0) {
            err.println(Messages.t("llm-lint: --jobs 不能是负数：1 表示一个一个扫，不写就按 CPU 核数来",
                    "llm-lint: --jobs cannot be negative; 1 means one file at a time, omit it to use the CPU count"));
            return 2;
        }
        int threads = jobs == 0 ? Math.min(Runtime.getRuntime().availableProcessors(), 8) : jobs;

        List<Path> toScan = paths;
        if (since != null) {
            try {
                toScan = ChangedFiles.collect(Path.of(""), since);
                if (explicitPaths) {
                    // 用户既给了路径又给了 --since：只在「他圈的范围」里报改动，别把别的模块也拉进来。
                    toScan = ChangedFiles.under(toScan, paths);
                }
            } catch (ChangedFiles.GitException failure) {
                err.println("llm-lint: " + failure.getMessage());
                return 2;
            }
            if (toScan.isEmpty()) {
                if (!quiet) {
                    if (explicitPaths) {
                        err.printf(Messages.t("llm-lint: --since %s 以来，这些路径下没有改动的 .java 文件%n",
                                    "llm-lint: no .java files changed under these paths since %s%n"), since);
                    } else {
                        err.printf(Messages.t("llm-lint: --since %s 以来没有改动的 .java 文件%n",
                                    "llm-lint: no .java files changed since %s%n"), since);
                    }
                }
                return 0;
            }
        }

        if (filesFrom != null) {
            List<Path> listed;
            try {
                listed = FileList.read(filesFrom);
            } catch (IOException failure) {
                err.println("llm-lint: " + failure.getMessage());
                return 2;
            }
            if (listed.isEmpty()) {
                if (!quiet) {
                    err.printf(Messages.t("llm-lint: --files-from %s 里没有能扫的 .java 文件%n",
                            "llm-lint: no scannable .java files listed in %s%n"), filesFrom);
                }
                return 0;
            }
            // 清单和路径、和 --since 一起给时取交集：三者谁窄听谁的。
            toScan = since != null || explicitPaths ? ChangedFiles.under(toScan, listed) : listed;
            if (toScan.isEmpty()) {
                if (!quiet) {
                    err.printf(Messages.t("llm-lint: --files-from 里的文件都不在你圈的范围里%n",
                            "llm-lint: none of the files in --files-from are under the paths you gave%n"));
                }
                return 0;
            }
        }

        LintResult result = new LintEngine(rules, excludes, threads).run(toScan);
        List<Finding> findings = result.findings().stream()
                .filter(f -> rank(f.severity()) >= rank(parseSeverity(minSeverityValue)))
                .toList();
        // 基线统计要的是「扫出来的全部问题」，不是去掉旧账之后剩下的那些。
        List<Finding> scanned = findings;

        if (saveBaseline != null) {
            Baseline base = baseline;
            try {
                if (baselinePath == null && Files.isRegularFile(saveBaseline)) {
                    // 只给了 --save-baseline 时，也把目标文件里已有的条目当底：重跑一次不该把
                    // 「这次新增」从 0 又数成全部，更不该把别人存过的条目弄丢。
                    base = Baseline.load(saveBaseline);
                }
                Baseline merged = base.plus(findings);
                Baseline.write(merged, saveBaseline);
                if (!quiet) {
                    err.printf(Messages.t("llm-lint: 基线 %s（共 %d 条，这次新增 %d 条）%n",
                                    "llm-lint: baseline %s (%d entries, %d new this run)%n"),
                            ConfigLoader.display(saveBaseline, Path.of("").toAbsolutePath()),
                            merged.size(), merged.size() - base.size());
                }
            } catch (IOException e) {
                err.println(Messages.t("llm-lint: 基线写不进去：", "llm-lint: cannot write the baseline: ") + e.getMessage());
                return 2;
            }
        }

        List<Finding> known = new ArrayList<>();
        List<Finding> fresh = new ArrayList<>();
        for (Finding finding : findings) {
            (baseline.contains(finding) ? known : fresh).add(finding);
        }
        findings = List.copyOf(fresh);

        if (fix) {
            String patch;
            try {
                patch = Fixer.patch(findings);
            } catch (IOException | IllegalArgumentException e) {
                err.println("llm-lint: " + e.getMessage());
                return 2;
            }
            if (patch.isEmpty()) {
                if (!findings.isEmpty()) {
                    err.println(Messages.t("llm-lint: 这些问题没有现成的补丁（目前只有 LLM006 和 LLM018 能自动修）",
                            "llm-lint: no ready-made patch for these findings (only LLM006 and LLM018 are auto-fixable today)"));
                }
            } else {
                out.print(patch);
                out.flush();
            }
        } else {
            String output = switch (format.toLowerCase(Locale.ROOT)) {
                case "json" -> JsonReport.format(findings);
                case "github" -> GitHubReport.format(findings);
                case "sarif" -> SarifReport.format(findings, rules);
                case "junit" -> JUnitReport.format(findings);
                case "html" -> HtmlReport.format(findings, result.filesScanned());
                case "text", "default" -> TextReport.format(findings, new SourceCache(), !noContext);
                default -> null;
            };
            if (output == null) {
                err.println(Messages.f("不认识的 --format：%s（text / json / github / sarif / junit / html）",
                    "Unknown --format: %s (text / json / github / sarif / junit / html)", format));
                return 2;
            }
            out.print(output);
            if (!output.isEmpty() && !output.endsWith("\n")) {
                out.println();
            }
            out.flush();
        }

        if (summary != null) {
            try {
                Path target = summary.toAbsolutePath();
                Files.createDirectories(target.getParent());
                Files.writeString(target,
                        SummaryReport.format(findings, rules, result.filesScanned(),
                                known.size(), result.suppressed()),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println(Messages.t("llm-lint: 摘要写不进去：", "llm-lint: cannot write the summary: ") + e.getMessage());
                return 2;
            }
        }

        if (markdown != null) {
            try {
                Path target = markdown.toAbsolutePath();
                Files.createDirectories(target.getParent());
                Files.writeString(target,
                        MarkdownReport.format(findings, result.filesScanned(),
                                known.size(), result.suppressed()),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println(Messages.t("llm-lint: 评论写不进去：", "llm-lint: cannot write the comment: ") + e.getMessage());
                return 2;
            }
        }

        if (!quiet) {
            if (config.file() != null) {
                err.printf(Messages.t("llm-lint: 配置 %s（%s）%n", "llm-lint: config %s (%s)%n"),
                        ConfigLoader.display(config.file(), Path.of("").toAbsolutePath()),
                        config.summary());
            }
            if (baselinePath != null) {
                err.printf(Messages.t("llm-lint: 基线 %s（%d 条）%n", "llm-lint: baseline %s (%d entries)%n"),
                        ConfigLoader.display(baselinePath, Path.of("").toAbsolutePath()),
                        baseline.size());
            }
            long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
            long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
            StringBuilder extra = new StringBuilder();
            if (!known.isEmpty()) {
                extra.append(Messages.t("，另有 ", ", ")).append(known.size())
                        .append(Messages.t(" 条在基线里", " more in the baseline"));
            }
            if (result.suppressed() > 0) {
                extra.append(Messages.t("，抑制 ", ", ")).append(result.suppressed())
                        .append(Messages.t(" 条", " suppressed"));
            }
            err.printf(Messages.t("llm-lint: %d 个问题（%d error / %d warning），扫了 %d 个文件%s%n",
                            "llm-lint: %d findings (%d error / %d warning), scanned %d files%s%n"),
                    findings.size(), errors, warnings, result.filesScanned(), extra);
            if (result.filesSkipped() > 0) {
                err.printf(Messages.t("llm-lint: 另有 %d 个文件解析失败被跳过%n",
                            "llm-lint: %d files failed to parse and were skipped%n"), result.filesSkipped());
            }
        }

        if (baselineStats) {
            Map<String, Integer> stale = baseline.staleByRule(scanned);
            int staleCount = stale.values().stream().mapToInt(Integer::intValue).sum();
            // 「按规则」要的是还在报的那些：总数减去已经修好的，不然刚修完的规则还会挂在
            // 上面，看着像没修。
            Map<String, Integer> remaining = new LinkedHashMap<>(baseline.byRule());
            stale.forEach((ruleId, count) -> remaining.merge(ruleId, -count, Integer::sum));
            remaining.values().removeIf(count -> count <= 0);

            err.printf(Messages.t("llm-lint: 基线 %s：共 %d 条，还在报 %d 条%n",
                            "llm-lint: baseline %s: %d entries, %d still reported%n"),
                    ConfigLoader.display(baselinePath, Path.of("").toAbsolutePath()),
                    baseline.size(), baseline.size() - staleCount);
            if (!remaining.isEmpty()) {
                err.printf(Messages.t("llm-lint:   按规则：%s%n", "llm-lint:   by rule: %s%n"), formatCounts(remaining));
            } else if (baseline.size() > 0) {
                err.println(Messages.t("llm-lint:   基线里的旧问题都不报了",
                    "llm-lint:   none of the baseline entries are reported anymore"));
            }
            if (staleCount > 0) {
                err.printf(Messages.t("llm-lint:   已经修好 %d 条（%s）—— 确认无误就删掉这些行，基线才算真的收紧%n",
                                "llm-lint:   %d entries are fixed (%s) - drop those lines once you are sure, "
                                        + "that is how the baseline actually tightens%n"),
                        staleCount, formatCounts(stale));
            } else {
                err.println(Messages.t("llm-lint:   没有修好的旧问题", "llm-lint:   no baseline entry has been fixed yet"));
            }
        }

        boolean fail = switch (failOnValue.toLowerCase(Locale.ROOT)) {
            case "never" -> false;
            case "warning" -> findings.stream().anyMatch(f -> rank(f.severity()) >= rank(Severity.WARNING));
            default -> findings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
        };
        return fail ? 1 : 0;
    }

    /** 显式 --config 最优先；其次从当前目录往上找；--no-config 把这些全关掉。 */
    private Config resolveConfig(List<Rule> canonical) throws ConfigException {
        if (noConfig) {
            return Config.EMPTY;
        }
        if (configFile != null) {
            return ConfigLoader.load(configFile, canonical);
        }
        return ConfigLoader.discover(Path.of("").toAbsolutePath(), paths, canonical);
    }

    /** 命令行 --baseline 最优先；其次配置文件里的 baseline（相对配置文件所在目录）。 */
    private Path resolveBaseline(Config config) {
        if (baselineFile != null) {
            return baselineFile;
        }
        String fromConfig = config.baseline();
        if (fromConfig == null) {
            return null;
        }
        Path file = Path.of(fromConfig);
        if (file.isAbsolute() || config.file() == null) {
            return file;
        }
        return config.file().toAbsolutePath().getParent().resolve(file);
    }

    private Integer writeInit(PrintWriter out, PrintWriter err) {
        Path target = Path.of(".llm-lint.yml");
        if (Files.exists(target)) {
            err.println(Messages.f("%s 已经在了，不覆盖；要重写就先删掉它。", "%s already exists; not overwriting. Delete it first.",
                    target));
            return 2;
        }
        try {
            Files.writeString(target, ConfigLoader.template(Rules.all()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println(Messages.f("写不了 %s：%s", "Cannot write %s: %s", target, e.getMessage()));
            return 2;
        }
        out.println(Messages.f("写好：%s（里面全是注释，去掉某行的 # 就是启用那条规则）",
                "Wrote %s (every rule starts commented out; drop the # to enable it)", target));
        out.println(Messages.t("改完跑一次 `llm-lint --print-config`，看实际生效的是什么。",
                "Then run `llm-lint --print-config` to see what actually takes effect."));
        return 0;
    }

    private Integer writeInitCi(PrintWriter out, PrintWriter err, Path dir) {
        Path target = dir.resolve(".github/workflows/llm-lint.yml");
        if (Files.exists(target)) {
            err.println(Messages.f("%s 已经在了，不覆盖；要重写就先删掉它。", "%s already exists; not overwriting. Delete it first.",
                    target));
            return 2;
        }
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, ciTemplate(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println(Messages.f("写不了 %s：%s", "Cannot write %s: %s", target, e.getMessage()));
            return 2;
        }
        out.println(Messages.f("写好：%s（push 到 main、开 PR 都会跑）",
                "Wrote %s (it runs on pushes to main and on pull requests)", target));
        out.println(Messages.t(
                "它用 pgy763/llm-lint@v1，第一次跑会从 release 下 jar；想钉版本就在 with: 下写 version: <版本号>。",
                "It uses pgy763/llm-lint@v1 and downloads the jar from the latest release; "
                        + "pin a version with version: <x.y.z> under with:."));
        out.println(Messages.t("想让规则开关跟着仓库走：llm-lint --init",
                "To pin rule switches in the repo: llm-lint --init"));
        return 0;
    }

    private Integer writeInitHook(PrintWriter out, PrintWriter err, Path dir) {
        Path git = dir.resolve(".git");
        if (!Files.isDirectory(git)) {
            err.println(Messages.f("%s 下面没有 .git；先在那里 git init，或者用 --init-hook <dir> 指到仓库根",
                    "No .git under %s; run git init there first, or point --init-hook <dir> at the repo root",
                    dir.toAbsolutePath()));
            return 2;
        }
        Path target = git.resolve("hooks/pre-commit");
        if (Files.exists(target)) {
            err.println(Messages.f("%s 已经在了，不覆盖；要么把它接进现有 hook，要么先删掉再重写。",
                    "%s already exists; chain it into the current hook or delete it first.", target));
            return 2;
        }
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, hookTemplate(), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows 上 chmod 不可用，git 照样按 shebang 执行。
            }
        } catch (IOException e) {
            err.println(Messages.f("写不了 %s：%s", "Cannot write %s: %s", target, e.getMessage()));
            return 2;
        }
        out.println(Messages.f("写好：%s（提交前只扫 staged 的 .java 文件，有 error 就拦下）",
                "Wrote %s (it scans staged .java files and blocks the commit on errors)", target));
        out.println(Messages.t(
                "绕过一次用 git commit --no-verify；机器上找不到 llm-lint 时 hook 会自己跳过并提示，不会把人卡死。",
                "Skip once with git commit --no-verify; if llm-lint is not installed the hook skips with a notice "
                        + "instead of blocking the commit."));
        return 0;
    }

    private static String hookTemplate() {
        return Messages.lang() == Lang.ZH ? """
                #!/bin/sh
                # llm-lint pre-commit hook —— 由 `llm-lint --init-hook` 生成。
                # 只扫这次 git add 的 .java 文件；有 error 级别的发现就拦下提交。
                # 临时绕过一次：git commit --no-verify
                # 也可以把 LLM_LINT 设成带参数的命令，比如 LLM_LINT="java -jar tools/llm-lint.jar"。

                files=$(git diff --cached --name-only --diff-filter=ACMR -- '*.java')
                [ -z "$files" ] && exit 0

                LLM_LINT="${LLM_LINT:-llm-lint}"
                if ! command -v "${LLM_LINT%% *}" >/dev/null 2>&1; then
                  echo "llm-lint: 找不到 ${LLM_LINT%% *}，跳过检查（装一份 llm-lint，或设置 LLM_LINT）" >&2
                  exit 0
                fi

                # shellcheck disable=SC2086
                $LLM_LINT --fail-on error $files

                """ : """
                #!/bin/sh
                # llm-lint pre-commit hook - generated by `llm-lint --init-hook`.
                # Scans only the .java files staged for this commit; error-level findings block it.
                # Skip once with: git commit --no-verify
                # LLM_LINT may include arguments, e.g. LLM_LINT="java -jar tools/llm-lint.jar".

                files=$(git diff --cached --name-only --diff-filter=ACMR -- '*.java')
                [ -z "$files" ] && exit 0

                LLM_LINT="${LLM_LINT:-llm-lint}"
                if ! command -v "${LLM_LINT%% *}" >/dev/null 2>&1; then
                  echo "llm-lint: '${LLM_LINT%% *}' not found, skipping (install llm-lint or set LLM_LINT)" >&2
                  exit 0
                fi

                # shellcheck disable=SC2086
                $LLM_LINT --fail-on error $files

                """;
    }

    private static String ciTemplate() {
        return Messages.lang() == Lang.ZH ? """
                # llm-lint：扫 Java 源码里 LLM 集成常见的坑。
                # 注解会挂在 PR 的「Files changed」对应行上；有 error 这条 job 就红。
                name: llm-lint

                on:
                  push:
                    branches: [main]
                  pull_request:

                permissions:
                  contents: read
                  # PR 里留一条可更新的汇总评论要用它；不想要评论就删掉这行、再写 pr-comment: 'false'
                  pull-requests: write

                jobs:
                  lint:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          # PR 里要跟目标分支比，历史得拿全
                          fetch-depth: 0
                      - uses: pgy763/llm-lint@v1
                        with:
                          path: src/main/java
                          # 只在 PR 里扫改动的文件（快很多）；push 到 main 时留空就是全量：
                          # since: origin/${{ github.base_ref }}
                          # 仓库里有 .llm-lint.yml 就会自动读；也可以显式指定：
                          # config: .llm-lint.yml
                          # 存量项目先记下旧问题，只拦新问题：
                          # baseline: .llm-lint.baseline

                """ : """
                # llm-lint: finds the usual LLM-integration traps in Java sources.
                # Annotations land on the matching lines in "Files changed"; any error fails this job.
                name: llm-lint

                on:
                  push:
                    branches: [main]
                  pull_request:

                permissions:
                  contents: read
                  # Needed to keep one updatable summary comment on the PR; drop this line and
                  # set pr-comment: 'false' if you do not want it.
                  pull-requests: write

                jobs:
                  lint:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          # A PR is compared against its base branch, so fetch the full history.
                          fetch-depth: 0
                      - uses: pgy763/llm-lint@v1
                        with:
                          path: src/main/java
                          # Only scan what the PR changed (much faster); empty on main means a full scan:
                          # since: origin/${{ github.base_ref }}
                          # A .llm-lint.yml in the repo is picked up automatically; you can also point at it:
                          # config: .llm-lint.yml
                          # For existing codebases, record old findings first and only fail on new ones:
                          # baseline: .llm-lint.baseline

                """;
    }

    private Integer printExplanation(String id, Config config, List<Rule> canonical,
                                     PrintWriter out, PrintWriter err) {
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        Rule rule = canonical.stream()
                .filter(r -> r.id().equals(wanted))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            err.println(Messages.f("没有这条规则：%s。看全部：llm-lint --list-rules",
                    "No such rule: %s. See all: llm-lint --list-rules", id));
            return 2;
        }
        out.println(rule.id() + "  " + rule.title());
        out.println();
        boolean zh = Messages.lang() == Lang.ZH;
        out.println(Messages.t("级别      ", "Level     ") + levelText(config, rule));
        out.println(Messages.t("框架      ", "Framework ") + framework(rule));
        out.println(Messages.t("文档      ", "Docs      ") + rule.docUrl());
        if (!rule.why().isEmpty()) {
            out.println();
            out.println(rule.why());
        }
        out.println();
        out.println(Messages.f("不想在某个文件里看它：在该行或上一行写 `// llm-lint:ignore %s`。",
                "To mute it in one place, put `// llm-lint:ignore %s` on that line or the line above.", rule.id()));
        out.println(Messages.f("整条关掉：`--disable %s`，或在 .llm-lint.yml 里写 `%s: off`。",
                "To turn the rule off entirely: `--disable %s`, or `%s: off` in .llm-lint.yml.", rule.id(), rule.id()));
        return 0;
    }

    /** 配置改过就说明改成了什么，没改就还是规则自己的级别。 */
    /** "LLM001 2、LLM007 120" —— 统计行的紧凑写法。 */
    private static String formatCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(Messages.t("、", ", ")));
    }

    private static String levelText(Config config, Rule rule) {
        return config.levelOf(rule.id())
                .map(level -> level == RuleLevel.OFF
                        ? Messages.t("off（.llm-lint.yml 里关掉了）", "off (turned off in .llm-lint.yml)")
                        : level.label() + Messages.f("（.llm-lint.yml 从默认的 %s 改的）",
                                " (changed from the default %s in .llm-lint.yml)", rule.severity().label()))
                .orElse(rule.severity().label());
    }

    private boolean cliGiven(String option) {
        CommandLine.ParseResult parsed = spec.commandLine().getParseResult();
        return parsed != null && parsed.hasMatchedOption(option);
    }

    private static String framework(Rule rule) {
        return switch (rule.framework()) {
            case "spring-ai" -> "Spring AI";
            case "langchain4j" -> "LangChain4j";
            default -> Messages.t("通用", "Both");
        };
    }

    private static Severity parseSeverity(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "error" -> Severity.ERROR;
            case "warning" -> Severity.WARNING;
            default -> Severity.INFO;
        };
    }

    private static int rank(Severity severity) {
        return switch (severity) {
            case ERROR -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    /** jar 里的 Implementation-Version；从 IDE 里跑就是 dev。 */
    public static final class Version implements IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[]{"llm-lint " + (version == null ? "dev" : version)};
        }
    }

    public static void main(String[] args) {
        // 没有终端（管道、CI、IDE 输出面板）时按 UTF-8 写；接在 Windows 控制台上时
        // 保持系统编码，否则中文会是乱码。
        if (System.console() == null) {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        }
        System.exit(new CommandLine(new Main()).execute(args));
    }
}