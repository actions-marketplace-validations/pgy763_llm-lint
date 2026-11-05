package io.github.pgy763.llmlint.config;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.Rules;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配置怎么解释成规则清单、怎么改级别、写错了说什么。 */
class ConfigTest {

    private static Config config(String text) throws ConfigException {
        return Config.from(MiniYaml.parse(text, "test.yml"), Path.of("test.yml"), Rules.all());
    }

    private static String errorOf(String text) {
        return assertThrows(ConfigException.class, () -> config(text)).getMessage();
    }

    private static Rule rule(List<Rule> rules, String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void 关掉的规则从清单里消失() throws Exception {
        Config config = config("""
                version: 1
                rules:
                  LLM004: off
                """);
        List<Rule> rules = config.apply(Rules.all());

        assertEquals(21, rules.size());
        assertTrue(rules.stream().noneMatch(r -> r.id().equals("LLM004")));
        assertEquals(RuleLevel.OFF, config.levelOf("LLM004").orElseThrow());
        assertEquals("关掉 1 条", config.summary());
    }

    @Test
    void 提高级别时规则和它报出来的结论一起改() throws Exception {
        Config config = config("rules:\n  LLM007: error\n");
        Rule escalated = rule(config.apply(Rules.all()), "LLM007");
        assertEquals(Severity.ERROR, escalated.severity());

        Path sample = Path.of("samples/bad/LLM007_SmallMaxTokens.java");
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setAttributeComments(false));
        CompilationUnit cu = parser.parse(sample).getResult().orElseThrow();

        List<Finding> findings = escalated.check(sample, cu);
        assertEquals(1, findings.size());
        assertEquals(Severity.ERROR, findings.get(0).severity(),
                "包了一层之后 findings 也要是新级别，否则输出和退出码会打架");
        assertEquals("LLM007", findings.get(0).ruleId());
    }

    @Test
    void 没写的规则原样通过() throws Exception {
        Config config = config("rules:\n  LLM004: off\n");
        Rule untouched = rule(config.apply(Rules.all()), "LLM007");

        assertFalse(untouched instanceof ConfiguredRule, "没改的规则不该被包一层");
        assertEquals(Severity.WARNING, untouched.severity(), "默认级别不该被配置钉死");
    }

    @Test
    void 写on或true等于没写() throws Exception {
        Config config = config("rules:\n  LLM007: on\n  LLM004: true\n");
        assertTrue(config.levelOf("LLM007").isEmpty());
        assertTrue(config.levelOf("LLM004").isEmpty());
        assertEquals(22, config.apply(Rules.all()).size());
        assertEquals("没有改动", config.summary());
    }

    @Test
    void 大小写和别名都认() throws Exception {
        Config config = config("rules:\n  llm001: WARN\n  LLM002: note\n");
        assertEquals(RuleLevel.WARNING, config.levelOf("LLM001").orElseThrow());
        assertEquals(RuleLevel.INFO, config.levelOf("LLM002").orElseThrow());
        assertEquals(Severity.WARNING, rule(config.apply(Rules.all()), "LLM001").severity());
    }

    @Test
    void exclude可以写成一行也可以用列表() throws Exception {
        Config inline = config("exclude: \"**/generated/**, **/test/**\"\n");
        assertEquals(List.of("**/generated/**", "**/test/**"), inline.exclude());

        Config block = config("exclude:\n  - \"**/generated/**\"\n  - '**/legacy/**'\n");
        assertEquals(List.of("**/generated/**", "**/legacy/**"), block.exclude());

        Config empty = config("exclude: []\n");
        assertEquals(List.of(), empty.exclude());
    }

    @Test
    void 未知规则给一个最像的() {
        String message = errorOf("rules:\n  LLM07: off\n");
        assertTrue(message.startsWith("test.yml:2:"), message);
        assertTrue(message.contains("是不是 LLM007"), message);
    }

    @Test
    void 未知级别列出能写什么() {
        String message = errorOf("rules:\n  LLM001: fatal\n");
        assertTrue(message.contains("LLM001"), message);
        assertTrue(message.contains("off / error / warning / info"), message);
    }

    @Test
    void 未知键要说清楚只能写哪五个() {
        String message = errorOf("rulez: 1\n");
        assertTrue(message.contains("不认识的键"), message);
        assertTrue(message.contains("version"), message);
    }

    @Test
    void 版本号只认1() {
        assertTrue(errorOf("version: 2\n").contains("version: 1"));
    }

    @Test
    void failOn和minSeverity会校验取值() throws Exception {
        Config config = config("fail-on: warning\nmin-severity: error\n");
        assertEquals("warning", config.failOnOrDefault());
        assertEquals("error", config.minSeverityOrDefault());

        assertTrue(errorOf("fail-on: sometimes\n").contains("error / warning / never"));
        assertTrue(errorOf("min-severity: fatal\n").contains("info / warning / error"));
    }

    @Test
    void 没写failOn和minSeverity时用默认值() throws Exception {
        Config config = config("rules:\n  LLM001: off\n");
        assertEquals("error", config.failOnOrDefault());
        assertEquals("info", config.minSeverityOrDefault());
    }

    @Test
    void rules下面全是注释等于没写() throws Exception {
        Config config = config("""
                version: 1
                rules:
                  # LLM004: off
                exclude:
                  # - "**/generated/**"
                """);
        assertEquals(22, config.apply(Rules.all()).size());
        assertEquals(List.of(), config.exclude());
    }

    @Test
    void 模板本身必须是一份合法配置() throws Exception {
        Config config = config(ConfigLoader.template(Rules.all()));
        assertEquals(22, config.apply(Rules.all()).size());
        assertEquals(List.of(), config.exclude());
        assertEquals("没有改动", config.summary());
    }

    @Test
    void printConfig的输出能被自己读回来() throws Exception {
        Config config = config("rules:\n  LLM004: off\n  LLM007: error\nexclude:\n  - \"**/generated/**\"\nfail-on: never\n");
        String printed = config.effectiveYaml(Rules.all(), config.exclude(), "never", "warning");

        Config reparsed = config(printed);
        assertEquals(RuleLevel.OFF, reparsed.levelOf("LLM004").orElseThrow());
        assertEquals(RuleLevel.ERROR, reparsed.levelOf("LLM007").orElseThrow());
        assertEquals(List.of("**/generated/**"), reparsed.exclude());
        assertEquals("never", reparsed.failOnOrDefault());
        assertEquals("warning", reparsed.minSeverityOrDefault());
        assertEquals(21, reparsed.apply(Rules.all()).size());
        assertFalse(printed.contains("LLM999"));
    }
}