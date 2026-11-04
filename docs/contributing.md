---
title: 参与进来 —— 提一条规则、报一个误报
description: llm-lint 怎么参与：报误报、报漏报、加一条规则、做工程活。加一条规则大约一个下午，四条待做的规则都写好了怎么认和样例。
---

# 参与进来

这个项目是一个人晚上写的，所以「多一条能静态查出来的规则」比「多一个功能」有用得多。下面按花的力气从小到大排。

## 十分钟就够

- **撞上误报**：把那段代码贴进 [误报模板](https://github.com/pgy763/llm-lint/issues/new?template=false-positive.yml)，说明为什么它其实没问题。误报比漏报更伤——一条误报就会让人把这个工具从 CI 里删掉。
- **它没报出来**：同上，用同一个模板选「漏报」，贴触发时的代码和日志。
- **只是想说声好用**：开个 [issue](https://github.com/pgy763/llm-lint/issues/new) 说一句，或者点个 star。

## 加一条规则（大约一个下午）

流程在 [CONTRIBUTING 的加规则清单](https://github.com/pgy763/llm-lint/blob/main/CONTRIBUTING.md#加一条规则) 里，六步：写 `Rule0NN` → 注册进 `Rules.all()` → `samples/bad` 与 `samples/good` 各一个文件 → `docs/rules.md` 加一节 → 跟着数字走的表（两份 README、规则表、changelog、`action.yml`、测试里的条数）→ 本地 `mvn -B -ntp verify`。

现在最想要的四条，判断口径和样例都写在 [ROADMAP](https://github.com/pgy763/llm-lint/blob/main/ROADMAP.md) 里：

| 规则 | 管什么 | 级别 | 难度 |
| --- | --- | --- | --- |
| LLM019 | `@Tool` 方法里做阻塞调用（查库、同步 HTTP、`Thread.sleep`） | warning | good first issue |
| LLM020 | `@Transactional` 方法里等异步结果（`.block()`、`Future.get()`） | warning | good first issue |
| LLM021 | 返回 `SseEmitter` / `ResponseBodyEmitter` 的方法带 `@Transactional` | error | 中等 |
| LLM022 | `@Scheduled` / `@Async` 方法里直接调模型 | warning | 中等 |

写之前先开个 issue 认领，避免两个人同时写一条。

## 工程活

- **Docker 镜像**：多阶段构建，`maven:3.9-eclipse-temurin-17` 打 jar、`eclipse-temurin:17-jre` 作运行层，推到 GHCR。给只用 Docker 的 CI 用。
- **`--list-rules --format markdown`**：把规则表直接生成 Markdown，顺手让 `docs/rules.md` 的表格由它校验，省得每次加规则都要手改五张表。
- **`--files-from <file|->`**：从文件或 stdin 读要扫的路径，和 `--since`、位置参数取交集。给「只扫这次改动」的自定义 CI 用。
- **规则文档自动校验**：锚点、样例文件、条数三类，缺一个就让 CI 红。

## 审 PR 看三条

1. `samples/bad` 里那个反例，真跑起来会不会坏？
2. `samples/good` 里的正例会不会被误伤？
3. 报出来的消息，能不能让没读过这条规则的人知道怎么改？

三条都过就合。做不到第 3 条的，宁可先不要。

## 不必费劲的方向

不做符号解析、不要 classpath、不联网、不自动改代码、不为凑数加规则——这些是设计选择，不是没做完。原因写在 [常见问题](faq.md) 里。

安全问题请走私密渠道，见 [SECURITY](https://github.com/pgy763/llm-lint/blob/main/SECURITY.md)，别开公开 issue。

行为准则很短：对代码严格，对人客气。见 [CODE_OF_CONDUCT](https://github.com/pgy763/llm-lint/blob/main/CODE_OF_CONDUCT.md)。