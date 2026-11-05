# Roadmap

这个项目很小，路线也就一条：**把「Spring AI / LangChain4j 项目上线之后才会发现的那类坑」一条条变成能静态查出来的规则**。下面这些是已经想清楚、可以直接动手的；每条都标了难度，`good first issue` 是真的第一次提 PR 就能做完的。

开 issue 之前先看一眼：想做的活如果不在下面，也欢迎，但请先开 issue 说一句思路，避免白写。

## 现在在哪

| 版本 | 主题 |
| --- | --- |
| 0.17.0 | `--files-from`：只扫清单里列的文件，接别的工具算好的改动列表 |
| 0.16.0 | LLM021 / LLM022：路线图里写好的规则提案全部落地 |
| 0.15.0 | LLM020：`@Transactional` 里等异步结果（路线图的第二条提案落地了） |
| 0.14.0 | LLM019：`@Tool` 方法里做阻塞调用（路线图的第一条提案落地了） |
| 0.13.0 | 贡献者入口：CONTRIBUTING、issue/PR 模板、这份路线图 |
| 0.12.0 | SARIF `partialFingerprints`，Code Scanning 不再重复计数 |
| 0.11.0 | JUnit / HTML 报告、`--init-hook` |
| 0.10.0 | `--lang en`、机器可读的规则目录 |

## 想加的规则

判断口径和现有二十二条一样：只读语法树、靠类型名后缀认模型调用、认不出来就不报。

**现在没有现成的规则提案了**——路线图里写好的 LLM019–LLM022 都已经实现。想加新的？先开一个 issue，说清楚「什么代码会出事、不拦会怎么坏、正例长什么样」，我们把它写成一条提案（怎么认、级别、样例）再动手；写代码那一步照 [CONTRIBUTING 的加规则清单](CONTRIBUTING.md#加一条规则)做就行。

## 想做的工程活

### Docker 镜像 · good first issue

`docker run --rm -v "$PWD:/src" ghcr.io/pgy763/llm-lint:latest /src/src/main/java` 就能跑，不需要装 JDK 和 Maven。

验收：多阶段构建（`maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre` 运行）、镜像里只有 jar、`ENTRYPOINT ["java","-jar","/app/llm-lint.jar"]`、`.github/workflows/docker.yml` 在打 tag 时推 `ghcr.io/pgy763/llm-lint:<tag>` 和 `:latest`、README 加一节用法。

### `--list-rules --format markdown` · good first issue

把二十二条规则渲染成 `docs/rules.md` 顶部那张表，文档就能由代码生成，不用手动同步「二十二条」这类数字。

验收：输出列与现有表格一致（编号 / 级别 / 框架 / 一句话）；有测试锁住「表里的规则数 = `Rules.all().size()`」；`tools/` 下加一个脚本，用它重写 `docs/rules.md` 的表格段落。

### 规则文档自动校验 · good first issue

现在 `DocsTest` 已经锁了「每条规则在 `docs/rules.md` 里有锚点」。可以再进一步：校验顶部表格里的级别、编号、标题和 `Rules.all()` 完全一致，级别写错就红。

## 不做的事

- **不做符号解析、不读 classpath。** 这是它能「一个 jar 一条命令」的原因，换来的代价是认不出跨文件、反射、`var` 的部分场景——这个取舍不重新讨论。
- **不联网。** 不上报、不遥测、不调云端规则库。
- **不做自动改代码。** `--fix` 只输出 diff，落不落盘由用户决定；「自动改坏」比「不自动改」贵得多。
- **不为了数量加规则。** 一条会误报的规则，比没有这条规则更糟。