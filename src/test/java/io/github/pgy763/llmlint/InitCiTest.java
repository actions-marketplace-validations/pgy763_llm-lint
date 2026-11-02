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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --init-ci：写一份能直接用的 GitHub Actions 配置，但绝不覆盖已有的。 */
class InitCiTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        Main main = new Main();
        CommandLine cmd = new CommandLine(main);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return new Run(cmd.execute(args), out.toString(), err.toString());
    }

    @Test
    void 写出能直接用的工作流(@TempDir Path tmp) throws IOException {
        Run run = run("--init-ci", tmp.toString());

        assertEquals(0, run.code(), run.err());
        Path workflow = tmp.resolve(".github/workflows/llm-lint.yml");
        assertTrue(Files.isRegularFile(workflow));
        String text = Files.readString(workflow);
        assertTrue(text.contains("name: llm-lint"), text);
        assertTrue(text.contains("uses: pgy763/llm-lint@v1"), text);
        assertTrue(text.contains("path: src/main/java"), text);
        assertTrue(text.contains("pull_request:"), text);
        assertTrue(run.out().contains("写好："), run.out());
    }

    @Test
    void 已经有工作流时不覆盖(@TempDir Path tmp) throws IOException {
        Path workflow = tmp.resolve(".github/workflows/llm-lint.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(workflow, "name: mine\n");

        Run run = run("--init-ci", tmp.toString());

        assertEquals(2, run.code());
        assertTrue(run.err().contains("不覆盖"), run.err());
        assertEquals("name: mine\n", Files.readString(workflow));
    }

    @Test
    void 写好之后提示怎么钉版本(@TempDir Path tmp) {
        Run run = run("--init-ci", tmp.toString());

        assertTrue(run.out().contains("version:"), run.out());
    }
}