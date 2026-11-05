package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Main;
import io.github.pgy763.llmlint.Rules;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 并行扫：结果必须和串行一模一样，顺序也不能因为线程调度飘。 */
class ParallelScanTest {

    private static final Path BAD = Path.of("samples/bad");

    private static LintResult scan(int jobs) throws IOException {
        return new LintEngine(Rules.all(), List.of(), jobs).run(List.of(BAD));
    }

    @Test
    void 并行和串行扫出来的完全一样() throws IOException {
        LintResult serial = scan(1);
        LintResult parallel = scan(4);

        assertEquals(serial.findings(), parallel.findings());
        assertEquals(serial.filesScanned(), parallel.filesScanned());
        assertEquals(serial.filesSkipped(), parallel.filesSkipped());
        assertEquals(serial.suppressed(), parallel.suppressed());
        assertEquals(20, parallel.findings().size());
    }

    @Test
    void 并行多跑几次顺序也不飘() throws IOException {
        List<Finding> first = scan(4).findings();
        for (int i = 0; i < 3; i++) {
            assertEquals(first, scan(4).findings());
        }
        for (int i = 1; i < first.size(); i++) {
            Finding prev = first.get(i - 1);
            Finding cur = first.get(i);
            int cmp = prev.file().compareTo(cur.file());
            if (cmp == 0) {
                cmp = Integer.compare(prev.line(), cur.line());
            }
            assertTrue(cmp <= 0, "顺序不对：" + prev + " → " + cur);
        }
    }

    @Test
    void 只给一个文件时也不出错() throws IOException {
        LintResult result = new LintEngine(Rules.all(), List.of(), 8)
                .run(List.of(Path.of("samples/bad/LLM010_HardcodedKey.java")));

        assertEquals(1, result.filesScanned());
        assertEquals(1, result.findings().size());
        assertEquals("LLM010", result.findings().get(0).ruleId());
    }

    @Test
    void 命令行可以指定并行数() {
        Run run = run("--jobs", "4", "--format", "json", "samples/bad");

        assertEquals(1, run.code(), run.combined());
        assertEquals(20, count(run.out(), "\"ruleId\""), run.out());
    }

    @Test
    void jobs写负数退出2() {
        Run run = run("--jobs", "-1", "samples/bad");

        assertEquals(2, run.code(), run.combined());
        assertTrue(run.err().contains("--jobs"), run.err());
    }

    private static int count(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    private record Run(int code, String out, String err) {
        String combined() {
            return out + err;
        }
    }

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err));
        int code = commandLine.execute(args);
        return new Run(code, out.toString(), err.toString());
    }
}