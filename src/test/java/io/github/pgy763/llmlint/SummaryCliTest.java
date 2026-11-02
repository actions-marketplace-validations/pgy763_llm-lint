package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --summary：把结论写成一段 Markdown，给 CI 的 step summary 用。 */
class SummaryCliTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        Main main = new Main();
        CommandLine cmd = new CommandLine(main);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void 摘要文件里按规则列条数(@TempDir Path tmp) throws IOException {
        Path summary = tmp.resolve("summary.md");

        Run run = run("--summary", summary.toString(), "samples/bad");

        assertEquals(1, run.code(), run.err());
        String text = Files.readString(summary);
        assertTrue(text.startsWith("## llm-lint\n\n**18 个问题**（"), text);
        assertTrue(text.contains("| LLM007 | warning | 1 |"), text);
        assertTrue(text.contains("| 规则 | 级别 | 条数 |"), text);
    }

    @Test
    void 没问题的目录也会写一份(@TempDir Path tmp) throws IOException {
        Path summary = tmp.resolve("good.md");

        Run run = run("--summary", summary.toString(), "samples/good");

        assertEquals(0, run.code(), run.err());
        assertTrue(Files.readString(summary).contains("没有发现问题"), Files.readString(summary));
    }

    @Test
    void 摘要的目录不存在会建出来(@TempDir Path tmp) throws IOException {
        Path summary = tmp.resolve("a/b/summary.md");

        Run run = run("--summary", summary.toString(), "samples/good");

        assertEquals(0, run.code(), run.err());
        assertTrue(Files.isRegularFile(summary));
        assertFalse(Files.readString(summary).isBlank());
    }

    @Test
    void 摘要写不进去时报错退出(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("summary.md");
        Files.createDirectories(dir);

        Run run = run("--summary", dir.toString(), "samples/good");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("摘要写不进去"), run.err());
    }
}