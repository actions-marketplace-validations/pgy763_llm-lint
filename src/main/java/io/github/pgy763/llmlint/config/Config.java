package io.github.pgy763.llmlint.config;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.lang.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 一份 {@code .llm-lint.yml} 编译出来的结果：哪条规则关掉、哪条改级别、哪些路径不扫、
 * 失败阈值是多少。
 *
 * <p>只记「和默认不一样的」东西。没写的规则保持原样，这样升级规则默认级别时，
 * 配置不会替用户把旧值钉死。
 */
public final class Config {

    /** 没有配置文件时用的空配置：什么都不改。 */
    public static final Config EMPTY = new Config(null, Map.of(), List.of(), null, null, null);

    private static final Set<String> TOP_LEVEL_KEYS =
            Set.of("version", "rules", "exclude", "fail-on", "min-severity", "baseline");

    private final Path file;
    private final Map<String, RuleLevel> levels;
    private final List<String> exclude;
    private final String failOn;
    private final String minSeverity;
    private final String baseline;

    private Config(Path file, Map<String, RuleLevel> levels, List<String> exclude,
                   String failOn, String minSeverity, String baseline) {
        this.file = file;
        this.levels = Map.copyOf(levels);
        this.exclude = List.copyOf(exclude);
        this.failOn = failOn;
        this.minSeverity = minSeverity;
        this.baseline = baseline;
    }

    /** 配置来自哪个文件；没有配置文件时是 null。 */
    public Path file() {
        return file;
    }

    /** {@code exclude} 里写的路径模式，原样保留，由扫描器解释。 */
    public List<String> exclude() {
        return exclude;
    }

    /** 配置里写了 {@code fail-on} 就用它，否则用默认的 error。 */
    public String failOnOrDefault() {
        return failOn == null ? "error" : failOn;
    }

    /** 配置里写了 {@code min-severity} 就用它，否则用默认的 info。 */
    public String minSeverityOrDefault() {
        return minSeverity == null ? "info" : minSeverity;
    }

    /** 配置里指定的基线文件（相对配置文件所在目录）；没写就是 null。 */
    public String baseline() {
        return baseline;
    }

    /** 这条规则被显式写过了吗；返回值可能是 OFF。 */
    public Optional<RuleLevel> levelOf(String ruleId) {
        return Optional.ofNullable(levels.get(ruleId.toUpperCase(Locale.ROOT)));
    }

