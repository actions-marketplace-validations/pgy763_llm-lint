---
title: 存量项目：只拦新问题
description: 用基线把已经存在的问题记进仓库，让 CI 只拦这次新犯的。
---

# 存量项目：把旧问题记下来，只拦新的

新项目从第一天就干净；**存量项目一接工具，先蹦出几百条**。这时候让整个团队停下来清历史债
不现实，关掉规则又等于没装。基线（baseline）解决的就是这个：把「现在已经存在的」问题
记一份清单提交进仓库，之后只报清单之外的。

```console
$ llm-lint --save-baseline .llm-lint.baseline src/main/java
llm-lint: 基线 .llm-lint.baseline（共 312 条，这次新增 312 条）
llm-lint: 312 个问题（48 error / 264 warning），扫了 187 个文件

$ llm-lint --baseline .llm-lint.baseline src/main/java
llm-lint: 基线 .llm-lint.baseline（312 条）
llm-lint: 0 个问题（0 error / 0 warning），扫了 187 个文件，另有 312 条在基线里
```

把 `.llm-lint.baseline` 提交进仓库，CI 里就能直接用。**第一次会红，第二次开始绿**，
新写的代码再犯同样的错照样拦。

## 一分钟

1. 本地跑一次，把现状存下来（`--fail-on never` 只是让这次命令别失败）：

   ```bash
   java -jar llm-lint.jar --save-baseline .llm-lint.baseline \
       --fail-on never src/main/java
   ```

2. 在 `.llm-lint.yml` 里写一行，让以后不用再传 `--baseline`：

   ```yaml
   version: 1
   baseline: .llm-lint.baseline
   ```

3. 把两个文件一起提交。之后本地和 CI 都只报新问题。

## 命令

| 选项 | 做什么 |
| --- | --- |
| `--save-baseline <file>` | 把这次扫出来的问题**并进**清单；已有的条目一条不删 |
| `--baseline <file>` | 读清单，清单里的不再报、也不再算进退出码 |

- 两个可以一起用：`--baseline` 决定「这次报什么」，`--save-baseline` 决定「写回什么」。
- 只给 `--save-baseline`、文件已存在时，也会先读它再合并，所以重跑一次不会把「这次新增」
  从 0 又数成全部。
- `--baseline` 指向的文件不存在 → **退出 2 报错**，不会静默当成空基线。否则某次路径写错，
  CI 突然几百条全红，最难查。
- 命令行优先于配置：传了 `--baseline` 就用它，没传才看 `.llm-lint.yml` 的 `baseline`。
  配置里的相对路径按**配置文件所在目录**算。

## 匹配规则：规则 + 文件 + 消息

一行清单长这样（`<TAB>` 分隔，用编辑器看是三个字段）：

```
LLM007	src/main/java/demo/SupportAssistant.java	`maxTokens(128)` 只留了 128 个 token。…
```

匹配时**不看行号和列号**，因为：

- 上面插几行 import、下面加个方法，旧问题不该「复活」——那是纯噪音；
- 反过来，消息变了就算新问题：`maxTokens(128)` 改成 `maxTokens(256)` 是改过了，
  该重新看一眼；`maxTokens(1024)` 则是真修好了。

文件改名算新问题（旧条目留着也无害，下次 `--save-baseline` 也不会自动删）。
想看哪些条目已经没人报了，`--save-baseline` 之后看 diff：删掉行对应的旧条目可以顺手清掉，
**清单只会因为你的编辑而变短**，工具不会偷偷删。

## 还剩多少旧账

清单交上去只是开始，之后要能看出「债还得怎么样了」。加 `--baseline-stats` 就会按规则列出来：

```console
$ llm-lint --baseline .llm-lint.baseline --baseline-stats src/main/java
llm-lint: 基线 .llm-lint.baseline：共 312 条，还在报 310 条
llm-lint:   按规则：LLM004 12、LLM007 180、LLM009 118
llm-lint:   已经修好 2 条（LLM010 2）—— 确认无误就删掉这些行，基线才算真的收紧
```

