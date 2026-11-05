---
title: llm-lint —— Spring AI / LangChain4j 的静态检查
description: 20 条规则，拦住事务里调模型、共享 ChatMemory、SSE 30 秒断流、maxTokens 截断、密钥硬编码这些上线才会发现的坑。不联网、不要 API key、不要 classpath。
---

# llm-lint

扫一遍 Spring AI / LangChain4j 的 Java 代码，在 commit 之前拦住那些人到线上才会发现的问题：事务里调模型、单例里放 `ChatMemory`、SSE 三十秒断流、`maxTokens` 把工具参数截断、密钥硬编码。

纯静态检查：不联网、不要 API key、不要项目的 classpath，只读 `.java` 源码。

```bash
# 单文件就能跑
java -jar llm-lint.jar src/main/java

# 或者走 GitHub Action
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
```

## 二十条规则

每条规则都对应一个具体故障：什么代码会出事、怎么改、为什么。完整说明和例子在 [规则手册](rules.md)。

| 编号 | 默认级别 | 拦的是什么 |
| --- | --- | --- |
| LLM001 | error | `@Transactional` 里调模型，占着数据库连接等几秒 |
| LLM002 | error | 单例 Bean 里一份 `ChatMemory`，所有人共用 |
| LLM003 | error | 让容器注入 `ChatClient`（它没有这个 Bean） |
| LLM004 | error | `new SseEmitter()` 用容器默认超时，30 秒断流 |
| LLM006 | warning | `produces = "text/event-stream"` 少了 charset |
| LLM007 | warning | `maxTokens` 太小，工具参数被截断 |
| LLM009 | warning | `@Bean` 建的 HTTP 客户端没配超时 |
| LLM010 | error | 硬编码的 `sk-…` / `AIza…` 密钥 |
| LLM016 | error | `@Tool` 方法返回 `Mono` / `Flux` |
| LLM018 | warning | 流式接口没声明 `produces` |
| LLM019 | warning | `@Tool` 方法里查库、调同步接口或 `Thread.sleep` |
| LLM020 | warning | `@Transactional` 方法里 `.block()` / `Thread.sleep` |

其余八条（LLM005、LLM008、LLM011–LLM015、LLM017）见[完整列表](rules.md)。

## 把规则固定在仓库里

在仓库根放一份 `.llm-lint.yml`，规则开关、排除路径、级别就跟着代码走，CI 里不用再手写参数：

```yaml
version: 1
rules:
  LLM004: off          # 网关统一配了超时
  LLM007: error        # 团队要求工具预算至少 512
exclude:
  - "**/generated/**"
```

跑 `llm-lint --init` 会写一份带注释的模板，`--print-config` 能看到合并默认值之后实际生效的配置。细节见[配置](config.md)。

## 存量项目

第一次接进来必然会报一批旧问题。把它们记进基线，CI 就只拦这次新犯的：

```bash
llm-lint --save-baseline .llm-lint.baseline --fail-on never   # 存一次
llm-lint --baseline .llm-lint.baseline                        # 之后只看新问题
```

`--baseline-stats` 会告诉你旧账还剩多少、哪几条已经修好可以删。见[基线](baseline.md)。

## 接进 CI

```yaml
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
```

有 error 就退出 1，同时输出 GitHub 注解、SARIF、JUnit、HTML 或 PR 评论。完整用法、所有选项和退出码在 [README](https://github.com/pgy763/llm-lint#readme)（[English](https://github.com/pgy763/llm-lint/blob/main/README.en.md)）。

## 文档

**上手**

- [装上，跑一次](start.md) —— 三种装法、退出码、六种输出格式、常用参数
- [接到各个平台上](integrations.md) —— GitHub Actions、GitLab、Jenkins、pre-commit、Maven

**按问题找**

- [按报错找规则](errors.md) —— 拿着 `NoSuchBeanDefinitionException`、`HikariPool … timed out` 这类原文来对照
- [常见问题](faq.md) —— 为什么不报、误报怎么办、和 SpotBugs 的区别、会不会联网

**用起来**

- [规则手册](rules.md) —— 二十条规则，会报的样子、改法、判断范围、为什么
- [配置](config.md) —— `.llm-lint.yml` 的六个键、发现顺序、优先级
- [基线](baseline.md) —— 存量项目怎么只拦新问题
- [版本历史](changelog.md) —— 每个版本改了什么、哪些变化可能影响你

**参与**

- [参与进来](contributing.md) —— 十分钟能做的事、加一条规则的六步、现在想要的三条
- [路线图](https://github.com/pgy763/llm-lint/blob/main/ROADMAP.md) —— 待做的规则与工程活，标了难度
- [安全问题](https://github.com/pgy763/llm-lint/blob/main/SECURITY.md) —— 走私密渠道，别开公开 issue

**维护者**

- [版本历史](changelog.md) —— 每个版本改了什么
- 推广清单留在仓库里：[docs/promote.md](https://github.com/pgy763/llm-lint/blob/main/docs/promote.md)

源码、releases 和 Marketplace 页面都在 [github.com/pgy763/llm-lint](https://github.com/pgy763/llm-lint)。