package io.github.pgy763.llmlint;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.engine.LintEngine;
import io.github.pgy763.llmlint.engine.LintResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则唯一的验收标准：samples/bad 每条只报自己，samples/good 一条不报。
 *
 * <p>这比「能编译」更接近真实使用——真实项目里误报一次，作者就会把工具删掉。
 */
class SamplesTest {

    private static final Path SAMPLES = Path.of("samples");

    private static List<Finding> lint(Path file) throws IOException {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setAttributeComments(false));
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        return Rules.all().stream().flatMap(r -> r.check(file, cu).stream()).toList();
    }

    private static List<Path> javaFiles(String dir) throws IOException {
        try (Stream<Path> walk = Files.list(SAMPLES.resolve(dir))) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Set<String> expectedIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 1; i <= 18; i++) {
            ids.add(String.format("LLM%03d", i));
        }
        return ids;
    }

    private static CommandLine silentCli() {
        return new CommandLine(new Main())
                .setOut(new PrintWriter(java.io.OutputStream.nullOutputStream()))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
    }

    @Test
    void badSample触发且只触发它自己那条规则() throws IOException {
        List<Path> bad = javaFiles("bad");
        assertEquals(18, bad.size(), "应该有十八个反例");

        for (Path file : bad) {
            String expected = file.getFileName().toString().substring(0, 6);
            List<Finding> findings = lint(file);
            assertEquals(1, findings.size(),
                    file.getFileName() + " 应该正好报一条，实际: " + findings);
            assertEquals(expected, findings.get(0).ruleId(),
                    file.getFileName() + " 报错了规则");
            assertTrue(findings.get(0).line() > 0, "要有行号");
            assertTrue(findings.get(0).column() > 0, "要有列号");
            assertTrue(findings.get(0).docUrl().startsWith("https://"), "要有手册链接");
        }
    }

    @Test
    void goodSample不报任何问题() throws IOException {
        List<Path> good = javaFiles("good");
        assertEquals(18, good.size(), "应该有十八个正例");

        for (Path file : good) {
            List<Finding> findings = lint(file);
            assertEquals(List.of(), findings, file.getFileName() + " 不该报，误报了：" + findings);
        }
    }

    @Test
    void 规则编号唯一且十八条() {
        Set<String> ids = new LinkedHashSet<>();
        Rules.all().forEach(r -> assertTrue(ids.add(r.id()), "编号重复: " + r.id()));
        assertEquals(18, ids.size());
        assertEquals(expectedIds(), ids);
    }

    @Test
    void 每条规则都有标题级别理由和文档链接() {
        for (Rule r : Rules.all()) {
            assertTrue(!r.title().isBlank(), r.id() + " 缺标题");
            assertTrue(!r.why().isBlank(), r.id() + " 缺 why（--explain 会空着）");
            assertTrue(r.docUrl().startsWith("https://"), r.id() + " 缺文档链接");
            assertTrue(Set.of("spring-ai", "langchain4j", "both").contains(r.framework()),
                    r.id() + " 的 framework 只能是 spring-ai / langchain4j / both");
        }
    }

    @Test
    void 抑制注释只压掉指定那一条() throws IOException {
        LintResult result = new LintEngine(Rules.all(), List.of()).run(List.of(SAMPLES.resolve("suppressed")));
        assertEquals(1, result.suppressed(), "应该抑制掉一条");
        assertEquals(1, result.findings().size(), "没被抑制的那条要留着");
        assertEquals("LLM010", result.findings().get(0).ruleId());
    }

    @Test
    void 命令行在正例上返回0_在反例上返回1() {
        CommandLine cli = silentCli();

        assertEquals(0, cli.execute(SAMPLES.resolve("good").toString()));
        assertEquals(1, cli.execute(SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--fail-on", "never", SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--min-severity", "error", SAMPLES.resolve("good").toString()));
        assertEquals(0, cli.execute("--disable",
                Rules.all().stream().map(Rule::id).collect(Collectors.joining(",")),
                SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--list-rules"));
        assertEquals(2, cli.execute("--format", "xml", SAMPLES.resolve("bad").toString()));
        assertEquals(2, cli.execute("--explain", "LLM999"));
    }

    @Test
    void exclude可以按文件名排除() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        // 排掉一个文件之后还剩 17 条，退出码仍是 1（还有 error）；这里只验内容
        assertEquals(1, cli.execute("--format", "json", "--exclude", "LLM012_*",
                SAMPLES.resolve("bad").toString()));
        String json = out.toString();
        assertTrue(!json.contains("LLM012"), "被排除的文件不该出现: " + json);
        assertTrue(json.contains("LLM011"), "没被排除的还要在");
    }

    @Test
    void minSeverity过滤掉warning() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        assertEquals(0, cli.execute("--format", "json", "--min-severity", "warning",
                SAMPLES.resolve("good").toString()));
        assertEquals("[]", out.toString().trim());
    }

    @Test
    void 链式调用写在循环里也报LLM013(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("Chained.java");
        Files.writeString(file, """
                package demo;

                import java.util.List;
                import org.springframework.ai.chat.client.ChatClient;

                public class Chained {
                    private final ChatClient chatClient;

                    public Chained(ChatClient chatClient) {
                        this.chatClient = chatClient;
                    }

                    public void warmUp(List<String> questions) {
                        for (String q : questions) {
                            chatClient.prompt().user(q).call().content();
                        }
                    }
                }
                """);
        List<Finding> findings = lint(file);
        assertEquals(List.of("LLM013"), findings.stream().map(Finding::ruleId).toList(),
                "链式调用（chatClient.prompt()...call()）的根变量是 chatClient，应该认出来");
    }

    @Test
    void explain打出规则详情() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        assertEquals(0, cli.execute("--explain", "LLM001"));
        String text = out.toString();
        assertTrue(text.contains("LLM001"), text);
        assertTrue(text.contains("https://"), text);
        assertTrue(text.contains("llm-lint:ignore"), text);
    }

    @Test
    void json输出带列号且十六条齐全() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        assertEquals(1, cli.execute("--format", "json", SAMPLES.resolve("bad").toString()));
        String json = out.toString();
        assertTrue(json.contains("\"column\":"), "json 里要有列号");
        for (String id : expectedIds()) {
            assertTrue(json.contains("\"" + id + "\""), "json 里缺 " + id);
        }
    }

    @Test
    void sarif输出是合法骨架且规则带helpUri() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        assertEquals(1, cli.execute("--format", "sarif", SAMPLES.resolve("bad").toString()));
        String sarif = out.toString();
        assertTrue(sarif.contains("\"version\": \"2.1.0\""), sarif.substring(0, Math.min(200, sarif.length())));
        assertTrue(sarif.contains("\"$schema\""));
        assertTrue(sarif.contains("\"helpUri\""), "规则要带 helpUri");
        assertTrue(sarif.contains("\"rules\"") && sarif.contains("\"results\""));
        assertTrue(sarif.contains("\"startLine\""));
        assertEquals(18, countOccurrences(sarif, "\"ruleId\""));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }

    @Test
    void github格式一行一条注解() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new Main())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(java.io.OutputStream.nullOutputStream()));
        assertEquals(1, cli.execute("--format", "github", SAMPLES.resolve("bad").toString()));
        List<String> lines = out.toString().lines().filter(l -> l.startsWith("::")).toList();
        assertEquals(18, lines.size(), String.join("\n", lines));
        assertTrue(lines.stream().allMatch(l -> l.contains("line=")));
    }
}