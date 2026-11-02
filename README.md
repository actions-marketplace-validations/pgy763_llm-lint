# llm-lint

[![ci](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml/badge.svg)](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml)

**扫一遍 Spring AI / LangChain4j 代码，在 commit 之前拦住那十个最贵的坑。**

它不跑测试、不连模型、不要 API key，只看代码。一条命令，几秒钟，告诉你哪一行的写法会在半夜出事。

那些坑的完整现场（报错原文、怎么复现、怎么改）在另一份手册里：<https://pgy763.github.io/llm-dev-pitfalls/>。这个工具就是那份手册的「检查器版本」。

---

## 30 秒

```bash
# 下载 jar（或自己打包，见文末）
curl -L -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar

java -jar llm-lint.jar src/main/java
```

输出长这样：

```
src/main/java/com/demo/OrderService.java
  42  ERROR  LLM001  @Transactional 里调模型
      `createOrder` 开着事务，里面调了 `ChatClient`。模型这一次要几秒，这几秒数据库连接一直没还回去——并发一上来，报的是「拿不到连接」，不是「模型慢」，排查方向很容易被带偏。把模型调用挪到事务外面，或者先查好数据再开事务。
      → https://pgy763.github.io/llm-dev-pitfalls/java/事务里调模型拖垮连接池
```

有 `ERROR` 时退出码是 `1`，可以直接卡住 CI。只有 `WARNING` 时退出码是 `0`。

## 接进 CI

最省事的是一个 composite action（它自己会装 JDK、下 jar）：

```yaml
- uses: actions/checkout@v4
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java     # 默认就是它
    fail-on: error          # error（默认）| warning | never
    # disable: LLM009       # 某条规则在你项目里是误报就关掉
```

问题会直接标在 diff 的对应行上（用的是 GitHub Actions 注解格式）。

不想用 action 的话，直接下 jar：

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
- run: |
    curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar
    java -jar llm-lint.jar --format github src/main/java
```

## 十条规则

| 编号 | 级别 | 抓什么 | 出事时你会看到 | 手册 |
| --- | --- | --- | --- | --- |
| LLM001 | ERROR | `@Transactional` 方法里调模型 | 连接池打满，报「拿不到连接」 | [事务里调模型拖垮连接池](https://pgy763.github.io/llm-dev-pitfalls/java/事务里调模型拖垮连接池) |
| LLM002 | ERROR | 单例 Bean 里放一份 `ChatMemory` | B 的对话里出现 A 的名字 | [多用户共用一份 ChatMemory](https://pgy763.github.io/llm-dev-pitfalls/java/多用户共用一份ChatMemory) |
| LLM003 | ERROR | 让容器注入 `ChatClient` | 启动就 `NoSuchBeanDefinitionException` | [注入 ChatClient 找不到 Bean](https://pgy763.github.io/llm-dev-pitfalls/java/注入ChatClient找不到Bean) |
| LLM004 | ERROR | `new SseEmitter()` 没给超时 | 回答到一半断了，容器默认 30 秒 | [SSE 连接 30 秒断开](https://pgy763.github.io/llm-dev-pitfalls/java/SSE连接30秒断开) |
| LLM005 | WARNING | 返回 `Flux`/`SseEmitter` 的方法里 `block()`/`collectList()` | 前端等很久，然后一次性全出来 | [WebFlux 里 block 流式结果](https://pgy763.github.io/llm-dev-pitfalls/java/WebFlux里block流式结果) |
| LLM006 | WARNING | `produces` 写了 `text/event-stream` 但没带 `charset` | 中文变问号，接口却是通的 | [SSE 响应中文乱码](https://pgy763.github.io/llm-dev-pitfalls/java/SSE响应中文乱码) |
| LLM007 | WARNING | `maxTokens(...)` ≤ 256 | 工具参数被截断，报 `Unexpected end-of-input` | [工具消息顺序错](https://pgy763.github.io/llm-dev-pitfalls/endpoint/工具消息顺序错) |
| LLM008 | WARNING | 空的 `catch (IOException)` 包着写流 | 客户端早断开，你还在写，日志里什么都没有 | [客户端断开后还在写 SSE](https://pgy763.github.io/llm-dev-pitfalls/java/客户端断开后还在写SSE) |
| LLM009 | WARNING | `@Bean` 里造 HTTP 客户端却没配超时 | 上游不回，线程一直挂着 | [Reactor 超时截断流式](https://pgy763.github.io/llm-dev-pitfalls/java/Reactor超时截断流式) |
| LLM010 | ERROR | 代码里写着 `sk-...` 这样的字面量 | 密钥进了 git 历史，删不掉了 | [密钥提交进了 git 历史](https://pgy763.github.io/llm-dev-pitfalls/auth/密钥提交进了git历史) |

`java -jar llm-lint.jar --list-rules` 随时能看这张表。

## 用法

```
llm-lint [--format text|json|github] [--fail-on error|warning|never]
         [--disable LLM004,LLM009] [--list-rules] <path...>

  --format     text（默认，人看）/ json（脚本吃）/ github（Actions 注解）
  --fail-on    error（默认）/ warning（警告也卡）/ never（只报告，不失败）
  --disable    不跑的规则；某个规则在你项目里是误报就关掉它
