package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReportTest {

    private static Finding finding(String rule, Severity severity, String file, int line, String message) {
        return new Finding(rule, severity, file, line, 9, "标题", message,
                "https://pgy763.github.io/llm-dev-pitfalls/java/x");
    }

    @AfterEach
    void reset() {
        Messages.use(Lang.ZH);
    }

    @Test
    void 页面能独立打开_带汇总和表格() {
        String html = HtmlReport.format(List.of(
                finding("LLM001", Severity.ERROR, "src/A.java", 12, "事务里调模型"),
                finding("LLM007", Severity.WARNING, "src/A.java", 17, "maxTokens 太小")), 4);

        assertTrue(html.startsWith("<!DOCTYPE html>"), html);
        assertTrue(html.contains("<meta charset=\"utf-8\">"), html);
        assertTrue(html.contains("<title>llm-lint</title>"), html);
        assertTrue(html.contains("<style>"), html);
        assertTrue(html.contains("2 个问题（1 error / 1 warning），扫了 4 个文件。"), html);
        assertTrue(html.contains("<h2>src/A.java</h2>"), html);
        assertTrue(html.contains("<table>"), html);
        assertTrue(html.contains("href=\"https://pgy763.github.io/llm-dev-pitfalls/java/x\""), html);
        assertTrue(html.contains("12:9"), html);
        assertTrue(html.endsWith("</html>\n"), html);
    }

    @Test
    void 消息里的html会被转义() {
        String html = HtmlReport.format(List.of(
                finding("LLM007", Severity.ERROR, "src/A.java", 1, "<script>alert(1)</script> & <b>")), 1);
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &lt;b&gt;"), html);
        assertFalse(html.contains("<script>alert"), html);
    }

    @Test
    void 干净的项目是一句话() {
        String html = HtmlReport.format(List.of(), 3);
        assertTrue(html.contains("没有发现问题，扫了 3 个文件。"), html);
        assertFalse(html.contains("<table>"), html);
    }

    @Test
    void 英文模式输出英文页面() {
        Messages.use(Lang.EN);
        String html = HtmlReport.format(List.of(
                finding("LLM001", Severity.ERROR, "src/A.java", 12, "model call")), 2);
        assertTrue(html.contains("1 findings (1 error / 0 warning), scanned 2 files."), html);
        assertTrue(html.contains("<th>Problem</th>"), html);
        assertTrue(html.contains("<html lang=\"en\">"), html);
    }
}