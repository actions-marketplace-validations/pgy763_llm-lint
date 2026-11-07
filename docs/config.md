---
title: 把规则固定在仓库里
description: .llm-lint.yml 的六个键、怎么被找到、优先级，以及写错时的报错长什么样。
---

# 把规则固定在仓库里

`.llm-lint.yml` 是给一个**项目**准备的配置文件：哪条规则不跑、哪条要更严、哪些目录不扫、
什么级别算失败——都写在仓库里，本地和 CI 读同一份。

## 为什么要有这个文件

没有它的时候，这些决定只活在流水线的命令行里：

```yaml
- run: java -jar llm-lint.jar --disable LLM009 --fail-on warning src/main/java
```

问题是这行命令没人看得见。新同事在本地跑 `java -jar llm-lint.jar src/main/java`，报出三条
「我们早就决定不管」的警告，于是他去查、去问，或者干脆不用这个工具了。反过来，某天有人把
`--disable LLM009` 从流水线里删掉，也没有 diff 之外的任何记录说明为什么曾经加过。

把这些写进 `.llm-lint.yml`，它就有 diff、有 review、有 blame：

- 决定跟着代码走，换个 CI 平台也不用重写参数；
- 本地一条命令和 CI 跑出来的结果一模一样；
- 关闭某条规则必须写注释说明理由，下一个人看得到。

## 一分钟

```bash
java -jar llm-lint.jar --init     # 在仓库根目录生成带注释的模板
$EDITOR .llm-lint.yml             # 按需去掉 # 号
java -jar llm-lint.jar src/main/java
```

