package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --fix} 对 LLM019 / LLM020 不给补丁，但要说清楚怎么改。
 *
 * <p>这两条要挪代码位置，机器改容易改坏；所以这里锁的是「给建议、不动文件」。
 */
class AdviceCliTest {

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

    /** 两个只在 LLM019 / LLM020 上出问题的样例，拷进临时目录。 */
    private static Path project(Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.copy(Path.of("samples/bad/LLM019_ToolBlocksOnDb.java"), src.resolve("OrderTools.java"));
        Files.copy(Path.of("samples/bad/LLM020_TransactionBlocks.java"), src.resolve("OrderService.java"));
        return dir;
    }

    @Test
    void fix对这两条给建议而不动文件(@TempDir Path dir) throws Exception {
        Path project = project(dir);
        String toolsBefore = Files.readString(project.resolve("src/OrderTools.java"), StandardCharsets.UTF_8);
        String serviceBefore = Files.readString(project.resolve("src/OrderService.java"), StandardCharsets.UTF_8);

        Run run = run("--fix", "--only", "LLM019,LLM020", project.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("建议 LLM019"), run.out());
        assertTrue(run.out().contains("建议 LLM020"), run.out());
        assertTrue(run.out().contains("工具方法只组装参数"), run.out());
        assertTrue(run.out().contains("TransactionSynchronizationManager"), run.out());

        assertEquals(toolsBefore, Files.readString(project.resolve("src/OrderTools.java"), StandardCharsets.UTF_8),
                "--fix 不该动文件");
        assertEquals(serviceBefore, Files.readString(project.resolve("src/OrderService.java"), StandardCharsets.UTF_8),
                "--fix 不该动文件");
    }

    @Test
    void write模式也是零处改动但照样给建议(@TempDir Path dir) throws Exception {
        Path project = project(dir);
        String before = Files.readString(project.resolve("src/OrderTools.java"), StandardCharsets.UTF_8);

        Run run = run("--fix", "--write", "--only", "LLM019,LLM020", project.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("一共改了 0 处"), run.out());
        assertTrue(run.out().contains("建议 LLM019"), run.out());
        assertEquals(before, Files.readString(project.resolve("src/OrderTools.java"), StandardCharsets.UTF_8));
    }

    @Test
    void 能自动修的那条不会多出建议(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.copy(Path.of("samples/bad/LLM006_SseCharset.java"), src.resolve("StreamController.java"));

        Run run = run("--fix", "--only", "LLM006", dir.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("+"), run.out());
        assertFalse(run.out().contains("建议 LLM006"), run.out());
    }
}