package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigException;
import io.github.pgy763.llmlint.config.ConfigLoader;
import io.github.pgy763.llmlint.config.RuleLevel;
import io.github.pgy763.llmlint.engine.Baseline;
import io.github.pgy763.llmlint.engine.LintEngine;
import io.github.pgy763.llmlint.engine.LintResult;
import io.github.pgy763.llmlint.engine.SourceCache;
import io.github.pgy763.llmlint.report.GitHubReport;
import io.github.pgy763.llmlint.report.JsonReport;
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
        description = "扫一遍 Spring AI / LangChain4j 代码，在 commit 之前拦住最贵的那些坑。")
public final class Main implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(arity = "0..*", paramLabel = "<path>",
            description = "要扫的目录或 .java 文件")
    private List<Path> paths = new ArrayList<>();

    @Option(names = "--format", defaultValue = "text",
            description = "输出格式：text（默认）、json、github（Actions 注解）、sarif（Code Scanning）")
    private String format = "text";

    @Option(names = "--fail-on", defaultValue = "error",
            description = "什么级别算失败：error（默认）、warning、never；命令行赢了配置文件")
    private String failOn = "error";

    @Option(names = "--min-severity", defaultValue = "info",
            description = "低于这个级别的不报也不计：info（默认）、warning、error")
    private String minSeverity = "info";

    @Option(names = "--disable", split = ",",
            description = "不跑的规则，逗号分隔，如 LLM004,LLM009；长期关掉请写进 .llm-lint.yml")
    private List<String> disable = new ArrayList<>();

    @Option(names = "--exclude", split = ",",
            description = "不扫的路径，逗号分隔，如 **/generated/**,**/test/java/**")
    private List<String> exclude = new ArrayList<>();

    @Option(names = "--jobs", paramLabel = "<n>",
            description = "同时扫几个文件：默认按 CPU 核数（最多 8），1 表示一个一个来")
    private int jobs;

    @Option(names = "--config", paramLabel = "<file>",
            description = "配置文件；默认从当前目录往上找 .llm-lint.yml")
    private Path configFile;

    @Option(names = "--no-config", description = "不读配置文件，只用命令行参数")
    private boolean noConfig;

    @Option(names = "--init", description = "在当前目录写一份带注释的 .llm-lint.yml 后退出")
    private boolean init;

    @Option(names = "--init-ci", arity = "0..1", paramLabel = "<dir>",
            description = "在 <dir>/.github/workflows/ 下写一份 GitHub Actions 配置（默认当前目录）")
    private Path initCi;

    @Option(names = "--print-config", description = "打印实际生效的配置（含默认值）后退出")
    private boolean printConfig;

    @Option(names = "--baseline", paramLabel = "<file>",
            description = "只报不在这个基线里的问题；旧问题记在文件里，不卡 CI（见 docs/baseline.md）")
    private Path baselineFile;

    @Option(names = "--save-baseline", paramLabel = "<file>",
            description = "把这次的结果记进基线文件（已有的保留），给存量项目开个头")
    private Path saveBaseline;

    @Option(names = "--baseline-stats",
            description = "打印基线里还剩多少条、已经修好多少条（需要 --baseline 或配置里的 baseline:）")
    private boolean baselineStats;

    @Option(names = "--summary", paramLabel = "<file>",
            description = "把一段 Markdown 摘要写进文件（适合 GitHub Actions 的 step summary）")
    private Path summary;

    @Option(names = "--no-context", description = "text 格式下不打印源码行")
    private boolean noContext;

    @Option(names = "--quiet", description = "不打印最后那行统计")
    private boolean quiet;

    @Option(names = "--list-rules", description = "列出全部规则后退出")
    private boolean listRules;

    @Option(names = "--explain", paramLabel = "<ID>",
            description = "详细说明一条规则，如 --explain LLM001")
    private String explain;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (init) {
            return writeInit(out, err);
        }
        if (initCi != null) {
            return writeInitCi(out, err, initCi);
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
            Set<String> active = new HashSet<>();
            rules.forEach(r -> active.add(r.id()));
            for (Rule r : canonical) {
                String level = active.contains(r.id())
                        ? config.levelOf(r.id()).map(RuleLevel::label).orElse(r.severity().label())
                        : "off";
                out.printf("%-7s %-8s %-13s %s%n", r.id(), level, framework(r), r.title());
            }
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

        if (paths.isEmpty()) {
            err.println("要给一个目录或 .java 文件，比如：llm-lint src/main/java");
            err.println("看全部规则：llm-lint --list-rules");
            return 2;
        }

        Path baselinePath = resolveBaseline(config);
        if (baselineStats && baselinePath == null) {
            err.println("llm-lint: --baseline-stats 得先有一份基线：加 --baseline <file>，"
                    + "或者在 .llm-lint.yml 里写 baseline:");
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
            err.println("llm-lint: --jobs 不能是负数：1 表示一个一个扫，不写就按 CPU 核数来");
            return 2;
        }
        int threads = jobs == 0 ? Math.min(Runtime.getRuntime().availableProcessors(), 8) : jobs;

        LintResult result = new LintEngine(rules, excludes, threads).run(paths);
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
                    err.printf("llm-lint: 基线 %s（共 %d 条，这次新增 %d 条）%n",
                            ConfigLoader.display(saveBaseline, Path.of("").toAbsolutePath()),
                            merged.size(), merged.size() - base.size());
                }
            } catch (IOException e) {
                err.println("llm-lint: 基线写不进去：" + e.getMessage());
                return 2;
            }
        }

        List<Finding> known = new ArrayList<>();
        List<Finding> fresh = new ArrayList<>();
        for (Finding finding : findings) {
            (baseline.contains(finding) ? known : fresh).add(finding);
        }
        findings = List.copyOf(fresh);

        String output = switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> JsonReport.format(findings);
            case "github" -> GitHubReport.format(findings);
            case "sarif" -> SarifReport.format(findings, rules);
            case "text", "default" -> TextReport.format(findings, new SourceCache(), !noContext);
            default -> null;
        };
        if (output == null) {
            err.println("不认识的 --format：" + format + "（text / json / github / sarif）");
            return 2;
        }
        out.print(output);
        if (!output.isEmpty() && !output.endsWith("\n")) {
            out.println();
        }
        out.flush();

        if (summary != null) {
            try {
                Path target = summary.toAbsolutePath();
                Files.createDirectories(target.getParent());
                Files.writeString(target,
                        SummaryReport.format(findings, rules, result.filesScanned(),
                                known.size(), result.suppressed()),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println("llm-lint: 摘要写不进去：" + e.getMessage());
                return 2;
            }
        }

        if (!quiet) {
            if (config.file() != null) {
                err.printf("llm-lint: 配置 %s（%s）%n",
                        ConfigLoader.display(config.file(), Path.of("").toAbsolutePath()),
                        config.summary());
            }
            if (baselinePath != null) {
                err.printf("llm-lint: 基线 %s（%d 条）%n",
                        ConfigLoader.display(baselinePath, Path.of("").toAbsolutePath()),
                        baseline.size());
            }
            long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
            long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
            StringBuilder extra = new StringBuilder();
            if (!known.isEmpty()) {
                extra.append("，另有 ").append(known.size()).append(" 条在基线里");
            }
            if (result.suppressed() > 0) {
                extra.append("，抑制 ").append(result.suppressed()).append(" 条");
            }
            err.printf("llm-lint: %d 个问题（%d error / %d warning），扫了 %d 个文件%s%n",
                    findings.size(), errors, warnings, result.filesScanned(), extra);
            if (result.filesSkipped() > 0) {
                err.printf("llm-lint: 另有 %d 个文件解析失败被跳过%n", result.filesSkipped());
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

            err.printf("llm-lint: 基线 %s：共 %d 条，还在报 %d 条%n",
                    ConfigLoader.display(baselinePath, Path.of("").toAbsolutePath()),
                    baseline.size(), baseline.size() - staleCount);
            if (!remaining.isEmpty()) {
                err.printf("llm-lint:   按规则：%s%n", formatCounts(remaining));
            } else if (baseline.size() > 0) {
                err.println("llm-lint:   基线里的旧问题都不报了");
            }
            if (staleCount > 0) {
                err.printf("llm-lint:   已经修好 %d 条（%s）—— 确认无误就删掉这些行，基线才算真的收紧%n",
                        staleCount, formatCounts(stale));
            } else {
                err.println("llm-lint:   没有修好的旧问题");
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
            err.println("已经有 " + target + " 了，不覆盖；要重写就先删掉它。");
            return 2;
        }
        try {
            Files.writeString(target, ConfigLoader.template(Rules.all()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("写不了 " + target + "：" + e.getMessage());
            return 2;
        }
        out.println("写好：" + target + "（里面全是注释，去掉某行的 # 就是启用那条规则）");
        out.println("改完跑一次 `llm-lint --print-config`，看实际生效的是什么。");
        return 0;
    }

    private Integer writeInitCi(PrintWriter out, PrintWriter err, Path dir) {
        Path target = dir.resolve(".github/workflows/llm-lint.yml");
        if (Files.exists(target)) {
            err.println("已经有 " + target + " 了，不覆盖；要重写就先删掉它。");
            return 2;
        }
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, ciTemplate(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("写不了 " + target + "：" + e.getMessage());
            return 2;
        }
        out.println("写好：" + target + "（push 到 main、开 PR 都会跑）");
        out.println("它用 pgy763/llm-lint@v1，第一次跑会从 release 下 jar；想钉版本就在 with: 下写 version: <版本号>。");
        out.println("想让规则开关跟着仓库走：llm-lint --init");
        return 0;
    }

    private static String ciTemplate() {
        return """
                # llm-lint：扫 Java 源码里 LLM 集成常见的坑。
                # 注解会挂在 PR 的「Files changed」对应行上；有 error 这条 job 就红。
                name: llm-lint

                on:
                  push:
                    branches: [main]
                  pull_request:

                permissions:
                  contents: read

                jobs:
                  lint:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: pgy763/llm-lint@v1
                        with:
                          path: src/main/java
                          # 仓库里有 .llm-lint.yml 就会自动读；也可以显式指定：
                          # config: .llm-lint.yml
                          # 存量项目先记下旧问题，只拦新问题：
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
            err.println("没有这条规则：" + id + "。看全部：llm-lint --list-rules");
            return 2;
        }
        out.println(rule.id() + "  " + rule.title());
        out.println();
        out.println("级别      " + levelText(config, rule));
        out.println("框架      " + framework(rule));
        out.println("文档      " + rule.docUrl());
        if (!rule.why().isEmpty()) {
            out.println();
            out.println(rule.why());
        }
        out.println();
        out.println("不想在某个文件里看它：在该行或上一行写 `// llm-lint:ignore " + rule.id() + "`。");
        out.println("整条关掉：`--disable " + rule.id() + "`，或在 .llm-lint.yml 里写 `" + rule.id() + ": off`。");
        return 0;
    }

    /** 配置改过就说明改成了什么，没改就还是规则自己的级别。 */
    /** "LLM001 2、LLM007 120" —— 统计行的紧凑写法。 */
    private static String formatCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("、"));
    }

    private static String levelText(Config config, Rule rule) {
        return config.levelOf(rule.id())
                .map(level -> level == RuleLevel.OFF
                        ? "off（.llm-lint.yml 里关掉了）"
                        : level.label() + "（.llm-lint.yml 从默认的 " + rule.severity().label() + " 改的）")
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
            default -> "通用";
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