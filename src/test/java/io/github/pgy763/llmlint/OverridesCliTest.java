package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按路径覆盖：exclude 是「这段路径整个不看」，overrides 是「照扫，但这里的级别不一样」。
 *
 * <p>生成代码里硬编码的 key 是模板吐出来的，不值得报；可 main 下的 maxTokens 又必须当 error 拦。
 * 这不冲突，只要配置能按路径说话。
 */
class OverridesCliTest {

    private record Run(int code, String out, String err) {
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

    private static final String CONFIG = """
            version: 1
            overrides:
              - files: "src/generated/**"
                rules:
                  LLM010: off
              - files: "src/main/**"
                rules:
                  LLM007: error
            """;

    /** 一个 main 下的 maxTokens(128)（LLM007 warning），一个 generated 下的硬编码 key（LLM010 error）。 */
    private static Path project(Path dir) throws Exception {
        Path main = dir.resolve("src/main/java/demo");
        Path generated = dir.resolve("src/generated/java/demo");
        Files.createDirectories(main);
        Files.createDirectories(generated);

        Files.writeString(main.resolve("Assistant.java"), """
                package demo;

                import org.springframework.ai.chat.client.ChatClient;
                import org.springframework.ai.chat.prompt.ChatOptions;
                import org.springframework.stereotype.Service;

                @Service
                public class Assistant {

                    private final ChatClient.Builder builder;

                    public Assistant(ChatClient.Builder builder) {
                        this.builder = builder;
                    }

                    public String summarize(String text) {
                        ChatClient client = builder.defaultOptions(ChatOptions.builder().maxTokens(128).build()).build();
                        return client.prompt().user(text).call().content();
                    }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(generated.resolve("Keys.java"), """
                package demo;

                public final class Keys {

                    static final String KEY = "sk-proj-AbCdEf0123456789AbCdEf0123456789";

                    private Keys() {
                    }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(dir.resolve(".llm-lint.yml"), CONFIG, StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    void 覆盖只改它那段路径下的级别(@TempDir Path dir) throws Exception {
        project(dir);
        Run run = run("--config", dir.resolve(".llm-lint.yml").toString(), "--format", "json", dir.toString());

        assertEquals(1, run.code, run.out);
        assertEquals(1, run.out.split("\"ruleId\"", -1).length - 1, run.out);
        assertTrue(run.out.contains("\"ruleId\":\"LLM007\""), run.out);
        assertTrue(run.out.contains("\"severity\":\"ERROR\""), "main 下被提到 error：" + run.out);
        assertFalse(run.out.contains("LLM010"), "generated 下那条被 off 掉了：" + run.out);
        assertTrue(run.out.contains("src/main/java/demo/Assistant.java"), run.out);
    }

    @Test
    void 规则目录本身还是默认级别(@TempDir Path dir) throws Exception {
        project(dir);
        Run run = run("--config", dir.resolve(".llm-lint.yml").toString(), "--list-rules");

        assertEquals(0, run.code, run.err());
        String seven = run.out.lines().filter(l -> l.startsWith("LLM007")).findFirst().orElse("");
        assertTrue(seven.contains(" warning "), "覆盖不该骗 --list-rules：" + seven);
        String ten = run.out.lines().filter(l -> l.startsWith("LLM010")).findFirst().orElse("");
        assertTrue(ten.contains(" error "), ten);
    }

    @Test
    void 后面那段盖掉前面那段(@TempDir Path dir) throws Exception {
        project(dir);
        Files.writeString(dir.resolve(".llm-lint.yml"), """
                version: 1
                overrides:
                  - files: "src/**"
                    rules:
                      LLM007: info
                  - files: "src/main/**"
                    rules:
                      LLM007: error
                """, StandardCharsets.UTF_8);

        Run run = run("--config", dir.resolve(".llm-lint.yml").toString(), "--format", "json", dir.toString());
        assertTrue(run.out.contains("\"ruleId\":\"LLM007\""), run.out);
        assertTrue(run.out.contains("\"severity\":\"ERROR\""), "后写的 src/main/** 更具体：" + run.out);
    }

    @Test
    void printConfig把覆盖也摊开并且读得回来(@TempDir Path dir) throws Exception {
        project(dir);
        Run printed = run("--config", dir.resolve(".llm-lint.yml").toString(), "--print-config");
        assertEquals(0, printed.code, printed.err());
        assertTrue(printed.out.contains("overrides:"), printed.out);
        assertTrue(printed.out.contains("- files: \"src/generated/**\""), printed.out);

        Path copy = dir.resolve("printed.yml");
        Files.writeString(copy, printed.out, StandardCharsets.UTF_8);
        Run again = run("--config", copy.toString(), "--format", "json", dir.toString());
        assertEquals(1, again.code, again.err());
        assertEquals(1, again.out.split("\"ruleId\"", -1).length - 1, again.out);
        assertTrue(again.out.contains("\"severity\":\"ERROR\""), again.out);
    }

    @Test
    void 覆盖写错了指出是哪一行(@TempDir Path dir) throws Exception {
        project(dir);
        Files.writeString(dir.resolve(".llm-lint.yml"), """
                version: 1
                overrides:
                  - files: "src/**"
                    rules:
                      LLM7: off
                """, StandardCharsets.UTF_8);

        Run run = run("--config", dir.resolve(".llm-lint.yml").toString(), dir.toString());
        assertEquals(2, run.code, run.err());
        assertTrue(run.err.contains(":5:"), run.err);
        assertTrue(run.err.contains("LLM7"), run.err);
    }

    @Test
    void 模板里给了overrides的例子() {
        String text = io.github.pgy763.llmlint.config.ConfigLoader.template(Rules.all());
        assertTrue(text.contains("# overrides:"), text);
        assertTrue(text.contains("LLM010: off"), text);
    }

    @Test
    void 没有覆盖时就是全局默认级别(@TempDir Path dir) throws Exception {
        project(dir);
        Files.delete(dir.resolve(".llm-lint.yml"));
        Run run = run("--format", "json", dir.toString());

        assertEquals(1, run.code, "LLM010 默认 error，没有配置就该拦下来：" + run.err());
        assertEquals(2, run.out.split("\"ruleId\"", -1).length - 1, run.out);
        assertTrue(run.out.contains("\"ruleId\":\"LLM007\""), run.out);
        assertTrue(run.out.contains("\"ruleId\":\"LLM010\""), run.out);
        assertTrue(run.out.contains("\"severity\":\"WARNING\""), "LLM007 默认还是 warning：" + run.out);
    }
}