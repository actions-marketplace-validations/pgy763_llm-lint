package io.github.pgy763.llmlint;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatsCliTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine command = new CommandLine(new Main());
        command.setOut(new PrintWriter(out, true));
        command.setErr(new PrintWriter(err, true));
        int code = command.execute(args);
        return new Result(code, out.toString(), err.toString());
    }

    @Test
    void junit格式给每个问题一个testcase() {
        Result result = run("--format", "junit", "samples/bad");
        assertEquals(1, result.code(), result.err());
        String xml = result.out();
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), xml);
        assertTrue(xml.contains("tests=\"19\" failures=\"10\""), xml);
        assertEquals(19, count(xml, "<testcase "));
    }

    @Test
    void html格式是一份能打开的页面() {
        Result result = run("--format", "html", "samples/good");
        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().startsWith("<!DOCTYPE html>"), result.out());
        assertTrue(result.out().contains("没有发现问题"), result.out());
    }

    @Test
    void 不认识的格式要列全六种() {
        Result result = run("--format", "yaml", "samples/good");
        assertEquals(2, result.code());
        assertTrue(result.err().contains("junit"), result.err());
        assertTrue(result.err().contains("html"), result.err());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            found++;
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }
}