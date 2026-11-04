package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.lang.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JUnit XML，给 Jenkins / GitLab / Buildkite 这类「只认 JUnit 报告」的 CI 用。
 *
 * <p>每个文件一个 {@code <testsuite>}，每个问题一个 {@code <testcase>}。error 是
 * {@code <failure>}（会让 job 红），warning / info 是 {@code <system-out>}（job 保持绿，
 * 与「默认只有 error 才失败」的退出码语义一致）。
 */
public final class JUnitReport {

    private JUnitReport() {
    }

    public static String format(List<Finding> findings) {
        Map<String, List<Finding>> byFile = new LinkedHashMap<>();
        for (Finding finding : findings) {
            byFile.computeIfAbsent(finding.file(), key -> new ArrayList<>()).add(finding);
        }
        int failures = 0;
        for (Finding finding : findings) {
            if (finding.severity() == Severity.ERROR) {
                failures++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuites name=\"llm-lint\" tests=\"").append(findings.size())
                .append("\" failures=\"").append(failures).append("\">\n");
        for (Map.Entry<String, List<Finding>> entry : byFile.entrySet()) {
            List<Finding> fileFindings = entry.getValue();
            int fileFailures = 0;
            for (Finding finding : fileFindings) {
                if (finding.severity() == Severity.ERROR) {
                    fileFailures++;
                }
            }
            sb.append("  <testsuite name=\"").append(escape(entry.getKey()))
                    .append("\" tests=\"").append(fileFindings.size())
                    .append("\" failures=\"").append(fileFailures).append("\">\n");
            for (Finding finding : fileFindings) {
                String name = Messages.f("%s 第 %d 行", "%s at line %d", finding.ruleId(), finding.line());
                sb.append("    <testcase classname=\"").append(escape(finding.ruleId()))
                        .append("\" name=\"").append(escape(name)).append("\" time=\"0\">\n");
                String detail = detail(finding);
                if (finding.severity() == Severity.ERROR) {
                    sb.append("      <failure type=\"error\" message=\"")
                            .append(escape(finding.ruleId() + " " + finding.title()))
                            .append("\">").append(escape(detail)).append("</failure>\n");
                } else {
                    sb.append("      <system-out>").append(escape(detail)).append("</system-out>\n");
                }
                sb.append("    </testcase>\n");
            }
            sb.append("  </testsuite>\n");
        }
        sb.append("</testsuites>\n");
        return sb.toString();
    }

    private static String detail(Finding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append(finding.severity().label().toUpperCase(Locale.ROOT)).append(" ")
                .append(finding.ruleId()).append(": ").append(finding.message()).append('\n')
                .append(finding.file()).append(':').append(finding.line()).append(':')
                .append(finding.column()).append('\n')
                .append(finding.docUrl());
        return sb.toString();
    }

    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                case '\n' -> sb.append("&#10;");
                case '\r' -> sb.append("&#13;");
                case '\t' -> sb.append("&#9;");
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}