---
title: 二十二条规则
description: 每条规则会报什么、怎么改、判断范围到哪、为什么这么判——按规则号查。
---

# 规则

二十二条规则，每条都对应一个「上线之后才发现」的具体故障。规则是纯静态的：不需要联网、不需要
API key、不需要项目的 classpath——它只读 `.java` 源码，靠类型名的后缀判断这是不是模型调用。

> 这套判断方式有意保守：**宁可漏报，不可乱报**。认不出来的（链式调用、跨文件推断、反射、配置类里拼出来的 Bean）一律不报。所以「0 个问题」只代表没命中这二十二条模式，不代表代码没问题。

表里的级别是**默认值**。某条规则在你项目里不合适——误报，或者你们的要求比它更严——写进仓库根的
`.llm-lint.yml` 就行，不用改工具、也不用每次在 CI 里手写参数：见 [config.md](config.md)。

这张表也能自己生成：`llm-lint --list-rules --format markdown`（级别、框架跟着配置走，测试会校验它和代码一致）。

每一条的详情在单独一页（下面有索引）。页面顶上的表、以及这一页的表格和索引，都由 `llm-lint --docs docs/rules` 生成——级别、框架、有没有补丁、文档链接都从代码来，重跑只换这一段；下面「为什么会踩」那部分是手写的，不会被覆盖。所以在终端里看和站上看是同一份事实，不会出现「文档说 warning、工具有一天改成 error」的错位。英文版同样每条一页：[English pages](https://pgy763.github.io/llm-lint/en/rules.html)，由 `llm-lint --docs-en docs/en/rules` 生成。

只想在本地看：`llm-lint --explain LLM007 --format markdown`，整本用 `--explain all --format markdown`。

<!-- generated:rules-index -->
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
### [LLM001 @Transactional 里调模型](https://pgy763.github.io/llm-lint/rules/llm001.html)

error · 通用 —— `@Transactional` 里调模型，占着数据库连接等几秒

<a id="llm002"></a>
### [LLM002 单例里共用一份 ChatMemory](https://pgy763.github.io/llm-lint/rules/llm002.html)

error · 通用 —— 单例 Bean 里一份 `ChatMemory`，所有人共用

<a id="llm003"></a>
### [LLM003 让容器注入 ChatClient](https://pgy763.github.io/llm-lint/rules/llm003.html)

error · Spring AI —— 让容器注入 `ChatClient`（它没有这个 Bean）

<a id="llm004"></a>
### [LLM004 SseEmitter 没设超时](https://pgy763.github.io/llm-lint/rules/llm004.html)

error · 通用 —— `new SseEmitter()` 用容器默认超时，30 秒断流

<a id="llm005"></a>
### [LLM005 流式方法里 block/collect](https://pgy763.github.io/llm-lint/rules/llm005.html)

warning · 通用 —— 流式方法里 `block()`，把事件循环占住

<a id="llm006"></a>
### [LLM006 produces 少了 charset](https://pgy763.github.io/llm-lint/rules/llm006.html)

warning · 通用 —— `produces = "text/event-stream"` 少了 charset

<a id="llm007"></a>
### [LLM007 maxTokens 太小](https://pgy763.github.io/llm-lint/rules/llm007.html)

warning · 通用 —— `maxTokens` 太小，工具参数被截断

<a id="llm008"></a>
### [LLM008 空的 catch 吞掉 IOException](https://pgy763.github.io/llm-lint/rules/llm008.html)

warning · 通用 —— 空 catch 吞掉写 SSE 时的 `IOException`

<a id="llm009"></a>
### [LLM009 HTTP 客户端没配超时](https://pgy763.github.io/llm-lint/rules/llm009.html)

warning · 通用 —— `@Bean` 建的 HTTP 客户端没配超时

<a id="llm010"></a>
### [LLM010 硬编码的 API key](https://pgy763.github.io/llm-lint/rules/llm010.html)

error · 通用 —— 硬编码的 `sk-…` / `AIza…` 密钥

<a id="llm011"></a>
### [LLM011 每次请求都新建 ChatClient/ChatModel](https://pgy763.github.io/llm-lint/rules/llm011.html)

error · 通用 —— 每个请求现建 `ChatClient` / `ChatModel`

<a id="llm012"></a>
### [LLM012 每次请求都新建 AiServices](https://pgy763.github.io/llm-lint/rules/llm012.html)

error · LangChain4j —— 每个请求现建 `AiServices`

<a id="llm013"></a>
### [LLM013 循环里逐个调模型/向量库](https://pgy763.github.io/llm-lint/rules/llm013.html)

warning · 通用 —— 循环里逐个调模型或向量库

<a id="llm014"></a>
### [LLM014 ChatMemoryProvider 用了写死的 id](https://pgy763.github.io/llm-lint/rules/llm014.html)

error · LangChain4j —— `ChatMemoryProvider` 用了写死的 id

<a id="llm015"></a>
### [LLM015 事务方法返回 Flux/Mono](https://pgy763.github.io/llm-lint/rules/llm015.html)

warning · 通用 —— 事务方法返回 `Flux` / `Mono`

<a id="llm016"></a>
### [LLM016 工具方法返回响应式类型](https://pgy763.github.io/llm-lint/rules/llm016.html)

error · 通用 —— `@Tool` 方法返回 `Mono` / `Flux`

<a id="llm017"></a>
### [LLM017 事务里查/写向量库](https://pgy763.github.io/llm-lint/rules/llm017.html)

error · 通用 —— 事务里查 / 写向量库

<a id="llm018"></a>
### [LLM018 流式接口没声明 produces](https://pgy763.github.io/llm-lint/rules/llm018.html)

warning · 通用 —— 流式接口没声明 `produces`

<a id="llm019"></a>
### [LLM019 工具方法里做阻塞调用](https://pgy763.github.io/llm-lint/rules/llm019.html)

warning · 通用 —— `@Tool` 方法里查库 / 调同步接口 / `Thread.sleep`

<a id="llm020"></a>
### [LLM020 事务方法里等异步结果](https://pgy763.github.io/llm-lint/rules/llm020.html)

warning · 通用 —— `@Transactional` 方法里 `.block()` / `Future.get()` / `Thread.sleep`

<a id="llm021"></a>
### [LLM021 事务方法返回长连接](https://pgy763.github.io/llm-lint/rules/llm021.html)

error · 通用 —— `@Transactional` 方法返回 `SseEmitter` / `ResponseBodyEmitter`

<a id="llm022"></a>
### [LLM022 定时任务里直接调模型](https://pgy763.github.io/llm-lint/rules/llm022.html)

warning · 通用 —— `@Scheduled` / `@Async` 方法里直接调模型

<!-- /generated:rules-index -->

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

- 不做符号解析，不读依赖的 jar：类型靠**声明处的简单类名后缀**判断。`var`、链式返回、方法返回值直接接下去的调用，一律认不出，也就一律不报。
- 不做跨文件推断：`@Bean` 在 A 文件、注入在 B 文件，各自独立看。
- 不检查配置（`application.yml`）、SQL、前端代码，也不联网探测。
- 不判断「业务上该不该在事务里」——只看模式，看法在文档里，判断权在你。
