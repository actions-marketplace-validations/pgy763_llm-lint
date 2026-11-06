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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code --docs <dir>}：生成段每次重写，手写段一个字都不能丢。 */
class DocsPagesCliTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = new picocli.CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void 每条规则写一页并且自带结束标记(@TempDir Path dir) throws Exception {
        Run run = run("--docs", dir.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("22"), run.out());

        Path first = dir.resolve("llm001.md");
        assertTrue(Files.isRegularFile(first));
        String text = Files.readString(first, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("---\n"), text);
        assertTrue(text.contains("permalink: /rules/llm001.html"), text);
        assertTrue(text.contains("<!-- /generated:llm001 -->"), text);
        assertTrue(text.contains("| 级别 | error |"), text);

        List<String> files;
        try (var stream = Files.list(dir)) {
            files = stream.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertEquals(22, files.size(), files.toString());
    }

    @Test
    void 重跑不覆盖手写部分(@TempDir Path dir) throws Exception {
        assertEquals(0, run("--docs", dir.toString()).code());
        Path first = dir.resolve("llm007.md");
        String before = Files.readString(first, StandardCharsets.UTF_8);
        String prose = before.replace("## 为什么会踩\n\n（这段手写：出现时的样子、改法、判断范围。工具每次生成只换上面的表。）\n",
                "## 这是团队自己写的\n\n别删我：这一段是手写的。\n");
        Files.writeString(first, prose, StandardCharsets.UTF_8);

        Run again = run("--docs", dir.toString());
        assertEquals(0, again.code(), again.err());
        String after = Files.readString(first, StandardCharsets.UTF_8);
        assertTrue(after.contains("别删我：这一段是手写的。"), after);
        assertTrue(after.contains("<!-- /generated:llm007 -->"), after);
        assertEquals(prose, after, "生成段没变时，重跑就应该一个字都不改");
    }

    @Test
    void 目录不存在就建出来并且报新建了几个(@TempDir Path dir) throws Exception {
        Path nested = dir.resolve("docs/rules");
        Run run = run("--docs", nested.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(Files.isRegularFile(nested.resolve("llm022.md")));
        assertTrue(run.out().contains("新建 22 个"), run.out());
    }

    @Test
    void 帮助里有docs() {
        Run run = run("--help");
        assertEquals(0, run.code());
        assertTrue(run.out().contains("--docs"), run.out());
    }
}