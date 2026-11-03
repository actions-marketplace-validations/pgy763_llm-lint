package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitReportTest {

    private static Finding finding(String rule, Severity severity, String file, int line, String message) {
        return new Finding(rule, severity, file, line, 9, "标题", message,
                "https://pgy763.github.io/llm-dev-pitfalls/java/x");
    }

    @Test
    void 每个文件一个testsuite每个问题一个testcase() {
        String xml = JUnitReport.format(List.of(
                finding("LLM001", Severity.ERROR, "src/A.java", 12, "事务里调模型"),
                finding("LLM007", Severity.WARNING, "src/A.java", 17, "maxTokens 太小"),
                finding("LLM010", Severity.ERROR, "src/B.java", 3, "硬编码 key")));

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), xml);
        assertTrue(xml.contains("<testsuites name=\"llm-lint\" tests=\"3\" failures=\"2\">"), xml);
        assertEquals(2, count(xml, "<testsuite "));
        assertTrue(xml.contains("<testsuite name=\"src/A.java\" tests=\"2\" failures=\"1\">"), xml);
        assertEquals(3, count(xml, "<testcase "));
        assertEquals(2, count(xml, "<failure type=\"error\""));
        assertEquals(1, count(xml, "<system-out>"));
    }

    @Test
    void warning不算失败_和默认退出码一致() {
        String xml = JUnitReport.format(List.of(
                finding("LLM007", Severity.WARNING, "src/A.java", 17, "maxTokens 太小")));
        assertTrue(xml.contains("tests=\"1\" failures=\"0\""), xml);
        assertFalse(xml.contains("<failure"), xml);
    }

    @Test
    void xml特殊字符会被转义() {
        String xml = JUnitReport.format(List.of(
                finding("LLM007", Severity.ERROR, "src/A&B.java", 1, "Mono<T> 里 a && b 变成 \"空\"\n第二行")));
        assertTrue(xml.contains("src/A&amp;B.java"), xml);
        assertTrue(xml.contains("Mono&lt;T&gt;"), xml);
        assertTrue(xml.contains("&quot;空&quot;"), xml);
        assertTrue(xml.contains("&#10;"), xml);
        assertFalse(xml.contains("<T>"), xml);
    }

    @Test
    void 没有问题时不写空的testsuite() {
        String xml = JUnitReport.format(List.of());
        assertTrue(xml.contains("<testsuites name=\"llm-lint\" tests=\"0\" failures=\"0\">"), xml);
        assertFalse(xml.contains("<testsuite "), xml);
        assertTrue(xml.endsWith("</testsuites>\n"), xml);
    }

    private static int count(String text, String needle) {
        int found = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            found++;
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }
}