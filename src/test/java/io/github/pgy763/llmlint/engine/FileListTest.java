package io.github.pgy763.llmlint.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileListTest {

    @Test
    void 只留存在的java并去掉重复() throws IOException {
        Path dir = Files.createTempDirectory("file-list");
        Path a = Files.writeString(dir.resolve("A.java"), "class A {}");
        Path b = Files.writeString(dir.resolve("B.java"), "class B {}");
        Files.writeString(dir.resolve("notes.md"), "x");

        List<Path> files = FileList.parse(List.of(
                a.toString(), b.toString(), a.toString(), "  ",
                "# 注释", dir.resolve("notes.md").toString(),
                dir.resolve("Gone.java").toString()));

        assertEquals(2, files.size(), files.toString());
        assertEquals(a.getFileName().toString(), files.get(0).getFileName().toString());
    }

    @Test
    void 读清单文件里的路径和注释() throws IOException {
        Path dir = Files.createTempDirectory("file-list-read");
        Files.createDirectories(dir.resolve("src"));
        Path java = Files.writeString(dir.resolve("src/Thing.java"), "class Thing {}");
        Path list = Files.writeString(dir.resolve("list.txt"),
                "# 改动\n\n" + java + "\n");

        List<Path> files = FileList.read(list);

        assertEquals(1, files.size(), files.toString());
        assertTrue(files.get(0).toString().endsWith("Thing.java"));
    }

    @Test
    void 清单文件不存在时说清楚() {
        IOException failure = assertThrows(IOException.class,
                () -> FileList.read(Path.of("definitely-not-here.txt")));

        assertTrue(failure.getMessage().contains("找不到 --files-from 给的文件"), failure.getMessage());
    }
}
