package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 存量项目的基线：把「已经决定先不管的旧问题」记在仓库里，CI 只拦新出现的那些。
 *
 * <p>格式故意不用 JSON：一行一条，规则、文件、消息用 Tab 隔开。改基线就是改这几行，
 * 在 PR 里能一眼看出这次接受了什么问题——JSON 会把这件事藏进大括号里。
 *
 * <p>匹配不看行号，只看「规则 + 文件 + 消息」。挪了代码、加了几行空行，旧问题不会突然
 * 复活；同一个文件里同一个位置的代码真改了，消息变了，就会重新报出来。
 */
public final class Baseline {

    private static final String HEADER =
            "# llm-lint baseline v1 —— 一行一条：规则<TAB>文件<TAB>消息；用 --save-baseline 更新，别手改";
    private static final char SEP = '\t';

    private final Set<String> entries;

    private Baseline(Set<String> entries) {
        this.entries = entries;
    }

    /** 什么都没有的基线：所有问题都是新的。 */
    public static Baseline empty() {
        return new Baseline(new LinkedHashSet<>());
    }

    /**
     * 读一份基线。文件不存在时直接报错——传了 --baseline 却读不到，静默当成空基线会让 CI
     * 突然开始报一堆旧问题，那是最难查的一种「配置错」。
     */
    public static Baseline load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("找不到基线文件：" + file + "（先用 --save-baseline 生成一份）");
        }
        Set<String> entries = new LinkedHashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i).strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            String[] parts = text.split(String.valueOf(SEP), -1);
            if (parts.length != 3) {
                throw new IOException(file + ":" + (i + 1)
                        + ": 基线每行要有三列（规则、文件、消息），这行是「" + text + "」");
            }
            entries.add(parts[0] + SEP + parts[1] + SEP + parts[2]);
        }
        return new Baseline(entries);
    }

    /** 把这份基线写到文件里；目录不存在会顺手建出来。 */
    public static void write(Baseline baseline, Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(absolute, baseline.toText(), StandardCharsets.UTF_8);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 这个问题是不是已经在基线里了（行号、列号不参与匹配）。 */
    public boolean contains(Finding finding) {
        return entries.contains(key(finding));
    }

    /** 把这次的结果并进来，已有的条目一个不动：基线只会变长，删条目得人来决定。 */
    public Baseline plus(List<Finding> findings) {
        Set<String> merged = new LinkedHashSet<>(entries);
        for (Finding finding : findings) {
            merged.add(key(finding));
        }
        return new Baseline(merged);
    }

    /** 写出去的样子：排过序，两次生成的 diff 只包含真正新增的条目。 */
    public String toText() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        entries.stream().sorted().forEach(line -> sb.append(line).append('\n'));
        return sb.toString();
    }

    /** 存的是转义后的行，消息里就算有 Tab 也不会把三列撑坏。 */
    private static String key(Finding finding) {
        return finding.ruleId() + SEP + escape(finding.file()) + SEP + escape(finding.message());
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    }