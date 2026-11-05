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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --fix --write：把补丁直接写回文件，而不是打印 diff。 */
class FixWriteCliTest {

    @TempDir
    Path tmp;

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

    /** samples/bad 复制一份到临时目录——--write 会改文件，不能动仓库里的样例。 */
    private Path copyBad() throws IOException {
        Path dir = tmp.resolve("src");
        Files.createDirectories(dir);
        try (var files = Files.list(Path.of("samples/bad"))) {
            List<Path> all = files.toList();
            for (Path file : all) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
        }
        return dir;
    }

    @Test
    void 直接把补丁写回文件() throws IOException {
        Path dir = copyBad();
        Run run = run("--fix", "--write", dir.toString());

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("一共改了 2 处，动了 2 个文件"), run.out());

        String charset = Files.readString(dir.resolve("LLM006_SseCharset.java"), StandardCharsets.UTF_8);
        assertTrue(charset.contains("text/event-stream;charset=UTF-8"), charset);
        String produces = Files.readString(dir.resolve("LLM018_StreamingWithoutProduces.java"), StandardCharsets.UTF_8);
        assertTrue(produces.contains("produces = \"text/event-stream;charset=UTF-8\""), produces);

        // 别的文件不许被动：没有任何补丁的文件要保持原样
        String untouched = Files.readString(dir.resolve("LLM010_HardcodedKey.java"), StandardCharsets.UTF_8);
        assertTrue(untouched.contains("sk-"), "这个文件不该被 --write 改");

        // 修完再扫：那两条不再报，剩下的照旧
        Run again = run(dir.toString());
        assertEquals(1, again.code());
        assertFalse(again.out().contains("LLM006"), again.out());
        assertFalse(again.out().contains("LLM018"), again.out());
        assertTrue(again.err().contains("20 个问题"), again.err());
    }

    @Test
    void 不带write时只打补丁不动文件() throws IOException {
        Path dir = copyBad();
        String before = Files.readString(dir.resolve("LLM006_SseCharset.java"), StandardCharsets.UTF_8);

        Run run = run("--fix", dir.toString());

        assertTrue(run.out().contains("--- a/"), run.out());
        assertTrue(run.out().contains("+    @GetMapping(value = \"/stream\", produces = \"text/event-stream;charset=UTF-8\")"),
                run.out());
        assertEquals(before, Files.readString(dir.resolve("LLM006_SseCharset.java"), StandardCharsets.UTF_8),
                "--fix 不带 --write 时不许动用户的文件");
    }

    @Test
    void 只写write不给fix会被拒绝() {
        Run run = run("--write", "samples/good");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("--write"), run.err());
        assertTrue(run.err().contains("--fix"), run.err());
    }

    @Test
    void 没有可修的项时什么都不写() throws IOException {
        Path dir = tmp.resolve("clean");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Clean.java"),
                "class Clean {\n    int add(int a, int b) {\n        return a + b;\n    }\n}\n",
                StandardCharsets.UTF_8);

        Run run = run("--fix", "--write", dir.toString());

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("一共改了 0 处"), run.out());
        assertTrue(run.err().isEmpty(), run.err());
    }
}