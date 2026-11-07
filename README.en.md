# llm-lint

[![ci](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml/badge.svg)](https://github.com/pgy763/llm-lint/actions/workflows/ci.yml)
[![Marketplace](https://img.shields.io/badge/Marketplace-llm--lint-purple)](https://github.com/marketplace/actions/llm-lint)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

English | [中文](README.md)

**Scan Spring AI / LangChain4j code and stop the expensive mistakes before you commit.**

Twenty-two rules, each one a failure you only find out about in production: a model call inside a
transaction draining the connection pool, a single shared chat memory leaking one user's context
into another's, `new SseEmitter()` dropping the stream after thirty seconds, an API key that is
already in your git history…

It does not run tests, call a model, need an API key, or need your classpath. It only reads source.
One command, a few seconds.

The full story behind each pitfall (verbatim errors, how to reproduce, how to fix) lives in a
separate handbook: <https://pgy763.github.io/llm-dev-pitfalls/>. This tool is the "linter version"
of that handbook — the twenty counter-examples in `samples/bad/` map one-to-one to its entries.

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
- To learn why a rule exists: `java -jar llm-lint.jar --explain LLM001`;
  add `--format markdown` to paste it into an issue or a doc (`--explain all --format markdown`
  prints all twenty-two at once).

You don't have to try it on your own project first: `samples/bad/` holds the twenty-two counter-examples
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

overrides:              # still scanned, but hard-coded keys in generated code are not findings
  - files: "src/generated/**"
    rules:
      LLM010: off

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
- **Per-path levels**: `exclude` skips a subtree entirely; `overrides` keep scanning it but change
  the level of findings under those paths (`off` drops them). Later entries win over earlier ones;
  see [docs/config.md](docs/config.md#overrides).
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

Every result carries `partialFingerprints`, so the same finding is not recounted on later commits — even if its line number moved.

For CI that only speaks JUnit (Jenkins, GitLab CI):

```yaml
- run: java -jar llm-lint.jar --format junit src/main/java > llm-lint.xml || true
# GitLab: artifacts:reports:junit: [llm-lint.xml]
# Jenkins: junit 'llm-lint.xml'
```

Errors become `<failure>` (red build), warnings go to `<system-out>` (build stays green) - the same
split the default exit code uses. Want something to send a human? `--format html` writes one page
with inline CSS; double-click it, no network needed.

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

## The twenty-two rules

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
| LLM016 | error | any | `@Tool` method returning `Mono` / `Flux` | the model gets `Mono@1a2b`, or the whole call 500s |
| LLM017 | error | any | vector store reads/writes inside a transaction | the connection pool dries up, same as LLM001 |
| LLM018 | warning | any | `Flux` returned without `produces` | the client still waits, then gets everything at once |
| LLM019 | warning | any | blocking call inside a `@Tool` method (db, sync HTTP, `sleep`) | the model waits on the tool, the whole response stalls |
| LLM020 | warning | any | `.block()` / `Future.get()` / `sleep` inside `@Transactional` | the transaction holds the connection while waiting |
| LLM021 | error | any | `@Transactional` method returns `SseEmitter` / `ResponseBodyEmitter` | the transaction lives until the stream ends, the pool runs out first |
| LLM022 | warning | any | model call inside `@Scheduled` / `@Async` | no request means no session: memory bleeds or is recreated every run |

Counter-examples, fixes and exact scope for each rule: **[docs/rules.md](docs/rules.md)** is the index; each rule also has its own page (e.g. [LLM007](https://pgy763.github.io/llm-lint/rules/llm007.html)), and the tables at the top and on this page are generated by `llm-lint --docs docs/rules`. The English pages live under `/en/rules/` (e.g. [LLM007](https://pgy763.github.io/llm-lint/en/rules/llm007.html)) and come from `llm-lint --docs-en docs/en/rules`. Can't find one? [Search](https://pgy763.github.io/llm-lint/en/search.html).
`java -jar llm-lint.jar --list-rules` prints this table any time.

## Usage

```
llm-lint [--format text|json|github|sarif|junit|html] [--fail-on error|warning|never]
         [--min-severity info|warning|error] [--disable LLM004,LLM009] [--only LLM001,LLM004]
         [--exclude "**/generated/**"] [--no-context] [--quiet]
         [--config .llm-lint.yml | --no-config] [--init] [--init-ci [<dir>]] [--init-hook [<dir>]] [--print-config]
         [--baseline .llm-lint.baseline] [--save-baseline .llm-lint.baseline]
         [--baseline-stats] [--summary <file>] [--markdown <file>] [--jobs N] [--since <ref>]
         [--files-from <file>] [--fix [--write]]
         [--lang <zh|en>] [--explain LLM001|all] [--list-rules [--format markdown|json]] [--docs <dir>] [--docs-en <dir>] <path...>

  --format        text (default) / json / github (Actions annotations) / sarif
                  / junit (what Jenkins and GitLab read) / html (a standalone page)
  --fail-on       error (default) / warning / never (report only)
  --min-severity  findings below this level are neither reported nor counted
  --disable       rules not to run; turn one off when it's a false positive in your project
  --only          run just these rules (comma separated); cannot be combined with --disable
  --exclude       paths not to scan (glob), comma separated
  --no-context    don't print the source line in text format
  --config        use this config file (default: discover .llm-lint.yml)
  --no-config     don't read any config file
  --init          write a commented .llm-lint.yml in the current directory
  --init-ci       write a GitHub Actions workflow under .github/workflows/
  --init-hook     write a pre-commit hook that scans staged .java files
  --print-config  print the effective config (with defaults), usable as a config file
  --baseline      read this baseline; known issues are not reported (see baseline: too)
  --save-baseline merge this run's findings into the baseline; never deletes entries
  --baseline-stats print how many baseline entries remain per rule and which are fixed
  --summary       write a Markdown summary to a file (great for job step summaries)
  --markdown      write one row per finding to a file (the Action posts it as a sticky PR comment)
  --jobs          how many files to scan at once: default CPU count (max 8), 1 = sequential
  --since         only scan files changed since this git ref, e.g. origin/main or HEAD~1
  --files-from    only scan the paths listed in this file, one per line; - reads stdin
  --fix           print a unified diff instead of a report (LLM006 / LLM018 have patches;
                  LLM019 / LLM020 get a written suggestion instead)
  --write         with --fix, write the patches into the files instead of printing them
  --lang          zh (default) / en; human-readable text only, JSON fields stay put
  --explain       level, framework, rationale and suppression for one rule; all for every rule;
                  per-path overrides are listed when your config has them
                  (with --format markdown you get a document you can paste)
  --list-rules    every rule; --format markdown for a table, --format json for a machine-readable catalog
  --docs-en       same as --docs, but writes the English pages (permalinks under /en/rules/)
  --docs          regenerate the per-rule pages under this directory (and the index table); the generated part is replaced, the hand-written part kept
```

`<path>` can be a directory or a single `.java` file. Directories are searched recursively, skipping
`target/` and `build/`. Files are parsed in parallel (up to 8 threads by default) while the output
order stays identical to serial runs; add `--jobs 1` on a busy machine or to reproduce a specific
order.

No JDK needed: the image is built in two stages, so the final layer is just a JRE and the jar,
for both `amd64` and `arm64` (native on Apple Silicon, no emulation).

```bash
docker run --rm -v "$PWD:/src" -w /src ghcr.io/pgy763/llm-lint:latest src/main/java
```

Swap `src/main/java` for `--list-rules` or `--since origin/main` — inside the container it's the same
llm-lint, `/src` is the mounted repo and the working directory, so `.llm-lint.yml`, baselines and
`--since` all follow the repo.

### English output and a machine-readable rule list

CI is read by the whole team, and `--lang en` (or `LLM_LINT_LANG=en`) switches the human-facing
text: help, reports, summaries, the PR comment and `--explain`. JSON / SARIF field names and values
do not move, so downstream scripts keep working.

For editor plugins, dashboards or doc generators, ask for the rule list as data:

```bash
llm-lint --list-rules --format json      # one object per rule, with level / docUrl / fixable
llm-lint --list-rules --format markdown  # a Markdown table you can paste into a README
```

The markdown output has the same shape as the table at the top of this repo's
[rule manual](docs/rules.md) (level and framework follow the config file). A test compares that
table against the code, so a wrong level or framework turns CI red.

One object per rule: `id`, `title`, `severity` (the rule's own default), `level` (what actually
applies this run; the config file and `--disable` show up here, `off` when turned off),
`framework`, `why`, `docUrl`, `fixable` (whether `--fix` can patch it).

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

### Scan a list of files

When CI already knows which files changed — a list from another tool, or the output of
`git diff --name-only` — hand it over instead of building the command by hand:

```bash
git diff --name-only origin/main | llm-lint --files-from -
llm-lint --files-from changed.txt src/main/java
```

One path per line; blank lines and `#` comments are skipped, and so are non-`.java` entries and files
that no longer exist (a deleted file still sitting in the list is normal, not an error). Combined with
a path or `--since`, the three intersect: whichever is narrowest wins.

### This line really is fine

```java
// llm-lint:ignore LLM004
SseEmitter emitter = new SseEmitter();
```

- `// llm-lint:ignore` — suppress every finding on that line
- `// llm-lint:ignore LLM004,LLM009` — suppress only those
- `// llm-lint:ignore-file` — suppress the whole file

### Run it before you commit

No need to wait for CI: `--init-hook` writes a `.git/hooks/pre-commit` that scans only the `.java`
files staged for this commit and blocks it on errors.

```bash
java -jar llm-lint.jar --init-hook        # into the current repository
```

The hook calls the `llm-lint` command; when it is missing, the hook prints a notice and lets the
commit through instead of trapping you. To use a specific jar:

```bash
LLM_LINT="java -jar tools/llm-lint.jar" git commit -m "..."   # for this commit only
```

Skip once with `git commit --no-verify`. If a `pre-commit` hook already exists, it is not
overwritten; chain into it instead.

### Let it write the patch

Some fixes only have one sensible shape — no need to copy it by hand:

```bash
java -jar llm-lint.jar --fix src > fix.patch
git apply fix.patch
```

It prints a unified diff instead of a report and **leaves your working tree alone by default** —
you apply it, review it, then commit.

If you would rather not pipe into `git apply` — or you are on a machine without it — add `--write`:

```bash
java -jar llm-lint.jar --fix --write src
```

Each file is rewritten in one go after the patch is computed in memory, so an error halfway through
leaves no half-written file. It prints how many spots changed per file and, on stderr, how many
findings still have no patch. `--write` without `--fix` exits `2`. Only LLM006 (SSE without charset) and LLM018 (streaming endpoint without
`produces`) have patches today; anything else says so instead of pretending.

LLM019 (`@Tool` doing blocking work) and LLM020 (waiting for async results inside a transaction)
would need code moves, which a patch tool tends to get wrong — so they get **advice, not a patch**:
`--fix` and `--fix --write` print a suggestion at the end and leave the file alone.

Exit codes still follow the findings: with errors, `--fix` exits `1`, so use `--fail-on never` or
`|| true` in scripts when you only want the patch.

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
- **It never changes code on its own**: report only. `--fix` hands you a patch; `--fix --write`
  applies it — but only when you ask for that flag.

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
git tag v0.28.0 && git push origin v0.28.0
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

## Contributing

The scope is deliberately narrow — syntax tree only, report only what is unambiguous — which makes **adding one rule an afternoon-sized job**.

- Adding a rule: follow the checklist in [CONTRIBUTING](CONTRIBUTING.md#加一条规则) (one class, one failing sample, one clean sample, one doc section); `mvn verify` tells you what you forgot.
- Looking for something to do: [ROADMAP](ROADMAP.md) has no open rule proposals left (LLM019–LLM022 all shipped) — what remains are engineering tasks, each labelled with difficulty. `good first issue` means it really is a first-PR-sized job.
- Found a false positive: open an issue with the **rule** template and paste the smallest snippet that reproduces it. A snippet beats a description ten times over.
- Just want to say it helped (or didn't): [open an issue](https://github.com/pgy763/llm-lint/issues/new) or leave a star.

This is maintained by one person in the evenings, so opening an issue before a large PR gets it reviewed much faster. A rejected PR usually means the detection trade-off is wrong, not that the code is bad: this kind of tool only survives by **preferring a miss over a false alarm**.

## See also

- [Documentation site](https://pgy763.github.io/llm-lint/) — [getting started](https://pgy763.github.io/llm-lint/start.html), [CI recipes](https://pgy763.github.io/llm-lint/integrations.html), [rules by error message](https://pgy763.github.io/llm-lint/errors.html), [FAQ](https://pgy763.github.io/llm-lint/faq.html), [rule reference](https://pgy763.github.io/llm-lint/rules.html)
- [llm-dev-pitfalls](https://pgy763.github.io/llm-dev-pitfalls/) — the handbook behind this linter, 51 entries with verbatim errors
- [hello-ai-backend](https://pgy763.github.io/hello-ai-backend/) — a book that starts with your first model call

## License

MIT