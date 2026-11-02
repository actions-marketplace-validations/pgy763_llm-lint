package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigLoader;
import io.github.pgy763.llmlint.config.RuleLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档和代码对不上，用户就会点进 404。这里把「规则里的链接」「schema 里的字段」
 * 「--init 写出来的模板」都锁在测试里。
 */
class DocsTest {

    private static final Path RULES_DOC = Path.of("docs/rules.md");
    private static final Path SCHEMA = Path.of("docs/llm-lint.schema.json");

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void 每条规则在仓库文档里都有锚点() throws IOException {
        String doc = read(RULES_DOC);
        for (Rule rule : Rules.all()) {
            String anchor = rule.id().toLowerCase(Locale.ROOT);
            assertTrue(doc.contains("<a id=\"" + anchor + "\"></a>"),
                    "docs/rules.md 少了 " + rule.id() + " 的锚点，docUrl 里的 #" + anchor + " 会 404");
            assertTrue(doc.contains("\n## " + rule.id() + " "),
                    "docs/rules.md 少了 " + rule.id() + " 的小节标题");
        }
    }

    @Test
    void 规则的docUrl指向真的存在的地方() throws IOException {
        String doc = read(RULES_DOC);
        for (Rule rule : Rules.all()) {
            String url = rule.docUrl();
            assertTrue(url.startsWith("https://"), rule.id() + " 的 docUrl 不是绝对地址：" + url);
            if (url.contains("/blob/main/docs/rules.md#")) {
                String fragment = url.substring(url.indexOf('#') + 1);
                assertTrue(doc.contains("<a id=\"" + fragment + "\"></a>"),
                        rule.id() + " 指向 #" + fragment + "，但 docs/rules.md 里没有这个锚点");
            }
        }
    }

    @Test
    void init模板第一行就指向schema() throws IOException {
        String template = ConfigLoader.template(Rules.all());

        assertTrue(template.startsWith("# yaml-language-server: $schema="), template);
        String first = template.lines().findFirst().orElse("");
        assertTrue(first.endsWith("docs/llm-lint.schema.json"), first);
        assertTrue(Files.isRegularFile(SCHEMA), "schema 文件得真的在仓库里");
    }

    @Test
    void schema列了全部顶层键() throws IOException {
        String schema = read(SCHEMA);

        for (String key : List.of("version", "rules", "exclude", "fail-on", "min-severity", "baseline")) {
            assertTrue(schema.contains("\"" + key + "\""), "schema 里少了 " + key);
        }
        assertTrue(schema.contains("\"additionalProperties\": false"), "顶层要禁止未知键，和解析器行为一致");
    }

    @Test
    void schema里的级别写法解析器都认() throws Exception {
        String schema = read(SCHEMA);

        for (String level : List.of("off", "on", "error", "warning", "info", "true", "false", "warn", "note")) {
            assertTrue(schema.contains("\"" + level + "\""), "schema 里少了级别写法 " + level);

            Path file = Files.createTempFile("llm-lint-level", ".yml");
            Files.writeString(file, "version: 1\nrules:\n  LLM007: " + level + "\n", StandardCharsets.UTF_8);
            Optional<RuleLevel> parsed = ConfigLoader.load(file, Rules.all()).levelOf("LLM007");

            if (level.equals("on") || level.equals("true")) {
                assertFalse(parsed.isPresent(), level + " 表示「跟随默认值」，不该钉死一个级别");
            } else {
                assertEquals(expected(level), parsed.orElseThrow().label(), "级别写法 " + level);
            }
        }
    }

    private static String expected(String level) {
        return switch (level) {
            case "off", "false" -> "off";
            case "warn" -> "warning";
            case "note" -> "info";
            default -> level;
        };
    }
}