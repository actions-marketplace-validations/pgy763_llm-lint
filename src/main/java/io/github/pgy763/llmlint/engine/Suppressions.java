package io.github.pgy763.llmlint.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 注释里的抑制指令。
 *
 * <pre>
 * // llm-lint:ignore                 这一行不报
 * // llm-lint:ignore LLM004,LLM009   只放过这两条
 * // llm-lint:ignore-file            整个文件不报
 * // llm-lint:ignore-file LLM008     整个文件只放过这一条
 * </pre>
 *
 * <p>写在问题所在行或它的上一行都算。规则关掉容易，但每一条都留在源码里，
 * 评审时看得见——这比配置文件里静默生效好。
 */
public final class Suppressions {

    private static final String MARK = "llm-lint:ignore";
    private static final Pattern ID = Pattern.compile("LLM\\d{3}");

    private Suppressions() {
    }

    /** 这一行的这条规则被抑制了吗。 */
    public static boolean suppressedAt(List<String> lines, int line, String ruleId) {
        for (int i = line - 1; i >= 0 && i >= line - 2; i--) {
            if (i >= lines.size()) {
                continue;
            }
            String text = lines.get(i);
            int at = text.indexOf(MARK);
            if (at < 0 || text.startsWith(MARK + "-file", at)) {
                continue;
            }
            if (applies(text.substring(at + MARK.length()), ruleId)) {
                return true;
            }
        }
        return false;
    }

    /** 整个文件被抑制了吗。 */
    public static boolean fileSuppressed(List<String> lines, String ruleId) {
        for (String text : lines) {
            int at = text.indexOf(MARK + "-file");
            if (at < 0) {
                continue;
            }
            if (applies(text.substring(at + MARK.length() + 5), ruleId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean applies(String rest, String ruleId) {
        String tail = rest;
        int commentEnd = tail.indexOf("*/");
        if (commentEnd >= 0) {
            tail = tail.substring(0, commentEnd);
        }
        int lineComment = tail.indexOf("//");
        if (lineComment >= 0) {
            tail = tail.substring(0, lineComment);
        }
        List<String> ids = new ArrayList<>();
        Matcher m = ID.matcher(tail.toUpperCase(Locale.ROOT));
        while (m.find()) {
            ids.add(m.group());
        }
        return ids.isEmpty() || ids.contains(ruleId);
    }
}