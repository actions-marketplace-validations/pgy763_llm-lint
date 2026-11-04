# 版本历史

每个版本都有对应的 [release](https://github.com/pgy763/llm-lint/releases)，jar 在 `releases/latest/download/llm-lint.jar`。0.x 期间小版本可能带行为变化，接进 CI 的仓库建议看一眼下面的「可能影响你」。

| 版本 | 主要变化 |
| --- | --- |
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

当前十八条规则的默认级别见[规则手册](rules.md)。