    /**
     * 把配置套到规则清单上：关掉的剔掉，改了级别的换成吐新级别的包装。
     * 顺序保持不变——输出顺序、{@code --list-rules} 的顺序都靠它。
     */
    public List<Rule> apply(List<Rule> rules) {
        List<Rule> out = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            RuleLevel level = levels.get(rule.id());
            if (level == null) {
                out.add(rule);
            } else if (level != RuleLevel.OFF) {
                out.add(new ConfiguredRule(rule, level.severity()));
            }
        }
        return List.copyOf(out);
    }

    /** 一行话说清这份配置干了什么，给收尾那行统计用。 */
    public String summary() {
        long off = levels.values().stream().filter(level -> level == RuleLevel.OFF).count();
        long changed = levels.size() - off;
        List<String> parts = new ArrayList<>(2);
        if (off > 0) {
            parts.add(Messages.f("关掉 %d 条", "%d off", off));
        }
        if (changed > 0) {
            parts.add(Messages.f("改级别 %d 条", "%d re-levelled", changed));
        }
        return parts.isEmpty()
                ? Messages.t("没有改动", "no changes")
                : String.join(Messages.t("、", ", "), parts);
    }

    /**
     * 把「现在的实际行为」打印成一份可以存回去的 YAML。排查 CI 里为什么行为和本地不一样时，
     * 这一份就是答案——它把默认值也摊开了。
     */
    public String effectiveYaml(List<Rule> canonical, List<String> excludes,
                                String effectiveFailOn, String effectiveMinSeverity) {
        StringBuilder sb = new StringBuilder();
        sb.append(Messages.t("# llm-lint 生效配置", "# llm-lint effective config"));
        if (file == null) {
            sb.append(Messages.t("（没找到 .llm-lint.yml，全部按默认值）",
                    " (no .llm-lint.yml found, all defaults)"));
        } else {
            sb.append(Messages.t("（来自 ", " (from "))
                    .append(ConfigLoader.display(file, Path.of("").toAbsolutePath()))
                    .append(Messages.t("）", ")"));
        }
        sb.append("\n\nversion: 1\n\nrules:\n");
        for (Rule rule : canonical) {
            RuleLevel level = levels.get(rule.id());
            sb.append("  ").append(rule.id()).append(": ")
                    .append(level == null ? rule.severity().label() : level.label())
                    .append('\n');
        }
        if (excludes.isEmpty()) {
            sb.append("\nexclude: []\n");
        } else {
            sb.append("\nexclude:\n");
            for (String pattern : excludes) {
                sb.append("  - \"").append(yamlEscape(pattern)).append("\"\n");
            }
        }
        sb.append("\nfail-on: ").append(effectiveFailOn).append('\n');
        sb.append("min-severity: ").append(effectiveMinSeverity).append('\n');
        if (baseline != null) {
            sb.append("baseline: \"").append(yamlEscape(baseline)).append("\"\n");
        }
        return sb.toString();
    }

    private static String yamlEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- 编译

    static Config from(MiniYaml.Mapping root, Path file, List<Rule> known) throws ConfigException {
        Map<String, RuleLevel> levels = new LinkedHashMap<>();
        List<String> exclude = new ArrayList<>();
        String failOn = null;
        String minSeverity = null;
        String baseline = null;

        Set<String> knownIds = new LinkedHashSet<>();
        for (Rule rule : known) {
            knownIds.add(rule.id());
        }

        for (MiniYaml.Entry entry : root.entries) {
            if (!TOP_LEVEL_KEYS.contains(entry.key)) {
                throw error(file, entry.line,
                        Messages.f("不认识的键「%s」", "Unknown key \"%s\"", entry.key),
                        Messages.t("能写的只有 version / rules / exclude / fail-on / min-severity / baseline",
                                "The only keys are version / rules / exclude / fail-on / min-severity / baseline"));
            }
            switch (entry.key) {
                case "version" -> readVersion(entry, file);
                case "rules" -> readRules(entry, file, knownIds, levels);
                case "exclude" -> readExclude(entry, file, exclude);
                case "fail-on" -> failOn = readChoice(entry, file, "error", "warning", "never");
                case "min-severity" -> minSeverity = readChoice(entry, file, "info", "warning", "error");
                case "baseline" -> baseline = readBaseline(entry, file);
                default -> throw new IllegalStateException("unreachable: " + entry.key);
            }
        }
        return new Config(file, levels, exclude, failOn, minSeverity, baseline);
    }

    private static void readVersion(MiniYaml.Entry entry, Path file) throws ConfigException {
        String raw = scalar(entry, file, "version").trim();
        if (!raw.equals("1")) {
            throw error(file, entry.line,
                    Messages.f("只认识 `version: 1`，写的是「%s」", "Only `version: 1` is supported, found \"%s\"", raw),
                    Messages.t("这个字段留着以后改格式用；现在照抄 `version: 1` 就行",
                            "The field is reserved for future format changes; just write `version: 1`"));
        }
    }

    private static void readRules(MiniYaml.Entry entry, Path file, Set<String> knownIds,
                                  Map<String, RuleLevel> levels) throws ConfigException {
        MiniYaml.Node value = entry.value;
        if (value instanceof MiniYaml.Scalar scalar && scalar.value.isEmpty()) {
            return; // `rules:` 下面全是注释，等于没写
        }
        if (!(value instanceof MiniYaml.Mapping mapping)) {
            throw error(file, entry.line,
                    Messages.t("rules 下面要写成 `LLM001: off` 这样的键值对",
                            "rules must be `LLM001: off` style key/value pairs"),
                    Messages.t("示例：\n    rules:\n      LLM004: off\n      LLM007: error",
                            "Example:\n    rules:\n      LLM004: off\n      LLM007: error"));
        }
        for (MiniYaml.Entry rule : mapping.entries) {
            String id = rule.key.trim().toUpperCase(Locale.ROOT);
            if (!knownIds.contains(id)) {
                throw unknownRule(file, rule.line, id, knownIds);
            }
            RuleLevel level = parseLevel(id, scalar(rule, file, "级别"), file, rule.line);
            if (level != null) {
                levels.put(id, level);
            }
        }
    }

    private static void readExclude(MiniYaml.Entry entry, Path file, List<String> exclude)
            throws ConfigException {
        MiniYaml.Node value = entry.value;
        if (value instanceof MiniYaml.Scalar scalar) {
            if (scalar.value.isEmpty()) {
                return;
            }
            for (String part : scalar.value.split(",")) {
                addPattern(part, exclude);
            }
            return;
        }
        if (!(value instanceof MiniYaml.Sequence sequence)) {
            throw error(file, entry.line,
                    Messages.t("exclude 下面要写成 `- \"**/generated/**\"` 这样的列表",
                            "exclude must be a list like `- \"**/generated/**\"`"));
        }
        for (MiniYaml.Node item : sequence.items) {
            if (!(item instanceof MiniYaml.Scalar scalar)) {
                throw error(file, item.line,
                        Messages.t("exclude 的每一项都要是路径字符串",
                                "Every exclude entry must be a path string"));
            }
            addPattern(scalar.value, exclude);
        }
    }

    private static void addPattern(String raw, List<String> exclude) {
        String pattern = raw.trim();
        if (!pattern.isEmpty()) {
            exclude.add(pattern);
        }
    }

    /** 基线路径原样保留，由 Main 解释成相对配置文件所在目录的路径。 */
    private static String readBaseline(MiniYaml.Entry entry, Path file) throws ConfigException {
        String raw = scalar(entry, file, "baseline").trim();
        return raw.isEmpty() ? null : raw;
    }

    private static String readChoice(MiniYaml.Entry entry, Path file, String... allowed)
            throws ConfigException {
        String raw = scalar(entry, file, entry.key).trim().toLowerCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(raw)) {
                return raw;
            }
        }
        throw error(file, entry.line,
                Messages.f("「%s」只能是 %s，写的是「%s」", "\"%s\" must be one of %s, found \"%s\"",
                        entry.key, String.join(" / ", allowed), raw));
    }

    private static String scalar(MiniYaml.Entry entry, Path file, String what) throws ConfigException {
        if (!(entry.value instanceof MiniYaml.Scalar scalar)) {
            throw error(file, entry.line,
                    Messages.f("%s 要写成一行文字，不能是嵌套结构",
                            "%s must be a single line of text, not a nested structure", what));
        }
        return scalar.value;
    }

    /** 返回 null 表示「保持默认级别」（写 on / true 是为了让读者一眼看出这条被显式打开过）。 */
    private static RuleLevel parseLevel(String id, String raw, Path file, int line)
            throws ConfigException {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "off", "false", "no", "0" -> RuleLevel.OFF;
            case "on", "true", "yes", "default" -> null;
            case "error" -> RuleLevel.ERROR;
            case "warning", "warn" -> RuleLevel.WARNING;
            case "info", "note" -> RuleLevel.INFO;
            case "" -> throw error(file, line,
                    Messages.f("规则 %s 少了级别", "Rule %s has no level", id),
                    Messages.t("能写 off / error / warning / info", "Allowed: off / error / warning / info"));
            default -> throw error(file, line,
                    Messages.f("规则 %s 的级别不认识：「%s」", "Unknown level for rule %s: \"%s\"", id, raw),
                    Messages.t("能写 off / error / warning / info", "Allowed: off / error / warning / info"));
        };
    }

    private static ConfigException unknownRule(Path file, int line, String id, Collection<String> known) {
        String guess = nearest(id, known);
        String hint = guess == null
                ? Messages.t("全部规则：llm-lint --list-rules", "All rules: llm-lint --list-rules")
                : Messages.f("是不是 %s？全部规则：llm-lint --list-rules",
                        "Did you mean %s? All rules: llm-lint --list-rules", guess);
        return error(file, line, Messages.f("没有 %s 这条规则", "No such rule: %s", id), hint);
    }

    private static String nearest(String id, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = distance(id, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    /** 编辑距离，只为把 `LLM7` 认成 `LLM007`。 */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static ConfigException error(Path file, int line, String message) {
        return new ConfigException(file + ":" + line + ": " + message);
    }

    private static ConfigException error(Path file, int line, String message, String hint) {
        return new ConfigException(file + ":" + line + ": " + message
                + Messages.t("\n  提示：", "\n  hint: ") + hint);
    }
}