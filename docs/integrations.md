---
title: 接到各个平台上
description: GitHub Actions、GitLab CI、Jenkins、pre-commit、Maven 的接法，以及 action 的十二个输入。
---

# 接到各个平台上

所有平台都用同一个 jar，区别只在「怎么拿到 jar」和「报告喂给谁」。

## GitHub Actions

最省事的是官方 action：

```yaml
name: llm-lint
on: [push, pull_request]
permissions:
  contents: read
  pull-requests: write      # 想在 PR 里留评论才需要
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0    # 用 since 时要跟目标分支比，历史得拿全
      - uses: pgy763/llm-lint@v1
        with:
          path: src/main/java
```

它会做四件事：扫代码、把问题打成 PR 行内注解、在 job 页面写一段摘要、在 PR 里留一条**会原地更新**的汇总评论（靠 `<!-- llm-lint -->` 标记找到自己上一条，不会刷屏）。fork 来的 PR 默认只读，评论那步会跳过并打印提示，扫描本身照常。

### 输入

| 输入 | 默认 | 说明 |
| --- | --- | --- |
| `path` | `src/main/java` | 要扫的目录或单个 `.java` 文件 |
| `config` | 空 | 留空 = 从工作区往上找 `.llm-lint.yml`；填了就显式用它 |
| `baseline` | 空 | 存量问题清单；留空就用配置里的 `baseline:` |
| `fail-on` | 空 | `error` / `warning` / `never`；留空 = 配置文件或默认 `error` |
| `min-severity` | 空 | `info` / `warning` / `error`；留空 = 配置文件或默认 `info` |
| `disable` | 空 | 逗号分隔的规则号，临时开关用；长期请写进配置文件 |
| `since` | 空 | 只扫这个 git 引用以来改动的文件，如 `origin/${{ github.base_ref }}` |
| `jobs` | 空 | 并行扫几个文件；留空按 CPU（最多 8），`1` 是串行 |
| `lang` | 空 | `zh` / `en`，只影响人看的文字 |
| `pr-comment` | `true` | 设成 `'false'` 就不发 PR 评论 |
| `version` | `latest` | 钉死版本号，如 `0.11.0` |
| `jar` | 空 | 用一份现成的 jar，跳过下载（自测或离线环境用） |

**没填的项不传参数**——这是有意的，只有这样才能让配置文件说话。命令行永远最优先。

### 用配置文件 + 基线的最小 workflow

```yaml
- uses: actions/checkout@v4
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
```

仓库根有 `.llm-lint.yml`（里面写了 `baseline: .llm-lint.baseline`）时，这一份就是全部，不需要任何参数。

### 上报 Code Scanning

```yaml
- run: |
    curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar
    java -jar llm-lint.jar --format sarif src/main/java > llm-lint.sarif || true
- uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: llm-lint.sarif
```

`|| true` 是为了让「有问题」不中断上传——问题体现在 SARIF 里，而不是这一步的退出码。SARIF 里每条规则的 `defaultConfiguration.level` 也跟着配置里的级别走，Code Scanning 会把 error 和 warning 分开显示。

### 只跑改动文件

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
- uses: pgy763/llm-lint@v1
  with:
    since: origin/${{ github.base_ref }}
```

PR 里只看这个 PR 碰过的 `.java`，快且结果更相关。合并到 main 之后的全量扫描另开一个 job（不带 `since`）。

## GitLab CI

```yaml
llm-lint:
  image: eclipse-temurin:17
  script:
    - curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar
    - java -jar llm-lint.jar --format junit src/main/java > llm-lint.xml || true
    - java -jar llm-lint.jar --format text src/main/java
  artifacts:
    when: always
    reports:
      junit: llm-lint.xml
```

第一次（`|| true` 那次）只负责产出报告，第二次才按退出码决定 job 红不红——这样报告和「红」是两件独立的事。error 在 JUnit 里是 `<failure>`，warning 是 `<system-out>`，GitLab 会把它们显示成两种颜色。

## Jenkins

```groovy
stage('llm-lint') {
    steps {
        sh 'curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar'
        sh 'java -jar llm-lint.jar --format junit src/main/java > llm-lint.xml || true'
        junit 'llm-lint.xml'
    }
}
```

想留个能点开看的报告，再加一句 `--format html`，用 `publishHTML` 发布。

## 本地：提交前

```bash
java -jar llm-lint.jar --init-hook        # 写 .git/hooks/pre-commit
```

钩子只扫 staged 的 `.java`，有 error 就拦下。要接进已有的 `pre-commit`，别覆盖它——把这两行加进去：

```bash
files=$(git diff --cached --name-only --diff-filter=ACMR -- '*.java')
[ -n "$files" ] && java -jar tools/llm-lint.jar --fail-on error $files
```

## 本地：生成 CI 配置骨架

```bash
java -jar llm-lint.jar --init-ci          # 写 .github/workflows/llm-lint.yml
```

写的是用官方 action 的最小 workflow，已存在则不覆盖（退出码 2），提示你先合并。

## Maven / Gradle

不想在 CI 里单独写一步，就让构建自己跑（会不会因为问题失败由 `--fail-on` 决定）：

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <phase>verify</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>io.github.pgy763.llmlint.Main</mainClass>
        <classpathScope>test</classpathScope>
        <arguments><argument>--fail-on</argument><argument>error</argument>
          <argument>src/main/java</argument></arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

用 jar 更简单，不碰构建配置：`java -jar tools/llm-lint.jar src/main/java`。