- 「还在报」= 清单里仍然对得上当前问题的条目；剩下的是**已经修好、可以从清单里删掉**的。
- 「按规则」只算还在报的那些：修好的规则不会留在这行里，只在「已经修好」里出现。
  全修完时这行换成「基线里的旧问题都不报了」。
- `--baseline-stats` 需要一份基线（`--baseline` 或配置里的 `baseline:`），没有就退出 `2`。
- 它只读不写，不改清单；删不删、什么时候删由人决定。
- `--quiet` 不会吞掉统计：显式要的这份明细比总结更重要。

**收紧清单的节奏**：修完一批 → `--baseline-stats` 看「已经修好」→ 手工删掉那几行 →
提交。diff 里只出现删除行，review 一眼能看出「这次还掉了几条债」。

## 文件格式

```
# llm-lint baseline v1 —— 一行一条：规则<TAB>文件<TAB>消息；用 --save-baseline 更新，别手改
LLM007	src/main/java/demo/SupportAssistant.java	`maxTokens(128)` 只留了 128 个 token。…
LLM010	src/main/java/demo/Config.java	硬编码的 API key：`sk-proj-…`
```

- 第一行是表头（`#` 开头都会被跳过），空行也跳过。
- 字段里的 `\`、`<TAB>`、换行会转义成 `\\`、`\t`、`\n`，所以消息里带 Tab 也不会把三列撑坏。
- 行数不对 → 报错带行号：

  ```
  .llm-lint.baseline:3: 基线每行要有三列（规则、文件、消息），这行是「LLM001	src/A.java」
  ```

- 文件按「规则号 → 文件 → 消息」排序，两次生成的字节完全一样，PR 的 diff 里只会出现
  真正新增的那几行。

## 接进 CI

```yaml
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
    # 配置里写了 baseline 就不用传；临时换一份才用：
    # baseline: .llm-lint.baseline
```

action 的 `baseline` 输入和 `--baseline` 等价。不用 action 时：

```bash
java -jar llm-lint.jar --format sarif --baseline .llm-lint.baseline src/main/java > llm-lint.sarif
```

## 常见问题

**PR 里基线文件冲突了怎么办？**
两份清单都是「并集」语义，随便留一份，然后跑一次 `--save-baseline` 合并即可；
或者干脆 `git checkout --theirs` 之后重跑。别手工一行行对。

**想收紧：把某条旧问题修掉之后，怎么让它别再进清单？**
修完跑 `--save-baseline` 不会自动删条目（这是故意的：防止一次误操作把债「洗白」）。
确认修好了，就在 diff 里把那行删掉——review 时一眼能看到「这次少了一条」。

**只想看清单里还剩多少？**
`--baseline-stats` 会按规则列出来（见上一节）；不带它时，`--baseline` 的统计行写
「另有 N 条在基线里」。

**基线要不要跟着分支走？**
跟着主分支走、各分支共用一份。特性分支上新加的问题会被拦下来，这正是目的；
如果某个分支要临时放行，用 `--baseline` 指到另一份文件，别去改主清单。

## 为什么不用 JSON

JSON 也能存，但：

- 文本一行一条，PR 的 diff 直接可读，「这次接受了哪 3 条」一目了然；JSON 会被格式化器
  重排，diff 全是噪音。
- 少一个 JSON 解析器，就少一个出错面：这里只需要「按 Tab 切三列 + 反转义」。

代价是消息里不能有裸的 `<TAB>` 和换行——已经转义了，所以实际没有限制。

## 验收

仓库里有两组测试盯着这块：

- `BaselineTest`：行号变化不复活、消息/文件/规则变化算新、合并只增不减、转义往返、
  重复生成字节一致、坏文件报行号。
- `BaselineCliTest`：`samples/bad` 18 条 → 存基线后退出 0；加一个同规则的新文件 →
  退出 1 且只报新的；再存一次 → 16 条；配置里写 `baseline:` 生效；文件不存在/写坏 → 退出 2。