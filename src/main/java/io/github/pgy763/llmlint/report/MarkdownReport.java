package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;

import java.util.List;

/**
 * PR 评论用的 Markdown：一条问题一行的明细表。
 *
 * <p>和 {@link SummaryReport} 的分工：摘要回答「这次有多脏、脏在哪条规则」，给 job 页面看；
 * 这里回答「具体哪一行、什么问题」，给评审的人看——所以文件、行号、规则链接都得有。
 *
 * <p>开头那行 {@link #MARKER} 是给 Action 用的：靠它认出「这条评论是我上次留的」，
 * 然后原地更新，而不是每跑一次就在 PR 里堆一条。
 */
public final class MarkdownReport {

    /** 评论开头的隐藏标记，Action 用它找到上一次的评论。 */
    public static final String MARKER = "<!-- llm-lint -->";

    private MarkdownReport() {
    }

    public static String format(List<Finding> findings, int filesScanned, int known, int suppressed) {
        StringBuilder sb = new StringBuilder(MARKER).append('\n');

        if (findings.isEmpty()) {
            sb.append("**llm-lint：没有发现问题**，扫了 ").append(filesScanned).append(" 个文件。\n");
            appendTail(sb, known, suppressed);
            return sb.toString();
        }

        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        sb.append("**llm-lint：").append(findings.size()).append(" 个问题**（")
                .append(errors).append(" error / ").append(warnings).append(" warning），扫了 ")
                .append(filesScanned).append(" 个文件。\n\n");

        sb.append("| 级别 | 规则 | 位置 | 问题 |\n")
                .append("| --- | --- | --- | --- |\n");
        for (Finding finding : findings) {
            sb.append("| ").append(finding.severity().label())
                    .append(" | [").append(finding.ruleId()).append("](").append(finding.docUrl()).append(")")
                    .append(" | `").append(cell(finding.file())).append(':').append(finding.line()).append('`')
                    .append(" | ").append(cell(finding.message())).append(" |\n");
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

    /**
     * 表格里的一个格子：竖线会把列切开，换行会把行切断，尖括号会被 GitHub 当成 HTML 吃掉。
     * 消息里这些字符都真出现过（比如 {@code Mono<T>}、{@code a || b}），所以统一转义。
     */
    static String cell(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "\\|")
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
    }
}