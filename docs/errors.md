# 按报错找规则

这一页是给「线上已经出事了，正在搜」的人准备的。左边是你搜到的那句话，右边是大概率的原因和负责拦住它的规则。

| 你看到的现象 / 报错 | 大概率是什么 | 规则 |
| --- | --- | --- |
| `NoSuchBeanDefinitionException: No qualifying bean of type 'ChatClient'` | 容器里没有 `ChatClient` 这个 Bean，自动配置只注册 `ChatClient.Builder` | [LLM003](rules.md#llm003) |
| `HikariPool-1 - Connection is not available, request timed out` | 事务里调了模型，数据库连接被占着等模型 | [LLM001](rules.md#llm001)、[LLM017](rules.md#llm017) |
| 流式回答到一半停住，`AsyncRequestTimeoutException` | `new SseEmitter()` 用了容器默认超时（Tomcat 30 秒） | [LLM004](rules.md#llm004) |
| B 的对话里出现 A 的名字 / 地址 | 单例里放了一份 `ChatMemory`，或者 id 写死 | [LLM002](rules.md#llm002)、[LLM014](rules.md#llm014) |
| `Unexpected end-of-input` / 工具参数解析失败 | `maxTokens` 太小，模型写到一半被截断 | [LLM007](rules.md#llm007) |
| 中文变成 `???`，接口返回 200 | `produces` 没带 `charset=UTF-8` | [LLM006](rules.md#llm006) |
| 前端一直转圈，最后一次性全出来 | 流式方法里 `block()`，或者没写 `produces` | [LLM005](rules.md#llm005)、[LLM018](rules.md#llm018) |
| 线程/端口耗尽，模型却说不慢 | 每个请求现建客户端，连接池反复重建 | [LLM011](rules.md#llm011)、[LLM012](rules.md#llm012) |
| 代码里出现 `sk-…`、`AIza…` | 密钥硬编码，可能已经进了 git 历史 | [LLM010](rules.md#llm010) |
| `Flux`/`Mono` 方法上的事务不回滚 | 事务绑定的是方法返回，不是流被订阅 | [LLM015](rules.md#llm015) |
| 客户端断开后 token 还在烧 | 空 `catch` 吞掉了写流的 `IOException` | [LLM008](rules.md#llm008) |
| 上游挂了，自己线程也挂住 | `@Bean` 造的 HTTP 客户端没配超时 | [LLM009](rules.md#llm009) |
| N 条数据要 N 倍时间 | 循环里逐个调模型 / 向量库 | [LLM013](rules.md#llm013) |
| 模型说「收到了 Mono@1a2b」 | `@Tool` 方法返回了 `Mono`/`Flux` | [LLM016](rules.md#llm016) |

下面几条是搜得最多的，展开说。

## `NoSuchBeanDefinitionException: … ChatClient`

Spring AI 的自动配置只注册 `ChatClient.Builder` 和 `ChatModel`，**没有 `ChatClient` 这种 Bean**。写 `@Autowired private ChatClient chatClient;` 时，错误信息只说找不到 Bean，而名字看起来完全合理，所以很多人会在这卡很久。

```java
// 会报：启动就挂
@Autowired
private ChatClient chatClient;

// 改法一：注入 Builder，构造里 build
private final ChatClient chatClient;

public SupportAssistant(ChatClient.Builder builder) {
    this.chatClient = builder.build();
}

// 改法二：自己定义 Bean
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
}
```

对应 [LLM003](rules.md#llm003)。

## `HikariPool … Connection is not available`

连接池只有几十个连接。事务方法的整个执行期间，当前线程都攥着一个连接；模型这次调用要几秒到几十秒，这段时间连接就一直没还回去。并发一上来，报的是「拿不到连接」，而不是「模型慢」——所以排查方向经常被带偏到数据库上。

先看事务方法里有没有这些东西：

- `chatModel.call(...)` / `chatClient.prompt()...call()` / `embeddingModel.embed(...)`
- `vectorStore.add(...)` / `vectorStore.similaritySearch(...)`

有的话，把模型和向量调用挪到事务外面，或者用编程式事务把范围缩到写库那几行。对应 [LLM001](rules.md#llm001)、[LLM017](rules.md#llm017)。

## `Unexpected end-of-input`

看着像 Jackson 的 bug，其实是预算不够：模型想调工具时，参数就是一段 JSON；`maxTokens` 是**硬上限**，写到一半就停了，客户端拿到半截 JSON 自然报 `Unexpected end-of-input`。

工具场景别低于 1024；而且要把「被截断」当成一种正常结果处理，而不是解析异常。对应 [LLM007](rules.md#llm007)。

## SSE 三十秒 / 回答到一半停住

`new SseEmitter()` 用的是容器默认超时，Tomcat 是 30 秒。模型还没生成完，连接先被掐了，接口本身没有任何错误日志。

```java
// 按最长的一条回答给，别用默认值
SseEmitter emitter = new SseEmitter(180_000L);
```

对应 [LLM004](rules.md#llm004)。

## 中文变成问号

`produces = "text/event-stream"` 不带 charset 时，Spring 按默认编码把 `String` 写出去。接口 200、抓包字节也对，只有前端那一行是坏的。

```java
// 改法
@GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
```

这条有现成补丁：[`--fix`](rules.md#llm006) 会直接给出 unified diff。

## 两条命令定位

```bash
# 这个文件里到底命中了什么
java -jar llm-lint.jar --format text src/main/java/demo/SupportController.java

# 这条规则为什么报、判断范围到哪
java -jar llm-lint.jar --explain LLM004
```

判断范围在 [规则手册](rules.md) 每节末尾都写了——先确认自己的代码是否落在范围内，再看要不要修。