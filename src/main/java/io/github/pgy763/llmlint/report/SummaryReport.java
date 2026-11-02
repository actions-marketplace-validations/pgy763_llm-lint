package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.Severity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一段 Markdown 摘要：给 GitHub Actions 的 step summary、PR 评论、issue 模板用。
 *
 * <p>只写结论和按规则的分布，不写每条问题的细节——细节在注解、SARIF 或终端里，
 * 这里要的是一眼看清「这次有多脏、脏在哪条规则」。
 */
public final class SummaryReport {

    private SummaryReport() {
    }

    public static String format(List<Finding> findings, List<Rule> rules, int filesScanned,
                                int known, int suppressed) {
        StringBuilder sb = new StringBuilder();
        sb.append("## llm-lint\n\n");

        if (findings.isEmpty()) {
            sb.append("没有发现问题，扫了 ").append(filesScanned).append(" 个文件。\n");
            appendTail(sb, known, suppressed);
            return sb.toString();
        }

        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        sb.append("**").append(findings.size()).append(" 个问题**（")
                .append(errors).append(" error / ").append(warnings).append(" warning），扫了 ")
                .append(filesScanned).append(" 个文件。\n\n");

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Finding finding : findings) {
            counts.merge(finding.ruleId(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> rows = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();

        sb.append("| 规则 | 级别 | 条数 |\n")
                .append("| --- | --- | ---: |\n");
        for (Map.Entry<String, Integer> row : rows) {
            sb.append("| ").append(row.getKey()).append(" | ")
                    .append(levelOf(row.getKey(), rules, findings)).append(" | ")
                    .append(row.getValue()).append(" |\n");
        }
        appendTail(sb, known, suppressed);
        return sb.toString();
    }

    private static void appendTail(StringBuilder sb, int known, int suppressed) {
        if (known <= 0 && suppressed <= 0) {
            return;
        }
        sb.append('\n');
        if (known > 0) {
            sb.append("另有 ").append(known).append(" 条在基线里，这次不报。\n");
        }
        if (suppressed > 0) {
            sb.append("抑制 ").append(suppressed).append(" 条（注释里写了 llm-lint:ignore）。\n");
        }
    }

    /** 同一编号在 findings 里的级别；配置改过级别时以 findings 为准。 */
    private static String levelOf(String ruleId, List<Rule> rules, List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.ruleId().equals(ruleId)) {
                return finding.severity().label();
            }
        }
        for (Rule rule : rules) {
            if (rule.id().equals(ruleId)) {
                return rule.severity().label();
            }
        }
        return "info";
    }
}