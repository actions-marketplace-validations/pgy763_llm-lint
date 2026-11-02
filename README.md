# llm-lint

中文 | [English](README.en.md)

[![ci](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml/badge.svg)](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml)

**扫一遍 Spring AI / LangChain4j 代码，在 commit 之前拦住那些最贵的坑。**

十五条规则，每一条都对应一个「上线之后才发现」的故障：事务里等模型把连接池拖干、所有人共用一份
对话记忆、`new SseEmitter()` 三十秒断流、密钥进了 git 历史……

它不跑测试、不连模型、不要 API key、不需要项目的 classpath，只看源码。一条命令，几秒钟。

那些坑的完整现场（报错原文、怎么复现、怎么改）在另一份手册里：<https://pgy763.github.io/llm-dev-pitfalls/>。
这个工具就是那份手册的「检查器版本」——`samples/bad/` 里十五条反例，每条都能对上手册里的一个条目。

---

## 30 秒

```bash
curl -L -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar

java -jar llm-lint.jar src/main/java
```

输出长这样：

```
src/main/java/com/demo/OrderService.java
  42:20  error  LLM001  @Transactional 里调模型
      42 |     ChatResponse r = chatClient.prompt().user(q).call().chatResponse();
         |                     ^
      `createOrder` 开着事务，里面调了模型。这几秒数据库连接一直没还回去——并发一上来，
      报的是「拿不到连接」，不是「模型慢」，排查方向很容易被带偏。
      → https://pgy763.github.io/llm-dev-pitfalls/java/事务里调模型拖垮连接池
llm-lint: 1 个问题（1 error / 0 warning），扫了 12 个文件
```

- 有 `error` 时退出码 `1`，可以直接卡住 CI；只有 `warning` 时退出码 `0`。
- 想改阈值：`--fail-on warning`（警告也卡）或 `--fail-on never`（只报告，不失败）。
- 想知道某条规则为什么存在：`java -jar llm-lint.jar --explain LLM001`。

不想先拿自己的项目试也行：`samples/bad/` 里是十五条反例，`java -jar llm-lint.jar samples/bad`
会把它们全报出来；`samples/good/` 是改好的版本，一条都不报。

两个框架都认：Spring AI 的 `ChatClient` / `ChatModel` / `ChatMemory`，LangChain4j 的
`ChatLanguageModel` / `StreamingChatLanguageModel` / `AiServices` / `ChatMemoryProvider`，
按类型名后缀判断，同一份代码里混着用也照扫。

## 把规则固定在仓库里

`--disable`、`--exclude`、`--fail-on` 写进 CI 命令行，决定就只活在流水线里：本地跑的人看不到，
新同事不知道某条警告早就决定不管，删掉那行参数也没有任何记录说明为什么曾经加过。

把这些决定写成仓库根目录的一个 `.llm-lint.yml`，本地和 CI 读同一份：

```yaml
version: 1

rules:
  LLM004: off        # 流式超时统一在网关配，这里不重复报
  LLM007: error      # 团队要求 maxTokens >= 512，默认的 warning 不够狠

exclude:
  - "**/generated/**"   # 代码生成器的产物，改了也会被覆盖

fail-on: error
```

然后什么都不用加：

```bash
java -jar llm-lint.jar src/main/java
```

- **怎么找**：从当前目录一路往上，到仓库根（含 `.git` 那一级）为止；没有再从这个被扫的路径
  往上找。`--config other.yml` 指定别的文件，`--no-config` 完全不读。
- **只写差异**：没写的规则跟着工具自己的默认级别走，升级时默认值的改进能自动吃到，不会被配置里
  抄下来的旧值钉死。
- **优先级**：命令行 > 配置文件 > 默认值。CI 里临时想放宽，`--fail-on never` 仍然说了算；
  `--exclude` 和配置里的 `exclude` 取**并集**。
- **写错了就说清楚**：未知的键、未知的规则、未知的级别都退出码 `2`，带 `文件:行号`，
  拼错的编号还会猜一下（`LLM07` → `是不是 LLM007？`）。不猜、不跳过——静默忽略一个拼错的
  规则名，比直接失败难查得多。

没有现成的就生成一份带注释的模板：

