# llm-lint

[![ci](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml/badge.svg)](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml)
[![Marketplace](https://img.shields.io/badge/Marketplace-llm--lint-purple)](https://github.com/marketplace/actions/llm-lint)

English | [中文](README.md)

**Scan Spring AI / LangChain4j code and stop the expensive mistakes before you commit.**

Fifteen rules, each one a failure you only find out about in production: a model call inside a
transaction draining the connection pool, a single shared chat memory leaking one user's context
into another's, `new SseEmitter()` dropping the stream after thirty seconds, an API key that is
already in your git history…

It does not run tests, call a model, need an API key, or need your classpath. It only reads source.
One command, a few seconds.

The full story behind each pitfall (verbatim errors, how to reproduce, how to fix) lives in a
separate handbook: <https://pgy763.github.io/llm-dev-pitfalls/>. This tool is the "linter version"
of that handbook — the fifteen counter-examples in `samples/bad/` map one-to-one to its entries.

---

## 30 seconds

```bash
curl -L -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar

java -jar llm-lint.jar src/main/java
```

The output looks like this:

```
src/main/java/com/demo/OrderService.java
  42:20  error  LLM001  model call inside @Transactional
      42 |     ChatResponse r = chatClient.prompt().user(q).call().chatResponse();
         |                     ^
      `createOrder` is transactional and calls the model inside it. For those seconds the
      database connection is never returned — under load the error is "cannot get a connection",
      not "the model is slow", which sends you looking in the wrong place.
      → https://pgy763.github.io/llm-dev-pitfalls/java/事务里调模型拖垮连接池
llm-lint: 1 issue (1 error / 0 warning), 12 files scanned
```

- Exit code is `1` when there is an `error`, so it can block CI directly; warnings alone exit `0`.
- To change the threshold: `--fail-on warning` (warnings fail too) or `--fail-on never` (report only).
- To learn why a rule exists: `java -jar llm-lint.jar --explain LLM001`.

You don't have to try it on your own project first: `samples/bad/` holds the fifteen counter-examples
and `java -jar llm-lint.jar samples/bad` reports all of them; `samples/good/` is the corrected code
and reports nothing.

Both frameworks are recognized: Spring AI's `ChatClient` / `ChatModel` / `ChatMemory` and
LangChain4j's `ChatLanguageModel` / `StreamingChatLanguageModel` / `AiServices` / `ChatMemoryProvider`,
matched by type-name suffix, so mixed codebases are fine.

## Found this from an error message?

Plenty of people arrive here by searching a stack trace. Each of these symptoms maps to one rule
and a check you can run right now:

| What you searched (or what the log says) | What it usually is | Rule |
| --- | --- | --- |
| `NoSuchBeanDefinitionException: ... ChatClient` | asking the container to inject `ChatClient`, which has no default bean | LLM003 |
| `HikariPool ... Connection is not available, request timed out` with model calls in the log | waiting on the model inside `@Transactional`, holding the DB connection | LLM001 |
| SSE stream dies after 30 seconds / `AsyncRequestTimeoutException` | `new SseEmitter()` without a timeout, container default is 30s | LLM004 |
| User A's name shows up in user B's conversation | one shared `ChatMemory` in a singleton bean | LLM002 |
| `Unexpected end-of-input` / truncated tool arguments | `maxTokens` too small, the function-call JSON is cut in half | LLM007 |
| Chinese text comes back as `???` but the endpoint returns 200 | `produces` without `charset=UTF-8` | LLM006 |
| Streaming endpoint spins, then everything arrives at once | `block()` / `collectList()` inside a streaming method | LLM005 |
| Threads or ports run out under load, the model isn't even slow | `new ChatClient` / `AiServices` per request | LLM011 / LLM012 |
| `sk-...` or `AIza...` found in git history | hard-coded API key | LLM010 |
| Transactional method returns `Flux` / `Mono`, rollback never happens | reactive return type breaks the transaction boundary | LLM015 |
| Client disconnected, server logs say nothing | empty `catch (IOException)` swallowing stream writes | LLM008 |
| Upstream hangs, threads pile up | `@Bean` HTTP client without timeouts | LLM009 |
| N records take N times as long | model / vector store called in a loop | LLM013 |
| Memory looks per-session but is global | `ChatMemoryProvider` with a hard-coded id | LLM014 |

One command, no CI needed:

```bash
java -jar llm-lint.jar src/main/java
```

Every finding carries the file, line, the reason, and the matching handbook entry.

## Pin the rules in the repository

Written into the CI command line, `--disable`, `--exclude` and `--fail-on` only exist inside the
pipeline: people running locally never see them, newcomers don't know a warning was deliberately
ignored, and deleting that argument leaves no record of why it was added.

Put those decisions in a `.llm-lint.yml` at the repository root, and local runs and CI read the
same file:

```yaml
version: 1

rules:
  LLM004: off        # SSE timeouts are configured at the gateway, don't report them twice
  LLM007: error      # team policy is maxTokens >= 512, the default warning is not strict enough

exclude:
  - "**/generated/**"   # generated code, overwritten anyway

fail-on: error
```

Then run it with no flags at all:

```bash
java -jar llm-lint.jar src/main/java
```

- **Discovery**: walk up from the current directory until the repository root (the level containing
  `.git`); if there is no config, walk up from each scanned path. `--config other.yml` picks a
  different file, `--no-config` reads none.
- **Only the differences**: rules you don't mention keep the tool's default level, so improvements
  to defaults arrive automatically instead of being pinned to stale values copied into the config.
- **Precedence**: command line > config file > defaults. A temporary `--fail-on never` in CI still
  wins, and `--exclude` is **unioned** with `exclude` from the config.
- **Errors say what's wrong**: unknown keys, rules and levels exit `2` with `file:line`, and a
  misspelled rule id gets a suggestion (`LLM07` → `did you mean LLM007?`). No guessing, no skipping —
  silently ignoring a typo is much harder to debug than failing.

If you don't have one yet, generate a commented template:

```bash
java -jar llm-lint.jar --init          # writes .llm-lint.yml, never overwrites
java -jar llm-lint.jar --print-config  # prints the effective config (with defaults)
```

`--print-config` is the first step when "CI and my machine disagree": it expands the defaults too,
so you can diff the two. Full schema, YAML subset and design trade-offs are in
**[docs/config.md](docs/config.md)**, and `samples/config-demo/` is a runnable example (three
counter-examples, one error left after the config).

## Existing codebases: record the old issues, block only new ones

New projects are clean from day one; an existing one reports hundreds of findings the first time
you wire the tool in. Save the current state as a baseline, commit it, and CI reports only what's
not on the list:

```bash
java -jar llm-lint.jar --save-baseline .llm-lint.baseline --fail-on never src/main/java
java -jar llm-lint.jar --baseline .llm-lint.baseline src/main/java
```

Once it's in `.llm-lint.yml` you don't need the flags any more:

```yaml
version: 1
baseline: .llm-lint.baseline
```

- The list is plain text, one `rule<TAB>file<TAB>message` per line, so a PR diff shows exactly what
  was accepted or added.
- Matching **ignores line and column**: inserting a few imports does not resurrect an old issue.
  A changed message (`maxTokens(128)` → `256`) does count as new, because the code really changed.
- `--save-baseline` only ever adds (existing entries are merged in); deleting entries has to be a
  deliberate diff — so one careless run can't "launder" the backlog.
- If the `--baseline` file does not exist, the tool exits `2` instead of silently treating it as
  empty: a typo in the path turning everything red is the worst failure mode.
- To see how the backlog is going: `--baseline-stats` lists how many remain per rule and which ones
  are already fixed (and can be deleted).

See **[docs/baseline.md](docs/baseline.md)**.

## Add it to CI

The easiest way is the composite action (it installs the JDK and downloads the jar itself):

```yaml
- uses: actions/checkout@v4
- uses: pgy763/llm-lint@v1
  with:
    path: src/main/java     # the default
    # config: .llm-lint.yml # discovered automatically, usually not needed
    fail-on: error          # error (default) | warning | never; leave empty for the config value
    # disable: LLM009       # temporary; long-lived switches belong in .llm-lint.yml
```

Findings are annotated on the corresponding lines in the diff (GitHub Actions annotation format),
and a short Markdown summary is appended to the job page, so you don't have to dig through logs.

No workflow yet? `java -jar llm-lint.jar --init-ci` writes one for you.

On pull requests it also leaves one comment — a row per finding (file, line, rule link) — and
**updates that same comment in place** instead of piling up new ones. The comment needs permission:

```yaml
permissions:
  contents: read
  pull-requests: write   # drop this line and set pr-comment: 'false' for annotations + job summary only
```

PRs from forks get a read-only token, so the comment is skipped there; the scan itself is unaffected.

To get findings into the repository's **Security → Code scanning** panel:

```yaml
- run: java -jar llm-lint.jar --format sarif src/main/java > llm-lint.sarif
- uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: llm-lint.sarif
```

Without the action, just download the jar:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
- run: |
    curl -sL -o llm-lint.jar https://github.com/pgy763/llm-lint/releases/latest/download/llm-lint.jar
    java -jar llm-lint.jar --format github src/main/java
```

## The fifteen rules

| ID | Level | Framework | What it catches | What you see when it bites |
| --- | --- | --- | --- | --- |
| LLM001 | error | any | model call inside `@Transactional` | connection pool exhausted, "cannot get a connection" |
| LLM002 | error | any | one `ChatMemory` in a singleton bean | user B sees user A's name |
| LLM003 | error | Spring AI | letting the container inject `ChatClient` | `NoSuchBeanDefinitionException` at startup |
| LLM004 | error | any | `new SseEmitter()` without a timeout | stream dies halfway, container default is 30s |
| LLM005 | warning | any | `block()` / `collectList()` in a streaming method | frontend waits, then everything arrives at once |
| LLM006 | warning | any | `produces` without `charset` | Chinese text becomes question marks, the endpoint still "works" |
| LLM007 | warning | any | `maxTokens(...)` ≤ 256 | tool arguments truncated, `Unexpected end-of-input` |
| LLM008 | warning | any | empty `catch (IOException)` around stream writes | client disconnected, you keep writing, logs are empty |
| LLM009 | warning | any | `@Bean` HTTP client without timeouts | upstream hangs, threads pile up |
| LLM010 | error | any | `sk-…` / `AIza…` literals in code | the key is in git history, permanently |
| LLM011 | error | any | `ChatClient` / `ChatModel` built per request | threads and ports run out before the model does |
| LLM012 | error | LangChain4j | `AiServices` built per request | rebuilt every request, memories get crossed |
| LLM013 | warning | any | calling the model / vector store in a loop | N records, N round trips, latency adds up linearly |
| LLM014 | error | LangChain4j | `ChatMemoryProvider` with a hard-coded id | looks per-session, is actually global |
| LLM015 | warning | any | transactional method returning `Flux` / `Mono` | the annotation looks active, rollback never covers it |

Counter-examples, fixes and exact scope for each rule: **[docs/rules.md](docs/rules.md)**.
`java -jar llm-lint.jar --list-rules` prints this table any time.

## Usage

```
llm-lint [--format text|json|github|sarif] [--fail-on error|warning|never]
         [--min-severity info|warning|error] [--disable LLM004,LLM009]
         [--exclude "**/generated/**"] [--no-context] [--quiet]
         [--config .llm-lint.yml | --no-config] [--init] [--init-ci [<dir>]] [--print-config]
         [--baseline .llm-lint.baseline] [--save-baseline .llm-lint.baseline]
         [--baseline-stats] [--summary <file>] [--markdown <file>] [--jobs N] [--since <ref>]
         [--explain LLM001] [--list-rules] <path...>

  --format        text (default, for humans) / json / github (Actions annotations) / sarif
  --fail-on       error (default) / warning / never (report only)
  --min-severity  findings below this level are neither reported nor counted
  --disable       rules not to run; turn one off when it's a false positive in your project
  --exclude       paths not to scan (glob), comma separated
  --no-context    don't print the source line in text format
  --config        use this config file (default: discover .llm-lint.yml)
  --no-config     don't read any config file
  --init          write a commented .llm-lint.yml in the current directory
  --init-ci       write a GitHub Actions workflow under .github/workflows/
  --print-config  print the effective config (with defaults), usable as a config file
  --baseline      read this baseline; known issues are not reported (see baseline: too)
  --save-baseline merge this run's findings into the baseline; never deletes entries
  --baseline-stats print how many baseline entries remain per rule and which are fixed
  --summary       write a Markdown summary to a file (great for job step summaries)
  --markdown      write one row per finding to a file (the Action posts it as a sticky PR comment)
  --jobs          how many files to scan at once: default CPU count (max 8), 1 = sequential
  --since         only scan files changed since this git ref, e.g. origin/main or HEAD~1
  --explain       level, framework, rationale and suppression for one rule
```

`<path>` can be a directory or a single `.java` file. Directories are searched recursively, skipping
`target/` and `build/`. Files are parsed in parallel (up to 8 threads by default) while the output
order stays identical to serial runs; add `--jobs 1` on a busy machine or to reproduce a specific
order.

### Scanning only what changed

No need to rescan the whole repo on every PR. `--since` scans only the files touched since that ref
forked off — uncommitted edits and untracked new files included:

```bash
llm-lint --since origin/main src/main/java
llm-lint --since origin/main          # no path: everything that changed in the repo
```

Any ref works: `origin/main`, `HEAD~1`, a tag. Give a path and only changes under it are reported;
give no path and the whole repo counts. If no Java file changed, it exits 0 without noise.
With the Action:

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0          # --since compares against the base branch, so fetch history
- uses: pgy763/llm-lint@v1
  with:
    since: origin/${{ github.base_ref }}
```

### This line really is fine

```java
// llm-lint:ignore LLM004
SseEmitter emitter = new SseEmitter();
```

- `// llm-lint:ignore` — suppress every finding on that line
- `// llm-lint:ignore LLM004,LLM009` — suppress only those
- `// llm-lint:ignore-file` — suppress the whole file

## About false positives

**Better to miss than to be wrong.** One false positive and the author removes the tool from the
pipeline. So:

- Types are only inferred when they are **declared in the same file** (fields, parameters, locals).
  Calls like `getChatClient().prompt()` are not reported.
- An empty `catch` is only reported when it actually wraps `write`/`send`/`flush`; empty catches
  around pure business logic are left alone.
- Clients built inside `@Bean` methods are not reported — that is the recommended pattern.
- A `ChatClient` you `build()` yourself is not reported by LLM003; only container injection is.
- LLM009 cannot see timeouts configured elsewhere — if you did configure them, write `LLM009: off`
  in `.llm-lint.yml`; per-project configuration exists exactly for this, instead of typing
  `--disable` into CI every time.
- We once had a rule for "`new SseEmitter(0L)` without `onTimeout`", and our own positive examples
  killed it: disabling the timeout and keeping the connection alive with heartbeats is normal.
  **We deleted it** instead of bolting on conditions.

It has run on these real codebases with zero findings:

- [hello-ai-backend](https://github.com/pgy763/hello-ai-backend) — 71 Java files (Spring AI calls and hand-written SSE)
- [llm-gateway](https://github.com/pgy763/llm-gateway) (45 files) and [prompt-ops](https://github.com/pgy763/prompt-ops) (20 files), entire `src/main/java`

## What it is not

- **Not AI code review**: no model, no network calls; not a byte of your code leaves the machine.
- **Not a type checker**: no symbol resolution, no dependency jars, just type-name suffixes.
- **It does not change code**: report only, no autofix. Fixes are described per rule; you decide.

## Limitations

- Java 17 syntax only (mainstream Spring AI 2.x / LangChain4j 1.x style)
- No `var`, no chained-return inference, no cross-method or cross-file inference, so a model call
  hidden in a helper is invisible
- No config files (`application.yml`), SQL or frontend; no network
- No Kotlin, no Groovy

These boundaries are deliberate: a tool you can run with `java -jar` in any Java project gets
actually used; one that needs a configured classpath does not.

## Building it / how this thing is verified

```bash
mvn -B verify           # compile + tests (including both directions of the samples)
mvn -B -DskipTests package
java -jar target/llm-lint.jar --list-rules
```

Requires **JDK 17+** and Maven.

`samples/` is the acceptance criteria for the rules, and the only one:

- `samples/bad/LLM0NN_*.java` — each file must report **exactly** one finding with that id
- `samples/good/LLM0NN_*.java` — the correct version of the same code must report **nothing**
- `samples/suppressed/` — verifies `// llm-lint:ignore` suppresses only the named finding
- `samples/config-demo/` — three counter-examples plus a `.llm-lint.yml`: after configuration
  **exactly one** error must remain
- `BaselineTest` / `BaselineCliTest` — baseline: old issues exit `0`, new ones still exit `1`

Change a rule by changing the first three directories. CI also runs our published action against
`samples/good` (must pass), `samples/bad` (must fail) and `samples/config-demo` (must fail even with
the repository config), to make sure "the jar people download actually works".

## Releasing

Bump the version in `pom.xml`, then push a tag:

```bash
git tag v0.8.0 && git push origin v0.8.0
```

The `release` workflow runs `mvn verify`, checks that the jar version matches the tag, creates a
release with `llm-lint.jar` attached, and moves the `v1` pointer to the new tag — so
`uses: pgy763/llm-lint@v1` always gets the latest version without anyone editing their workflow.

The release body is generated too: commits are grouped into sections by prefix (`feat:` / `fix:` /
`docs:` …) and a `previous...current` compare link is appended, so keep commit titles conventional.
Re-running the same tag (say, after moving it) updates both the body and the jar.

The jar asset is not optional: `action.yml` downloads the tool from
`releases/latest/download/llm-lint.jar`; without it, users get the previous version.

To get onto the [Marketplace](https://github.com/marketplace): on the release page tick
*Publish this Action to the GitHub Marketplace* and pick a category. That step is manual; only then
does the version show up in Actions search.

## See also

- [llm-dev-pitfalls](https://pgy763.github.io/llm-dev-pitfalls/) — the handbook behind this linter, 51 entries with verbatim errors
- [hello-ai-backend](https://pgy763.github.io/hello-ai-backend/) — a book that starts with your first model call

## License

MIT