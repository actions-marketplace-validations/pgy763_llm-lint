package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Fix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 补丁生成：贴到源码上、变成 unified diff。 */
class FixerTest {

    private static final String ANNOTATION = "@GetMapping(\"/stream\")";
    private static final int ANNOTATION_END = 4 + ANNOTATION.length();

    private static final String SOURCE = String.join("\n",
            "class Demo {",
            "    " + ANNOTATION,
            "    Flux<String> stream() {",
            "        return Flux.just(\"a\");",
            "    }",
            "}");

    private static Fix replaceAnnotation() {
        return new Fix(2, 5, 2, ANNOTATION_END, "@GetMapping(produces = \"text/event-stream\")");
    }

    @Test
    void 按行列把源码换掉() {
        String fixed = Fixer.apply(SOURCE, List.of(replaceAnnotation()));

        assertTrue(fixed.contains("    @GetMapping(produces = \"text/event-stream\")\n"), fixed);
        assertTrue(fixed.contains("class Demo {"), fixed);
    }

    @Test
    void 一处文件里多个补丁都贴得上() {
        String text = "a();\nb();\n";
        List<Fix> fixes = List.of(
                new Fix(1, 1, 1, 3, "x()"),
                new Fix(2, 1, 2, 3, "y()"));

        assertEquals("x();\ny();\n", Fixer.apply(text, fixes));
    }

    @Test
    void 行列超出文件时说明是补丁的问题() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Fixer.apply("a\n", List.of(new Fix(9, 1, 9, 1, "b"))));
        assertTrue(error.getMessage().contains("行号超出文件"), error.getMessage());
    }

    @Test
    void 生成带上下文的unified_diff() {
        String patch = Fixer.patch("src/Demo.java", SOURCE, List.of(replaceAnnotation()));

        assertTrue(patch.startsWith("--- a/src/Demo.java\n+++ b/src/Demo.java\n"), patch);
        assertTrue(patch.contains("@@ -1,5 +1,5 @@"), patch);
        assertTrue(patch.contains("-    " + ANNOTATION), patch);
        assertTrue(patch.contains("+    @GetMapping(produces = \"text/event-stream\")"), patch);
        assertTrue(patch.contains(" class Demo {"), patch);
    }

    @Test
    void 隔得远的改动分成两个hunk() {
        StringBuilder sb = new StringBuilder("class A {\n    int x;\n");
        for (int i = 0; i < 8; i++) {
            sb.append("    int f").append(i).append(";\n");
        }
        sb.append("    int y;\n}\n");
        String text = sb.toString();
        // 只换「x」和「y」，中间隔 8 行 —— 两个 hunk
        List<Fix> fixes = List.of(
                new Fix(2, 9, 2, 9, "first"),
                new Fix(11, 9, 11, 9, "last"));

        String patch = Fixer.patch("A.java", text, fixes);

        assertEquals(2, patch.split("@@ -", -1).length - 1, patch);
        assertTrue(patch.contains("+    int first;"), patch);
        assertTrue(patch.contains("+    int last;"), patch);
    }

    @Test
    void 没有补丁就什么都不输出() {
        assertEquals("", Fixer.patch("A.java", SOURCE, List.of()));
        assertEquals("", Fixer.patch("A.java", SOURCE,
                List.of(new Fix(2, 5, 2, ANNOTATION_END, ANNOTATION))));
    }
}