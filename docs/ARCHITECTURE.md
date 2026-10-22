# Architecture

Four layers, one direction of dependency. Nothing below knows about anything
above it.

```
ui/            Compose screens and design system
  ▲
RedViewModel   UiState (single immutable snapshot), intents, lifecycle scopes
  ▲
AgentEngine    the loop: build messages → call model → parse tools → execute
  ▲            ▲
data/          net/
Store + vault  provider clients (OpenRouter / OpenAI / Gemini / Tavily / E2B / Drive)
```

## Module: `ui/`

- `ChatScreen` — the operation view: prompt composer, message list, collapsible
  step cards, stop/retry controls.
- `SettingsScreen` — keys, model, persona, memory, theme, About (prompt hash).
- `TerminalScreen` — live command feed, sandbox status, kill switch.
- `Onboarding` — first-run key entry with per-key live validation and demo mode.
- `OperationsScreen` — the ledger: list, create, delete operations.
- `Components` — the MONO design system: hairlines, pills, toggles, segmented
  controls, elastic sliders, sheets, and the motion set (decrypt, count-up,
  stagger, dot field, dot spinner, flip words, text cursor).
- `theme/Theme.kt` — black/white tokens, type scale, and device-derived metrics
  (gutter, display size, radii) resolved from screen width, height and font scale.

**Rules.** No screen performs I/O. No screen owns mutable business state. Screens
receive `UiState` and emit intents. This is what keeps the recomposition count low
and the frame budget predictable.

## Module: `RedViewModel`

Holds a `MutableStateFlow<UiState>`. Every public method is an intent, every
mutation produces a new `UiState` copy. Long work runs in `viewModelScope` with a
single tracked `runJob`, which is how *Stop* works: it cancels the job and the
engine's cancellation checks unwind cleanly.

`UiState` also carries ephemeral UI concerns — toast text, terminal lines,
per-key validation results — so that rotation and process death do not lose an
in-flight screen.

## Module: `AgentEngine`

The loop, in order:

1. Assemble the message array: system prompt (asset), memory preamble, trimmed
   history, the new user turn.
2. Stream a completion from the configured provider.
3. Emit deltas to the UI as they arrive.
4. If the model returns tool calls, validate each one against the tool schema.
5. Execute tools sequentially; append `tool` results; trim each to budget.
6. Repeat until the model answers without a tool call, or the step budget is
   exhausted, or the operator stops the run.

Design decisions worth knowing:

- **Sequential tools.** Parallel calls were tried and rejected: two sandbox
  commands racing in one VM is a debugging nightmare for the human watching.
- **Result truncation** is a hard character budget per tool, applied before the
  result enters history, so a `find /` cannot wedge the context window.
- **Failure injection.** When the same tool fails twice with the same arguments,
  the engine inserts a corrective system note instead of retrying identically.
- **The engine never touches the vault.** It asks `Store` for a secret by name;
  the string exists only for the duration of one request.

## Module: `data/`

- `Store` — serialises `AppState` (settings, operations, memory) to
  `filesDir/red-state.json` with kotlinx.serialization; owns the vault.
- `RedRepository` — the façade the ViewModel talks to: load, save, delete,
  export. Keeps the ViewModel free of file paths.

## Module: `net/`

- `Providers` — one client class per family, sharing a small `HttpJson` helper
  with timeouts, retries on 429/5xx with jitter, and streaming line parsing.
- Tool implementations (`web_search`, `terminal`, `read_file`, `write_file`,
  `drive_save`) live next to the clients they call.

## Threading

| Work | Dispatcher |
|---|---|
| Disk read/write | `Dispatchers.IO` |
| Network | OkHttp's own pool, awaited from `IO` |
| State mutation | main (confined to the ViewModel) |
| Model streaming parse | `IO`, deltas posted to main |

## Persistence format

```jsonc
{
  "settings": { "model": "…", "persona": "…", "theme": "ink", "stepBudget": 12 },
  "conversations": [
    { "id": "…", "title": "…", "messages": [ { "role": "…", "content": "…", "steps": [] } ] }
  ],
  "memory": [ "…" ]
}
```

Written atomically: a temp file is fsynced and renamed over the old state, so a
crash mid-write leaves the previous good snapshot intact. Version field on top
allows forward migration.

## Why not Room, why not a DI framework

The state is a few dozen kilobytes and is read once, written whole. A SQLite
schema would add a migration burden with no benefit. Dependencies are constructed
in `RedApp` and passed down — three objects do not justify a framework, and
skipping it keeps cold start under 300 ms on a mid-range phone.

## Build pipeline

`app/build.gradle.kts` copies the repository-root `system_prompt.md` into
`src/main/assets/` via a `Sync` task that every variant depends on. The copy is
git-ignored, so the commit history has exactly one copy of the prompt and no
chance of the two drifting.
