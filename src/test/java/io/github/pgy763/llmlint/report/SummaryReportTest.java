package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rules;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryReportTest {

    private static Finding finding(String ruleId, Severity severity, String file) {
        return new Finding(ruleId, severity, file, 1, 1, "标题", "消息", "https://example.com");
    }

    @Test
    void 按条数从多到少列出来() {
        List<Finding> findings = List.of(
                finding("LLM007", Severity.WARNING, "a.java"),
                finding("LLM007", Severity.WARNING, "b.java"),
                finding("LLM010", Severity.ERROR, "c.java"));

        String text = SummaryReport.format(findings, Rules.all(), 5, 0, 0);

        assertTrue(text.startsWith("## llm-lint\n\n**3 个问题**（1 error / 2 warning），扫了 5 个文件。\n"), text);
        int seven = text.indexOf("| LLM007 | warning | 2 |");
        int ten = text.indexOf("| LLM010 | error | 1 |");
        assertTrue(seven > 0 && ten > seven, text);
    }

    @Test
    void 没问题时也说扫了多少() {
        String text = SummaryReport.format(List.of(), Rules.all(), 3, 2, 1);

        assertTrue(text.contains("没有发现问题，扫了 3 个文件。"), text);
        assertTrue(text.contains("另有 2 条在基线里"), text);
        assertTrue(text.contains("抑制 1 条"), text);
    }

    @Test
    void 级别以这次的结果为准() {
        // 配置把 LLM007 改成了 error，摘要里就该是 error，而不是规则默认的 warning。
        List<Finding> findings = List.of(finding("LLM007", Severity.ERROR, "a.java"));

        String text = SummaryReport.format(findings, Rules.all(), 1, 0, 0);

        assertEquals("## llm-lint\n\n**1 个问题**（1 error / 0 warning），扫了 1 个文件。\n\n"
                + "| 规则 | 级别 | 条数 |\n"
                + "| --- | --- | ---: |\n"
                + "| LLM007 | error | 1 |\n", text);
    }
}