一个真的在用的配置长这样（就是 [`samples/config-demo`](https://github.com/pgy763/llm-lint/tree/main/samples/config-demo) 里那份）：

```yaml
version: 1

rules:
  LLM004: off        # 流式超时统一在网关配，这里不重复报
  LLM007: error      # 团队要求 maxTokens >= 512，默认的 warning 不够狠

exclude:
  - "**/generated/**"   # 代码生成器的产物，改了也会被覆盖

fail-on: error
```

对这份配置，`samples/config-demo` 里的三个反例最后只剩一条报错：LLM004 被关掉，LLM010 落在
被排除的 `generated/` 里，LLM007 本来只是 warning，被提到 error 之后卡住了 CI。

## 配置长什么样

顶层只认七个键。多写一个键会直接报错，而不是被静默忽略——配置文件里打错的键，比没有配置更危险。

| 键 | 必填 | 值 | 说明 |
| --- | --- | --- | --- |
| `version` | 否 | `1` | 格式版本。现在只认 1，留着以后改格式用。 |
| `rules` | 否 | 映射，键是规则编号 | 关掉某条规则，或改它的级别。 |
| `exclude` | 否 | 列表，或逗号分隔的字符串 | 不扫的路径，规则同 `--exclude`。 |
| `fail-on` | 否 | `error` / `warning` / `never` | 什么级别让退出码变成 1。默认 `error`。 |
| `min-severity` | 否 | `info` / `warning` / `error` | 低于这个级别的不报也不计。默认 `info`。 |
| `baseline` | 否 | 路径字符串 | 存量问题清单，相对配置文件所在目录。写了就只报清单之外的新问题。 |
| `overrides` | 否 | 列表 | 按路径改级别：某段路径下的结果换个级别，规则本身不动（见下面「overrides」）。 |

### rules

```yaml
rules:
  LLM004: off        # 不跑
  LLM007: error      # 提到 error，会卡 CI
  LLM009: info       # 降成提示
  LLM011: on         # 显式打开，等于默认，只是让人一眼看出这里想过
```

级别可以写的值：

| 写法 | 效果 |
| --- | --- |
| `off` / `false` / `no` / `0` | 不跑这条规则 |
| `error` | 报错，`--fail-on error`（默认）下卡 CI |
| `warning` / `warn` | 警告，默认不卡 CI |
| `info` / `note` | 提示 |
| `on` / `true` / `yes` / `default` | 跟随规则自己的默认级别 |

大小写不敏感，`llm007: Error` 也行。**没写的规则一律跟随默认**——这是有意的，理由见
[只记差异](#只记差异)。

### baseline

```yaml
version: 1
baseline: .llm-lint.baseline
```

存量项目第一次接入时，先把现状存成清单（仓库里提交一份），之后只拦新问题：

```bash
java -jar llm-lint.jar --save-baseline .llm-lint.baseline --fail-on never src/main/java
```

- 相对路径按**配置文件所在目录**算（不是当前目录），所以从子目录里跑也指得对。
- 命令行 `--baseline` 优先于这里；`--save-baseline` 总是写到命令行给的那个文件。
- 清单格式、匹配规则、怎么更新见 **[baseline.md](baseline.md)**；看还剩多少旧账用
  `--baseline-stats`。

### exclude

两种写法等价：

```yaml
exclude:
  - "**/generated/**"
  - "**/test/java/**"
```

```yaml
exclude: "**/generated/**,**/test/java/**"
```

匹配规则和 `--exclude` 完全一样：带 `*` 的按 glob 匹配，不带 `*` 的按子串匹配。命令行再传
`--exclude` 时是**并集**，不是覆盖——配置里定的基线，不该被 CI 里临时加的一条顶掉。

带 `*` 的写法会拿**完整路径和它的每一段后缀**去对，所以 `src/generated/**` 这种相对仓库根的
写法，从仓库根扫、从子目录扫都能对上，不用先猜「当前目录是哪儿」再决定写不写 `**/` 前缀。
文件名通配（`*Test.java`）、只写一段目录（`generated/**`）也照常有效。

### overrides

`exclude` 是「这段路径整个不看」。可生成代码、老代码往往需要的是**照扫，只是级别松一点**——
那用 `overrides`：

```yaml
version: 1
overrides:
  - files: "src/generated/**"
    rules:
      LLM010: off
  - files: "src/main/**"
    rules:
      LLM007: error
```

- 只影响**那些路径下的结果**：`src/generated` 里硬编码的 key 不再报，`src/main` 里的
  `maxTokens(128)` 从 warning 提到 error，其它地方还是默认级别。
- **规则本身不动**：`--list-rules`、SARIF 的 `defaultConfiguration.level` 仍然是全局级别；
  变的是这条结果落在哪个文件里。
- 同一规则被多段覆盖命中时，**后写的盖先写的**，所以把更具体的路径写在后面。
- `files` 的写法跟 `exclude` 一样（glob 或子串，同样认相对仓库根的路径）。
- `off` 是丢掉这条结果，不是不扫这个文件：文件照样计入扫描文件数，存基线时也不会记它。
- 写错了同样退出 `2` 并带 `文件:行号`：`overrides` 不是列表、某段少了 `files` 或 `rules`、
  规则号拼错，都会说清楚是哪一段的问题。

### fail-on 和 min-severity

```yaml
fail-on: warning      # 警告也卡
min-severity: warning # info 级别的不报
```

这两个键在配置文件里和在命令行上是同一套语义，区别只有优先级。

## 它从哪读

按顺序找 `.llm-lint.yml`（找不到再试 `.llm-lint.yaml`）：

1. 从**当前目录**开始，一路往上，直到仓库根（含 `.git` 的那一级）为止；
2. 还没找到，就从**被扫描的路径**往上找——所以 `cd src && java -jar ../llm-lint.jar .` 也能读到
   仓库根的那份；
3. 都没有：等同于没有这个功能，行为和旧版本完全一样。

撞到 `.git` 就停止往上，是为了不读到隔壁项目的配置。`monorepo` 里子项目想要自己的配置，
就在子项目目录放一份——从那里往上找时先命中的是它。

两种显式指定：

```bash
java -jar llm-lint.jar --config ci/llm-lint.yml src/main/java   # 用指定文件
java -jar llm-lint.jar --no-config src/main/java                # 完全不读配置
```

`--no-config` 是有用的：排查「是不是配置文件在捣鬼」时，它是控制变量。

## 优先级

**命令行 > 配置文件 > 工具默认值。**

只有你真的在命令行里写了某个参数，它才覆盖配置。这是靠 picocli 的
`ParseResult.hasMatchedOption(...)` 判断的，不是靠「值不等于默认值」——否则你显式写
`--fail-on error`（和默认值相同）时就没法覆盖配置里的 `fail-on: never`。

| 配置里有 | 命令行 | 实际效果 |
| --- | --- | --- |
| `fail-on: never` | 什么都没写 | 只报告，退出码 0 |
| `fail-on: never` | `--fail-on error` | 退出码 1 |
| `exclude: ["**/generated/**"]` | `--exclude "**/main/**"` | 两个都排除（并集） |
| `LLM004: off` | `--disable LLM004` | 还是关着，不重复计数 |
| `LLM007: error` | 什么都没写 | 按 error 计 |

## 写错了会怎样

配置错误一律退出码 `2`，错误信息带**文件、行号**，能猜的就猜：

```
$ java -jar llm-lint.jar --config .llm-lint.yml src/main/java
.llm-lint.yml:3: 没有 LLM07 这条规则
  提示：是不是 LLM007？全部规则：llm-lint --list-rules
```

```
.llm-lint.yml:7: 规则 LLM007 的级别不认识：「serious」
  提示：能写 off / error / warning / info
```

```
.llm-lint.yml:2: 不认识的键「rulez」
  提示：能写的只有 version / rules / exclude / overrides / fail-on / min-severity
```

```
.llm-lint.yml:4: 缩进只能用空格，不能用 Tab
```

设计上**宁可报错，也不猜**：拼错的规则名如果被当成「没写」而悄悄跳过，你以为关掉了，其实
它还在报——这种不一致比直接失败难查得多。

## --init 和 --print-config

```bash
java -jar llm-lint.jar --init
```

在当前目录写 `.llm-lint.yml`：二十二条规则各一行注释（带默认级别），外加 exclude、overrides、fail-on、
min-severity 的注释示例。**全部是注释，等于没生效**，你按需去掉 `#`。文件已存在时**不覆盖**，
退出码 `2`——`--init` 是给新项目用的，不是用来重置的。

```bash
java -jar llm-lint.jar --print-config
```

打印**当前实际生效**的配置，把默认值也摊开：

```yaml
# llm-lint 生效配置（来自 .llm-lint.yml）

version: 1

rules:
  LLM001: error
  LLM002: error
  ...
  LLM004: off
  LLM007: error
  ...

exclude:
  - "**/generated/**"

fail-on: error
min-severity: info
```

它同时是排查工具和生成器：把输出重定向回 `.llm-lint.yml`，就得到一份「把现在的行为钉死」的
配置。CI 和本地结果不一样时，先比这两个输出。

## 在 CI 里用

仓库根放了配置文件之后，action 不需要任何参数：

```yaml
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java
```

`config` 输入默认留空，意味着「从当前目录往上找」；只有要指定别的路径时才填。注意 action 的
`fail-on` / `min-severity` 默认也是**空**的——没填就不传参数，配置文件才有机会说话。临时要
收紧或放宽，在 workflow 里写一次即可，命令行永远最优先。

不用 action 的话：

```yaml
- run: java -jar llm-lint.jar --format github src/main/java
```

配置会被自动读到，不需要额外的 `--config`。

## 支持的 YAML 子集

llm-lint 为了保持「一个 jar 丢下去就能跑」，没有引入 YAML 库，手写了一个严格的小解析器。
它支持：

- 区块映射（`rules:` 下面缩进写 `LLM004: off`）；
- 区块列表（`- "**/generated/**"`）；
- 行内列表（`exclude: ["a", "b"]`，三种引号写法都认）；
- 单引号（`''` 转义）和双引号（`\n`、`\t`、`\"`、`\\`）；
- `#` 注释，行尾的也算；
- UTF-8 BOM 和 CRLF。

**不支持**，遇到就报错（不会猜）：

- 锚点 / 别名（`&a`、`*a`）、多文档（`---`）；
- 跨行的块标量（`|`、`>`）；
- 行内映射（`{a: b}`）、流式嵌套（`[[a]]`）；
- Tab 缩进；
- 重复的键（YAML 里后者覆盖前者，这里直接报错，因为多半是手误）。

这些限制换来的是一行不依赖的解析器，以及所有错误都能精确到 `文件:行号`。

## 只记差异

配置文件只应该写**和默认不一样**的项。没写的规则跟着工具自己的默认级别走。

理由是升级：假设 0.3 里 LLM007 是 warning，你在配置里写了 `LLM007: warning`「固定下来」。
0.4 把它改成了 error（因为漏报太贵），你的配置却把它按回 warning，而且没有任何人记得
为什么。相反，只写「这条我们关掉了」「这条我们提到 error」，工具的默认值改进就能自动吃到。

`--print-config` 打印的是**展开后**的结果（方便排查），`--init` 生成的模板是**只写差异**的
形状（方便长期维护）。两者用途不同。

## 级别为什么在 Rule 层改

配置里的级别改写没有在最后遍历 findings 改 severity，而是用
`ConfiguredRule` 装饰器把规则包了一层。这样一次改动同时作用于：

- 文本输出里的级别列和统计；
- `--format json` 里的 `severity`；
- `--format sarif` 里规则的 `defaultConfiguration.level`（Code Scanning 用它决定是 error 还是 warning）；
- `--fail-on` 的退出码判断；
- `--list-rules` 和 `--explain` 显示的内容。

如果只在最后改 findings，SARIF 里「规则的级别」和「这条结果的级别」就会打架，Code Scanning
会把它们归到不同的严重度。

## 验收

`mvn -B verify` 里的 `MiniYamlTest`、`ConfigTest`、`ConfigLoaderTest`、`ProjectConfigTest`
覆盖了解析、编译、发现顺序、优先级和 CLI 行为；CI 还会用 `samples/config-demo` 跑一遍真实
场景（三条反例 → 一条 error、退出码 1），以及 `--init` 写出的模板必须能被自己读回去。