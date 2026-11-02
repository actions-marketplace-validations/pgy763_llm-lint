package io.github.pgy763.llmlint.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** --since 的算法：解析 git 输出是纯函数；真仓库那两条用守卫式测试（沙箱里跳过，CI 上跑）。 */
class ChangedFilesTest {

    @Test
    void 只留java文件且按路径排序(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("src/main/java/a"));
        Files.writeString(root.resolve("src/main/java/a/Zeta.java"), "class Zeta {}");
        Files.writeString(root.resolve("src/main/java/a/Alpha.java"), "class Alpha {}");
        Files.writeString(root.resolve("README.md"), "hi");

        List<Path> files = ChangedFiles.parse(
                "README.md\nsrc/main/java/a/Zeta.java\nsrc/main/java/a/Alpha.java\n"
                        + "src/main/java/a/Gone.java\n", root);

        assertEquals(List.of(
                root.resolve("src/main/java/a/Alpha.java").normalize(),
                root.resolve("src/main/java/a/Zeta.java").normalize()), files);
    }

    @Test
    void 同一路径出现两次只算一次(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("A.java"), "class A {}");

        List<Path> files = ChangedFiles.parse("A.java\nA.java\n\n  A.java  \n", root);

        assertEquals(1, files.size());
    }

    @Test
    void 换行符是crlf也认(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("B.java"), "class B {}");

        List<Path> files = ChangedFiles.parse("B.java\r\nC.java\r\n", root);

        assertEquals(List.of(root.resolve("B.java").normalize()), files);
    }

    @Test
    void 输出为空时是空列表() {
        assertTrue(ChangedFiles.parse("", Path.of("root")).isEmpty());
        assertTrue(ChangedFiles.parse(null, Path.of("root")).isEmpty());
    }

    @Test
    void ref写成空白要报人话() {
        ChangedFiles.GitException failure = assertThrows(ChangedFiles.GitException.class,
                () -> ChangedFiles.collect(Path.of(""), "  "));
        assertTrue(failure.getMessage().contains("--since"), failure.getMessage());
    }

    @Test
    void 真仓库里只算改动的java文件(@TempDir Path repo) throws Exception {
        assumeTrue(gitWorks(), "这台机器不让起子进程（沙箱），跳过；CI 上会跑");
        exec(repo, "init", "-q");
        exec(repo, "config", "user.email", "test@example.com");
        exec(repo, "config", "user.name", "test");
        Files.writeString(repo.resolve("Kept.java"), "class Kept {}");
        Files.writeString(repo.resolve("Notes.md"), "hi");
        exec(repo, "add", ".");
        exec(repo, "commit", "-m", "first");
        Files.writeString(repo.resolve("Kept.java"), "class Kept { int changed; }");
        Files.writeString(repo.resolve("Fresh.java"), "class Fresh {}");

        List<Path> files = ChangedFiles.collect(repo, "HEAD");

        assertEquals(List.of(
                repo.resolve("Fresh.java").toAbsolutePath().normalize(),
                repo.resolve("Kept.java").toAbsolutePath().normalize()), files);
    }

    @Test
    void 不在仓库里时给的是仓库提示(@TempDir Path dir) {
        assumeTrue(gitWorks(), "这台机器不让起子进程（沙箱），跳过；CI 上会跑");
        ChangedFiles.GitException failure = assertThrows(ChangedFiles.GitException.class,
                () -> ChangedFiles.collect(dir, "HEAD"));
        assertTrue(failure.getMessage().contains("--since"), failure.getMessage());
    }

    @Test
    void 同时给路径时只留路径下的改动(@TempDir Path root) {
        Path a = root.resolve("module-a/src/main/java/A.java").normalize();
        Path b = root.resolve("module-b/src/main/java/B.java").normalize();

        assertEquals(List.of(a),
                ChangedFiles.under(List.of(a, b), List.of(root.resolve("module-a"))));
        assertEquals(List.of(a, b), ChangedFiles.under(List.of(a, b), List.of(root)));
        assertEquals(List.of(a, b), ChangedFiles.under(List.of(a, b), List.of()));
    }

    @Test
    void 路径写成某个文件时只留它自己(@TempDir Path root) {
        Path a = root.resolve("src/main/java/A.java").normalize();
        Path b = root.resolve("src/main/java/B.java").normalize();

        assertEquals(List.of(a), ChangedFiles.under(List.of(a, b), List.of(a)));
    }

    // --- 小工具 ---------------------------------------------------------

    private static boolean gitWorks() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void exec(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = dir.toAbsolutePath().toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " 失败：" + output);
        }
    }
}