```

`<path>` 可以是目录，也可以是单个 `.java`。目录会递归找 `.java`，跳过 `target/` 和 `build/`。

## 误报这件事

**宁可漏，不可错。** 误报一次，作者就会把这个工具从流水线里删掉。所以：

- 类型只在**本文件里声明得出来**的时候才判断（字段、参数、局部变量）。`getChatClient().prompt()` 这种拿不到类型的调用，不报。
- 空 `catch` 只在它确实包着 `write`/`send`/`flush` 时报，纯业务逻辑的空 catch 不管。
- LLM009 认不出你在别处统一配的超时——如果配了，`--disable LLM009`。

宁可少抓几个，也不要在别人的仓库里制造噪音。

目前在这些真实代码上跑过，没有误报：

- [hello-ai-backend](https://github.com/pgy763/hello-ai-backend) 的 71 个 Java 文件（含 Spring AI 调用与手写 SSE 推送）
- [llm-gateway](https://github.com/pgy763/llm-gateway)、[prompt-ops](https://github.com/pgy763/prompt-ops) 的整个 `src/main/java`

## 它不是

- **不是 AI 代码审查**：没有模型、没有网络请求，你的代码一个字节都不会离开本机。
- **不需要 classpath**：不要求把 Spring AI 的 jar 给它，用的是语法树，不是编译结果。
- **不改代码**：只报告，不自动修。改法写在每条说明里，你自己判断。

## 局限

- 只支持 Java 17 语法（Spring AI 2.x / LangChain4j 1.x 的主流写法）
- 不做跨方法、跨文件的数据流分析，所以「把模型调用藏进一个 helper」它看不出来
- 不是类型检查器：`var`、泛型、接口到实现的推断都不做
- `Kotlin`、`Groovy` 不支持

这些边界是故意的：一个能在任何 Java 项目里 `java -jar` 就跑的工具，比一个要配 classpath 的工具更容易被真的用起来。

## 自己打包

```bash
mvn -B verify           # 编译 + 跑测试（含 samples 验收）
mvn -B -DskipTests package
java -jar target/llm-lint.jar --list-rules
```

需要 **JDK 17+** 和 Maven。

测试的验收标准在 `samples/`：`samples/bad/LLM00X_*.java` 每个文件必须**只**触发它自己那条规则，`samples/good/` 必须**一条都不报**。改规则先改这两个目录。

## 相关

- [llm-dev-pitfalls](https://pgy763.github.io/llm-dev-pitfalls/) —— 这份检查器背后的手册，51 条，每条都有报错原文
- [hello-ai-backend](https://pgy763.github.io/hello-ai-backend/) —— 从接第一个模型开始写的书

## License

MIT