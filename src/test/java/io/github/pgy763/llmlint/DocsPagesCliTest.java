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

    @Test
    void 索引页的表格也跟着换(@TempDir Path dir) throws Exception {
        Path pages = dir.resolve("docs/rules");
        Files.createDirectories(pages);
        Path index = dir.resolve("docs/rules.md");
        String original = "---\ntitle: 测试\n---\n\n<!-- generated:rules-index -->\n"
                + "| 旧表 |\n<!-- /generated:rules-index -->\n\n## 手写部分\n\n别删我。\n";
        Files.writeString(index, original, StandardCharsets.UTF_8);

        Run run = run("--docs", pages.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("索引页也换了一段"), run.out());

        String text = Files.readString(index, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("---\ntitle: 测试\n---"), "front matter 不该被动");
        assertTrue(text.contains("| 编号 | 级别 | 框架 | 拦的是什么 |"), text);
        assertTrue(text.contains("| [LLM001](#llm001) | error |"), text);
        assertTrue(!text.contains("| 旧表 |"), "旧的表格行应该被换掉");
        assertTrue(text.contains("别删我。"), "标记之后的手写内容要留着");

        String once = Files.readString(index, StandardCharsets.UTF_8);
        assertEquals(0, run("--docs", pages.toString()).code());
        assertEquals(once, Files.readString(index, StandardCharsets.UTF_8), "重跑不该再改索引页");
    }

    @Test
    void 索引页没有标记就跳过不动(@TempDir Path dir) throws Exception {
        Path pages = dir.resolve("docs/rules");
        Files.createDirectories(pages);
        Path index = dir.resolve("docs/rules.md");
        Files.writeString(index, "手写的索引，没有标记。\n", StandardCharsets.UTF_8);

        Run run = run("--docs", pages.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("没找到"), run.out());
        assertEquals("手写的索引，没有标记。\n", Files.readString(index, StandardCharsets.UTF_8));
    }

    @Test
    void 英文页面写到自己的目录并且链接带en前缀(@TempDir Path dir) throws Exception {
        Path en = dir.resolve("en/rules");
        Files.createDirectories(dir.resolve("en"));
        Path index = dir.resolve("en/rules.md");
        String nl = String.valueOf((char) 10);
        String header = String.join(nl, "---", "title: 22 rules", "---", "",
                "<!-- generated:rules-index -->", "<!-- /generated:rules-index -->", "",
                "## Suppressing a finding", "", "keep me", "");
        Files.writeString(index, header, StandardCharsets.UTF_8);

        Run run = run("--docs-en", en.toString());
        assertEquals(0, run.code(), run.err());
        String page = Files.readString(en.resolve("llm001.md"), StandardCharsets.UTF_8);
        assertTrue(page.contains("permalink: /en/rules/llm001.html"), page);
        assertTrue(page.contains("generated by `llm-lint --docs-en"), page);

        String idx = Files.readString(index, StandardCharsets.UTF_8);
        assertTrue(idx.contains("| Rule | Level | Framework | What it catches |"), idx);
        assertTrue(idx.contains("/en/rules/llm001.html"), idx);
        assertTrue(idx.contains("keep me"), "索引页的手写部分被弄丢了");
    }
}
