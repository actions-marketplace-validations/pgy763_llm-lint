package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.RuleLevel;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;

import java.util.List;

/**
 * 一条规则的说明，写成 Markdown。
 *
 * <p>给两种人用：一种是把规则贴进 issue、PR 或群里解释「这条为什么报」的人，
 * 另一种是想给博客、内部 wiki 生成一份规则速查表的人 —— {@code --explain all --format markdown}
 * 直接把二十二条拼成一份文档，不用手抄。
 */
public final class ExplainReport {

    private ExplainReport() {
    }

    public static String markdown(List<Rule> rules, Config config) {
        StringBuilder sb = new StringBuilder();
        boolean zh = Messages.lang() == Lang.ZH;
        for (Rule rule : rules) {
            sb.append("## ").append(rule.id()).append(' ').append(rule.title()).append("\n\n");
            sb.append("| ").append(zh ? "项" : "Field")
                    .append(" | ").append(zh ? "值" : "Value").append(" |\n");
            sb.append("| --- | --- |\n");
            sb.append("| ").append(zh ? "级别" : "Level").append(" | ")
                    .append(levelText(rule, config)).append(" |\n");
            sb.append("| ").append(zh ? "框架" : "Framework").append(" | ")
                    .append(framework(rule)).append(" |\n");
            sb.append("| ").append(zh ? "文档" : "Docs").append(" | ")
                    .append(rule.docUrl()).append(" |\n\n");
            if (!rule.why().isEmpty()) {
                sb.append(rule.why()).append("\n\n");
            }
            String overrides = overridesList(rule, config);
            if (!overrides.isEmpty()) {
                sb.append(Messages.t("按路径覆盖：", "Per-path overrides: ")).append(overrides).append("\n\n");
            }
            sb.append(Messages.f("不想在某个文件里看它：在该行或上一行写 `// llm-lint:ignore %s`。",
                            "To mute it in one place, put `// llm-lint:ignore %s` on that line or the line above.",
                            rule.id()))
                    .append(' ');
            sb.append(Messages.f("整条关掉：`--disable %s`，或在 .llm-lint.yml 里写 `%s: off`。",
                            "To turn the rule off entirely: `--disable %s`, or `%s: off` in .llm-lint.yml.",
                            rule.id(), rule.id()))
                    .append("\n\n");
        }
        return sb.toString();
    }

    /** 生效级别：配置文件改过就说明改成了什么。 */
    private static String levelText(Rule rule, Config config) {
        return config.levelOf(rule.id())
                .map(level -> level == RuleLevel.OFF
                        ? Messages.t("off（.llm-lint.yml 里关掉了）", "off (turned off in .llm-lint.yml)")
                        : level.label() + Messages.f("（.llm-lint.yml 从默认的 %s 改的）",
                                " (changed from the default %s in .llm-lint.yml)", rule.severity().label()))
                .orElse(rule.severity().label());
    }

    /** 按路径覆盖：哪些路径下这条换了级别，只有 "`a` → off；`b` → error" 这段。没有覆盖返回空串。 */
    public static String overridesList(Rule rule, Config config) {
        List<Config.PathOverride> overrides = config.overridesFor(rule.id());
        if (overrides.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < overrides.size(); i++) {
            Config.PathOverride override = overrides.get(i);
            if (i > 0) {
                sb.append(Messages.t("；", "; "));
            }
            sb.append('`').append(override.pattern()).append("` → ")
                    .append(override.levels().get(rule.id()).label());
        }
        return sb.toString();
    }

    private static String framework(Rule rule) {
        return switch (rule.framework()) {
            case "spring-ai" -> "Spring AI";
            case "langchain4j" -> "LangChain4j";
            default -> Messages.t("通用", "Both");
        };
    }
}