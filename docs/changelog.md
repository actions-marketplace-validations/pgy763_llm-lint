---
title: 版本历史
description: 每个版本改了什么，哪些变化可能影响已经接入的仓库。
---

# 版本历史

每个版本都有对应的 [release](https://github.com/pgy763/llm-lint/releases)，jar 在 `releases/latest/download/llm-lint.jar`。0.x 期间小版本可能带行为变化，接进 CI 的仓库建议看一眼下面的「可能影响你」。

| 版本 | 主要变化 |
| --- | --- |
| 0.27.0 | `overrides`：同一套规则，在某些路径下换级别（生成代码松、主代码严） |
| 0.26.0 | 站点加搜索：`--docs` 顺带生成 `search.json`，`/search.html` 用它过滤二十二条规则 |
| 0.25.1 | 文档同步：贡献页的「工程活」清掉已做的项，测试数量跟着 229 走 |
| 0.25.0 | 每条规则有英文页（`docs/en/rules/`，`--docs-en` 生成），英文索引也由工具维护 |
| 0.24.0 | `--docs` 连索引页 `docs/rules.md` 的表格和链接一起生成；规则多了 `summary()` 这一列 |
| 0.23.0 | 每条规则一页（`docs/rules/llm001.md` …）：页面顶上的表由 `--docs` 生成，手写部分重跑不会丢 |
| 0.22.0 | `--explain` 支持 `--format markdown` 与 `all`；CI 里加一步「自己过自己的规则」 |
| 0.21.0 | `--only` 只跑指定规则；`--fix --write` 把补丁直接写回文件 |
| 0.20.0 | Docker 镜像出 `linux/arm64`：打 tag 时构建两个架构，Amd64 与 Apple Silicon 都跑原生镜像 |
| 0.19.0 | `--list-rules --format markdown`：规则表能直接生成；LLM003 的框架标成 Spring AI；测试锁住表和代码一致 |
| 0.18.0 | Docker 镜像 `ghcr.io/pgy763/llm-lint`：多阶段构建，最终层只有 JRE 和 jar |
| 0.17.0 | `--files-from <file|->`：只扫清单里列的文件，和路径、`--since` 取交集 |
| 0.16.0 | 新规则 LLM021（事务方法返回长连接）、LLM022（定时任务里调模型）；规则总数 20 → 22 |
| 0.15.0 | 新规则 LLM020（`@Transactional` 里等异步结果）；规则总数 19 → 20 |
| 0.14.0 | 新规则 LLM019（`@Tool` 方法里做阻塞调用）；规则总数 18 → 19 |
| 0.13.1 | 文档站加「参与进来」一页，把提规则、报误报的入口放到站上 |
| 0.13.0 | 贡献者入口：CONTRIBUTING、ROADMAP（四条备选规则）、issue / PR 模板、SECURITY |
| 0.12.0 | SARIF 带 `partialFingerprints`，Code Scanning 里同一条问题不会每次都算新告警 |
| 0.11.0 | `--format junit`（Jenkins/GitLab）、`--format html`、`--init-hook` 写 pre-commit |
| 0.10.0 | `--lang en` 英文输出；`--list-rules --format json` 机器可读的规则目录 |
| 0.9.1 | `--fix` 输出 unified diff（LLM006、LLM018） |
| 0.9.0 | 三条新规则 LLM016（`@Tool` 返回响应式）、LLM017（事务里查写向量库）、LLM018（流式缺 `produces`） |
| 0.8.0 | PR 里留一条原地更新的汇总评论；`--markdown` 出明细 |
| 0.7.1 | `--since` 和路径一起给时，只报路径下的改动 |
| 0.7.0 | `--since <ref>` 只扫这次改动的文件 |
| 0.6.1 | 报错原文对照表、`llms.txt`、Marketplace 描述英文化 |
| 0.6.0 | `--summary` 摘要文件、`--init-ci`、`exclude` 支持相对仓库根写法 |
| 0.5.0 | 文件并行扫描，`--jobs` 控制线程数 |
| 0.4.2 | `--baseline-stats` 的「按规则」只统计还在报的 |
| 0.4.1 | `--baseline-stats` 看历史债；release 正文自动生成 |
| 0.4.0 | 存量项目基线：`--baseline` / `--save-baseline`、配置里的 `baseline:` |
| 0.3.0 | 按项目配置：`.llm-lint.yml`、`--init` / `--print-config` / `--config` / `--no-config` |
| 0.2.0 | 十五条规则、SARIF/JSON 输出、GitHub Action |
| 0.1.0 | 第一个版本 |

## 可能影响你

- **0.9.0**：项目从「十五条规则」变成十八条。如果你在 CI 里写死了条数（`grep -c`、JSON 长度断言），要一起改。
- **0.6.0**：`exclude` 的通配现在会拿完整路径的每一段后缀去试，`src/generated/**` 这种写法在子目录里扫也生效。之前只写 `**/generated/**` 才管用的项目，排除范围可能变大。
- **0.4.0**：第一次引入基线时，`--baseline` 指向不存在的文件会**退出 2**，而不是当空基线。这是故意的——CI 突然全红比报错难查得多。

## 规则级别的变化

默认级别属于工具判断，可能随版本调整；配置里**没写**的规则会跟着走，写了的保持你定的。所以配置文件只写「关掉」和「改级别」两类差异，别把默认值抄进去。

当前二十二条规则的默认级别见[规则手册](rules.md)。