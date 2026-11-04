# Roadmap

这个项目很小，路线也就一条：**把「Spring AI / LangChain4j 项目上线之后才会发现的那类坑」一条条变成能静态查出来的规则**。下面这些是已经想清楚、可以直接动手的；每条都标了难度，`good first issue` 是真的第一次提 PR 就能做完的。

开 issue 之前先看一眼：想做的活如果不在下面，也欢迎，但请先开 issue 说一句思路，避免白写。

## 现在在哪

| 版本 | 主题 |
| --- | --- |
| 0.13.0 | 贡献者入口：CONTRIBUTING、issue/PR 模板、这份路线图 |
| 0.12.0 | SARIF `partialFingerprints`，Code Scanning 不再重复计数 |
| 0.11.0 | JUnit / HTML 报告、`--init-hook` |
| 0.10.0 | `--lang en`、机器可读的规则目录 |

## 想加的规则

判断口径和现有十八条一样：只读语法树、靠类型名后缀认模型调用、认不出来就不报。每个提案都写好了「怎么认」和「样例长什么样」，照着写就行；写之前把 [CONTRIBUTING 的加规则清单](CONTRIBUTING.md#加一条规则)过一遍。

### LLM019 `@Tool` 方法里做阻塞调用 · good first issue

**为什么**：工具调用跑在请求线程上。方法里一条 JDBC 查询、一次同步 HTTP、甚至 `Thread.sleep`，都会把整个响应时间拉长；模型那边还在等工具返回，用户看到的是「卡住了」。

**怎么认**：方法带 `@Tool`（`dev.langchain4j.agent.tool.Tool` 或 Spring AI 的 `@Tool`），方法体里出现下列任一：

- 调用 `.block()`、`.blockFirst()`、`.blockLast()`、`.join()`、`.get()`（在 `Mono`/`Flux`/`Future` 上）
- `Thread.sleep(`
- 调用接收者类型名以 `Repository`、`Mapper`、`JdbcTemplate`、`RestTemplate`、`WebClient` 结尾或等于其一

**级别**：warning。工具方法里读一次缓存是合理的，所以别定成 error。

**样例**：`samples/bad/LLM019_ToolBlocksOnDb.java` 一个 `@Tool` 方法里调 `orderRepository.findById(...)`；`samples/good/LLM019_ToolReadsCache.java` 只读 `Map`。

### LLM020 事务方法里等异步结果 · good first issue

**为什么**：`@Transactional` 方法里 `.block()` 或 `Thread.sleep()`，数据库连接就一直攥在手里——和 LLM001 是同一个坑的另一半：LLM001 是等模型，这条是等任何东西。

**怎么认**：方法（或所在类）带 `@Transactional`，方法体里出现 `.block()` / `.blockFirst()` / `.blockLast()` / `Future.get()` / `Thread.sleep(`。

**级别**：warning（`Thread.sleep` 也可能是限流退避，别拦死）。

**样例**：`samples/bad/LLM020_TransactionBlocks.java` 事务里 `flux.blockLast()`；`samples/good/LLM020_BlockOutsideTransaction.java` 同样的调用放在事务外。

### LLM021 事务方法返回长连接 · medium

**为什么**：`@Transactional` 方法返回 `SseEmitter` 或 `Flux`，等于让事务活到整个流结束——连接池会在流还没结束时先耗尽。LLM015 覆盖了「返回 `Flux`/`Mono`」，这条补上 SSE 那一半。

**怎么认**：方法返回类型是 `SseEmitter` 或 `ResponseBodyEmitter`，且方法（或类）带 `@Transactional`。

**级别**：error。

**样例**：`samples/bad/LLM021_TransactionalSseEmitter.java`；正例是标了 `@Transactional` 的普通方法 + 另一个非事务的流式方法。

### LLM022 定时任务 / 异步方法里调模型 · medium

**为什么**：`@Scheduled`、`@Async` 里没有请求上下文。用 `ChatMemory` 时要么拿到别人的会话，要么每次新建一份——LLM002 讲的是 Bean 级共享，这条讲的是「根本没有请求边界」。

**怎么认**：方法带 `@Scheduled` 或 `@Async`，方法体里出现模型调用（接收者类型名以 `ChatClient`、`ChatModel`、`EmbeddingModel`、`ChatLanguageModel`、`StreamingChatModel` 结尾）。

**级别**：warning。

**样例**：`samples/bad/LLM022_ScheduledModelCall.java`；正例是 `@Scheduled` 里只发事件、由请求线程去调模型。

## 想做的工程活

### Docker 镜像 · good first issue

`docker run --rm -v "$PWD:/src" ghcr.io/pgy763/llm-lint:latest /src/src/main/java` 就能跑，不需要装 JDK 和 Maven。

验收：多阶段构建（`maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre` 运行）、镜像里只有 jar、`ENTRYPOINT ["java","-jar","/app/llm-lint.jar"]`、`.github/workflows/docker.yml` 在打 tag 时推 `ghcr.io/pgy763/llm-lint:<tag>` 和 `:latest`、README 加一节用法。

### `--list-rules --format markdown` · good first issue

把十八条规则渲染成 `docs/rules.md` 顶部那张表，文档就能由代码生成，不用手动同步「十八条」这类数字。

验收：输出列与现有表格一致（编号 / 级别 / 框架 / 一句话）；有测试锁住「表里的规则数 = `Rules.all().size()`」；`tools/` 下加一个脚本，用它重写 `docs/rules.md` 的表格段落。

### `--files-from <file|->` · medium

CI 矩阵、增量扫描、编辑器插件都需要「给我一份文件清单，你只扫这些」。`--since` 解决的是 git 场景，这个解决的是「别人已经算好了」。

验收：`-` 表示从 stdin 读，一行一个路径；不存在的文件跳过并在 stderr 说一句；和 `--since`、位置参数同时给时按交集处理（和现在 `--since` + 路径的行为一致）。

### 规则文档自动校验 · good first issue

现在 `DocsTest` 已经锁了「每条规则在 `docs/rules.md` 里有锚点」。可以再进一步：校验顶部表格里的级别、编号、标题和 `Rules.all()` 完全一致，级别写错就红。

## 不做的事

- **不做符号解析、不读 classpath。** 这是它能「一个 jar 一条命令」的原因，换来的代价是认不出跨文件、反射、`var` 的部分场景——这个取舍不重新讨论。
- **不联网。** 不上报、不遥测、不调云端规则库。
- **不做自动改代码。** `--fix` 只输出 diff，落不落盘由用户决定；「自动改坏」比「不自动改」贵得多。
- **不为了数量加规则。** 一条会误报的规则，比没有这条规则更糟。