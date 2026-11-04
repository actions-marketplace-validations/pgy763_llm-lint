# 让更多人找到它

工具不是靠名字被找到的，是靠**别人正在搜的那句话**。搜「Spring AI linter」的人是同行，
搜 `NoSuchBeanDefinitionException ChatClient`、`SseEmitter 30 秒断开`、`HikariPool Connection is
not available` 的人才是真正在踩坑的人——而且数量多一个数量级。

所以原则只有一条：**把报错原文放在能被索引的地方**，规则名放第二位。

## 仓库里已经做好的

- `README.md` / `README.en.md` 各有一张「搜着报错找过来的」对照表：报错原文 → 病因 → 规则编号。
- `action.yml` 的 Marketplace 描述改成英文关键词，能和「Spring AI」「LangChain4j」「static
  analysis」对上。
- 仓库 topics 已有：`cli` `code-review` `github-actions` `java` `langchain4j` `linter`
  `spring-ai` `static-analysis`。
- `llms.txt` 在仓库根：AI 搜索/助手读到时能直接拿到「这是什么 + 文档在哪」。

## 一次点几下（只有仓库所有者能做）

1. **About 描述**（Settings → General，或仓库首页右上齿轮）改成英文，覆盖搜索词：

   ```
   Static analysis for Spring AI / LangChain4j (Java): 19 rules for the failures that only show
   up in production. CLI + GitHub Action, SARIF, baseline.
   ```

2. **Website** 填手册地址：`https://pgy763.github.io/llm-dev-pitfalls/`。这会在手册和仓库之间
   形成双向链接，对搜索引擎和「这项目靠不靠谱」的判断都有用。

3. **Topics** 建议补：`llm` `ai` `openai` `spring-boot` `code-quality` `sast` `code-scanning`
   `ai-agents`。（GitHub 最多 20 个，宁可选准，不要堆。）

4. **Marketplace 勾选**：每次发版后到 release 页面确认 *Publish this Action to the GitHub
   Marketplace* 仍勾着；分类选 **Code quality**（其次 Security）。

5. 改完自查：

   ```bash
   curl -s https://api.github.com/repos/pgy763/llm-lint | grep -E '"description"|"homepage"'
   ```

## 外部提交（按性价比排序）

1. **[mre/awesome-static-analysis](https://github.com/mre/awesome-static-analysis)** —— Java 段
   加一行，这是这个领域最常被翻的清单：

   ```markdown
   - [llm-lint](https://github.com/pgy763/llm-lint) - Static analysis for Spring AI / LangChain4j
     (Java): model calls in transactions, shared chat memory, SSE timeouts, hard-coded keys.
   ```

2. **手册站点反向导流**（最强的一条，已完成）：`llm-dev-pitfalls` 里六个能对上规则的 Java 坑
   ——事务里调模型、共用 ChatMemory、注入 ChatClient、SSE 30 秒断开、WebFlux 里 block、SSE 中文
   乱码——文章末尾各加了一行指向对应规则（LLM001–LLM006，commit `f98defe`）。剩下的 Python/RAG
   文章暂时没有对应规则；等规则扩到那边再补。手册的搜索流量已经存在，把它导到工具上，比从零
   做 SEO 快得多。

3. **Show HN**：`Show HN: llm-lint – static analysis for Spring AI / LangChain4j code`
   正文只讲一件事：上线后才发现的 18 个坑，以及为什么它不用 classpath、不联网。

4. **Reddit**：`r/java`、`r/SpringBoot`。标题用报错原文，比标题用项目名有效：
   `Why does my SSE stream die after 30 seconds? (and 14 other Spring AI mistakes I lint for)`

5. **中文渠道**：V2EX / 掘金 / 知乎。标题示例：
   「Spring AI 上线后最常见的 18 个坑，我写了个静态检查器，一条命令扫出来」。

6. GitHub 上按 `awesome-llm`、`awesome-ai-tools` 之类的 topic 找还在维护的清单，按它们的
   贡献格式提 PR；一次只提一个，附一句为什么适合这个清单。

## 内容层面的长尾

- 每条规则的锚点（`docs/rules.md#llm007`）已经稳定，`--explain` 和文档都指向它，外部引用不会失效。
- 写新规则时，标题里带上**报错原文**（「`Unexpected end-of-input`：maxTokens 太小」），
  比「LLM007 小 maxTokens」更容易被搜到。
- release 正文是按 commit 前缀自动生成的，`feat:` 后面那句人话会直接进 release 页面，也是索引内容。

## 不要做

- 不买 star、不在无关 issue/PR 里贴链接——GitHub 会判 spam，反而降权。
- 不把描述堆成一串关键词；Marketplace 审核会拒，读者也一眼看出。
- 不在同一个社区连发多帖；一周一个渠道，讲具体问题，比一次刷屏有效。