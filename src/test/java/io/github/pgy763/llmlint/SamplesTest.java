package io.github.pgy763.llmlint;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Test
    void badSample触发且只触发它自己那条规则() throws IOException {
        List<Path> bad = javaFiles("bad");
        assertEquals(10, bad.size(), "应该有十个反例");

        for (Path file : bad) {
            String expected = file.getFileName().toString().substring(0, 6);
            List<Finding> findings = lint(file);
            assertEquals(1, findings.size(),
                    file.getFileName() + " 应该正好报一条，实际: " + findings);
            assertEquals(expected, findings.get(0).ruleId(),
                    file.getFileName() + " 报错了规则");
            assertTrue(findings.get(0).line() > 0, "要有行号");
            assertTrue(findings.get(0).docUrl().startsWith("https://"), "要有手册链接");
        }
    }

    @Test
    void goodSample不报任何问题() throws IOException {
        List<Path> good = javaFiles("good");
        assertEquals(10, good.size(), "应该有十个正例");

        for (Path file : good) {
            List<Finding> findings = lint(file);
            assertEquals(List.of(), findings, file.getFileName() + " 不该报，误报了：" + findings);
        }
    }

    @Test
    void 规则编号唯一且十个() {
        Set<String> ids = new LinkedHashSet<>();
        Rules.all().forEach(r -> assertTrue(ids.add(r.id()), "编号重复: " + r.id()));
        assertEquals(10, ids.size());
        assertEquals(
                Set.of("LLM001", "LLM002", "LLM003", "LLM004", "LLM005",
                        "LLM006", "LLM007", "LLM008", "LLM009", "LLM010"),
                ids);
    }

    @Test
    void 命令行在正例上返回0_在反例上返回1() {
        CommandLine cli = new CommandLine(new Main())
                .setOut(new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()))
                .setErr(new java.io.PrintWriter(java.io.OutputStream.nullOutputStream()));

        assertEquals(0, cli.execute(SAMPLES.resolve("good").toString()));
        assertEquals(1, cli.execute(SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--fail-on", "never", SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--disable",
                "LLM001,LLM002,LLM003,LLM004,LLM005,LLM006,LLM007,LLM008,LLM009,LLM010",
                SAMPLES.resolve("bad").toString()));
        assertEquals(0, cli.execute("--list-rules"));
    }
}