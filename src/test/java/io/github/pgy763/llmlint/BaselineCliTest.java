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

    /** 把 samples/bad 复制进临时目录：一份「别人家的存量代码」，22 条问题一条不少。 */
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
        assertTrue(save.err().contains("这次新增 22 条"), save.err());
        assertEquals(23, Files.readAllLines(baseline, StandardCharsets.UTF_8).size(), "表头 + 22 条");

        Run again = run("--baseline", baseline.toString(), "--format", "json", src.toString());
        assertEquals(0, again.code, "旧问题全部记在基线里，退出码应该是 0：" + again.combined());
        assertTrue(again.out().contains("[]"), again.out());
        assertTrue(again.err().contains("另有 22 条在基线里"), again.err());
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
        assertTrue(run.err().contains("另有 22 条在基线里"), run.err());
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
        assertEquals(24, Files.readAllLines(baseline, StandardCharsets.UTF_8).size(), "表头 + 23 条");
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
        assertTrue(run.err().contains("llm-lint.baseline（22 条）"), run.err());
    }

    @Test
    void 统计能看出还剩多少旧账(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        Run stats = run("--baseline", baseline.toString(), "--baseline-stats", "--format", "json", src.toString());

        assertEquals(0, stats.code, stats.combined());
        assertTrue(stats.err().contains("共 22 条，还在报 22 条"), stats.err());
        assertTrue(stats.err().contains("按规则：LLM001 1、LLM002 1"), stats.err());
        assertTrue(stats.err().contains("没有修好的旧问题"), stats.err());
    }

    @Test
    void 修好的旧问题会被统计出来(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        // 把一个文件删掉 = 那个旧问题没了，基线里那一行就成了「可以删的旧账」
        Files.delete(src.resolve("LLM010_HardcodedKey.java"));

        Run stats = run("--baseline", baseline.toString(), "--baseline-stats", src.toString());

        assertEquals(0, stats.code, stats.combined());
        assertTrue(stats.err().contains("共 22 条，还在报 21 条"), stats.err());
        assertTrue(stats.err().contains("已经修好 1 条（LLM010 1）"), stats.err());
        // 「按规则」只算还在报的：修好的 LLM010 不该再列出来
        assertTrue(stats.err().contains("按规则：LLM001 1"), stats.err());
        assertFalse(stats.err().contains("按规则：LLM010"), stats.err());
    }

    @Test
    void 旧账全修完时说一句都不报了(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        for (String name : List.of("LLM001_TransactionalModelCall.java", "LLM002_SharedChatMemory.java",
                "LLM003_ChatClientInjection.java", "LLM004_SseEmitterDefaultTimeout.java",
                "LLM005_BlockInStreamingMethod.java", "LLM006_SseCharset.java", "LLM007_SmallMaxTokens.java",
                "LLM008_SwallowedIoException.java", "LLM009_HttpClientWithoutTimeout.java",
                "LLM010_HardcodedKey.java", "LLM011_ClientPerRequest.java", "LLM012_AiServicesPerRequest.java",
                "LLM013_ModelCallInLoop.java", "LLM014_MemoryConstantId.java", "LLM015_ReactiveInTransaction.java",
                "LLM016_ToolReturnsReactive.java", "LLM017_VectorInTransaction.java", "LLM018_StreamingWithoutProduces.java", "LLM019_ToolBlocksOnDb.java", "LLM020_TransactionBlocks.java", "LLM021_TransactionalSseEmitter.java", "LLM022_ScheduledModelCall.java")) {
            Files.delete(src.resolve(name));
        }

        Run stats = run("--baseline", baseline.toString(), "--baseline-stats", src.toString());

        assertEquals(0, stats.code, stats.combined());
        assertTrue(stats.err().contains("共 22 条，还在报 0 条"), stats.err());
        assertTrue(stats.err().contains("基线里的旧问题都不报了"), stats.err());
        assertTrue(stats.err().contains("已经修好 22 条"), stats.err());
    }

    @Test
    void 没有基线时统计退出2(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);

        Run run = run("--baseline-stats", src.toString());

        assertEquals(2, run.code, run.combined());
        assertTrue(run.err().contains("--baseline-stats"), run.err());
        assertTrue(run.err().contains("baseline:"), "要提示配置里的写法：" + run.err());
    }

    @Test
    void quiet不会把统计也吞掉(@TempDir Path tmp) throws IOException {
        Path src = copyBad(tmp);
        Path baseline = tmp.resolve("llm-lint.baseline");
        run("--save-baseline", baseline.toString(), "--fail-on", "never", src.toString());

        Run stats = run("--baseline", baseline.toString(), "--baseline-stats", "--quiet", src.toString());

        assertEquals(0, stats.code, stats.combined());
        assertTrue(stats.err().contains("还在报 22 条"), "显式要的统计不该被 --quiet 吞掉：" + stats.err());
        assertFalse(stats.err().contains("个问题（"), "--quiet 该压掉的是那行总结：" + stats.err());
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
        // 没有 --baseline、也没有配置：行为和以前完全一样（22 条，退出 1）
        Path src = copyBad(tmp);

        Run run = run("--format", "json", src.toString());

        assertEquals(1, run.code, run.combined());
        assertEquals(22, count(run.out(), "\"ruleId\""));
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