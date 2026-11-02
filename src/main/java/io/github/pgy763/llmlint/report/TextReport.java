package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;

import java.util.List;

/** 人看的格式：按文件分组，一行位置，一行原因，一行手册链接。 */
public final class TextReport {

    private TextReport() {
    }

    public static String format(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        String currentFile = null;
        for (Finding f : findings) {
            if (!f.file().equals(currentFile)) {
                if (currentFile != null) {
                    sb.append('\n');
                }
                sb.append(f.file()).append('\n');
                currentFile = f.file();
            }
            sb.append("  ").append(f.line())
                    .append("  ").append(f.severity())
                    .append("  ").append(f.ruleId())
                    .append("  ").append(f.title()).append('\n');
            sb.append("      ").append(f.message()).append('\n');
            sb.append("      → ").append(f.docUrl()).append('\n');
        }
        return sb.toString();
    }
}