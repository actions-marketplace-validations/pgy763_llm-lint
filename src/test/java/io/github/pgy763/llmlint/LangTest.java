package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.lang.Lang;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangTest {

    @Test
    void 不写lang就是中文() {
        Result result = run("samples/bad", "--format", "text", "--fail-on", "never");
        assertEquals(0, result.code);
        assertTrue(result.err.contains("个问题"), result.err);
        assertTrue(result.err.contains("扫了"), result.err);
    }

    @Test
    void lang_en把报告和统计都换成英文() {
        Result result = run("--lang", "en", "samples/bad", "--format", "text", "--fail-on", "never");
        assertEquals(0, result.code);
        assertTrue(result.out.contains("error"), result.out);
        assertTrue(result.err.contains("findings"), result.err);
        assertTrue(result.err.contains("scanned 20 files"), result.err);
        assertTrue(!result.err.contains("个问题"), result.err);
    }

    @Test
    void 英文帮助里没有中文提法() {
        Result result = run("--lang", "en", "--help");
        assertEquals(0, result.code);
        assertTrue(result.out.contains("--lang"), result.out);
        assertTrue(result.out.contains("Language for human-readable output"), result.out);
    }

    @Test
    void 不认识的lang退出2() {
        Result result = run("--lang", "de", "samples/bad");
        assertEquals(2, result.code);
        assertTrue(result.err.contains("unknown language"), result.err);
        assertTrue(result.err.contains("zh, en"), result.err);
    }

    @Test
    void lang的写法认别名() {
        assertEquals(Lang.ZH, Lang.of(null));
        assertEquals(Lang.ZH, Lang.of("  "));
        assertEquals(Lang.ZH, Lang.of("zh-CN"));
        assertEquals(Lang.ZH, Lang.of("Chinese"));
        assertEquals(Lang.EN, Lang.of("EN"));
        assertEquals(Lang.EN, Lang.of("en_US"));
        assertThrows(IllegalArgumentException.class, () -> Lang.of("de"));
    }

    @Test
    void lang不影响json的字段名() {
        Result result = run("--lang", "en", "--format", "json", "--fail-on", "never", "samples/bad");
        assertEquals(0, result.code);
        assertTrue(result.out.contains("\"ruleId\""), result.out);
        assertTrue(result.out.contains("\"docUrl\""), result.out);
        assertTrue(!result.out.contains("规则"), result.out);
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new Main());
        commandLine.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
        int code = commandLine.execute(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}