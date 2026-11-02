package io.github.pgy763.llmlint.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 只认 {@code .llm-lint.yml} 用得上的那点 YAML：区块映射、区块列表、行内 {@code [a, b]}、
 * 单双引号、注释。
 *
 * <p>锚点、别名、多文档、块标量（{@code |} / {@code >}）、行内映射一律不支持——这份文件是
 * 给人手写的，与其猜一个半吊子实现，不如直接报错。所有错误都带行号，这是重点：写错了要能
 * 一眼找到是哪一行。
 */
final class MiniYaml {

    private MiniYaml() {
    }

    abstract static class Node {
        final int line;

        Node(int line) {
            this.line = line;
        }
    }

    static final class Scalar extends Node {
        final String value;

        Scalar(String value, int line) {
            super(line);
            this.value = value;
        }
    }

    static final class Entry {
        final String key;
        final int line;
        final Node value;

        Entry(String key, int line, Node value) {
            this.key = key;
            this.line = line;
            this.value = value;
        }
    }

    static final class Mapping extends Node {
        final List<Entry> entries = new ArrayList<>();

        Mapping(int line) {
            super(line);
        }

        Node get(String key) {
            for (Entry e : entries) {
                if (e.key.equals(key)) {
                    return e.value;
                }
            }
            return null;
        }
    }

    static final class Sequence extends Node {
        final List<Node> items = new ArrayList<>();

        Sequence(int line) {
            super(line);
        }
    }

    private record Line(int no, int indent, String text) {
    }

    /**
     * 解析整份文件。
     *
     * @param text  文件内容
     * @param label 出错时打印的名字（通常是文件路径）
     */
    static Mapping parse(String text, String label) throws ConfigException {
        List<Line> lines = preprocess(text, label);
        if (lines.isEmpty()) {
            return new Mapping(1);
        }
        Parser parser = new Parser(lines, label);
        Node root = parser.block(lines.get(0).indent());
        if (parser.pos < lines.size()) {
            Line rest = lines.get(parser.pos);
            throw new ConfigException(label + ":" + rest.no() + ": 这一行的缩进对不上上面的层级");
        }
        if (root instanceof Mapping mapping) {
            return mapping;
        }
        throw new ConfigException(label + ":" + root.line + ": 顶层要写成 `键: 值`，不能是一个列表");
    }

    /** 去掉空行、注释、行尾空白，记下每行的缩进和行号。 */
    private static List<Line> preprocess(String text, String label) throws ConfigException {
        List<Line> out = new ArrayList<>();
        String[] raw = text.split("\r?\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String s = raw[i];
            if (i == 0 && s.startsWith("\uFEFF")) {
                s = s.substring(1);
            }
            int indent = 0;
            while (indent < s.length() && s.charAt(indent) == ' ') {
                indent++;
            }
            if (indent < s.length() && s.charAt(indent) == '\t') {
                throw new ConfigException(label + ":" + (i + 1) + ": 缩进只能用空格，不能用 Tab");
            }
            String body = stripComment(s.substring(indent)).stripTrailing();
            if (body.isEmpty()) {
                continue;
            }
            out.add(new Line(i + 1, indent, body));
        }
        return out;
    }

