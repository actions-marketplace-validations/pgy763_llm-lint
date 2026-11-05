package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --only：这次只跑哪几条规则。 */
class OnlyRulesCliTest {

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

    @Test
    void 只跑一条规则() {
        Run run = run("--only", "LLM010", "samples/bad");

        assertEquals(1, run.code());
        assertTrue(run.err().contains("1 个问题（1 error / 0 warning）"), run.err());
        assertTrue(run.out().contains("LLM010"), run.out());
        assertFalse(run.out().contains("LLM001"), run.out());
    }

    @Test
    void 逗号分隔跑两条() {
        Run run = run("--only", "LLM007,LLM010", "samples/bad");

        assertEquals(1, run.code());
        assertTrue(run.err().contains("2 个问题"), run.err());
        assertTrue(run.out().contains("LLM007"), run.out());
        assertTrue(run.out().contains("LLM010"), run.out());
        assertFalse(run.out().contains("LLM001"), run.out());
    }

    @Test
    void 小写和空格也认() {
        Run run = run("--only", " llm007 , llm010 ", "samples/bad");

        assertEquals(1, run.code());
        assertTrue(run.err().contains("2 个问题"), run.err());
    }

    @Test
    void 规则号写错了给建议() {
        Run run = run("--only", "LLM07", "samples/bad");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("没有 LLM07 这条规则"), run.err());
        assertTrue(run.err().contains("是不是 LLM007"), run.err());
    }

    @Test
    void 和disable一起用会被拒绝() {
        Run run = run("--only", "LLM010", "--disable", "LLM001", "samples/bad");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("--only"), run.err());
        assertTrue(run.err().contains("--disable"), run.err());
    }

    @Test
    void listRules也只看这几条() {
        Run run = run("--list-rules", "--only", "LLM001");

        assertEquals(0, run.code(), run.err());
        long rows = run.out().lines().filter(line -> line.startsWith("LLM0")).count();
        assertEquals(22, rows, "表里还是全部 22 条，只是没选中的标成 off");
        long on = run.out().lines().filter(line -> line.startsWith("LLM0") && !line.contains(" off ")).count();
        assertEquals(1, on, run.out());
    }

    @Test
    void 帮助里有only和write() {
        Run run = run("--help");

        assertEquals(0, run.code());
        assertTrue(run.out().contains("--only"), run.out());
        assertTrue(run.out().contains("--write"), run.out());
    }
}