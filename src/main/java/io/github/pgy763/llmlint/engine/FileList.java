package io.github.pgy763.llmlint.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code --files-from} 读进来的那份清单：一行一个路径，空行和 # 开头的行跳过。
 *
 * <p>清单通常不是人写的，是别的工具吐出来的（git diff --name-only、find、CI 的改动列表），
 * 所以这里宽容一点：不是 .java 的行直接忽略，写错了的路径也忽略——删掉的文件还在清单里是常事，
 * 为这个报错只会让人把 --files-from 关掉。
 */
public final class FileList {

    private FileList() {
    }

    /** 读清单；{@code -} 表示从标准输入读。 */
    public static List<Path> read(Path file) throws IOException {
        if ("-".equals(file.toString())) {
            // 不关 System.in：进程还要往下跑。
            return parse(new String(System.in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("找不到 --files-from 给的文件：" + file
                    + "（一行一个路径；写 - 表示从标准输入读）");
        }
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /** 解析清单里的一行行文本：只留存在的 .java，去重、排序。 */
    public static List<Path> parse(List<String> lines) {
        Set<Path> files = new LinkedHashSet<>();
        List<Path> result = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.toLowerCase(Locale.ROOT).endsWith(".java")) {
                continue;
            }
            Path path = Path.of(line).normalize();
            if (!Files.isRegularFile(path)) {
                continue;
            }
            if (files.add(path)) {
                result.add(path);
            }
        }
        result.sort((a, b) -> a.toString().compareTo(b.toString()));
        return result;
    }
}