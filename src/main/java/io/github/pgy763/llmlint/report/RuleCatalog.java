package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.RuleLevel;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 规则目录的 JSON：给编辑器插件、内部看板、文档生成用。
 *
 * <p>{@code level} 是这次实际生效的级别（配置文件可能把某条关了或升了级，
 * 命令行 --disable 也会在这里体现为 off），{@code severity} 是规则自带的默认级别。
 * 没有这批数据，外部工具只能去解析 --list-rules 的表格。
 */
public final class RuleCatalog {

    /** 有现成补丁的规则；和 --fix 的支持范围保持一致。 */
    private static final Set<String> FIXABLE = Set.of("LLM006", "LLM018");

    /** 这条规则有没有现成补丁；文档页和规则目录共用一份清单。 */
    public static boolean isFixable(String id) {
        return FIXABLE.contains(id);
    }

    private RuleCatalog() {
    }

    public static String format(List<Rule> rules, Config config, Set<String> disabled) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            sb.append("  {\n");
            field(sb, "id", rule.id(), true);
            field(sb, "title", rule.title(), true);
            field(sb, "severity", rule.severity().label(), true);
            field(sb, "level", levelOf(rule, config, disabled).label(), true);
            field(sb, "framework", rule.framework(), true);
            field(sb, "why", rule.why(), true);
            field(sb, "docUrl", rule.docUrl(), true);
            sb.append("    \"fixable\": ").append(FIXABLE.contains(rule.id())).append('\n');
            sb.append(i + 1 < rules.size() ? "  },\n" : "  }\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static RuleLevel levelOf(Rule rule, Config config, Set<String> disabled) {
        if (disabled != null && disabled.contains(rule.id().toUpperCase(Locale.ROOT))) {
            return RuleLevel.OFF;
        }
        RuleLevel level = config.levelOf(rule.id()).orElse(null);
        if (level != null) {
            return level;
        }
        return switch (rule.severity()) {
            case ERROR -> RuleLevel.ERROR;
            case WARNING -> RuleLevel.WARNING;
            case INFO -> RuleLevel.INFO;
        };
    }

    private static void field(StringBuilder sb, String name, String value, boolean comma) {
        sb.append("    \"").append(name).append("\": \"").append(escape(value)).append('"');
        sb.append(comma ? ",\n" : "\n");
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}