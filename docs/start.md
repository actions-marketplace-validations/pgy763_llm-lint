---
title: 装上，跑一次
description: 三种装法（jar、GitHub Action、pre-commit）、第一次运行、退出码 0/1/2、六种输出格式和常用参数。
---

# 装上，跑一次

llm-lint 是一个 jar。**Java 17 以上**，没有别的依赖，不需要在项目里加任何构建插件。

## 三种装法

**1. 直接下 jar（最快）**

```bash
curl -sL -o llm-lint.jar \
  https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar

java -jar llm-lint.jar src/main/java
```

把这个文件放进 `tools/` 提交进仓库也完全可以——一个 2 MB 的 jar，换来所有人版本一致。

**2. GitHub Action**

```yaml
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
```

`@v1` 永远指向最新的 0.x。要钉死版本就写 `version: 0.11.0`，或者用 `jar:` 指向仓库里那份。

**3. 提交前钩子**

```bash
java -jar llm-lint.jar --init-hook
```

写进 `.git/hooks/pre-commit`，之后每次 `git commit` 只扫这次 `git add` 的 `.java` 文件。机器上找不到 `llm-lint` 时它会自己跳过并提示，不会把人卡住。

## 第一次运行

```bash
$ java -jar llm-lint.jar src/main/java

src/main/java/demo/SupportController.java:24:19 warning LLM004 new SseEmitter() 没给超时
src/main/java/demo/SupportAssistant.java:17:26 warning LLM007 maxTokens(128) 只留了 128 个 token
2 个问题（0 error / 2 warning），扫了 12 个文件
```

第一段是问题，第二段是统计。机器可读的版本用 `--format json`。

退出码只有三种，和 CI 里的「红不红」一一对应：

| 退出码 | 含义 |
| --- | --- |
| `0` | 没有达到 `--fail-on` 门槛的问题（默认是 error） |
| `1` | 有问题达到了门槛 |
| `2` | 用法或配置错了（参数写错、配置文件解析失败、路径不存在……） |

`2` 永远是「工具没法正常跑」，不是「代码有问题」——这两件事在 CI 里要区分开，否则配置写错看起来像代码坏了。

## 换一种输出

```bash
java -jar llm-lint.jar --format json  src/main/java    # 给脚本用
java -jar llm-lint.jar --format sarif src/main/java    # GitHub Code Scanning
java -jar llm-lint.jar --format github src/main/java   # PR 里的行内注解
java -jar llm-lint.jar --format junit  src/main/java   # Jenkins / GitLab
java -jar llm-lint.jar --format html   src/main/java > report.html
```

`json` / `sarif` 的字段是稳定的；`text` / `github` / `junit` / `html` 里的自然语言会跟着 `--lang` 变。

## 常用的四种收紧方式

```bash
# 警告也拦（默认只拦 error）
java -jar llm-lint.jar --fail-on warning src/main/java

# 这次先不管某两条
java -jar llm-lint.jar --disable LLM004,LLM009 src/main/java

# 不扫生成的代码
java -jar llm-lint.jar --exclude "**/generated/**" src/main/java

# 只看这次改动的文件（PR 里最快）
java -jar llm-lint.jar --since origin/main
```

这些参数写多了就该搬进仓库：[配置](config.md) 一份 `.llm-lint.yml`，本地和 CI 读同一份。

## 大仓库

文件是并行解析的，默认按 CPU 核数（最多 8 个线程），输出顺序和串行完全一致。想对比或复现某个顺序：

```bash
java -jar llm-lint.jar --jobs 1 src/main/java
```

## 下一步

- 第一次接进存量仓库：[基线](baseline.md)——先把旧问题记下来，只拦新的
- 想改规则开关、级别、排除路径：[配置](config.md)
- 接到 GitHub / GitLab / Jenkins：[各平台接法](integrations.md)
- 想知道某条规则为什么报：[规则手册](rules.md)