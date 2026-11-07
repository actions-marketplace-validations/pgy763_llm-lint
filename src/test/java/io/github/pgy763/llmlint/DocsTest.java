package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.config.Config;
import io.github.pgy763.llmlint.config.ConfigLoader;
import io.github.pgy763.llmlint.config.RuleLevel;
import io.github.pgy763.llmlint.lang.Lang;
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.report.RulePages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档和代码对不上，用户就会点进 404。这里把「规则里的链接」「schema 里的字段」
 * 「--init 写出来的模板」都锁在测试里。
 */
class DocsTest {

    private static final Path RULES_DOC = Path.of("docs/rules.md");
    private static final Path SCHEMA = Path.of("docs/llm-lint.schema.json");
    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path DOCKER_WORKFLOW = Path.of(".github/workflows/docker.yml");

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Path page(Rule rule) {
        return Path.of("docs/rules").resolve(RulePages.fileName(rule));
    }

    @Test
    void 每条规则在仓库文档里都有锚点() throws IOException {
        String doc = read(RULES_DOC);
        for (Rule rule : Rules.all()) {
            String anchor = rule.id().toLowerCase(Locale.ROOT);
            assertTrue(doc.contains("<a id=\"" + anchor + "\"></a>"),
                    "docs/rules.md 少了 " + rule.id() + " 的锚点，docUrl 里的 #" + anchor + " 会 404");
            assertTrue(doc.contains("https://pgy763.github.io/llm-lint/rules/" + anchor + ".html"),
                    "docs/rules.md 的索引没有链到 " + rule.id() + " 自己的页面");
            assertTrue(Files.isRegularFile(page(rule)), "少了 " + page(rule) + "，站上就是 404");
            assertTrue(read(page(rule)).contains("\n## " + rule.id() + " "),
                    page(rule) + " 少了 " + rule.id() + " 的小节标题");
        }
    }

    @Test
    void 规则页的生成段和代码完全一致() throws Exception {
        // 手改生成段、或者加了规则忘了重跑 --docs，这里都会红。
        for (Rule rule : Rules.all()) {
            String text = read(page(rule));
            assertTrue(text.startsWith(RulePages.head(rule)),
                    page(rule) + " 的生成段和 RulePages.head() 对不上了——改完代码跑一次 `llm-lint --docs docs/rules`");
            String marker = RulePages.endMarker(rule);
            int at = text.indexOf(marker);
            assertTrue(at > 0, page(rule) + " 少了结束标记 " + marker);
            assertFalse(text.substring(at + marker.length()).isBlank(),
                    page(rule) + " 的手写部分被清空了（只剩生成段）");
            assertFalse(text.contains("这段手写：出现时的样子"),
                    page(rule) + " 还留着占位符，手写内容没填");
        }
    }

    @Test
    void 页面标题和永久链接跟着规则走() throws Exception {
        for (Rule rule : Rules.all()) {
            String text = read(page(rule));
            String anchor = rule.id().toLowerCase(Locale.ROOT);
            assertTrue(text.contains("permalink: /rules/" + anchor + ".html"), page(rule) + " 的 permalink 不对");
            assertTrue(text.contains("title: \"" + rule.id() + " " + rule.title() + "\""),
                    page(rule) + " 的 title 没跟上规则标题");
            assertTrue(text.contains("description: \""), page(rule) + " 缺 description，社交卡片会没话说");
        }
    }

    @Test
    void 规则的docUrl指向真的存在的地方() throws IOException {
        String doc = read(RULES_DOC);
        for (Rule rule : Rules.all()) {
            String url = rule.docUrl();
            assertTrue(url.startsWith("https://"), rule.id() + " 的 docUrl 不是绝对地址：" + url);
            if (url.contains("/blob/main/docs/rules.md#")) {
                String fragment = url.substring(url.indexOf('#') + 1);
                assertTrue(doc.contains("<a id=\"" + fragment + "\"></a>"),
                        rule.id() + " 指向 #" + fragment + "，但 docs/rules.md 里没有这个锚点");
            }
        }
    }

    @Test
    void 规则手册顶部的表列全了每一条() throws IOException {
        String doc = read(RULES_DOC);
        long rows = doc.lines().filter(l -> l.startsWith("| [LLM0")).count();
        assertEquals(Rules.all().size(), rows,
                "docs/rules.md 顶部那张表少列了规则：表里 " + rows + " 行，代码里 " + Rules.all().size() + " 条");
        for (Rule rule : Rules.all()) {
            String anchor = rule.id().toLowerCase(Locale.ROOT);
            assertTrue(doc.contains("| [" + rule.id() + "](#" + anchor + ")"),
                    "表里缺 " + rule.id() + " 这一行");
        }
    }