```bash
java -jar llm-lint.jar --init          # 写 .llm-lint.yml，已存在则不覆盖
java -jar llm-lint.jar --print-config  # 打印实际生效的配置（含默认值），可存回去
```

`--print-config` 是排查「CI 和本地为什么不一样」的第一步：它把默认值也摊开，两边比一下就知道
差在哪。完整字段、YAML 子集限制和设计取舍见 **[docs/config.md](docs/config.md)**，
`samples/config-demo/` 是一个能跑的样例（三条反例，配置后只剩一条 error）。

## 存量项目：先记下旧问题，只拦新的

新项目从第一天就干净；存量项目一接工具，先蹦出几百条。把现状存成一份基线、提交进仓库，
之后 CI 只报清单之外的：

```bash
java -jar llm-lint.jar --save-baseline .llm-lint.baseline --fail-on never src/main/java
java -jar llm-lint.jar --baseline .llm-lint.baseline src/main/java
```

写进 `.llm-lint.yml` 之后就不用每次带参数：

```yaml
version: 1
baseline: .llm-lint.baseline
```

- 清单是纯文本，一行一条 `规则<TAB>文件<TAB>消息`，PR 的 diff 里能直接看出这次接受/新增了什么。
- 匹配**不看行号列号**：上面插几行 import 不会让旧问题复活；消息变了（`maxTokens(128)` →
  `256`）才算新问题，因为那确实改过了。
- `--save-baseline` 只增不减（已有的条目会合并进来），删旧条目要自己改 diff——防止一次误操作
  把历史债「洗白」。
- `--baseline` 指向的文件不存在就退出 `2`，不会静默当成空清单：路径写错时几百条全红最难查。
- 想知道债还得怎么样了：`--baseline-stats` 按规则列出还剩多少条、哪几条已经修好（可以删行）。

详见 **[docs/baseline.md](docs/baseline.md)**。

## 接进 CI

最省事的是一个 composite action（它自己装 JDK、下 jar）：

```yaml
- uses: actions/checkout@v4
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java     # 默认就是它
    # config: .llm-lint.yml # 默认从当前目录往上找，一般不用填
    fail-on: error          # error（默认）| warning | never；不填就用配置文件里的
    # disable: LLM009       # 临时的；长期的开关写进 .llm-lint.yml
```

问题会直接标在 diff 的对应行上（GitHub Actions 注解格式），job 页面还会多一段摘要
（按规则列出条数），不用翻日志。还没有 workflow 的话：`java -jar llm-lint.jar --init-ci`
直接在当前仓库写一份。

想让问题进仓库的 **Security → Code scanning** 面板：

```yaml
- run: java -jar llm-lint.jar --format sarif src/main/java > llm-lint.sarif
- uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: llm-lint.sarif
```

不用 action 的话，直接下 jar：

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
- run: |
    curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar
    java -jar llm-lint.jar --format github src/main/java
