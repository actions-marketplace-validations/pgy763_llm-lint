package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * exclude 的写法要和人的直觉一致：`src/generated/**` 这种相对仓库根的写法，从仓库根扫、
 * 从子目录扫，都得排除掉。
 */
class ExcludeTest {

    private static final String SAMPLE = "maxTokens";

    private static Path tree(Path root) throws IOException {
        Path main = root.resolve("src/main/java/demo");
        Path generated = root.resolve("src/generated/java/demo");
        Files.createDirectories(main);
        Files.createDirectories(generated);
        Files.writeString(main.resolve("Assistant.java"), assistant());
        Files.writeString(generated.resolve("Generated.java"), assistant());
        return root;
    }

    private static String assistant() {
        return """
                package demo;

                class Assistant {
                    void summarize() {
                        var options = ChatOptions.builder().maxTokens(128).build();
                    }
                }
                """;
    }

    private static LintResult scan(Path root, String... excludes) throws IOException {
        return new LintEngine(Rules.all(), List.of(excludes), 1).run(List.of(root));
    }

    @Test
    void 相对仓库根的写法也能排除(@TempDir Path tmp) throws IOException {
        LintResult result = scan(tree(tmp), "src/generated/**");

        assertEquals(1, result.filesScanned());
        assertEquals(1, result.findings().size());
        assertTrue(result.findings().get(0).file().contains("src/main/java"), result.findings().get(0).file());
    }

    @Test
    void 带前缀通配的写法照样排除(@TempDir Path tmp) throws IOException {
        LintResult result = scan(tree(tmp), "**/generated/**");

        assertEquals(1, result.filesScanned());
        assertTrue(result.findings().get(0).file().contains("src/main/java"));
    }

    @Test
    void 只写一段目录名也能排除(@TempDir Path tmp) throws IOException {
        LintResult result = scan(tree(tmp), "generated/**");

        assertEquals(1, result.filesScanned());
    }

    @Test
    void 从子目录扫时相对根写法仍有效(@TempDir Path tmp) throws IOException {
        Path root = tree(tmp);

        LintResult result = scan(root.resolve("src/generated"), "src/generated/**");

        assertEquals(0, result.filesScanned());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void 文件名通配还是管用(@TempDir Path tmp) throws IOException {
        Path root = tree(tmp);
        Files.writeString(root.resolve("src/main/java/demo/AssistantTest.java"), assistant());

        LintResult result = scan(root, "*Test.java");

        assertEquals(2, result.filesScanned());
        assertEquals(2, result.findings().size());
        for (var finding : result.findings()) {
            assertFalse(finding.file().contains("AssistantTest.java"), finding.file());
        }
    }

    @Test
    void 不含通配符的按子串排(@TempDir Path tmp) throws IOException {
        LintResult result = scan(tree(tmp), "GENERATED");

        assertEquals(1, result.filesScanned());
    }

    @Test
    void 没写排除就两个文件都扫(@TempDir Path tmp) throws IOException {
        LintResult result = scan(tree(tmp));

        assertEquals(2, result.filesScanned());
        assertEquals(2, result.findings().size());
    }

    @Test
    void 排除单个文件(@TempDir Path tmp) throws IOException {
        Path root = tree(tmp);

        LintResult result = scan(root, "**/Generated.java");

        assertEquals(1, result.filesScanned());
        assertFalse(result.findings().isEmpty());
        assertEquals("LLM007", result.findings().get(0).ruleId());
    }
}