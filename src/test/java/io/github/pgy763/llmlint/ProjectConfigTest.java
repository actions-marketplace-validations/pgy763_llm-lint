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

/**
 * 按项目配置这一层的验收：仓库里的 .llm-lint.yml 真的能替代 CI 里手写的参数，
 * 同时命令行仍然说最后一句话。
 */
class ProjectConfigTest {

    private static final Path DEMO = Path.of("samples/config-demo");
    private static final Path DEMO_CONFIG = DEMO.resolve(".llm-lint.yml");

    private record Run(int code, String out, String err) {

        String combined() {
            return out + "\n" + err;
        }
    }

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    private static int count(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }

    @Test
    void 配置文件一到位就把三条问题收成一条() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--format", "json", DEMO.toString());

        assertEquals(1, run.code, run.combined());
        assertEquals(1, count(run.out, "\"ruleId\""), run.out);
        assertTrue(run.out.contains("\"ruleId\":\"LLM007\""), run.out);
        assertTrue(run.out.contains("\"severity\":\"ERROR\""), "配置把 warning 提到了 error");
        assertFalse(run.out.contains("LLM004"), "关掉的规则不该出现");
        assertFalse(run.out.contains("LLM010"), "被 exclude 的目录不该被扫");
    }

    @Test
    void 不写config也能从被扫的路径找到仓库配置() {
        Run run = run("--format", "json", DEMO.toString());

        assertEquals(1, run.code, run.combined());
        assertEquals(1, count(run.out, "\"ruleId\""), run.out);
        assertTrue(run.err.contains(".llm-lint.yml"), "统计行要说清楚用了哪份配置：" + run.err);
    }

    @Test
    void noConfig等于回到没有这个功能的样子() {
        Run run = run("--no-config", "--format", "json", DEMO.toString());

        assertEquals(1, run.code, run.combined());
        assertEquals(3, count(run.out, "\"ruleId\""), run.out);
        assertTrue(run.out.contains("LLM004"), run.out);
        assertTrue(run.out.contains("LLM007"), run.out);
        assertTrue(run.out.contains("LLM010"), run.out);
        assertFalse(run.err.contains(".llm-lint.yml"), run.err);
    }

    @Test
    void 命令行failOn覆盖配置文件() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--fail-on", "never", DEMO.toString());
        assertEquals(0, run.code, run.combined());
    }

    @Test
    void 配置文件里的failOn也生效也能被命令行拧回去(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("never.yml");
        Files.writeString(config, "rules:\n  LLM007: error\nfail-on: never\n");

        assertEquals(0, run("--config", config.toString(), DEMO.toString()).code);
        assertEquals(1, run("--config", config.toString(), "--fail-on", "error", DEMO.toString()).code);
    }

    @Test
    void 命令行的disable和配置叠加() {
        Run run = run("--config", DEMO_CONFIG.toString(),
                "--disable", "LLM007", "--format", "json", DEMO.toString());

        assertEquals(0, run.code, run.combined());
        assertEquals("[]", run.out.trim());
    }

    @Test
    void 命令行exclude和配置取并集() {
        Run run = run("--config", DEMO_CONFIG.toString(),
                "--exclude", "**/main/**", "--format", "json", DEMO.toString());

        assertEquals(0, run.code, run.combined());
        assertEquals("[]", run.out.trim(), "配置排掉 generated，命令行排掉 main，一个文件都不剩");
    }

    @Test
    void 配置写错说清楚是第几行哪条规则(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("bad.yml");
        Files.writeString(config, "version: 1\nrules:\n  LLM07: off\n");

        Run run = run("--config", config.toString(), DEMO.toString());

        assertEquals(2, run.code, run.combined());
        assertTrue(run.err.contains(":3:"), run.err);
        assertTrue(run.err.contains("是不是 LLM007"), run.err);
        assertTrue(run.err.contains("--list-rules"), run.err);
    }

    @Test
    void 显式指定的配置不存在也退出2(@TempDir Path tmp) {
        Run run = run("--config", tmp.resolve("nope.yml").toString(), DEMO.toString());

        assertEquals(2, run.code, run.combined());
        assertTrue(run.err.contains("找不到配置文件"), run.err);
    }

    @Test
    void printConfig摊开全部规则和合并后的排除路径() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--print-config",
                "--fail-on", "warning", "--exclude", "**/tmp/**");

        assertEquals(0, run.code, run.combined());
        assertTrue(run.out.contains("LLM004: off"), run.out);
        assertTrue(run.out.contains("LLM007: error"), run.out);
        assertTrue(run.out.contains("LLM015: "), "没写的规则也要印出来：" + run.out);
        assertTrue(run.out.contains("fail-on: warning"), run.out);
        assertTrue(run.out.contains("**/generated/**"), run.out);
        assertTrue(run.out.contains("**/tmp/**"), run.out);
    }

    @Test
    void listRules把关掉的规则标成off() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--list-rules");

        assertEquals(0, run.code, run.combined());
        assertEquals(18, run.out.lines().count(), run.out);
        assertTrue(run.out.lines().anyMatch(l -> l.startsWith("LLM004") && l.contains(" off ")), run.out);
        assertTrue(run.out.lines().anyMatch(l -> l.startsWith("LLM007") && l.contains(" error ")), run.out);
    }

    @Test
    void explain对关掉的规则也解释并标注off() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--explain", "LLM004");

        assertEquals(0, run.code, run.combined());
        assertTrue(run.out.contains("off（.llm-lint.yml 里关掉了）"), run.out);
    }

    @Test
    void github注解用的是改过之后的级别() {
        Run run = run("--config", DEMO_CONFIG.toString(), "--format", "github", DEMO.toString());

        assertEquals(1, run.code, run.combined());
        String annotations = run.out.lines().filter(l -> l.startsWith("::")).reduce("", (a, b) -> a + b + "\n");
        assertEquals(1, annotations.lines().filter(l -> !l.isEmpty()).count(), annotations);
        assertTrue(annotations.startsWith("::error"), "LLM007 现在是 error：" + annotations);
    }

    /** 真实终端的 stdout 是带缓冲的：只 print 不 flush，用户那边就是一个字都没有。 */
    private static final class FlushSpy extends PrintWriter {

        private boolean flushed;

        private FlushSpy(StringWriter target) {
            super(target);
        }

        @Override
        public void flush() {
            flushed = true;
            super.flush();
        }
    }

    @Test
    void printConfig结束前要把缓冲区刷出去() {
        FlushSpy out = new FlushSpy(new StringWriter());
        int code = new CommandLine(new Main())
                .setOut(out)
                .setErr(new PrintWriter(new StringWriter()))
                .execute("--config", DEMO_CONFIG.toString(), "--print-config");

        assertEquals(0, code);
        assertTrue(out.flushed, "--print-config 必须 flush，否则管道里什么都拿不到");
    }
}