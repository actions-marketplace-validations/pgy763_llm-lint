package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code --explain ... --format markdown}：贴文档、贴 PR、生成速查表都用得上。 */
class ExplainMarkdownTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = new CommandLineRunner().execute(args, out, err);
        return new Run(code, out.toString(), err.toString());
    }

    /** 单独抽出来，免得每个用例都写一遍 CommandLine 的装配。 */
    private static final class CommandLineRunner {
        int execute(String[] args, StringWriter out, StringWriter err) {
            return new picocli.CommandLine(new Main())
                    .setOut(new PrintWriter(out))
                    .setErr(new PrintWriter(err))
                    .execute(args);
        }
    }

    @Test
    void 单条规则能出markdown块() {
        Run run = run("--explain", "LLM007", "--format", "markdown");
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().startsWith("## LLM007 "), run.out());
        assertTrue(run.out().contains("| 级别 | warning |"), run.out());
        assertTrue(run.out().contains("| 框架 | 通用 |"), run.out());
        assertTrue(run.out().contains("docs/rules.md#llm007"), run.out());
        assertTrue(run.out().contains("`// llm-lint:ignore LLM007`"), run.out());
        assertTrue(run.out().endsWith("\n"), "文件末尾要留一个换行，拼起来才不会黏在一起");
    }

    @Test
    void 带配置文件时写明级别被改过() {
        Run run = run("--config", "samples/config-demo/.llm-lint.yml",
                "--explain", "LLM007", "--format", "markdown");
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("| 级别 | error（.llm-lint.yml 从默认的 warning 改的） |"), run.out());
    }

    @Test
    void all把每条都列出来而且顺序不变() {
        Run run = run("--explain", "all", "--format", "markdown");
        assertEquals(0, run.code(), run.err());
        List<String> headings = run.out().lines()
                .filter(line -> line.startsWith("## LLM"))
                .toList();
        List<String> expected = Rules.all().stream().map(rule -> "## " + rule.id() + " " + rule.title()).toList();
        assertEquals(expected, headings, "同样的规则、同样的顺序，生成出来的文档才稳定");
    }

    @Test
    void 英文下表格也是英文() {
        Run run = run("--lang", "en", "--explain", "LLM001", "--format", "markdown");
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("| Level | error |"), run.out());
        assertTrue(run.out().contains("| Framework | Both |"), run.out());
        assertTrue(run.out().contains("To mute it in one place"), run.out());
        assertFalse(run.out().contains("| 级别 |"), run.out());
    }

    @Test
    void all在普通文本模式下也能一条条打出来() {
        Run run = run("--explain", "all");
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("LLM001"), run.out());
        assertTrue(run.out().contains("LLM022"), run.out());
        assertEquals(Rules.all().size(),
                run.out().lines().filter(line -> line.matches("^LLM\\d{3} .+")).count());
    }

    @Test
    void 不认识的规则号还是退出2() {
        Run run = run("--explain", "LLM999", "--format", "markdown");
        assertEquals(2, run.code());
        assertTrue(run.err().contains("没有这条规则"), run.err());
    }

    @Test
    void explain里能看到按路径覆盖的级别(@TempDir Path dir) throws Exception {
        Path config = dir.resolve(".llm-lint.yml");
        String yaml = String.join(String.valueOf((char) 10),
                "version: 1",
                "overrides:",
                "  - files: src/generated/**",
                "    rules:",
                "      LLM007: off");
        Files.writeString(config, yaml, StandardCharsets.UTF_8);

        Run text = run("--config", config.toString(), "--explain", "LLM007");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("路径覆盖"), text.out());
        assertTrue(text.out().contains("`src/generated/**` → off"), text.out());

        Run md = run("--config", config.toString(), "--explain", "LLM007", "--format", "markdown");
        assertEquals(0, md.code(), md.err());
        assertTrue(md.out().contains("按路径覆盖：`src/generated/**` → off"), md.out());

        Run other = run("--config", config.toString(), "--explain", "LLM001");
        assertEquals(0, other.code(), other.err());
        assertFalse(other.out().contains("路径覆盖"), other.out());
    }
}
