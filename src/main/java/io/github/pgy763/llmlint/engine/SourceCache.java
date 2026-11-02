package io.github.pgy763.llmlint.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 源码行缓存：抑制注释和代码帧都要用，同一个文件只读一次；并行扫时多线程共用。 */
public final class SourceCache {

    private final Map<Path, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> lines(Path file) {
        return cache.computeIfAbsent(file, f -> {
            try {
                return Files.readAllLines(f, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return List.of();
            }
        });
    }

    /** 取第 line 行（1 起），越界返回空串。 */
    public String line(Path file, int line) {
        List<String> all = lines(file);
        if (line < 1 || line > all.size()) {
            return "";
        }
        return all.get(line - 1);
    }

    /** Finding 里存的是 / 分隔的字符串，这里换回 Path 再取。 */
    public String lineOf(String file, int line) {
        if (file == null || file.isBlank()) {
            return "";
        }
        try {
            return line(Path.of(file), line);
        } catch (RuntimeException e) {
            return "";
        }
    }
}