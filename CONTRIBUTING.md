# 参与进来

llm-lint 是一个很小的工具，改动面窄、边界清楚，很适合作为第一次给开源项目提 PR 的练习。这个文件把「怎么跑起来」「怎么加一条规则」「我们会怎么审」写清楚，照着做就行。

- 想加规则：看[加一条规则](#加一条规则)，或者从 [ROADMAP](ROADMAP.md) 里挑一条已经写好判断思路的
- 发现误报：用「误报」模板开 issue，**附上能复现的最小代码片段**（十几行就够），比描述有用十倍
- 别的东西（文档、CI、性能、输出格式）：直接开 issue 聊，先对齐再动手，避免白写

## 本地跑起来

需要 JDK 17+ 和 Maven 3.9+，没有别的依赖。

```bash
git clone https://github.com/pgy763/llm-lint.git
cd llm-lint
mvn -B -ntp verify
java -jar target/llm-lint.jar samples/bad
```

`verify` 会跑全部测试（现在是 183 个）：`samples/bad` 里每个文件必须**正好**触发一条规则，`samples/good` 里必须一条都不触发。这条约束是这套项目的地基，别绕开它。

> **Windows 上如果 `mvn verify` 报 `Cannot run program "cmd.exe" ... CreatePipe`**，加 `-DforkCount=0` 让测试在同一个 JVM 里跑：`mvn -B -ntp verify -DforkCount=0`。这是某些沙箱环境的限制，CI（Linux）不需要。

命令行里常用的几个：

```bash
java -jar target/llm-lint.jar --list-rules          # 二十二条规则的编号、级别、标题
java -jar target/llm-lint.jar --explain LLM007     # 一条规则的完整解释
java -jar target/llm-lint.jar --format json samples/bad | head -30
```

## 加一条规则

一条规则 = 一个 Java 类 + 一个反例 + 一个正例 + 文档 + 一堆跟着数字走的地方。按顺序做：

**1. 写规则类**，放在 `src/main/java/io/github/pgy763/llmlint/rules/`，命名 `Rule021XxxYyy.java`：

```java
public final class Rule021TransactionalSseEmitter extends AbstractRule {

    @Override
    public String id() { return "LLM021"; }

    @Override
    public String title() { return "事务方法返回长连接"; }

    @Override
    public Severity severity() { return Severity.WARNING; }

    @Override
    public String framework() { return "通用"; }

    @Override
    public String why() { return "一句话说清为什么这是个坑"; }

    @Override
    public String docUrl() { return docInRepo() + "#llm021"; }

    @Override
    public List<Finding> check(Path file, CompilationUnit unit) {
        // 只读语法树，靠类型名后缀判断，不做符号解析、不联网
    }
}
```

几条约定，写的时候留意：

- **只读语法树。** 不解析 import、不做类型推断、不读别的文件——认不出来就**不报**。宁可漏报，不可乱报。
- **一个反例文件只触发一条规则。** 新规则的样例不能顺带命中已有规则。
- **消息里说清楚「怎么改」**，而不只是「这里不对」；`at(file, node, message)` 会把行号列号算好。

**2. 在 `Rules.all()` 里追加**（`src/main/java/io/github/pgy763/llmlint/Rules.java`），顺序就是输出顺序。

**3. 加样例**：`samples/bad/LLM021_TransactionalSseEmitter.java`（必须正好报一条 LLM021）和 `samples/good/LLM021_TransactionalSseEmitter.java`（一条都不报）。`tools/gen_samples.py` 里也有样例清单要同步。

**4. 写文档** `docs/rules.md`：顶部表格加一行，正文加

```markdown
<a id="llm021"></a>
## LLM021 事务方法返回长连接

**搜这些能找到这条：** `…`、`…`

**为什么会踩：** …（讲机制，不讲口号）

**怎么改：** …（给出能直接抄的代码）
```

**5. 跟着数字走的地方**（漏了就会有测试红）：

| 位置 | 改什么 |
| --- | --- |
| `README.md` / `README.en.md` | 规则表加一行，「二十二条」的说法 |
| `docs/rules.md` 顶部 | 表格加一行 |
| `docs/changelog.md` | 新版本一行 |
| `action.yml` 描述 | 里面的「22 rules」 |
| `llms.txt` | 规则数量 |
| 测试里的条数断言 | `SamplesTest`、`SummaryCliTest`、`MarkdownCliTest`、`ParallelScanTest`、`BaselineCliTest`、`ConfigTest`、`ProjectConfigTest` |
| `.github/workflows/ci.yml` | json 自检的 `range(1, 20)` 和 `grep -c` 的数量 |

一次 `mvn -B -ntp verify` 会把漏掉的地方指出来——测试名基本都写的是中文，红在哪一行照着改就行。

**6. 自己先验一遍**：

```bash
mvn -B -ntp verify
java -jar target/llm-lint.jar samples/bad | grep LLM021   # 应该正好一条
java -jar target/llm-lint.jar samples/good                # 应该一条都没有
```

## 提 PR

- 一个 PR 只做一件事。加规则就只加规则，别顺手重构 `LintEngine`。
- 提交信息说清楚**为什么**：

  ```
  feat: LLM021 事务方法返回长连接（0.15.0）

  工具调用跑在请求线程上，里面的 JDBC 查询会把整个响应拖住……
  ```

- 标题不用加 `[WIP]`，草稿用 GitHub 的 Draft PR。
- CI 必须绿；`-DforkCount=0` 是本地 Windows 的绕法，别写进 `pom.xml`。
- 不确定判断范围（这条规则该不该认 `var`、该不该跨方法）？在 issue 里先问，比写完再改省事。

## 我们会怎么审

看三件事，顺序固定：

1. **反例是真的会出故障吗**——如果一条规则报的问题「其实没事」，它就是在制造噪音，不进主干。
2. **正例会不会被误伤**——新规则的样例和 `samples/good` 里的既有写法，都要能干净通过。
3. **消息能不能教会人**——只说「不要这样写」的规则会被打回去；能说出「为什么会这样、改成什么样」的才留。

审的时候可能会请你补一个反例文件，或者把判断范围收窄——这不是挑刺，是这类工具唯一活得下去的方式。

## 行为准则

对人不对事，别在 issue 里评价别人的代码水平。技术分歧用「反例代码 + 期望输出」说话。完整版见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## 安全

发现安全问题（比如规则会把密钥打进日志）请不要开公开 issue，见 [SECURITY.md](SECURITY.md)。

---

## Contributing (English)

The short version: JDK 17+, `mvn -B -ntp verify` (add `-DforkCount=0` on Windows sandboxes), one rule = one class in `rules/` + one failing sample in `samples/bad` + one clean sample in `samples/good` + a section in `docs/rules.md`. Rules read the Java syntax tree only — no type resolution, no network, no classpath; when in doubt, don't report. Open an issue before starting anything larger than a rule, and keep pull requests single-purpose. See [ROADMAP](ROADMAP.md) for rule ideas with detection sketches already written down.