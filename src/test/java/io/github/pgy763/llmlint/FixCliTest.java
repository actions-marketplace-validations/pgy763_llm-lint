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

/** --fix：不打印报告，改成输出一份 unified diff。 */
class FixCliTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        Main main = new Main();
        CommandLine cmd = new CommandLine(main);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    private static Path write(Path dir, String name, String body) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        return file;
    }

    @Test
    void 给没写produces的流式接口出补丁(@TempDir Path tmp) throws IOException {
        write(tmp, "src/DemoController.java", String.join("\n",
                "package demo;",
                "",
                "@RestController",
                "public class DemoController {",
                "    @GetMapping(\"/stream\")",
                "    public Flux<String> stream() {",
                "        return Flux.just(\"a\");",
                "    }",
                "}",
                ""));

        Run run = run("--fix", tmp.toString());

        // LLM018 是 warning，默认不拦；补丁照样给
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().startsWith("--- a/"), run.out());
        assertTrue(run.out().contains("+    @GetMapping(value = \"/stream\", produces = \"text/event-stream;charset=UTF-8\")"),
                run.out());
        assertTrue(run.out().contains("-    @GetMapping(\"/stream\")"), run.out());
        // 报告本身不该混进来
        assertFalse(run.out().contains("LLM018 流式接口"), run.out());
    }

    @Test
    void 乱码的produces也能补上charset(@TempDir Path tmp) throws IOException {
        write(tmp, "src/ChatController.java", String.join("\n",
                "package demo;",
                "",
                "@RestController",
                "public class ChatController {",
                "    @GetMapping(value = \"/stream\", produces = \"text/event-stream\")",
                "    public SseEmitter stream() {",
                "        return new SseEmitter(0L);",
                "    }",
                "}",
                ""));

        Run run = run("--fix", tmp.toString());

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("produces = \"text/event-stream;charset=UTF-8\""), run.out());
        assertTrue(run.out().contains("+    @GetMapping(value = \"/stream\""), run.out());
    }

    @Test
    void 没有能自动修的问题时说清楚(@TempDir Path tmp) throws IOException {
        write(tmp, "src/Service.java", String.join("\n",
                "package demo;",
                "",
                "import org.springframework.transaction.annotation.Transactional;",
                "",
                "@Service",
                "public class Service {",
                "    private final ChatClient chatClient;",
                "",
                "    public Service(ChatClient chatClient) {",
                "        this.chatClient = chatClient;",
                "    }",
                "",
                "    @Transactional",
                "    public String ask(String q) {",
                "        return chatClient.prompt(q).call().content();",
                "    }",
                "}",
                ""));

        Run run = run("--fix", tmp.toString());

        assertEquals(1, run.code(), run.err());
        assertEquals("", run.out().trim(), run.out());
        assertTrue(run.err().contains("没有现成的补丁"), run.err());
        assertTrue(run.err().contains("LLM006"), run.err());
        assertTrue(run.err().contains("LLM018"), run.err());
    }

    @Test
    void 干净的项目什么都不输出也不啰嗦() {
        Run run = run("--fix", "samples/good");

        assertEquals(0, run.code(), run.err());
        assertEquals("", run.out().trim(), run.out());
        assertFalse(run.err().contains("没有现成的补丁"), run.err());
    }

    @Test
    void 帮助里有fix() {
        Run run = run("--help");

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("--fix"), run.out());
    }
}