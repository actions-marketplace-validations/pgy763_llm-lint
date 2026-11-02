package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigException;
import io.github.pgy763.llmlint.config.ConfigLoader;
import io.github.pgy763.llmlint.config.RuleLevel;
import io.github.pgy763.llmlint.engine.LintEngine;
import io.github.pgy763.llmlint.engine.LintResult;
import io.github.pgy763.llmlint.engine.SourceCache;
import io.github.pgy763.llmlint.report.GitHubReport;
import io.github.pgy763.llmlint.report.JsonReport;
import io.github.pgy763.llmlint.report.SarifReport;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

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

    @Option(names = "--config", paramLabel = "<file>",
            description = "配置文件；默认从当前目录往上找 .llm-lint.yml")
    private Path configFile;

    @Option(names = "--no-config", description = "不读配置文件，只用命令行参数")
    private boolean noConfig;

    @Option(names = "--init", description = "在当前目录写一份带注释的 .llm-lint.yml 后退出")
    private boolean init;

    @Option(names = "--print-config", description = "打印实际生效的配置（含默认值）后退出")
    private boolean printConfig;

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

        LintResult result = new LintEngine(rules, excludes).run(paths);
        List<Finding> findings = result.findings().stream()
                .filter(f -> rank(f.severity()) >= rank(parseSeverity(minSeverityValue)))
                .toList();

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

        if (!quiet) {
            if (config.file() != null) {
                err.printf("llm-lint: 配置 %s（%s）%n",
                        ConfigLoader.display(config.file(), Path.of("").toAbsolutePath()),
                        config.summary());
            }
            long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
            long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
            err.printf("llm-lint: %d 个问题（%d error / %d warning），扫了 %d 个文件%s%n",
                    findings.size(), errors, warnings, result.filesScanned(),
                    result.suppressed() > 0 ? "，抑制 " + result.suppressed() + " 条" : "");
            if (result.filesSkipped() > 0) {
                err.printf("llm-lint: 另有 %d 个文件解析失败被跳过%n", result.filesSkipped());
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