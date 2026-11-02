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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存量项目接入的验收：第一遍把旧问题记进基线，之后只拦新出现的；
 * 基线写坏了、找不到了，要退出 2 并说清楚。
 */
class BaselineCliTest {

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

    /** 把 samples/bad 复制进临时目录：一份「别人家的存量代码」，15 条问题一条不少。 */
    private static Path copyBad(Path tmp) throws IOException {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        try (Stream<Path> files = Files.list(Path.of("samples/bad"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Files.copy(file, src.resolve(file.getFileName()));
            }
        }
        return src;
    }

    @Test
    void 存了基线之后旧问题不再报也不再卡退出码(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");

        Run save = run("--save-baseline", baseline.toString(), "--fail-on", "never",
                "--format", "json", src.toString());
        assertEquals(0, save.code, save.combined());
        assertTrue(save.err().contains("这次新增 15 条"), save.err());
        assertEquals(16, Files.readAllLines(baseline, StandardCharsets.UTF_8).size(), "表头 + 15 条");

        Run again = run("--baseline", baseline.toString(), "--format", "json", src.toString());
        assertEquals(0, again.code, "旧问题全部记在基线里，退出码应该是 0：" + again.combined());
        assertTrue(again.out().contains("[]"), again.out());
        assertTrue(again.err().contains("另有 15 条在基线里"), again.err());
    }

    @Test
    void 基线之外的新问题照样拦(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        // 同一份反例换个文件名，就是「新写的代码里又犯了一次」
        Files.copy(Path.of("samples/bad/LLM010_HardcodedKey.java"),
                src.resolve("LLM010_AnotherOne.java"));

        Run run = run("--baseline", baseline.toString(), "--format", "json", src.toString());

        assertEquals(1, run.code, run.combined());
        assertTrue(run.out().contains("LLM010_AnotherOne.java"), run.out());
        assertFalse(run.out().contains("LLM001_TransactionalModelCall.java"), "旧的仍然不该出现：" + run.out());
        assertTrue(run.err().contains("另有 15 条在基线里"), run.err());
    }

    @Test
    void 再存一次基线会把新问题并进去(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());
        Files.copy(Path.of("samples/bad/LLM010_HardcodedKey.java"),
                src.resolve("LLM010_AnotherOne.java"));

        Run save = run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        assertEquals(0, save.code, save.combined());
        assertEquals(17, Files.readAllLines(baseline, StandardCharsets.UTF_8).size(), "表头 + 16 条");
        assertTrue(save.err().contains("这次新增 1 条"), save.err());
        assertEquals(0, run("--baseline", baseline.toString(), src.toString()).code);
    }

    @Test
    void 基线写在配置文件里也会生效(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());
        Path config = tmp.resolve(".llm-lint.yml");
        Files.writeString(config, """
                version: 1
                baseline: "llm-lint.baseline"
                """, StandardCharsets.UTF_8);

        Run run = run("--config", config.toString(), "--format", "json", src.toString());

        assertEquals(0, run.code, run.combined());
        assertTrue(run.out().contains("[]"), run.out());
        assertTrue(run.err().contains("llm-lint.baseline（15 条）"), run.err());
    }

    @Test
    void 基线文件找不到时退出2(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);

        Run run = run("--baseline", tmp.resolve("没写过.baseline").toString(), src.toString());

        assertEquals(2, run.code, run.combined());
        assertTrue(run.err().contains("找不到基线文件"), run.err());
        assertTrue(run.err().contains("--save-baseline"), "要告诉用户怎么生成：" + run.err());
    }

    @Test
    void 基线文件写坏时指出行号(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("bad.baseline");
        Files.writeString(baseline, "# 表头\nLLM001\tsrc/A.java\n", StandardCharsets.UTF_8);

        Run run = run("--baseline", baseline.toString(), src.toString());

        assertEquals(2, run.code, run.combined());
        assertTrue(run.err().contains(":2:"), run.err());
    }

    @Test
    void 空基线等于没写(@TempDir Path tmp) throws IOException {
        // 没有 --baseline、也没有配置：行为和以前完全一样（15 条，退出 1）
        Path src = copyBad(tmp);

        Run run = run("--format", "json", src.toString());

        assertEquals(1, run.code, run.combined());
        assertEquals(15, count(run.out(), "\"ruleId\""));
    }

    private static int count(String text, String needle) {
        int total = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            total++;
            at = text.indexOf(needle, at + needle.length());
        }
        return total;
    }
}