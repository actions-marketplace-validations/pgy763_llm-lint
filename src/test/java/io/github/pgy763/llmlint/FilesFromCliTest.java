package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --files-from：清单怎么并进扫描范围。 */
class FilesFromCliTest {

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

    private Path list(String... lines) throws IOException {
        Path file = tmp.resolve("files.txt");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void 只扫清单里列的文件() throws Exception {
        Path file = list(
                "samples/bad/LLM001_TransactionalModelCall.java",
                "samples/bad/LLM010_HardcodedKey.java");

        Run run = run("--files-from", file.toString());

        assertEquals(1, run.code(), run.err());
        assertTrue(run.out().contains("LLM001"), run.out());
        assertTrue(run.out().contains("LLM010"), run.out());
        assertFalse(run.out().contains("LLM004"), run.out());
    }

    @Test
    void 清单里的注释空行和非java行都被忽略() throws Exception {
        Path file = list(
                "# 这次改动涉及的文件",
                "",
                "README.md",
                "samples/bad/DoesNotExist.java",
                "   samples/bad/LLM004_SseEmitterDefaultTimeout.java   ");

        Run run = run("--files-from", file.toString());

        assertEquals(1, run.code(), run.err());
        assertTrue(run.out().contains("LLM004"), run.out());
        assertFalse(run.out().contains("LLM001"), run.out());
    }

    @Test
    void 清单里的文件都不存在时安静退出() throws Exception {
        Path file = list("samples/bad/Nope.java");

        Run run = run("--files-from", file.toString());

        assertEquals(0, run.code());
        assertTrue(run.err().contains("没有能扫的"), run.err());
    }

    @Test
    void 找不到清单文件退出2() {
        Run run = run("--files-from", tmp.resolve("nope.txt").toString());

        assertEquals(2, run.code());
        assertTrue(run.err().contains("--files-from"), run.err());
    }

    @Test
    void 和路径一起给时取交集() throws Exception {
        Path file = list(
                "samples/bad/LLM001_TransactionalModelCall.java",
                "samples/bad/LLM010_HardcodedKey.java");

        Run run = run("--files-from", file.toString(), "samples/bad/LLM010_HardcodedKey.java");

        assertEquals(1, run.code(), run.err());
        assertTrue(run.out().contains("LLM010"), run.out());
        assertFalse(run.out().contains("LLM001"), run.out());
    }

    @Test
    void 清单从标准输入读() {
        ByteArrayInputStream stdin = new ByteArrayInputStream(
                "samples/bad/LLM010_HardcodedKey.java\n".getBytes(StandardCharsets.UTF_8));
        java.io.InputStream original = System.in;
        System.setIn(stdin);
        try {
            Run run = run("--files-from", "-");

            assertEquals(1, run.code(), run.err());
            assertTrue(run.out().contains("LLM010"), run.out());
        } finally {
            System.setIn(original);
        }
    }

    @Test
    void 帮助里有files_from() {
        Run run = run("--help");

        assertEquals(0, run.code());
        assertTrue(run.out().contains("--files-from"), run.out());
    }
}
