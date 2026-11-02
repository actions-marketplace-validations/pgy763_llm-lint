package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 基线的验收：行号不参与匹配、合并只增不减、文件坏了要说得清哪一行。 */
class BaselineTest {

    @TempDir
    Path tmp;

    private static Finding finding(String rule, String file, int line, String message) {
        return new Finding(rule, Severity.WARNING, file, line, 1, "标题", message, "https://example.com");
    }

    @Test
    void 空基线里什么都不算旧问题() {
        Baseline baseline = Baseline.empty();

        assertTrue(baseline.isEmpty());
        assertEquals(0, baseline.size());
        assertFalse(baseline.contains(finding("LLM001", "src/A.java", 3, "m")));
        assertTrue(baseline.toText().startsWith("#"));
    }

    @Test
    void 写出去再读回来还是同一份() throws IOException {
        Path file = tmp.resolve("baseline.txt");
        Baseline saved = Baseline.empty().plus(List.of(
                finding("LLM007", "src/A.java", 10, "maxTokens 太小"),
                finding("LLM010", "src/B.java", 4, "硬编码的 key")));

        Baseline.write(saved, file);
        Baseline loaded = Baseline.load(file);

        assertEquals(2, loaded.size());
        assertTrue(loaded.contains(finding("LLM007", "src/A.java", 10, "maxTokens 太小")));
        assertTrue(loaded.contains(finding("LLM010", "src/B.java", 4, "硬编码的 key")));
    }

    @Test
    void 代码挪了行号变了旧问题不会复活() throws IOException {
        Path file = tmp.resolve("baseline.txt");
        Baseline.write(Baseline.empty().plus(List.of(
                finding("LLM007", "src/A.java", 10, "maxTokens 太小"))), file);

        Baseline loaded = Baseline.load(file);

        assertTrue(loaded.contains(finding("LLM007", "src/A.java", 99, "maxTokens 太小")));
        assertTrue(loaded.contains(finding("LLM007", "src/A.java", 1, "maxTokens 太小")));
    }

    @Test
    void 消息变了就是新问题() throws IOException {
        Path file = tmp.resolve("baseline.txt");
        Baseline.write(Baseline.empty().plus(List.of(
                finding("LLM007", "src/A.java", 10, "maxTokens(128)"))), file);

        Baseline loaded = Baseline.load(file);

        assertFalse(loaded.contains(finding("LLM007", "src/A.java", 10, "maxTokens(256)")));
        assertFalse(loaded.contains(finding("LLM007", "src/B.java", 10, "maxTokens(128)")));
        assertFalse(loaded.contains(finding("LLM008", "src/A.java", 10, "maxTokens(128)")));
    }

    @Test
    void 合并只增不减() {
        Baseline baseline = Baseline.empty().plus(List.of(finding("LLM001", "src/A.java", 1, "旧的")));

        Baseline merged = baseline.plus(List.of(finding("LLM002", "src/A.java", 2, "新的")));

        assertEquals(1, baseline.size(), "原来的基线不该被改");
        assertEquals(2, merged.size());
        assertTrue(merged.contains(finding("LLM001", "src/A.java", 1, "旧的")));
        assertTrue(merged.contains(finding("LLM002", "src/A.java", 2, "新的")));
    }

    @Test
    void 重复合并不会长出重复条目() {
        Baseline baseline = Baseline.empty().plus(List.of(finding("LLM001", "src/A.java", 1, "同一个")));

        Baseline merged = baseline.plus(List.of(finding("LLM001", "src/A.java", 5, "同一个")));

        assertEquals(1, merged.size());
    }

    @Test
    void 消息里的Tab和反斜杠不会把三列撑坏() throws IOException {
        Path file = tmp.resolve("baseline.txt");
        String message = "第一行\t带 Tab\\和反斜杠\n第二行";
        Baseline.write(Baseline.empty().plus(List.of(finding("LLM001", "src/A.java", 1, message))), file);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "表头 + 一条");
        assertEquals(3, lines.get(1).split("\t", -1).length);

        assertTrue(Baseline.load(file).contains(finding("LLM001", "src/A.java", 7, message)));
    }

    @Test
    void 两次生成的内容一模一样() throws IOException {
        Baseline baseline = Baseline.empty().plus(List.of(
                finding("LLM010", "src/Z.java", 1, "z"),
                finding("LLM002", "src/A.java", 1, "a")));
        Path first = tmp.resolve("a.txt");
        Path second = tmp.resolve("b.txt");

        Baseline.write(baseline, first);
        Baseline.write(baseline, second);

        assertEquals(Files.readString(first), Files.readString(second));
        assertTrue(Files.readString(first).indexOf("LLM002") < Files.readString(first).indexOf("LLM010"),
                "排过序，diff 里只该出现新增的那几行");
    }

    @Test
    void 按规则统计时按规则号排序() {
        Baseline baseline = Baseline.empty()
                .plus(List.of(finding("LLM010", "src/A.java", 1, "a"),
                        finding("LLM001", "src/A.java", 2, "b"),
                        finding("LLM010", "src/B.java", 3, "c")));

        assertEquals(List.of("LLM001", "LLM010"), List.copyOf(baseline.byRule().keySet()));
        assertEquals(1, baseline.byRule().get("LLM001"));
        assertEquals(2, baseline.byRule().get("LLM010"));
    }

    @Test
    void 已经不再出现的问题算修好了() {
        Baseline baseline = Baseline.empty()
                .plus(List.of(finding("LLM007", "src/A.java", 1, "还报"),
                        finding("LLM010", "src/B.java", 2, "修好了")));

        var stale = baseline.staleByRule(List.of(finding("LLM007", "src/A.java", 99, "还报")));

        assertEquals(1, stale.size());
        assertEquals(1, stale.get("LLM010"));
        assertFalse(stale.containsKey("LLM007"), "还在报的不能算修好");
    }

    @Test
    void 文件不存在时告诉用户下一步() {
        IOException e = assertThrows(IOException.class, () -> Baseline.load(tmp.resolve("没有这个文件.txt")));

        assertTrue(e.getMessage().contains("找不到基线文件"), e.getMessage());
        assertTrue(e.getMessage().contains("--save-baseline"), e.getMessage());
    }

    @Test
    void 列数不对时报出行号() throws IOException {
        Path file = tmp.resolve("bad.txt");
        Files.writeString(file, "# 表头\n\nLLM001\t只有两列\n");

        IOException e = assertThrows(IOException.class, () -> Baseline.load(file));

        assertTrue(e.getMessage().contains(":3:"), e.getMessage());
        assertTrue(e.getMessage().contains("三列"), e.getMessage());
    }

    @Test
    void 注释和空行不算条目() throws IOException {
        Path file = tmp.resolve("baseline.txt");
        Files.writeString(file, "# llm-lint baseline v1\n\nLLM001\tsrc/A.java\t旧的\n\n");

        assertEquals(1, Baseline.load(file).size());
    }
}