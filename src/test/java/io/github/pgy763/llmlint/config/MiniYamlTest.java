package io.github.pgy763.llmlint.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 这个解析器的价值在于「写错了马上告诉我第几行」，所以错误分支和正常分支一样重要。 */
class MiniYamlTest {

    private static MiniYaml.Mapping parse(String text) throws ConfigException {
        return MiniYaml.parse(text, "test.yml");
    }

    private static MiniYaml.Mapping mapping(MiniYaml.Node node) {
        return (MiniYaml.Mapping) node;
    }

    private static String text(MiniYaml.Node node) {
        return ((MiniYaml.Scalar) node).value;
    }

    private static List<String> items(MiniYaml.Node node) {
        return ((MiniYaml.Sequence) node).items.stream()
                .map(MiniYamlTest::text)
                .toList();
    }

    private static String errorOf(String text) {
        return assertThrows(ConfigException.class, () -> parse(text)).getMessage();
    }

    @Test
    void 嵌套映射和行尾注释() throws Exception {
        MiniYaml.Mapping root = parse("""
                version: 1
                rules:
                  LLM001: off        # 这条先关掉
                  LLM007: error
                exclude:
                  - "**/generated/**"
                """);
        assertEquals(3, root.entries.size());
        assertEquals("1", text(root.get("version")));

        MiniYaml.Mapping rules = mapping(root.get("rules"));
        assertEquals(2, rules.entries.size());
        assertEquals("off", text(rules.get("LLM001")));
        assertEquals("error", text(rules.get("LLM007")));

        assertEquals(List.of("**/generated/**"), items(root.get("exclude")));
    }

    @Test
    void 行内列表能用引号也能带注释符号() throws Exception {
        MiniYaml.Mapping root = parse("""
                exclude: ["**/a b/**", '**/generated/**', x#y]
                """);
        assertEquals(List.of("**/a b/**", "**/generated/**", "x#y"), items(root.get("exclude")));
    }

    @Test
    void 空的行内列表() throws Exception {
        MiniYaml.Mapping root = parse("exclude: []\n");
        assertEquals(List.of(), items(root.get("exclude")));
    }

    @Test
    void 双引号里的转义() throws Exception {
        MiniYaml.Mapping root = parse("a: \"x\\ny\"\nb: \"tab\\there\"\n");
        assertEquals("x\ny", text(root.get("a")));
        assertEquals("tab\there", text(root.get("b")));
    }

    @Test
    void 单引号和双写单引号() throws Exception {
        MiniYaml.Mapping root = parse("a: 'it''s'\nb: 'x # y'\n");
        assertEquals("it's", text(root.get("a")));
        assertEquals("x # y", text(root.get("b")));
    }

    @Test
    void 键带引号也认() throws Exception {
        MiniYaml.Mapping root = parse("\"LLM001\": off\n");
        assertEquals("off", text(root.get("LLM001")));
    }

    @Test
    void 空文件和只有注释的文件都是空映射() throws Exception {
        assertEquals(0, parse("").entries.size());
        assertEquals(0, parse("# 什么都没有\n\n   \n").entries.size());
    }

    @Test
    void BOM和CRLF都处理得掉() throws Exception {
        MiniYaml.Mapping root = parse("\uFEFFversion: 1\r\nrules:\r\n  LLM001: off\r\n");
        assertEquals("1", text(root.get("version")));
        assertEquals("off", text(mapping(root.get("rules")).get("LLM001")));
    }

    @Test
    void 键重复要带上行号() {
        String message = errorOf("a: 1\na: 2\n");
        assertTrue(message.startsWith("test.yml:2:"), message);
        assertTrue(message.contains("键重复"), message);
    }

    @Test
    void Tab缩进要说清楚() {
        String message = errorOf("rules:\n\tLLM001: off\n");
        assertTrue(message.startsWith("test.yml:2:"), message);
        assertTrue(message.contains("Tab"), message);
    }

    @Test
    void 少了冒号() {
        String message = errorOf("just some text\n");
        assertTrue(message.contains("少了冒号"), message);
    }

    @Test
    void 冒号后面必须有空格() {
        assertTrue(errorOf("a:1\n").contains("空格"));
    }

    @Test
    void 缩进多了() {
        assertTrue(errorOf("a: 1\n  b: 2\n").contains("缩进多了"));
    }

    @Test
    void 顶层不能是列表() {
        assertTrue(errorOf("- a\n- b\n").contains("不能是一个列表"));
    }

    @Test
    void 不支持行内映射() {
        assertTrue(errorOf("rules: {LLM001: off}\n").contains("行内映射"));
    }

    @Test
    void 行内列表少了右括号() {
        assertTrue(errorOf("exclude: [\"a\"\n").contains("]"));
    }

    @Test
    void 列表项也能写成键值对() throws Exception {
        MiniYaml.Mapping root = MiniYaml.parse("""
                overrides:
                  - files: "src/generated/**"
                    rules:
                      LLM010: off
                  - files: "src/main/**"
                    rules:
                      LLM007: error
                """, "test.yml");

        MiniYaml.Sequence items = (MiniYaml.Sequence) root.get("overrides");
        assertEquals(2, items.items.size());

        MiniYaml.Mapping first = (MiniYaml.Mapping) items.items.get(0);
        assertEquals("src/generated/**", ((MiniYaml.Scalar) first.get("files")).value);
        MiniYaml.Mapping firstRules = (MiniYaml.Mapping) first.get("rules");
        assertEquals("off", ((MiniYaml.Scalar) firstRules.get("LLM010")).value);

        MiniYaml.Mapping second = (MiniYaml.Mapping) items.items.get(1);
        assertEquals("src/main/**", ((MiniYaml.Scalar) second.get("files")).value);
    }

    @Test
    void 列表项以引号开头时不当键值对() throws Exception {
        MiniYaml.Mapping root = MiniYaml.parse("rules:\n  - \"a: b\"\n  - plain\n", "test.yml");
        MiniYaml.Sequence items = (MiniYaml.Sequence) root.get("rules");
        assertEquals(2, items.items.size());
        assertEquals("a: b", ((MiniYaml.Scalar) items.items.get(0)).value);
        assertEquals("plain", ((MiniYaml.Scalar) items.items.get(1)).value);
    }

    @Test
    void 引号没闭合() {
        assertTrue(errorOf("a: \"half\n").contains("引号没有闭合"));
    }

    @Test
    void 双引号里不认识的转义() {
        assertTrue(errorOf("a: \"x\\qy\"\n").contains("不认识的转义"));
    }
}