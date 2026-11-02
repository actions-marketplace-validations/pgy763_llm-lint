package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PR 评论那几行 Markdown：会不会把表格切坏，决定别人能不能一眼看懂。 */
class MarkdownReportTest {

    private static Finding finding(String ruleId, Severity severity, String file, int line,
                                   String message) {
        return new Finding(ruleId, severity, file, line, 9, "标题", message,
                "https://github.com/pgy763/llm-lint/blob/main/docs/rules.md#" + ruleId.toLowerCase());
    }

    @Test
    void 没有问题时就一句话() {
        String text = MarkdownReport.format(List.of(), 3, 0, 0);

        assertTrue(text.startsWith(MarkdownReport.MARKER), text);
        assertTrue(text.contains("**llm-lint：没有发现问题**，扫了 3 个文件。"), text);
        assertFalse(text.contains("| 级别 |"), text);
    }

    @Test
    void 每条问题一行带文件行号和规则链接() {
        String text = MarkdownReport.format(List.of(
                finding("LLM007", Severity.WARNING, "src/A.java", 12, "只留了 128 个 token"),
                finding("LLM004", Severity.ERROR, "src/B.java", 30, "SSE 没超时")), 2, 0, 0);

        assertTrue(text.contains("**llm-lint：2 个问题**（1 error / 1 warning），扫了 2 个文件。"), text);
        assertTrue(text.contains("| warning | [LLM007](https://github.com/pgy763/llm-lint/blob/main/docs/rules.md#llm007) "
                + "| `src/A.java:12` | 只留了 128 个 token |"), text);
        assertTrue(text.contains("| error | [LLM004]"), text);
    }

    @Test
    void 竖线和尖括号不会把表格切坏() {
        String text = MarkdownReport.format(List.of(
                finding("LLM015", Severity.WARNING, "src/A.java", 3, "`Mono<T>` 里 a | b & c")), 1, 0, 0);

        assertTrue(text.contains("`Mono&lt;T&gt;` 里 a \\| b &amp; c"), text);
        for (String line : text.split("\n")) {
            if (line.startsWith("| warning |")) {
                long pipes = 0;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '|' && (i == 0 || line.charAt(i - 1) != '\\')) {
                        pipes++;
                    }
                }
                assertEquals(5, pipes, line);
            }
        }
    }

    @Test
    void 基线里的和抑制掉的会在末尾说一声() {
        String text = MarkdownReport.format(List.of(), 1, 2, 1);

        assertTrue(text.contains("另有 2 条在基线里，这次不报。"), text);
        assertTrue(text.contains("抑制 1 条"), text);
    }
}