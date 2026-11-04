# 安全策略

## 支持范围

只有最新一个 release 会被修。0.x 期间接口还在动，安全修复会直接出现在下一个版本里。

## 怎么报

**请不要开公开 issue。** 用 GitHub 的私密漏洞报告：仓库 → **Security** → **Report a vulnerability**（[直达链接](https://github.com/pgy763/llm-lint/security/advisories/new)）。这条通道只有维护者能看到。

方便的话在报告里带上：

- 受影响的版本（`java -jar llm-lint.jar --version`）
- 最小复现：一段 Java 源码 + 一条命令
- 你预期的行为和实际行为

一般 3 天内会回第一句话；修好之后会在 [release](https://github.com/pgy763/llm-lint/releases) 里写清楚影响面，如果你愿意，会把你的名字写进致谢。

## 这个工具的攻击面

llm-lint 不联网、不上报、不执行被扫的代码，所以风险集中在「输入」和「输出」两侧：

- **读**：被扫的 `.java`、`--config` 指向的 `.llm-lint.yml`、`--baseline` 指向的基线文件、`--files-from` 的文件清单
- **写**：`--summary` / `--markdown` / `--fix` 的输出路径，`--init` / `--init-ci` / `--init-hook` 创建的文件
- **出**：文本 / JSON / SARIF / JUnit / HTML 报告——它们会被贴进 PR 评论、上传到 Code Scanning、被别的系统解析

值得报的问题，比如：

- 某个构造让解析器死循环、栈溢出或吃掉几个 G 内存（正则回溯、超深嵌套的表达式）
- 报告转义不全，导致 `--format html` / `--format junit` 输出能被注入脚本或伪造条目
- `--init-hook` 写出来的脚本有 shell 注入（文件名里的引号、换行）
- 路径处理让它读到或写到不该碰的地方（`../`、符号链接、Windows 短路径）

不太算问题的：

- **规则漏报。** 这套判断有意保守，认不出来的直接不报，见 [局限](docs/faq.md)。
- **规则误报。** 用 issue 模板报，那是质量问题，不是安全问题。
- **密钥被写进报告。** LLM010 报的就是硬编码密钥，报告里会出现你代码里那一行的片段——这是预期行为；如果它出现在不该出现的地方（比如日志级别、CI 注解的标题行），那算问题，请报。

## 依赖

运行时只依赖 `javaparser-core` 和 `picocli`，版本钉在 `pom.xml` 里。依赖出现安全公告时，会在下一个 release 里升级并在 changelog 里写一句。