```

## 十五条规则

| 编号 | 级别 | 只看这个框架 | 拦的是什么 | 出事时你会看到 |
| --- | --- | --- | --- | --- |
| LLM001 | error | 通用 | `@Transactional` 方法里调模型 | 连接池打满，报「拿不到连接」 |
| LLM002 | error | 通用 | 单例 Bean 里放一份 `ChatMemory` | B 的对话里出现 A 的名字 |
| LLM003 | error | Spring AI | 让容器注入 `ChatClient` | 启动就 `NoSuchBeanDefinitionException` |
| LLM004 | error | 通用 | `new SseEmitter()` 没给超时 | 回答到一半断了，容器默认 30 秒 |
| LLM005 | warning | 通用 | 流式方法里 `block()` / `collectList()` | 前端等很久，然后一次性全出来 |
| LLM006 | warning | 通用 | `produces` 没带 `charset` | 中文变问号，接口却是通的 |
| LLM007 | warning | 通用 | `maxTokens(...)` ≤ 256 | 工具参数被截断，报 `Unexpected end-of-input` |
| LLM008 | warning | 通用 | 空 `catch (IOException)` 包着写流 | 客户端早断开，你还在写，日志里什么都没有 |
| LLM009 | warning | 通用 | `@Bean` 造 HTTP 客户端却没配超时 | 上游不回，线程一直挂着 |
| LLM010 | error | 通用 | 代码里写着 `sk-…` / `AIza…` 字面量 | 密钥进了 git 历史，删不掉了 |
| LLM011 | error | 通用 | 每个请求现建 `ChatClient` / `ChatModel` | 高峰期线程和端口先于模型耗尽 |
| LLM012 | error | LangChain4j | 每个请求现建 `AiServices` | 每请求重装配，记忆还串了 |
| LLM013 | warning | 通用 | 循环里逐个调模型 / 向量库 | N 条数据 N 次往返，延迟线性叠加 |
| LLM014 | error | LangChain4j | `ChatMemoryProvider` 用了写死的 id | 看着按会话隔离，其实全站一份 |
| LLM015 | warning | 通用 | 事务方法返回 `Flux` / `Mono` | 注解看着生效，回滚没覆盖到 |

每条的反例、改法、判断范围：**[docs/rules.md](docs/rules.md)**。
`java -jar llm-lint.jar --list-rules` 随时能看这张表。

## 用法

```
llm-lint [--format text|json|github|sarif] [--fail-on error|warning|never]
         [--min-severity info|warning|error] [--disable LLM004,LLM009]
         [--exclude "**/generated/**"] [--no-context] [--quiet]
         [--config .llm-lint.yml | --no-config] [--init] [--init-ci [<dir>]] [--print-config]
         [--baseline .llm-lint.baseline] [--save-baseline .llm-lint.baseline]
         [--baseline-stats] [--summary <file>] [--jobs N]
         [--explain LLM001] [--list-rules] <path...>

  --format        text（默认，人看）/ json / github（Actions 注解）/ sarif（Code Scanning）
  --fail-on       error（默认）/ warning（警告也卡）/ never（只报告，不失败）
  --min-severity  低于这个级别的不报也不计
  --disable       不跑的规则；某条规则在你项目里是误报就关掉它
  --exclude       不扫的路径（glob），逗号分隔
  --no-context    text 格式下不打印源码行
  --config        用指定的配置文件（默认自动找仓库里的 .llm-lint.yml）
  --no-config     不读配置文件
  --init          在当前目录写一份带注释的 .llm-lint.yml
  --init-ci       在 .github/workflows/ 下写一份 GitHub Actions 配置
  --print-config  打印实际生效的配置（含默认值），可以存回当配置文件
  --baseline      读这份基线，清单里的旧问题不再报（也见配置里的 baseline:）
  --save-baseline 把这次的问题并进这份基线；已有条目一条不删
  --baseline-stats 打印基线里还剩多少条、哪几条已经修好可以删
  --summary       把一段 Markdown 摘要写进文件（适合 CI 的 job 摘要）
  --jobs          同时扫几个文件：默认按 CPU 核数（最多 8），1 表示一个一个来
  --explain       一条规则的级别、框架、理由、抑制方式
```

`<path>` 可以是目录，也可以是单个 `.java`。目录会递归找 `.java`，跳过 `target/` 和 `build/`。
文件是并行解析的（默认最多 8 个线程），输出顺序和串行完全一样，大仓库快一倍上下；
机器吃紧或想复现某个顺序时加 `--jobs 1`。

### 某一行确实没有问题

```java
// llm-lint:ignore LLM004
SseEmitter emitter = new SseEmitter();
```

- `// llm-lint:ignore` —— 压掉该行全部问题
- `// llm-lint:ignore LLM004,LLM009` —— 只压这几条
- `// llm-lint:ignore-file` —— 整个文件不报

## 误报这件事

**宁可漏，不可错。** 误报一次，作者就会把这个工具从流水线里删掉。所以：

- 类型只在**本文件里声明得出来**的时候才判断（字段、参数、局部变量）。`getChatClient().prompt()` 这种拿不到类型的调用，不报。
- 空 `catch` 只在它确实包着 `write`/`send`/`flush` 时报，纯业务逻辑的空 catch 不管。
- `@Bean` 方法里建客户端不报——那正是推荐写法。
- 自己 `build()` 出来的 `ChatClient` 字段不报 LLM003，只有「让容器注入」才报。
- LLM009 认不出你在别处统一配的超时——如果配了，在 `.llm-lint.yml` 里写 `LLM009: off`，
  下面那套「按项目配置」就是为这种情况准备的，不用每次在 CI 里手写 `--disable`。
