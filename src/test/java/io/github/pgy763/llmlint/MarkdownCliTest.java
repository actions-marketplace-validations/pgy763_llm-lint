package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --markdown：给 PR 评论用的明细文件，Action 会拿它原地更新同一条评论。 */
class MarkdownCliTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        CommandLine cmd = new CommandLine(new Main());
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    private static long rows(String text) {
        return text.lines()
                .filter(line -> line.startsWith("| ") && !line.startsWith("| 级别")
                        && !line.startsWith("| ---"))
                .count();
    }

    @Test
    void 评论文件带标记和一行一条问题(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("comment.md");

        Run run = run("--markdown", file.toString(), "samples/bad");

        assertEquals(1, run.code(), run.err());
        String text = Files.readString(file);
        assertTrue(text.startsWith("<!-- llm-lint -->\n"), text);
        assertTrue(text.contains("**llm-lint：22 个问题**（11 error / 11 warning），扫了 22 个文件。"), text);
        assertTrue(text.contains("| 级别 | 规则 | 位置 | 问题 |"), text);
        assertEquals(22, rows(text), text);
    }

    @Test
    void 没问题的目录写一句话就够了(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("ok.md");

        Run run = run("--markdown", file.toString(), "samples/good");

        assertEquals(0, run.code(), run.err());
        String text = Files.readString(file);
        assertTrue(text.contains("没有发现问题"), text);
        assertEquals(0, rows(text), text);
    }

    @Test
    void 评论的目录不存在会建出来(@TempDir Path tmp) {
        Path file = tmp.resolve("a/b/comment.md");

        Run run = run("--markdown", file.toString(), "samples/good");

        assertEquals(0, run.code(), run.err());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void 评论写不进去时报错退出(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("comment.md");
        Files.createDirectories(dir);

        Run run = run("--markdown", dir.toString(), "samples/good");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("评论写不进去"), run.err());
    }

    @Test
    void 规则清单能直接出markdown表格() {
        Run run = run("--list-rules", "--format", "markdown");

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().startsWith("| 编号 | 级别 | 框架 | 拦的是什么 |"), run.out());
        assertEquals(Rules.all().size(),
                run.out().lines().filter(line -> line.startsWith("| [LLM")).count());
        assertTrue(run.out().contains("| [LLM003](#llm003) | error | Spring AI |"), run.out());
    }

    @Test
    void 终端里的表和文档站上的表是同一张() throws Exception {
        // 手册说「这张表也能自己生成」：那就得真的一样，不然等于骗人。
        Run run = run("--list-rules", "--format", "markdown");
        String doc = Files.readString(Path.of("docs/rules.md"), StandardCharsets.UTF_8);
        for (String line : run.out().lines().filter(l -> l.startsWith("| [LLM")).toList()) {
            assertTrue(doc.contains(line + "\n"), "文档站的表里没有这一行：" + line);
        }
    }
}