    @Test
    void init模板第一行就指向schema() throws IOException {
        String template = ConfigLoader.template(Rules.all());

        assertTrue(template.startsWith("# yaml-language-server: $schema="), template);
        String first = template.lines().findFirst().orElse("");
        assertTrue(first.endsWith("docs/llm-lint.schema.json"), first);
        assertTrue(Files.isRegularFile(SCHEMA), "schema 文件得真的在仓库里");
    }

    @Test
    void schema列了全部顶层键() throws IOException {
        String schema = read(SCHEMA);

        for (String key : List.of("version", "rules", "exclude", "fail-on", "min-severity", "baseline")) {
            assertTrue(schema.contains("\"" + key + "\""), "schema 里少了 " + key);
        }
        assertTrue(schema.contains("\"additionalProperties\": false"), "顶层要禁止未知键，和解析器行为一致");
    }

    @Test
    void schema里的级别写法解析器都认() throws Exception {
        String schema = read(SCHEMA);

        for (String level : List.of("off", "on", "error", "warning", "info", "true", "false", "warn", "note")) {
            assertTrue(schema.contains("\"" + level + "\""), "schema 里少了级别写法 " + level);

            Path file = Files.createTempFile("llm-lint-level", ".yml");
            Files.writeString(file, "version: 1\nrules:\n  LLM007: " + level + "\n", StandardCharsets.UTF_8);
            Optional<RuleLevel> parsed = ConfigLoader.load(file, Rules.all()).levelOf("LLM007");

            if (level.equals("on") || level.equals("true")) {
                assertFalse(parsed.isPresent(), level + " 表示「跟随默认值」，不该钉死一个级别");
            } else {
                assertEquals(expected(level), parsed.orElseThrow().label(), "级别写法 " + level);
            }
        }
    }

    private static String expected(String level) {
        return switch (level) {
            case "off", "false" -> "off";
            case "warn" -> "warning";
            case "note" -> "info";
            default -> level;
        };
    }

    @Test
    void 贡献者文档都在且互相链得上() throws IOException {
        List<String> files = List.of("CONTRIBUTING.md", "ROADMAP.md", "SECURITY.md", "CODE_OF_CONDUCT.md",
                ".github/PULL_REQUEST_TEMPLATE.md",
                ".github/ISSUE_TEMPLATE/bug.yml", ".github/ISSUE_TEMPLATE/false-positive.yml",
                ".github/ISSUE_TEMPLATE/rule-idea.yml", ".github/ISSUE_TEMPLATE/config.yml");
        for (String name : files) {
            assertTrue(Files.isRegularFile(Path.of(name)), "少了 " + name + "，GitHub 上会给贡献者看到 404");
        }

        for (String readme : List.of("README.md", "README.en.md")) {
            String text = read(Path.of(readme));
            assertTrue(text.contains("CONTRIBUTING.md"), readme + " 没有链到 CONTRIBUTING.md");
            assertTrue(text.contains("ROADMAP.md"), readme + " 没有链到 ROADMAP.md");
        }
    }

    @Test
    void 贡献文档里的相对链接不会404() throws IOException {
        Pattern link = Pattern.compile("\\]\\(([^)\\s]+\\.(?:md|yml))");
        for (String name : List.of("CONTRIBUTING.md", "ROADMAP.md", "SECURITY.md", "CODE_OF_CONDUCT.md")) {
            Matcher m = link.matcher(read(Path.of(name)));
            while (m.find()) {
                String target = m.group(1).split("#")[0];
                if (target.startsWith("http") || target.isEmpty()) {
                    continue;
                }
                assertTrue(Files.exists(Path.of(target)), name + " 链到了不存在的 " + target);
            }
        }
    }

    @Test
    void 路线图里的规则号还没被占用() throws IOException {
        Pattern wanted = Pattern.compile("(?m)^### (LLM\\d{3})\\b");
        Set<String> planned = new TreeSet<>();
        Matcher m = wanted.matcher(read(Path.of("ROADMAP.md")));
        while (m.find()) {
            planned.add("LLM" + m.group(1));
        }

        Set<String> shipped = Rules.all().stream().map(Rule::id).collect(Collectors.toCollection(TreeSet::new));
        Set<String> overlap = new TreeSet<>(planned);
        overlap.retainAll(shipped);
        assertTrue(overlap.isEmpty(),
                "ROADMAP 里还写着 " + overlap + "，但它已经实现了——实现完记得从路线图里划掉");
    }

