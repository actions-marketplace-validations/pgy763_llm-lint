package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.engine.LintEngine;
import io.github.pgy763.llmlint.report.GitHubReport;
import io.github.pgy763.llmlint.report.JsonReport;
import io.github.pgy763.llmlint.report.TextReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(
        name = "llm-lint",
        mixinStandardHelpOptions = true,
        version = "llm-lint 0.1.0",
        description = "扫一遍 Spring AI / LangChain4j 代码，在 commit 之前拦住那十个最贵的坑。")
public final class Main implements Callable<Integer> {

    @Parameters(arity = "0..*", paramLabel = "<path>",
            description = "要扫的目录或 .java 文件")
    private List<Path> paths = new ArrayList<>();

    @Option(names = "--format", defaultValue = "text",
            description = "输出格式：text（默认）、json、github（Actions 注解）")
    private String format = "text";

    @Option(names = "--fail-on", defaultValue = "error",
            description = "什么级别算失败：error（默认）、warning、never")
    private String failOn = "error";

    @Option(names = "--disable", split = ",",
            description = "不跑的规则，逗号分隔，如 LLM004,LLM009")
    private List<String> disable = new ArrayList<>();

    @Option(names = "--list-rules", description = "列出全部规则后退出")
    private boolean listRules;

    @Override
    public Integer call() throws Exception {
        List<Rule> rules = Rules.all();
        if (!disable.isEmpty()) {
            List<String> ids = disable.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).toList();
            rules = rules.stream().filter(r -> !ids.contains(r.id())).toList();
        }

        if (listRules) {
            for (Rule r : rules) {
                System.out.printf("%-7s %-8s %s%n", r.id(), r.severity(), r.title());
            }
            return 0;
        }

        if (paths.isEmpty()) {
            System.err.println("要给一个目录或 .java 文件，比如：llm-lint src/main/java");
            System.err.println("看全部规则：llm-lint --list-rules");
            return 2;
        }

        List<Finding> findings = new LintEngine(rules).run(paths);

        String output = switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> JsonReport.format(findings);
            case "github" -> GitHubReport.format(findings);
            default -> TextReport.format(findings);
        };
        System.out.print(output);
        if (!output.isEmpty() && !output.endsWith("\n")) {
            System.out.println();
        }

        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        System.err.printf("llm-lint: %d 个问题（%d error / %d warning）%n", findings.size(), errors, warnings);

        boolean fail = switch (failOn.toLowerCase(Locale.ROOT)) {
            case "never" -> false;
            case "warning" -> errors + warnings > 0;
            default -> errors > 0;
        };
        return fail ? 1 : 0;
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