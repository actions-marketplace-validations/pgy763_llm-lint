package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --since 的命令行外观；真正的 git 计算在 ChangedFilesTest 里。 */
class SinceCliTest {

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
    void 不认识的ref退出2并说清楚是since的问题() {
        Run run = run("--since", "origin/no-such-branch", "samples/bad");

        assertEquals(2, run.code(), run.err());
        assertTrue(run.err().contains("--since"), run.err());
    }

    @Test
    void 只写since不给路径也能跑到since分支() {
        Run run = run("--since", "HEAD");

        // 沙箱里 git 起不来、CI 里干净树没有改动，两种都算走到了 --since：
        // 唯一不能出现的是「要给一个目录」那句。
        assertFalse(run.err().contains("要给一个目录"), run.err());
    }

    @Test
    void 帮助里有since() {
        Run run = run("--help");

        assertEquals(0, run.code());
        assertTrue(run.out().contains("--since"), run.out());
    }
}