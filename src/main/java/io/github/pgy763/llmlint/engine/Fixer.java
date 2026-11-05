package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Fix;

import io.github.pgy763.llmlint.lang.Messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把带 {@link Fix} 的检查结果变成源码补丁。
 *
 * <p>只做两件事：把补丁贴到内存里的源码上（{@link #apply}），以及生成一份 unified diff
 * （{@link #patch}）。真正写文件交给用户——{@code git apply} 或编辑器，工具不擅自改工作区。
 */
public final class Fixer {

    /** diff 里每个改动前后带几行上下文，和 git 默认值一致。 */
    private static final int CONTEXT = 3;

    private Fixer() {
    }

    /** 按文件分组，逐个读源码生成补丁；没有任何可修的项时返回空串。 */
    public static String patch(List<Finding> findings) throws IOException {
        Map<String, List<Fix>> byFile = group(findings);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, List<Fix>> entry : byFile.entrySet()) {
            String file = entry.getKey();
            String original;
            try {
                original = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IOException(Messages.f("读不出 %s：%s", "cannot read %s: %s", file, e.getMessage()), e);
            }
            out.append(patch(file, original, entry.getValue()));
        }
        return out.toString();
    }

    /**
     * 直接把补丁写回文件（{@code --fix --write}）。返回每个文件改了几处，没改动的文件不在里面。
     *
     * <p>写之前先把整份文件读进内存、改完再一次性覆盖，所以中途出错不会留下半个文件。
     */
    public static Map<String, Integer> write(List<Finding> findings) throws IOException {
        Map<String, Integer> done = new LinkedHashMap<>();
        for (Map.Entry<String, List<Fix>> entry : group(findings).entrySet()) {
            String file = entry.getKey();
            Path path = Path.of(file);
            String original;
            try {
                original = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IOException(Messages.f("读不出 %s：%s", "cannot read %s: %s", file, e.getMessage()), e);
            }
            String fixed = apply(original, entry.getValue());
            if (fixed.equals(original)) {
                continue;
            }
            Files.writeString(path, fixed, StandardCharsets.UTF_8);
            done.put(file, entry.getValue().size());
        }
        return done;
    }

    /** 有补丁的结果按文件分组，保持扫描顺序。 */
    private static Map<String, List<Fix>> group(List<Finding> findings) {
        Map<String, List<Fix>> byFile = new LinkedHashMap<>();
        for (Finding finding : findings) {
            if (finding.fix() != null) {
                byFile.computeIfAbsent(finding.file(), key -> new ArrayList<>()).add(finding.fix());
            }
        }
        return byFile;
    }

    /**
     * diff 里用的路径：在仓库里扫的时候给相对路径，git apply 才认得；不在当前目录下的保持原样。
     */
    private static String display(String file) {
        Path path = Path.of(file);
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        String shown = abs.startsWith(cwd) && !abs.equals(cwd) ? cwd.relativize(abs).toString() : file;
        return shown.replace('\\', '/');
    }

    /** 一份文件的 unified diff；没有补丁时返回空串。 */
    public static String patch(String file, String original, List<Fix> fixes) {
        String fixed = apply(original, fixes);
        if (fixed.equals(original)) {
            return "";
        }
        List<String> before = lines(original);
        List<String> after = lines(fixed);
        List<Op> ops = script(before, after);
        List<int[]> hunks = hunks(ops);
        if (hunks.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append("--- a/").append(file).append('\n');
        out.append("+++ b/").append(file).append('\n');
        for (int[] hunk : hunks) {
            int from = hunk[0];
            int to = hunk[1];
            int oldCount = 0;
            int newCount = 0;
            for (int i = from; i <= to; i++) {
                char type = ops.get(i).type;
                if (type != '+') {
                    oldCount++;
                }
                if (type != '-') {
                    newCount++;
                }
            }
            Op first = ops.get(from);
            out.append("@@ -").append(Math.max(1, first.oldLine)).append(',').append(oldCount)
                    .append(" +").append(Math.max(1, first.newLine)).append(',').append(newCount)
                    .append(" @@\n");
            for (int i = from; i <= to; i++) {
                Op op = ops.get(i);
                out.append(op.type).append(op.text).append('\n');
            }
        }
        return out.toString();
    }

    /** 把补丁贴到源码文本上，得到修好之后的样子。 */
    public static String apply(String text, List<Fix> fixes) {
        List<Edit> edits = new ArrayList<>();
        for (Fix fix : fixes) {
            int start = offset(text, fix.beginLine(), fix.beginColumn());
            int end = offset(text, fix.endLine(), fix.endColumn()) + 1;
            if (start > end || end > text.length()) {
                throw new IllegalArgumentException("补丁位置超出文件："
                        + fix.beginLine() + ":" + fix.beginColumn()
                        + " → " + fix.endLine() + ":" + fix.endColumn());
            }
            edits.add(new Edit(start, end, fix.replacement()));
        }
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder sb = new StringBuilder(text);
        for (Edit edit : edits) {
            sb.replace(edit.start, edit.end, edit.replacement);
        }
        return sb.toString();
    }

    /** 行列（1 起、列按字符算）换成字符串里的偏移。 */
    static int offset(String text, int line, int column) {
        int offset = 0;
        for (int current = 1; current < line; current++) {
            int next = text.indexOf('\n', offset);
            if (next < 0) {
                throw new IllegalArgumentException(Messages.f("行号超出文件：%d", "line out of range: %d", line));
            }
            offset = next + 1;
        }
        return Math.min(text.length(), offset + Math.max(0, column - 1));
    }

    private static List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }

    /** 行级 diff：先找最长公共子序列，再把「删一段、加一段」按先行后列摆整齐。 */
    private static List<Op> script(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] = before.get(i).equals(after.get(j))
                        ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }

        List<Op> raw = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before.get(i).equals(after.get(j))) {
                raw.add(new Op(' ', before.get(i), i + 1, j + 1));
                i++;
                j++;
            } else if (common[i + 1][j] >= common[i][j + 1]) {
                raw.add(new Op('-', before.get(i), i + 1, j + 1));
                i++;
            } else {
                raw.add(new Op('+', after.get(j), i + 1, j + 1));
                j++;
            }
        }
        while (i < n) {
            raw.add(new Op('-', before.get(i), i + 1, j + 1));
            i++;
        }
        while (j < m) {
            raw.add(new Op('+', after.get(j), i + 1, j + 1));
            j++;
        }
        return tidy(raw);
    }

    /** 把同一处改动里的「加」挪到「删」后面，git apply 和人都更容易读。 */
    private static List<Op> tidy(List<Op> ops) {
        List<Op> out = new ArrayList<>(ops);
        int i = 0;
        while (i < out.size()) {
            if (out.get(i).type == ' ') {
                i++;
                continue;
            }
            int start = i;
            List<Op> removed = new ArrayList<>();
            List<Op> added = new ArrayList<>();
            while (i < out.size() && out.get(i).type != ' ') {
                if (out.get(i).type == '-') {
                    removed.add(out.get(i));
                } else {
                    added.add(out.get(i));
                }
                i++;
            }
            for (int k = 0; k < removed.size() + added.size(); k++) {
                out.set(start + k, k < removed.size() ? removed.get(k) : added.get(k - removed.size()));
            }
        }
        return out;
    }

    /** 每个改动前后各留 CONTEXT 行；挨得近的就合成一个 hunk。 */
    private static List<int[]> hunks(List<Op> ops) {
        List<int[]> hunks = new ArrayList<>();
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).type == ' ') {
                i++;
                continue;
            }
            int start = Math.max(0, i - CONTEXT);
            int end = i;
            while (end + 1 < ops.size() && ops.get(end + 1).type != ' ') {
                end++;
            }
            // 往后看：下一个改动离得够近（中间空行 ≤ 2*CONTEXT）就并进来
            while (true) {
                int next = end + 1;
                while (next < ops.size() && ops.get(next).type == ' ') {
                    next++;
                }
                if (next >= ops.size() || next - end - 1 > CONTEXT * 2) {
                    break;
                }
                end = next;
                while (end + 1 < ops.size() && ops.get(end + 1).type != ' ') {
                    end++;
                }
            }
            int tail = Math.min(ops.size() - 1, end + CONTEXT);
            hunks.add(new int[]{start, tail});
            i = tail + 1;
        }
        return hunks;
    }

    private record Op(char type, String text, int oldLine, int newLine) {
    }

    private record Edit(int start, int end, String replacement) {
    }
}