---
title: 二十二条规则
description: 每条规则会报什么、怎么改、判断范围到哪、为什么这么判——按规则号查。
---

# 规则

二十二条规则，每条都对应一个「上线之后才发现」的具体故障。规则是纯静态的：不需要联网、不需要
API key、不需要项目的 classpath——它只读 `.java` 源码，靠类型名的后缀判断这是不是模型调用。

> 这套判断方式有意保守：**宁可漏报，不可乱报**。认不出来的（链式调用、跨文件推断、反射、
> 配置类里拼出来的 Bean）一律不报。所以「0 个问题」只代表没命中这二十二条模式，不代表代码没问题。

表里的级别是**默认值**。某条规则在你项目里不合适——误报，或者你们的要求比它更严——写进仓库根的
`.llm-lint.yml` 就行，不用改工具、也不用每次在 CI 里手写参数：见 [config.md](config.md)。

这张表也能自己生成：`llm-lint --list-rules --format markdown`（级别、框架跟着配置走，测试会校验它和代码一致）。
每一条的简版说明（级别、框架、为什么、怎么抑制）用 `llm-lint --explain LLM007 --format markdown`，
整本用 `--explain all --format markdown`。

| 编号 | 级别 | 框架 | 拦的是什么 |
| --- | --- | --- | --- |
| [LLM001](#llm001) | error | 通用 | `@Transactional` 里调模型，占着数据库连接等几秒 |
| [LLM002](#llm002) | error | 通用 | 单例 Bean 里一份 `ChatMemory`，所有人共用 |
| [LLM003](#llm003) | error | Spring AI | 让容器注入 `ChatClient`（它没有这个 Bean） |
| [LLM004](#llm004) | error | 通用 | `new SseEmitter()` 用容器默认超时，30 秒断流 |
| [LLM005](#llm005) | warning | 通用 | 流式方法里 `block()`，把事件循环占住 |
| [LLM006](#llm006) | warning | 通用 | `produces = "text/event-stream"` 少了 charset |
| [LLM007](#llm007) | warning | 通用 | `maxTokens` 太小，工具参数被截断 |
| [LLM008](#llm008) | warning | 通用 | 空 catch 吞掉写 SSE 时的 `IOException` |
| [LLM009](#llm009) | warning | 通用 | `@Bean` 建的 HTTP 客户端没配超时 |
| [LLM010](#llm010) | error | 通用 | 硬编码的 `sk-…` / `AIza…` 密钥 |
| [LLM011](#llm011) | error | 通用 | 每个请求现建 `ChatClient` / `ChatModel` |
| [LLM012](#llm012) | error | LangChain4j | 每个请求现建 `AiServices` |
| [LLM013](#llm013) | warning | 通用 | 循环里逐个调模型或向量库 |
| [LLM014](#llm014) | error | LangChain4j | `ChatMemoryProvider` 用了写死的 id |
| [LLM015](#llm015) | warning | 通用 | 事务方法返回 `Flux` / `Mono` |
| [LLM016](#llm016) | error | 通用 | `@Tool` 方法返回 `Mono` / `Flux` |
| [LLM017](#llm017) | error | 通用 | 事务里查 / 写向量库 |
| [LLM018](#llm018) | warning | 通用 | 流式接口没声明 `produces` |
| [LLM019](#llm019) | warning | 通用 | `@Tool` 方法里查库 / 调同步接口 / `Thread.sleep` |
| [LLM020](#llm020) | warning | 通用 | `@Transactional` 方法里 `.block()` / `Future.get()` / `Thread.sleep` |
| [LLM021](#llm021) | error | 通用 | `@Transactional` 方法返回 `SseEmitter` / `ResponseBodyEmitter` |
| [LLM022](#llm022) | warning | 通用 | `@Scheduled` / `@Async` 方法里直接调模型 |

---

<a id="llm001"></a>
## LLM001 `@Transactional` 里调模型

**搜这些能找到这条：** `HikariPool-1 - Connection is not available, request timed out`、`接口卡三十秒，日志里全是拿不到连接`

**级别** error · **框架** 通用 · **手册** [事务里调模型拖垮连接池](https://pgy763.github.io/llm-dev-pitfalls/java/事务里调模型拖垮连接池)

```java
// 会报
@Transactional
public String answer(String question) {
    ChatResponse response = chatModel.call(new Prompt(question));
    return response.getResult().getOutput().getText();
}
```

改法：先把要查的查完、要写的写好，再提交事务；模型调用放在事务外面。确实需要「模型结果和
数据一起落库」的，用编程式事务把范围缩到写库那几行。

为什么：模型这一次要几秒，这几秒里数据库连接一直被这个线程占着、没还回池子。连接池只有
几十个，并发一上来先报「拿不到连接」，而不是「模型慢」——排查方向很容易被带偏。

判断范围：方法上或类上有 `@Transactional`，方法体里出现了声明类型以 `ChatClient`、`ChatModel`、
`ChatLanguageModel`、`StreamingChatModel`、`EmbeddingModel`、`EmbeddingClient` 结尾的接收者。
每个方法只报第一处。

---

<a id="llm002"></a>
## LLM002 单例里共用一份 `ChatMemory`

**搜这些能找到这条：** `用户 A 的名字出现在用户 B 的对话里`、`ChatMemory 串台`

**级别** error · **框架** 通用 · **手册** [多用户共用一份 ChatMemory](https://pgy763.github.io/llm-dev-pitfalls/java/多用户共用一份ChatMemory)

```java
// 会报
@Service
public class ChatService {
    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
}
```

改法：按会话 id 从 `ChatMemoryProvider` / `ChatMemoryStore` 取，或者用
`MessageChatMemoryAdvisor` 按会话隔离。

为什么：`ChatMemory` 里装的是对话。单例 Bean 只 `new` 一份，就是全站一个会话——A 说过的名字、
地址会出现在 B 的上下文里。这类问题在压测里完全看不出来，上线后第一个投诉的是用户。

判断范围：`@Service` / `@Component` / `@Repository` / `@Controller` 这类单例 Bean 里，字段或
构造参数的类型以 `ChatMemory` / `ChatMemoryStore` 结尾。

---

<a id="llm003"></a>
## LLM003 让容器注入 `ChatClient`

**搜这些能找到这条：** `NoSuchBeanDefinitionException: No qualifying bean of type 'ChatClient'`、`启动就挂，找不到 ChatClient`

**级别** error · **框架** Spring AI · **手册** [注入 ChatClient 找不到 Bean](https://pgy763.github.io/llm-dev-pitfalls/java/注入ChatClient找不到Bean)

```java
// 会报：启动就是 NoSuchBeanDefinitionException
@Autowired
private ChatClient chatClient;
```

改法：注入 `ChatClient.Builder`，在构造里 `build()`；或者自己定义一个 `@Bean ChatClient`。

为什么：Spring AI 的自动配置只注册 `ChatClient.Builder` 和 `ChatModel`，没有 `ChatClient`
这种 Bean。这一点在文档里写着，但错误信息只说「找不到 Bean」，看名字又完全合理，很容易卡住。

判断范围：字段或构造参数类型以 `ChatClient` 结尾，且带 `@Autowired` / `@Resource` / `@Inject`，
或者出现在构造器参数里。自己 `build()` 出来的字段不报。

---

<a id="llm004"></a>
## LLM004 `new SseEmitter()` 没设超时

**搜这些能找到这条：** `AsyncRequestTimeoutException`、`流式回答到三十秒就断`

**级别** error · **框架** 通用 · **手册** [SSE 连接 30 秒断开](https://pgy763.github.io/llm-dev-pitfalls/java/SSE连接30秒断开)

```java
// 会报
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(String q) {
    SseEmitter emitter = new SseEmitter();
    ...
}
```

改法：`new SseEmitter(180_000L)`——按最长的一条回答给，别用默认值；真要无限就显式传 `0`，
并且配上 `onTimeout` 收尾。

为什么：无参构造用的是容器默认超时（Tomcat 是 30 秒）。模型还没生成完，连接先被掐了，
前端看到的是「回答到一半停住」，接口本身却没有任何错误。

---

<a id="llm005"></a>
## LLM005 流式方法里 `block()` / `collectList()`

**搜这些能找到这条：** `前端一直转圈，最后一次性全出来`、`Flux 里 block() 之后很慢`

**级别** warning · **框架** 通用 · **手册** [WebFlux 里 block 流式结果](https://pgy763.github.io/llm-dev-pitfalls/java/WebFlux里block流式结果)

```java
// 会报
public Flux<String> stream(String q) {
    List<String> all = chatModel.stream(new Prompt(q))
            .map(r -> r.getResult().getOutput().getText())
            .collectList()
            .block();
    return Flux.fromIterable(all);
}
```

改法：把 `Flux` 直接返回给框架，让它订阅；需要转换就在链上 `map`。非要在业务里处理完，
用 `SseEmitter` 自己推，而不是把流收干。

为什么：响应式流要在事件循环里跑。`block()` 把线程占住，Netty 的少量线程很快被耗尽；而且流
已经被订阅过，再 `block` 拿到的也不是你以为的完整结果。

判断范围：方法返回 `Flux` / `Mono` / `SseEmitter` / `StreamingResponseBody` / `ResponseBodyEmitter`，
方法体里出现 `block` / `blockFirst` / `blockLast` / `collectList` / `collectMap` / `collect`。

---

<a id="llm006"></a>
## LLM006 `produces` 少了 charset

**搜这些能找到这条：** `中文变成 ???`、`SSE 接口 200，只有汉字是坏的`

**级别** warning · **框架** 通用 · **手册** [SSE 响应中文乱码](https://pgy763.github.io/llm-dev-pitfalls/java/SSE响应中文乱码)

```java
// 会报
@GetMapping(value = "/chat", produces = "text/event-stream")
```

改法：`produces = "text/event-stream;charset=UTF-8"`，或
`MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"`。

为什么：不带 charset 时 Spring 按默认编码把 `String` 写出去，中文在浏览器里是问号。接口
返回 200，抓包看字节也对，只有前端那一行是坏的。

这条有现成补丁：`llm-lint --fix src` 会输出把 charset 补上的 unified diff，`git apply` 即可。

---

<a id="llm007"></a>
## LLM007 `maxTokens` 太小

**搜这些能找到这条：** `Unexpected end-of-input`、`maxTokens 太小，工具参数被截断`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
options.setMaxTokens(128);
```

改法：工具场景留 1024 以上；并且把「是否被截断」当成一种正常结果处理，而不是当解析 bug。

为什么：`maxTokens` 是上限，不是目标。模型想调工具时参数就是一段 JSON，预算不够就写到一半
被截断，客户端看到 `Unexpected end-of-input`，看着像 Jackson 的 bug，其实是预算不够。

判断范围：`maxTokens` / `setMaxTokens` / `maxCompletionTokens` 的参数是字面量且不超过 256。

---

<a id="llm008"></a>
## LLM008 空 catch 吞掉 `IOException`

**搜这些能找到这条：** `客户端断开之后 token 还在烧`、`写流时的 IOException 被 catch 吞了`

**级别** warning · **框架** 通用 · **手册** [客户端断开后还在写 SSE](https://pgy763.github.io/llm-dev-pitfalls/java/客户端断开后还在写SSE)

```java
// 会报
try {
    emitter.send(SseEmitter.event().data(chunk));
} catch (IOException e) {
    // 客户端断了？不管它
}
```

改法：捕获后 `emitter.complete()` 并 `return`——连接已经没了，继续写只是白烧 token。

为什么：空 catch 等于把「写失败」当成「写成功」。连接断了也看不出来，日志干干净净，用户那头
停在半句。等你去查时，最像出问题的地方（模型）恰恰是最没问题的。

判断范围：`catch` 的参数是 `IOException` / `Exception` / `Throwable`，catch 体为空，且 `try` 里
有 `write` / `send` / `flush` / `flushBuffer` / `writeAndFlush`。

---

<a id="llm009"></a>
## LLM009 `@Bean` 建的 HTTP 客户端没配超时

**搜这些能找到这条：** `上游挂了，自己的线程也挂住`、`HTTP 客户端没配 timeout`

**级别** warning · **框架** 通用 · **手册** [请求挂死：超时太长](https://pgy763.github.io/llm-dev-pitfalls/endpoint/请求挂死超时太长)

```java
// 会报
@Bean
public WebClient webClient() {
    return WebClient.builder().baseUrl("https://api.openai.com").build();
}
```

改法：`.clientConnector(new ReactorClientHttpConnector(HttpClient.create()
.responseTimeout(Duration.ofSeconds(30))))`，RestTemplate 用
`SimpleClientHttpRequestFactory` 设 `connectTimeout` / `readTimeout`。

为什么：没设超时的客户端默认等到天荒地老。对端不回，请求线程就一直挂着，最后是线程池先满——
看起来像「服务卡死」，其实是自己没给自己留后路。

判断范围：`@Bean` 方法返回 `WebClient` / `RestClient` / `RestTemplate` / `HttpClient` /
`OkHttpClient`，方法体里没有任何 `timeout` / `readTimeout` / `connectTimeout` / `callTimeout` /
`responseTimeout` 调用。

---

<a id="llm010"></a>
## LLM010 硬编码的 API key

**搜这些能找到这条：** `sk-...`、`AIza...`

**级别** error · **框架** 通用 · **手册** [密钥提交进了 git 历史](https://pgy763.github.io/llm-dev-pitfalls/auth/密钥提交进了git历史)

```java
// 会报
private static final String API_KEY = "sk-proj-AbCdEf0123456789";
```

改法：`System.getenv("LLM_API_KEY")` 或配置中心。已经提交过的，先吊销再换——删掉那一行不够。

为什么：密钥一旦进了 git，就永远在历史里。`git rm` 只删了最新一版，clone 和 fork 都还带着；
公开仓库上的爬虫几分钟内就会来扫。

判断范围：字符串字面量里出现 `sk-` / `sk-proj-` / `sk-ant-` / `AIza` / `hf_`，且不含 `${`
和 `***`（占位符不报）。

---

<a id="llm011"></a>
## LLM011 每个请求都新建 `ChatClient` / `ChatModel`

**搜这些能找到这条：** `连接池反复重建、端口耗尽`、`每个请求都 new 一个 ChatClient`

**级别** error · **框架** 通用 · **本页**

```java
// 会报
public String answer(String question) {
    ChatClient client = ChatClient.create(chatModel);
    return client.prompt().user(question).call().content();
}
```

改法：在 `@Bean` 方法里建一次，业务类注入复用。

为什么：`ChatClient` / `ChatModel` / `ChatLanguageModel` 内部带着连接池、重试器和序列化器。
每次请求现建，等于每个请求重开连接池、重做 TLS 握手；并发一高，线程和端口先于模型耗尽。

判断范围：非 `@Bean` 的方法里，以客户端类型名（`ChatClient`、`ChatModel`、`ChatLanguageModel`、
`EmbeddingModel` 等）为接收者的 `create(...)` 或 `builder()` 调用。`AiServices` 归 LLM012。

---

<a id="llm012"></a>
## LLM012 每个请求都新建 `AiServices`

**搜这些能找到这条：** `启动慢、内存涨`、`每个请求都重建 AiServices`

**级别** error · **框架** LangChain4j · **本页**

```java
// 会报
public Assistant assistant() {
    return AiServices.create(Assistant.class, model);
}
```

改法：在 `@Bean` 里建一个单例 `AiServices`，记忆按会话 id 从 `ChatMemoryProvider` 取。

为什么：`AiServices` 把 memory、retriever、tools 一次性组装好。每请求建一次，等于每请求
重新装配；更隐蔽的是它默认带的记忆会被所有请求共用，串话就从这里开始。

---

<a id="llm013"></a>
## LLM013 循环里逐个调模型或向量库

**搜这些能找到这条：** `N 条数据要 N 倍时间`、`循环里逐个调模型`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
for (String text : texts) {
    vectors.add(embeddingModel.embed(text));
}
```

改法：能批量的一次带走（`embedAll(texts)`、一次查多个 id）；实在要逐条的，至少并发发出去
再一起等。

为什么：模型和向量库的耗时几乎全在网络往返上。循环里一次一条，10 条就是 10 次 RTT，延迟
线性叠加，而批量接口往往一次就能带走。

判断范围：`call` / `chat` / `generate` / `generateStream` / `stream` / `similaritySearch` /
`search` / `embed` / `embedAll` / `embedForResponse` / `retrieve`，接收者是模型或向量库类型，
且调用出现在 `for` / `for-each` / `while` / `do` 里，或者 `forEach` / `map` 这类流操作符的
lambda 里。

---

<a id="llm014"></a>
## LLM014 `ChatMemoryProvider` 用了写死的 id

**搜这些能找到这条：** `所有人共用一份记忆`、`conversationId 写死`

**级别** error · **框架** LangChain4j · **手册** [多用户共用一份 ChatMemory](https://pgy763.github.io/llm-dev-pitfalls/java/多用户共用一份ChatMemory)

```java
// 会报
public ChatMemory memoryFor(String userId) {
    return memoryProvider.get("default");
}
```

改法：id 从请求里来（会话 id / userId），每个会话一条，用完按 TTL 清。

为什么：这条和 LLM002 是一对——LLM002 管的是字段级单例，这条管的是「看起来按会话取、其实
id 写死了」。代码形态上已经「隔离」了，实际还是全站一份。

---

<a id="llm015"></a>
## LLM015 事务方法返回 `Flux` / `Mono`

**搜这些能找到这条：** `@Transactional 里的 Flux 不回滚`、`rollback 不生效`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
@Transactional
public Flux<String> saveAll(List<String> rows) {
    rowRepository.saveAll(rows);
    return Flux.fromIterable(rows).map(String::toUpperCase);
}
```

改法：事务里只做同步的事，提交之后再拼流；或者把事务边界放到 `Flux` 的订阅里，用
`TransactionalOperator`。

为什么：`@Transactional` 绑定的是「方法返回时」这件事，而 `Flux` 返回时什么都没发生——
真正的订阅和数据库操作在方法外面、另一个线程上。事务注解看着生效了，回滚却没覆盖到。

---

<a id="llm016"></a>
## LLM016 `@Tool` 方法返回 `Mono` / `Flux`

**搜这些能找到这条：** `模型说「收到了 Mono@1a2b」`、`@Tool 方法返回 Mono`

**级别** error · **框架** 通用 · **本页**

```java
// 会报
@Tool(description = "查订单状态")
public Mono<String> orderStatus(String orderId) {
    return Mono.just("shipped");
}
```

改法：工具方法同步返回结果——要查库、要调 HTTP，就在方法里算完再 `return`，别把流交出去。

为什么：模型调用工具时，框架要的是「能直接塞回上下文的结果」。返回 `Mono` 时没人替你订阅，
轻则模型拿到 `Mono@1a2b` 这样的字符串，重则序列化失败、整轮对话 500。异步的价值在服务端并发，
不在这一个返回值上。

---

<a id="llm017"></a>
## LLM017 事务里查/写向量库

**搜这些能找到这条：** `事务里 vectorStore.add 之后连接池超时`、`RAG 写入拖垮连接池`

**级别** error · **框架** 通用 · **本页**

```java
// 会报
@Transactional
public void ingest(String title, String body) {
    vectorStore.add(List.of(new Document(body)));
}
```

改法：向量写入/检索挪到事务外面，事务只包住数据库那一段。

为什么：向量的写入和检索都要先算嵌入、再走一次网络。事务里等这几百毫秒，数据库连接一直被
这个线程占着，并发一上来先报「拿不到连接」。这条和 LLM001 是同一类问题，只是向量调用看起来
更无害，评审时更容易放过。

---

<a id="llm018"></a>
## LLM018 流式接口没声明 `produces`

**搜这些能找到这条：** `流式接口没写 produces`、`前端等很久，最后一次性全出来`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
@GetMapping("/tickets/stream")
public Flux<String> stream() {
    return Flux.just("a", "b");
}
```

改法：`@GetMapping(value = "/tickets/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)`。

为什么：返回 `Flux` 却没写 `produces`，Spring 会按 Jackson 的 JSON 转换器走：先把整个流收完，
再一次性写出去；中间的代理也更容易把响应攒着。客户端看到的还是「等很久，然后全出来」——
流式白开了，还多占了一路连接。

这条有现成补丁：`llm-lint --fix src` 会输出加上 `produces` 的 unified diff（字符串字面量，
不用补 import），`git apply` 即可。

---

<a id="llm019"></a>
## LLM019 `@Tool` 方法里做阻塞调用

**搜这些能找到这条：** `工具调用很慢`、`@Tool 里查数据库`、`模型一直在等工具`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
@Tool("查订单现在到哪了")
public String orderStatus(String orderId) {
    return orderRepository.findById(orderId).status();
}
```

改法：查库、调同步接口、`Thread.sleep` 这些挪到调用工具之前，或者让工具只读已经准备好的数据；
确实要慢活的，让工具先返回一句「正在处理」，把活交给后台。

为什么：工具调用跑在请求线程上。模型等工具、工具等数据库，整轮响应就被这一条查询拖住了；
排查时看到的却常常是「模型变慢」。

判断范围：方法带 `@Tool`，方法体里出现 `.block()` / `.blockFirst()` / `.blockLast()` /
`.join()`、`Thread.sleep(`、Future 上的 `.get()`，或者调用接收者类型名以 `Repository` /
`Mapper` / `JdbcTemplate` / `RestTemplate` / `WebClient` 结尾。只读 `Map`、缓存、静态工具类的
调用不算。

---

<a id="llm020"></a>
## LLM020 事务方法里等异步结果

**搜这些能找到这条：** `事务里 block`、`@Transactional 里 sleep`、`拿不到连接`、`Connection is not available`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
@Transactional
public void refreshAll(String orderId) {
    Flux.just(orderId).map(String::trim).blockLast();
}
```

改法：把等待挪到事务外面——先 `block` 拿到结果，再开事务只写库；或者把方法拆成两个，
事务只包住真正写库的那几行。

为什么：事务的边界是方法，不是那段异步代码。方法里 block 一下、睡一会儿，数据库连接就从方法开始
一直攥到方法结束；等的是模型还是别的线程并不重要，池子一样会被占满。这条和 LLM001 是同一个坑的
两半：LLM001 是等模型，这条是等任何东西。

判断范围：方法（或所在类）带 `@Transactional`，方法体里出现 `.block()` / `.blockFirst()` /
`.blockLast()`、Future 上的 `.get()`、`Thread.sleep(`。内嵌类里的方法不看；`Thread.sleep` 可能是
限流退避，所以级别是 warning 不是 error。

<a id="llm021"></a>
## LLM021 事务方法返回长连接

**搜这些能找到这条：** `流式接口连接池被占满`、`事务里 stream`、`@Transactional 返回 SseEmitter`

**级别** error · **框架** 通用 · **本页**

```java
// 会报
@Transactional
public SseEmitter stream(String prompt) {
    SseEmitter emitter = new SseEmitter(180_000L);
    emitter.send(SseEmitter.event().data(prompt));
    return emitter;
}
```

改法：事务里只做写库那几行，把 `SseEmitter` 的创建和发送挪到事务外面；要写「开始了 / 结束了」这类
记录，用一个单独的事务方法去写。

为什么：返回 `SseEmitter` 的方法，事务要等整个流结束才提交——生成多久，连接就攥多久。一个长回答几十秒，
几个并发就能把连接池占满；现象是「流式接口一多就卡」，根子却是事务一直没结束。LLM015 管的是返回
`Flux` / `Mono` 的那一半，这条补上 SSE。

判断范围：方法返回类型简单名是 `SseEmitter` 或 `ResponseBodyEmitter`，且方法（或所在类）带
`@Transactional`。

<a id="llm022"></a>
## LLM022 定时任务里直接调模型

**搜这些能找到这条：** `@Scheduled 里调模型`、`定时任务上下文`、`会话串台`

**级别** warning · **框架** 通用 · **本页**

```java
// 会报
@Scheduled(cron = "0 0 8 * * *")
public void dailyDigest() {
    String text = chatClient.prompt("总结一下今天的工单").call().content();
}
```

改法：定时任务只决定「要处理什么」——把任务写进队列、表或者事件，模型调用交给有请求上下文的路径；
如果确实要在后台跑，就显式带上会话 id，别依赖「当前用户」。

为什么：`@Scheduled` / `@Async` 里没有请求，也就没有会话边界。用 `ChatMemory` 时要么拿到别人的会话，
要么每次都新建一份，上下文永远对不上；出了事也没有请求日志可查。LLM002 讲的是 Bean 级共享，
这条讲的是「根本没有请求边界」。

判断范围：方法带 `@Scheduled` 或 `@Async`，方法体里出现模型调用（接收者类型名以 `ChatClient` /
`ChatModel` / `EmbeddingModel` / `ChatLanguageModel` / `StreamingChatModel` 结尾）。只调 Repository
或发事件的不算。

## 抑制

单行或上一行写注释即可：

```java
// llm-lint:ignore LLM004
SseEmitter emitter = new SseEmitter();
```

- `// llm-lint:ignore` —— 压掉该行的全部问题
- `// llm-lint:ignore LLM004,LLM009` —— 只压这几条
- `// llm-lint:ignore-file` —— 整个文件不报（放在文件任意位置）

被压掉的问题会计进统计行（`抑制 N 条`），不会让数字凭空消失。

## 这些规则不做什么

- 不做符号解析，不读依赖的 jar：类型靠**声明处的简单类名后缀**判断。`var`、链式返回、
  方法返回值直接接下去的调用，一律认不出，也就一律不报。
- 不做跨文件推断：`@Bean` 在 A 文件、注入在 B 文件，各自独立看。
- 不检查配置（`application.yml`）、SQL、前端代码，也不联网探测。
- 不判断「业务上该不该在事务里」——只看模式，看法在文档里，判断权在你。