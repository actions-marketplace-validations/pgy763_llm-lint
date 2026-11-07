package io.github.pgy763.llmlint.engine;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 排除路径和按路径覆盖规则共用的一套匹配。
 *
 * <p>带通配符时，会拿完整路径和它的每一段后缀去试，所以 {@code src/generated/**}
 * 这种「相对仓库根」的写法，不管是在仓库根扫还是 cd 进子目录扫，都能对上；
 * 不带通配符时按子串匹配，写 {@code generated} 这种一段目录名也管用。
 */
public final class Globs {

    private Globs() {
    }

    /** {@code file} 是已经换成正斜杠的路径，也可以直接传原始路径。 */
    public static boolean matches(String pattern, String file) {
        String raw = pattern == null ? "" : pattern.trim();
        if (raw.isEmpty()) {
            return false;
        }
        String normalized = file.replace('\\', '/');
        if (raw.contains("*") || raw.contains("?")) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + raw);
            for (String candidate : candidates(normalized)) {
                if (matcher.matches(Path.of(candidate))) {
                    return true;
                }
            }
            return false;
        }
        return normalized.toLowerCase(Locale.ROOT).contains(raw.toLowerCase(Locale.ROOT));
    }

    /** 完整路径、文件名，以及每一段 / 之后的后缀（`src/generated/A.java` → `generated/A.java` …）。 */
    private static List<String> candidates(String normalized) {
        List<String> out = new ArrayList<>();
        out.add(normalized);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            out.add(normalized.substring(slash + 1));
        }
        for (int i = normalized.indexOf('/'); i >= 0; i = normalized.indexOf('/', i + 1)) {
            out.add(normalized.substring(i + 1));
        }
        return out;
    }
}