---
title: 22 rules
description: What every llm-lint rule catches, one page per rule — generated from the code, so the site and the CLI never disagree.
permalink: /en/rules.html
---

# Rules

Twenty-two rules, each one a failure that only shows up after deploy. The analysis is purely static: no
network, no API key, no project classpath — it reads `.java` sources and decides by type-name suffix
whether a call looks like a model call.

> Deliberately conservative: **better to miss than to cry wolf.** Anything it cannot recognise (chained
> calls, cross-file inference, reflection, beans built in a config class) is not reported. "0 findings"
> means "none of these twenty-two patterns", not "the code is fine".

The level shown in the table is the **default**. If a rule does not fit your project — a false positive,
or your team is stricter — say so in `.llm-lint.yml` at the repo root; no need to change the tool or to
hand-write flags in CI: see [config](/en/config.html) (Chinese for now:
[config.md](https://github.com/pgy763/llm-lint/blob/main/docs/config.md)). Looking for one rule? [Search](search.html).

The tables below are generated: `llm-lint --docs-en docs/en/rules` rewrites the part between the
markers, and that is the only part of this page you should not edit. 中文版：[规则手册](/rules.html).

<!-- generated:rules-index -->
| Rule | Level | Framework | What it catches |
| --- | --- | --- | --- |
| [LLM001](#llm001) | error | Both | Model call inside a transaction, holding a DB connection for seconds |
| [LLM002](#llm002) | error | Both | One ChatMemory bean shared by every user |
| [LLM003](#llm003) | error | Spring AI | Asking the container for a ChatClient bean (there is none) |
| [LLM004](#llm004) | error | Both | new SseEmitter() without a timeout, dies at the 30s container default |
| [LLM005](#llm005) | warning | Both | block() inside a streaming handler, taking the event loop hostage |
| [LLM006](#llm006) | warning | Both | produces = "text/event-stream" without a charset |
| [LLM007](#llm007) | warning | Both | maxTokens too small, tool arguments get cut off |
| [LLM008](#llm008) | warning | Both | empty catch swallows IOException while writing SSE |
| [LLM009](#llm009) | warning | Both | HTTP client built in @Bean without a timeout |
| [LLM010](#llm010) | error | Both | hard-coded sk-... / AIza... API key |
| [LLM011](#llm011) | error | Both | new ChatClient / ChatModel per request |
| [LLM012](#llm012) | error | LangChain4j | new AiServices per request |
| [LLM013](#llm013) | warning | Both | model or vector store call inside a loop |
| [LLM014](#llm014) | error | LangChain4j | ChatMemoryProvider with a hard-coded id |
| [LLM015](#llm015) | warning | Both | transactional method returning Flux / Mono |
| [LLM016](#llm016) | error | Both | @Tool method returning Mono / Flux |
| [LLM017](#llm017) | error | Both | vector store read / write inside a transaction |
| [LLM018](#llm018) | warning | Both | streaming endpoint without produces |
| [LLM019](#llm019) | warning | Both | @Tool method hitting a DB / blocking client / Thread.sleep |
| [LLM020](#llm020) | warning | Both | .block() / Future.get() / Thread.sleep inside @Transactional |
| [LLM021](#llm021) | error | Both | @Transactional method returning SseEmitter / ResponseBodyEmitter |
| [LLM022](#llm022) | warning | Both | model call directly inside @Scheduled / @Async |

---

<a id="llm001"></a>
### [LLM001 Model call inside @Transactional](https://pgy763.github.io/llm-lint/en/rules/llm001.html)

error · Both —— Model call inside a transaction, holding a DB connection for seconds

<a id="llm002"></a>
### [LLM002 Shared ChatMemory in a singleton](https://pgy763.github.io/llm-lint/en/rules/llm002.html)

error · Both —— One ChatMemory bean shared by every user

<a id="llm003"></a>
### [LLM003 Container-injected ChatClient](https://pgy763.github.io/llm-lint/en/rules/llm003.html)

error · Spring AI —— Asking the container for a ChatClient bean (there is none)

<a id="llm004"></a>
### [LLM004 SseEmitter without a timeout](https://pgy763.github.io/llm-lint/en/rules/llm004.html)

error · Both —— new SseEmitter() without a timeout, dies at the 30s container default

<a id="llm005"></a>
### [LLM005 block/collect in a streaming method](https://pgy763.github.io/llm-lint/en/rules/llm005.html)

warning · Both —— block() inside a streaming handler, taking the event loop hostage

<a id="llm006"></a>
### [LLM006 produces is missing charset](https://pgy763.github.io/llm-lint/en/rules/llm006.html)

warning · Both —— produces = "text/event-stream" without a charset

<a id="llm007"></a>
### [LLM007 maxTokens too small](https://pgy763.github.io/llm-lint/en/rules/llm007.html)

warning · Both —— maxTokens too small, tool arguments get cut off

<a id="llm008"></a>
### [LLM008 Empty catch swallows IOException](https://pgy763.github.io/llm-lint/en/rules/llm008.html)

warning · Both —— empty catch swallows IOException while writing SSE

<a id="llm009"></a>
### [LLM009 HTTP client without a timeout](https://pgy763.github.io/llm-lint/en/rules/llm009.html)

warning · Both —— HTTP client built in @Bean without a timeout

<a id="llm010"></a>
### [LLM010 Hardcoded API key](https://pgy763.github.io/llm-lint/en/rules/llm010.html)

error · Both —— hard-coded sk-... / AIza... API key

<a id="llm011"></a>
### [LLM011 New ChatClient/ChatModel per request](https://pgy763.github.io/llm-lint/en/rules/llm011.html)

error · Both —— new ChatClient / ChatModel per request

<a id="llm012"></a>
### [LLM012 New AiServices per request](https://pgy763.github.io/llm-lint/en/rules/llm012.html)

error · LangChain4j —— new AiServices per request

<a id="llm013"></a>
### [LLM013 Model/vector store call in a loop](https://pgy763.github.io/llm-lint/en/rules/llm013.html)

warning · Both —— model or vector store call inside a loop

<a id="llm014"></a>
### [LLM014 ChatMemoryProvider with a hardcoded id](https://pgy763.github.io/llm-lint/en/rules/llm014.html)

error · LangChain4j —— ChatMemoryProvider with a hard-coded id

<a id="llm015"></a>
### [LLM015 Transactional method returns Flux/Mono](https://pgy763.github.io/llm-lint/en/rules/llm015.html)

warning · Both —— transactional method returning Flux / Mono

<a id="llm016"></a>
### [LLM016 Tool method returns a reactive type](https://pgy763.github.io/llm-lint/en/rules/llm016.html)

error · Both —— @Tool method returning Mono / Flux

<a id="llm017"></a>
### [LLM017 Vector store access inside a transaction](https://pgy763.github.io/llm-lint/en/rules/llm017.html)

error · Both —— vector store read / write inside a transaction

<a id="llm018"></a>
### [LLM018 Streaming endpoint without produces](https://pgy763.github.io/llm-lint/en/rules/llm018.html)

warning · Both —— streaming endpoint without produces

<a id="llm019"></a>
### [LLM019 Blocking call inside a @Tool method](https://pgy763.github.io/llm-lint/en/rules/llm019.html)

warning · Both —— @Tool method hitting a DB / blocking client / Thread.sleep

<a id="llm020"></a>
### [LLM020 Waiting on async work inside a transaction](https://pgy763.github.io/llm-lint/en/rules/llm020.html)

warning · Both —— .block() / Future.get() / Thread.sleep inside @Transactional

<a id="llm021"></a>
### [LLM021 Transactional method returns a streaming emitter](https://pgy763.github.io/llm-lint/en/rules/llm021.html)

error · Both —— @Transactional method returning SseEmitter / ResponseBodyEmitter

<a id="llm022"></a>
### [LLM022 Model call inside a scheduled or async method](https://pgy763.github.io/llm-lint/en/rules/llm022.html)

warning · Both —— model call directly inside @Scheduled / @Async

<!-- /generated:rules-index -->

## Suppressing a finding

Put a comment on the line (or the line above):

```java
// llm-lint:ignore LLM004
SseEmitter emitter = new SseEmitter();
```

- `// llm-lint:ignore` — drop every finding on that line
- `// llm-lint:ignore LLM004,LLM009` — drop only those
- `// llm-lint:ignore-file` — skip the whole file (anywhere in the file)

Suppressed findings still show up in the summary line (`suppressed N`), so the numbers never quietly
disappear.

## What these rules do not do

- No symbol resolution, no jars: types are judged by the **simple name at the declaration**.
  `var`, chained returns, and calls made straight off a method result are invisible — and therefore
  not reported.
- No cross-file inference: an `@Bean` in file A and its injection in file B are judged separately.
- No config files, no SQL, no front-end code, no network probes.
- No opinion on whether a transaction *should* be there — the pattern is reported, the judgement is
  yours and it is written down on each rule page.