- 一开始还写过一条「`new SseEmitter(0L)` 没配 `onTimeout`」的规则，被自己的正例打回来了：
  把超时关掉、靠心跳维持连接是常规做法。**删掉**，而不是加条件硬凑。

目前在这些真实代码上跑过，一条都没报：

- [hello-ai-backend](https://github.com/pgy763/hello-ai-backend) 的 71 个 Java 文件（含 Spring AI 调用与手写 SSE 推送）
- [llm-gateway](https://github.com/pgy763/llm-gateway)（45 个文件）、[prompt-ops](https://github.com/pgy763/prompt-ops)（20 个文件）的整个 `src/main/java`

## 它不是

- **不是 AI 代码审查**：没有模型、没有网络请求，你的代码一个字节都不会离开本机。
- **不是类型检查器**：不做符号解析，不读依赖的 jar，靠类型名后缀判断。
- **不改代码**：只报告，不自动修。改法写在每条说明里，你自己判断。

## 局限

- 只支持 Java 17 语法（Spring AI 2.x / LangChain4j 1.x 的主流写法）
- `var`、链式返回、跨方法/跨文件推断都不做，所以「把模型调用藏进 helper」它看不出来
- 不查配置（`application.yml`）、SQL、前端代码；不联网
- `Kotlin`、`Groovy` 不支持

这些边界是故意的：一个能在任何 Java 项目里 `java -jar` 就跑的工具，比一个要配 classpath
的工具更容易被真的用起来。

## 自己打包 / 这套东西怎么验收

```bash
mvn -B verify           # 编译 + 跑测试（含 samples 双向验收）
mvn -B -DskipTests package
java -jar target/llm-lint.jar --list-rules
```

需要 **JDK 17+** 和 Maven。

`samples/` 是规则的验收标准，也是唯一的标准：

- `samples/bad/LLM0NN_*.java` —— 每个文件必须**正好**报一条，且编号等于文件名前缀
- `samples/good/LLM0NN_*.java` —— 同一处的正确写法，必须**一条都不报**
- `samples/suppressed/` —— 验证 `// llm-lint:ignore` 只压掉指定的那一条
- `samples/config-demo/` —— 三个反例 + 一份 `.llm-lint.yml`，配好之后必须**只剩一条** error
- `BaselineTest` / `BaselineCliTest` —— 基线：旧问题存进去后退出 0，新犯的照样退出 1
- `ExcludeTest` / `SummaryCliTest` / `InitCiTest` —— 相对路径排除、摘要文件、`--init-ci`

改规则先改前三个目录。CI 还会用自己发布的 action 扫一遍 `samples/good`（必须通过）、
`samples/bad`（必须失败）和 `samples/config-demo`（带着仓库配置也必须失败），确保「别人拿到
jar 真的能用」。

## 发版

改完 `pom.xml` 的版本号，打 tag 就行：

```bash
git tag v0.6.0 && git push origin v0.6.0
```

`release` 会跑 `mvn verify`、核对 jar 里的版本和 tag 一致、建同名 release 并把 `llm-lint.jar`
传上去，再把 `v1` 指针挪到这个 tag —— 所以 `uses: pgy763/llm-lint@v1` 一直拿到最新版，用户
不用跟着改 workflow。

release 正文也是自动写的：按 commit 前缀（`feat:` / `fix:` / `docs:` …）分组成中文小节，
末尾附上 `上一个版本...本版` 的 compare 链接。所以 commit 标题尽量守约定，正文就能直接看。
重跑同一个 tag（比如 tag 被挪过）时，正文和 jar 会一起更新，不会只换 jar。

jar 这个 asset 不能省：`action.yml` 默认从 `releases/latest/download/llm-lint.jar` 取工具本体，
release 里没有它，别人装到的就是上一版。

想上 [Marketplace](https://github.com/marketplace)：在 release 页面勾 *Publish this Action to
the GitHub Marketplace*、选个分类。这一步只能手工点，勾完这个版本才会出现在 Actions 的搜索里。

## 相关

- [llm-dev-pitfalls](https://pgy763.github.io/llm-dev-pitfalls/) —— 这份检查器背后的手册，51 条，每条都有报错原文
- [hello-ai-backend](https://pgy763.github.io/hello-ai-backend/) —— 从接第一个模型开始写的书

## License

MIT