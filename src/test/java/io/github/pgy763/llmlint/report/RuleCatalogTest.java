package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.Rules;
import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCatalogTest {

    private static final List<Rule> RULES = Rules.all();

    @Test
    void 每条规则一条记录() {
        String json = RuleCatalog.format(RULES, Config.EMPTY, Set.of());
        assertEquals(18, count(json, "\"id\": \"LLM"));
        assertTrue(json.startsWith("[\n"));
        assertTrue(json.endsWith("]\n"));
    }

    @Test
    void 字段齐全且级别是默认值() {
        String json = RuleCatalog.format(RULES, Config.EMPTY, Set.of());
        for (String field : List.of("id", "title", "severity", "level", "framework", "why", "docUrl", "fixable")) {
            assertTrue(json.contains("\"" + field + "\": "), field);
        }
        assertTrue(json.contains("\"id\": \"LLM007\""), json);
        assertTrue(json.contains("\"severity\": \"warning\""), json);
        assertTrue(json.contains("\"level\": \"warning\""), json);
    }

    @Test
    void 只有两条规则可以自动修() {
        String json = RuleCatalog.format(RULES, Config.EMPTY, Set.of());
        assertEquals(2, count(json, "\"fixable\": true"));
        assertTrue(json.contains("\"fixable\": false"));
    }

    @Test
    void 配置里的开关和级别会体现在level上() throws Exception {
        Config config = ConfigLoader.load(Path.of("samples/config-demo/.llm-lint.yml"), RULES);
        String json = RuleCatalog.format(RULES, config, Set.of());
        assertTrue(blockOf(json, "LLM004").contains("\"level\": \"off\""), blockOf(json, "LLM004"));
        assertTrue(blockOf(json, "LLM007").contains("\"level\": \"error\""), blockOf(json, "LLM007"));
        assertTrue(blockOf(json, "LLM007").contains("\"severity\": \"warning\""), blockOf(json, "LLM007"));
    }

    @Test
    void 命令行disable也体现为off() {
        String json = RuleCatalog.format(RULES, Config.EMPTY, Set.of("LLM010"));
        assertTrue(blockOf(json, "LLM010").contains("\"level\": \"off\""), blockOf(json, "LLM010"));
        assertFalse(blockOf(json, "LLM011").contains("\"level\": \"off\""), blockOf(json, "LLM011"));
    }

    @Test
    void 每个字符串值都在一行里闭合() {
        String json = RuleCatalog.format(RULES, Config.EMPTY, Set.of());
        json.lines()
                .filter(line -> line.contains("\"title\"") || line.contains("\"why\""))
                .forEach(line -> assertTrue(line.endsWith("\",") || line.endsWith("\""), line));
    }

    /** 从某个 id 开始到下一个 id 之间的片段。 */
    private static String blockOf(String json, String id) {
        int start = json.indexOf("\"id\": \"" + id + "\"");
        if (start < 0) {
            return "";
        }
        int end = json.indexOf("\"id\": \"", start + 1);
        return end < 0 ? json.substring(start) : json.substring(start, end);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}