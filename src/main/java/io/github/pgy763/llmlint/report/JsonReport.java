package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;

import java.util.List;

/** 给脚本和别的工具吃的格式。 */
public final class JsonReport {

    private JsonReport() {
    }

    public static String format(List<Finding> findings) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("  {")
                    .append("\"ruleId\":\"").append(escape(f.ruleId())).append("\",")
                    .append("\"severity\":\"").append(f.severity().name()).append("\",")
                    .append("\"file\":\"").append(escape(f.file())).append("\",")
                    .append("\"line\":").append(f.line()).append(',')
                    .append("\"title\":\"").append(escape(f.title())).append("\",")
                    .append("\"message\":\"").append(escape(f.message())).append("\",")
                    .append("\"docUrl\":\"").append(escape(f.docUrl())).append("\"")
                    .append('}');
            if (i < findings.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        return sb.append("]\n").toString();
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
                }
            }
        }
        return b.toString();
    }
}