    /** 去掉注释；引号里的 # 不算注释。 */
    private static String stripComment(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    private static final class Parser {

        private final List<Line> lines;
        private final String label;
        private int pos;

        Parser(List<Line> lines, String label) {
            this.lines = lines;
            this.label = label;
        }

        Node block(int indent) throws ConfigException {
            return isItem(lines.get(pos).text()) ? sequence(indent) : mapping(indent);
        }

        private Mapping mapping(int indent) throws ConfigException {
            Mapping map = new Mapping(lines.get(pos).no());
            while (pos < lines.size()) {
                Line line = lines.get(pos);
                if (line.indent() < indent) {
                    break;
                }
                if (line.indent() > indent) {
                    throw err(line, "缩进多了：上一行没有留下要展开的键");
                }
                if (isItem(line.text())) {
                    break;
                }

                int colon = colonOf(line.text());
                if (colon < 0) {
                    throw err(line, "这一行不是 `键: 值`（少了冒号？）");
                }
                if (colon + 1 < line.text().length()
                        && !Character.isWhitespace(line.text().charAt(colon + 1))) {
                    throw err(line, "冒号后面要有一个空格，比如 `LLM001: off`");
                }
                String key = scalarText(line.text().substring(0, colon).trim(), line);
                if (key.isEmpty()) {
                    throw err(line, "键不能是空的");
                }
                String rest = line.text().substring(colon + 1).trim();
                pos++;
                Node value;
                if (rest.isEmpty()) {
                    value = pos < lines.size() && lines.get(pos).indent() > indent
                            ? block(lines.get(pos).indent())
                            : new Scalar("", line.no());
                } else {
                    value = inline(rest, line);
                }
                if (map.get(key) != null) {
                    throw err(line, "键重复了：" + key);
                }
                map.entries.add(new Entry(key, line.no(), value));
            }
            return map;
        }

        private Sequence sequence(int indent) throws ConfigException {
            Sequence seq = new Sequence(lines.get(pos).no());
            while (pos < lines.size()) {
                Line line = lines.get(pos);
                if (line.indent() != indent || !isItem(line.text())) {
                    if (line.indent() > indent) {
                        throw err(line, "缩进多了：列表项要跟上一行的 `-` 对齐");
                    }
                    break;
                }
                String rest = line.text().length() == 1 ? "" : line.text().substring(1).trim();
                if (rest.isEmpty()) {
                    throw err(line, "`-` 后面要跟一个值");
                }
                if (rest.startsWith("[")) {
                    throw err(line, "列表里不支持再套一层列表");
                }
                int colon = colonOf(rest);
                if (colon >= 0 && (colon + 1 == rest.length()
                        || Character.isWhitespace(rest.charAt(colon + 1)))) {
                    throw err(line, "列表里只放标量（路径、编号），不要写成 `键: 值`");
                }
                seq.items.add(new Scalar(scalarText(rest, line), line.no()));
                pos++;
            }
            return seq;
        }

        private Node inline(String rest, Line line) throws ConfigException {
            if (rest.startsWith("[")) {
                return flowSequence(rest, line);
            }
            if (rest.startsWith("{")) {
                throw err(line, "不支持行内映射 `{...}`，请换成缩进写法");
            }
            return new Scalar(scalarText(rest, line), line.no());
        }

        private Sequence flowSequence(String rest, Line line) throws ConfigException {
            if (rest.length() < 2 || !rest.endsWith("]")) {
                throw err(line, "行内列表少了结尾的 `]`");
            }
            Sequence seq = new Sequence(line.no());
            String inner = rest.substring(1, rest.length() - 1);
            for (String part : splitCommas(inner, line)) {
                String item = part.trim();
                if (item.isEmpty()) {
                    continue;
                }
                if (item.startsWith("[")) {
                    throw err(line, "行内列表里不能再套一层列表");
                }
                seq.items.add(new Scalar(scalarText(item, line), line.no()));
            }
            return seq;
        }

        private List<String> splitCommas(String inner, Line line) throws ConfigException {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            char quote = 0;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (quote != 0) {
                    current.append(c);
                    if (c == '\\' && quote == '"' && i + 1 < inner.length()) {
                        current.append(inner.charAt(++i));
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                    current.append(c);
                } else if (c == ',') {
                    parts.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            if (quote != 0) {
                throw err(line, "引号没有闭合");
            }
            parts.add(current.toString());
            return parts;
        }

        private String scalarText(String raw, Line line) throws ConfigException {
            String s = raw.trim();
            if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"')) {
                char quote = s.charAt(0);
                if (s.charAt(s.length() - 1) != quote) {
                    throw err(line, "引号没有闭合");
                }
                String inner = s.substring(1, s.length() - 1);
                return quote == '\'' ? inner.replace("''", "'") : unescape(inner, line);
            }
            return s;
        }

        private String unescape(String s, Line line) throws ConfigException {
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (++i >= s.length()) {
                    throw err(line, "反斜杠后面什么都没有");
                }
                switch (s.charAt(i)) {
                    case '\\' -> out.append('\\');
                    case '"' -> out.append('"');
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    default -> throw err(line, "双引号里不认识的转义：\\" + s.charAt(i));
                }
            }
            return out.toString();
        }

        private ConfigException err(Line line, String message) {
            return new ConfigException(label + ":" + line.no() + ": " + message);
        }
    }

    private static boolean isItem(String text) {
        return text.length() > 1 && text.charAt(0) == '-' && Character.isWhitespace(text.charAt(1));
    }

    /** 引号之外的第一个冒号；没有就是 -1。 */
    private static int colonOf(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ':') {
                return i;
            }
        }
        return -1;
    }
}