    @Test
    void docker镜像的承诺还在() throws IOException {
        String dockerfile = read(DOCKERFILE);
        assertTrue(dockerfile.contains("maven:3.9-eclipse-temurin-17"), "构建层要固定住，别用 latest");
        assertTrue(dockerfile.contains("eclipse-temurin:17-jre"), "最终层应该只有 JRE");
        assertTrue(dockerfile.contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/llm-lint.jar\"]"),
                "ENTRYPOINT 得能直接接参数，docker run <image> --version 才管用");
        assertTrue(dockerfile.contains("USER scanner"), "别用 root 跑");

        String workflow = read(DOCKER_WORKFLOW);
        assertTrue(workflow.contains("ghcr.io"), "镜像得推到 README 里写的那个地址");
        assertTrue(workflow.contains("packages: write"), "推 GHCR 需要 packages: write");
        assertTrue(workflow.contains("linux/arm64"), "tag 要出 arm64，Apple Silicon 才不用模拟");
        assertTrue(workflow.contains("setup-qemu-action"), "在 amd64 runner 上构建 arm64 需要 QEMU");

        String readme = read(Path.of("README.md"));
        assertTrue(readme.contains("ghcr.io/pgy763/llm-lint:latest"), "README 里的 docker run 得和 workflow 对得上");
    }

    @Test
    void 规则手册的表和代码对得上() throws IOException {
        // 表的第 2、3 列（级别、框架）是用户最先看的，写错了比少一行更要紧。
        Map<String, String[]> rows = new HashMap<>();
        for (String line : read(RULES_DOC).lines().toList()) {
            if (!line.startsWith("| [LLM")) {
                continue;
            }
            String[] cells = line.split(Pattern.quote("|"));
            String id = cells[1].trim();
            id = id.substring(1, id.indexOf(']'));
            rows.put(id, new String[]{cells[2].trim(), cells[3].trim()});
        }
        for (Rule rule : Rules.all()) {
            String[] cells = rows.get(rule.id());
            assertNotNull(cells, "规则手册顶部的表里没有 " + rule.id());
            String framework = switch (rule.framework()) {
                case "spring-ai" -> "Spring AI";
                case "langchain4j" -> "LangChain4j";
                default -> "通用";
            };
            assertEquals(rule.severity().label(), cells[0], rule.id() + " 在表里的级别和代码不一样");
            assertEquals(framework, cells[1], rule.id() + " 在表里的框架和代码不一样");
        }
    }

    @Test
    void 索引页的生成段和代码完全一致() throws IOException {
        // 这一段是 llm-lint --docs docs/rules 写的：表格、锚点、链接都必须等于此刻代码生成的结果，
        // 不然站上说的和工具说的就是两回事。改了规则就重跑一次，别手改。
        String doc = read(RULES_DOC);
        assertTrue(doc.contains(RulePages.INDEX_START), "索引页缺开始标记 " + RulePages.INDEX_START);
        assertTrue(doc.contains(RulePages.INDEX_END), "索引页缺结束标记 " + RulePages.INDEX_END);
        String regenerated = RulePages.mergeIndex(doc, Rules.all());
        assertTrue(regenerated != null, "索引页的标记不成对");
        assertEquals(doc, regenerated, "索引页的生成段过期了：跑一次 llm-lint --docs docs/rules");
    }

    @Test
    void 英文规则页和英文索引也跟代码对得上() throws IOException {
        Lang keep = Messages.lang();
        Messages.use(Lang.EN);
        try {
            for (Rule rule : Rules.all()) {
                Path page = Path.of("docs/en/rules").resolve(RulePages.fileName(rule));
                assertTrue(Files.isRegularFile(page), "少了 " + page);
                String text = read(page);
                String lower = rule.id().toLowerCase(Locale.ROOT);
                assertTrue(text.contains("permalink: /en/rules/" + lower + ".html"),
                        page + " 的永久链接不是 /en/rules/");
                assertTrue(text.startsWith(RulePages.head(rule, "/en/rules/")),
                        page + " 的生成段过期了：跑一次 llm-lint --docs-en docs/en/rules");
                assertFalse(text.contains("## Why it bites"), page + " 还留着英文占位符");
                assertTrue(text.contains("## How to fix"), page + " 少了手写的改法");
            }
            String index = read(Path.of("docs/en/rules.md"));
            assertTrue(index.contains("| Rule | Level | Framework | What it catches |"), "英文索引缺生成段；跑一次 llm-lint --docs-en docs/en/rules");
            for (Rule rule : Rules.all()) {
                assertTrue(index.contains("/en/rules/" + rule.id().toLowerCase(Locale.ROOT) + ".html"),
                        "英文索引里没有 " + rule.id() + " 的页面链接");
            }
        } finally {
            Messages.use(keep);
        }
    }
}
