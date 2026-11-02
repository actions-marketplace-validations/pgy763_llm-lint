package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.engine.SourceCache;

import java.util.List;

/**
 * 人看的格式：按文件分组，一行位置，一段源码（带 ^ 指向具体位置），一行原因，一行手册链接。
 *
 * <p>带源码是刻意的：报错行和「为什么要管」放在一起，人才不用来回切窗口。
 */
public final class TextReport {

    private static final int MAX_CODE_WIDTH = 120;

    private TextReport() {
    }

    public static String format(List<Finding> findings, SourceCache sources, boolean context) {
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
            String pos = f.column() > 0 ? f.line() + ":" + f.column() : String.valueOf(f.line());
            sb.append("  ").append(pos).append("  ").append(f.severity().label())
                    .append("  ").append(f.ruleId())
                    .append("  ").append(f.title()).append('\n');
            if (context) {
                sb.append(codeFrame(sources.lineOf(f.file(), f.line()), f.column(), f.line()));
            }
            sb.append("      ").append(f.message()).append('\n');
            sb.append("      → ").append(f.docUrl()).append('\n');
        }
        return sb.toString();
    }

    private static String codeFrame(String code, int column, int line) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String shown = code;
        boolean cut = false;
        if (shown.length() > MAX_CODE_WIDTH) {
            shown = shown.substring(0, MAX_CODE_WIDTH);
            cut = true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("      ").append(line).append(" | ").append(cut ? shown + "…" : shown).append('\n');
        if (column > 0 && column <= shown.length()) {
            int width = String.valueOf(line).length();
            sb.append("      ").append(" ".repeat(width)).append(" | ")
                    .append(" ".repeat(column - 1)).append('^').append('\n');
        }
        return sb.toString();
    }
}