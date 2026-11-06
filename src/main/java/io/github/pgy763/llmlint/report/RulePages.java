package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;

import java.util.Locale;

/**
 * 文档站上每条规则一页：上面那一段由工具生成，下面「为什么会踩」的手写内容不动。
 *
 * <p>生成和手写混在一页里，靠尾部标记 {@code <!-- /generated:llm001 -->} 分界：
 * 重跑 {@code llm-lint --docs docs/rules} 只替换标记以上的部分。这样级别改了、规则标题改了、
 * 文档链接改了，站点跟着变，不用人去二十几个文件里找哪张表过期了。
 */
public final class RulePages {

    /** 生成段的开始提示，放在 <a id> 之后、二级标题之前。 */
    private static final String NOTE = "<!-- 这一段由 `llm-lint --docs docs/rules` 生成，别手改；下面手写的部分会保留 -->";

    private RulePages() {
    }

    /** 文件名：llm001.md。 */
    public static String fileName(Rule rule) {
        return rule.id().toLowerCase(Locale.ROOT) + ".md";
    }

    /** 生成段的结束标记；从它往下的内容由人写。 */
    public static String endMarker(Rule rule) {
        return "<!-- /generated:" + rule.id().toLowerCase(Locale.ROOT) + " -->";
    }

    /** 生成段：front matter + 页面标题 + 一张表 + 规则自带的「为什么」+ 抑制写法。 */
    public static String head(Rule rule) {
        boolean zh = Messages.lang() == Lang.ZH;
        String id = rule.id();
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(quoted(id + " " + rule.title())).append('\n');
        sb.append("description: ").append(quoted(oneLine(rule.why()))).append('\n');
        sb.append("permalink: /rules/").append(fileName(rule), 0, fileName(rule).length() - 3).append(".html\n");
        sb.append("---\n");
        sb.append("<a id=\"").append(id.toLowerCase(Locale.ROOT)).append("\"></a>\n");
        sb.append(NOTE).append('\n');
        sb.append("## ").append(id).append(' ').append(rule.title()).append("\n\n");
        sb.append("| ").append(zh ? "项" : "Field").append(" | ").append(zh ? "值" : "Value").append(" |\n");
        sb.append("| --- | --- |\n");
        row(sb, zh ? "级别" : "Level", rule.severity().label());
        row(sb, zh ? "框架" : "Framework", framework(rule));
        row(sb, zh ? "自动修正" : "Autofix", RuleCatalog.isFixable(id)
                ? Messages.t("有现成补丁：`--fix`（`--fix --write` 直接改）",
                        "yes: `--fix` (or `--fix --write` to apply it)")
                : Messages.t("没有现成补丁，按下面的改法手工改",
                        "no patch; fix it by hand as described below"));
        row(sb, zh ? "更多" : "More", rule.docUrl());
        row(sb, zh ? "关掉它" : "Turn it off", Messages.t(".llm-lint.yml 里写 `" + id + ": off`，或命令行 `--disable "
                + id + "`", "`" + id + ": off` in .llm-lint.yml, or `--disable " + id + "` on the command line"));
        sb.append('\n');
        if (!rule.why().isEmpty()) {
            sb.append(rule.why()).append("\n\n");
        }
        sb.append(Messages.f("只想在某一处放过它：在该行或上一行写 `// llm-lint:ignore %s`。",
                "To mute it in one place, put `// llm-lint:ignore %s` on that line or the line above.", id));
        sb.append('\n');
        sb.append(endMarker(rule));
        return sb.toString();
    }

    /**
     * 拼一页：已经有的手写部分原样保留，只换掉生成段。
     *
     * @param existing 现有文件内容，没有就传 null
     */
    public static String merge(Rule rule, String existing) {
        String head = head(rule);
        if (existing == null) {
            return head + "\n\n" + Messages.t(
                    "## 为什么会踩\n\n（这段手写：出现时的样子、改法、判断范围。工具每次生成只换上面的表。）\n",
                    "## Why it bites\n\n(hand-written: what it looks like, how to fix, how far the rule goes.)\n");
        }
        String marker = endMarker(rule);
        int at = existing.indexOf(marker);
        if (at < 0) {
            return head + '\n' + existing.strip() + '\n';
        }
        String tail = existing.substring(at + marker.length());
        return head + (tail.isBlank() ? "\n" : tail);
    }

    private static void row(StringBuilder sb, String key, String value) {
        sb.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static String framework(Rule rule) {
        return switch (rule.framework()) {
            case "spring-ai" -> "Spring AI";
            case "langchain4j" -> "LangChain4j";
            default -> Messages.t("通用", "Both");
        };
    }

    /** front matter 里一律用双引号包着，内部的引号和反斜杠要转义。 */
    private static String quoted(String raw) {
        String text = raw.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + text + "\"";
    }

    /** description 只要一句，换行和多余空白都压成单个空格。 */
    private static String oneLine(String raw) {
        String text = raw.replaceAll("\\s+", " ").strip();
        if (text.length() <= 120) {
            return text;
        }
        int cut = text.lastIndexOf('。', 120);
        if (cut > 40) {
            return text.substring(0, cut + 1);
        }
        return text.substring(0, 119) + "…";
    }
}