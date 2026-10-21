<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/logo.png" width="120" alt="Crishoper RED">
</p>

<p align="center">
  <a href="https://github.com/srijan7815-bit/crishoper-red/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/srijan7815-bit/crishoper-red?color=FF3441&label=apk"></a>
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-101013">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-101013">
  <img alt="Compose" src="https://img.shields.io/badge/Compose-BOM%202024.06-101013">
  <img alt="No telemetry" src="https://img.shields.io/badge/telemetry-none-40D9A0">
</p>

# Crishoper RED

**A native Android agent console. Your keys, your device, your data.**

Crishoper RED is a single-purpose Android application: an operator console for an
LLM agent that can *actually do things* — search the web, run commands in a
throw-away Linux sandbox, read and write files, remember facts between sessions,
and save results to your own Google Drive.

It is not a chat wrapper. It is not a WebView. It is a real Kotlin/Compose
application that talks to model providers **with credentials you enter yourself**,
and that keeps every byte of state on the phone.

```
┌──────────────────────────────────────────────────────────────────┐
│  Compose UI  ──  RedViewModel  ──  AgentEngine  ──  providers    │
│      │               │                 │                         │
│   StateFlow      RedRepository     tool calls ──► Tavily / E2B   │
│      │               │                 │                         │
│   Encrypted vault (AES-256-GCM, AndroidKeyStore)                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## Table of contents

1. [What it does](#1-what-it-does)
2. [Design principles](#2-design-principles)
3. [The agent loop](#3-the-agent-loop)
4. [Bring your own keys](#4-bring-your-own-keys)
5. [Install](#5-install)
6. [Build from source](#6-build-from-source)
7. [Repository layout](#7-repository-layout)
8. [Security model](#8-security-model)
9. [Building and releasing](#9-building-and-releasing)
10. [Roadmap](#10-roadmap)
11. [License](#11-license)

---

## 1. What it does

| Capability | Detail |
|---|---|
| **Agent runs** | A plan-act-observe loop with a hard step budget (default 12), streaming output, and a stop button that cancels mid-flight. |
| **Web search** | Tavily search + extract, routed as a `web_search` tool the model calls when it needs facts newer than its weights. |
| **Linux sandbox** | An ephemeral E2B micro-VM per operation: `terminal`, `read_file`, `write_file`. Destroyed on demand. |
| **Memory** | Explicit `remember` / `recall` tools plus a reviewable memory list you can edit or wipe. |
| **Drive export** | `drive_save` writes finished work to the operator's own Google Drive. |
| **Operations** | Conversations are modelled as *operations*: named, resumable, deletable, browseable from a drawer. |
| **Visibility** | Every tool step is visible while it runs and collapses into a single summary block in the final answer. |
| **Terminal** | A live terminal pane that mirrors what the agent executes, so nothing happens off-screen. |

Everything above works offline-except-the-API-inc: the only network traffic is to
the provider you configured, with the key you entered.

## 2. Design principles

**Native, not embedded.** No WebView, no hybrid bridge, no bundled JS framework.
The UI is Jetpack Compose; the networking is OkHttp; state is a serialized
snapshot; secrets are in the Android Keystore. This keeps the app small, the
frames cheap, and the attack surface honest.

**Smoothness over decoration.** Motion is limited to short fades and drawer
transitions. There are no shaders, no 3D objects, no ambient particle fields. A
screen that stutters is a bug.

**Adaptive layout.** Screen class, width in dp, font scale and aspect ratio are
read from the device and used to pick gutters, type sizes and column counts, so
the same build reads correctly on a 5" phone, a foldable and a tablet.

**Two palettes.** A graphite *ink* theme (near-black surfaces, `#FF3441` accent)
and a warm *paper* theme (`#F5F4F2` with `#E01124`). Both are contrast-checked;
the app follows the system light/dark setting unless overridden in Settings.

**Reversible actions.** Destructive operations (delete operation, wipe memory,
forget a key, kill a sandbox) always ask first. Nothing is deleted on a single tap.

## 3. The agent loop

```
user prompt
   │
   ├─► system prompt (shipped asset, hash-pinned, shown in Settings › About)
   │
   ├─► model  ──►  text?            ──► stream to UI, done
   │            └►  tool call?      ──► validate ──► execute ──► observe
   │                                                   │
   └──────────────── step budget ──────────────────────┘
```

Tool results are truncated to a fixed token budget before being fed back, so a
noisy `terminal` call cannot blow the context window. If the model loops on the
same failing tool, the engine injects a correction instead of silently repeating.

Supported providers: **OpenRouter** (primary), plus direct OpenAI and Google
Generative Language endpoints. Default model is `thinkingmachines/inkling-small`
with a free-tier fallback; the model field accepts any OpenRouter slug.

## 4. Bring your own keys

The app ships with **no credentials and no proxy service**. You paste your own:

| Key | Used for | Where to get it |
|---|---|---|
| OpenRouter | All model calls | <https://openrouter.ai/keys> |
| Tavily | `web_search` | <https://app.tavily.com> |
| E2B | sandbox + terminal | <https://e2b.dev/dashboard> |
| Google Drive | `drive_save` | OAuth client you create |

Keys are validated with a cheap live probe when you save them, stored in
`AndroidKeyStore`-backed AES-256-GCM, and never leave the device except as the
`Authorization` header of the request they belong to. A **Demo mode** in
onboarding lets you look around the UI before any key exists.

## 5. Install

Download the latest signed APK from
[Releases](https://github.com/srijan7815-bit/crishoper-red/releases), then:

```bash
adb install -r crishoper-red-2.0.0.apk
```

Requirements: Android 8.0 (API 26) or newer, arm64 or x86_64.

> The release build is signed with the project key. Verify it before installing
> from an untrusted mirror:
>
> ```bash
> apksigner verify --print-certs crishoper-red-2.0.0.apk
> ```

## 6. Build from source

Prerequisites: **JDK 17**, **Android SDK 34** (`platforms;android-34`,
`build-tools;35.0.0`).

```bash
git clone https://github.com/srijan7815-bit/crishoper-red.git
cd crishoper-red/android
./gradlew :app:assembleDebug          # unsigned debug APK
./gradlew :app:assembleRelease        # requires keystore.properties (below)
```

`local.properties` with `sdk.dir=/path/to/Android/sdk` is required on a fresh
machine (it is git-ignored).

### Signing a release

```properties
# android/keystore.properties  (git-ignored — never commit this file)
storeFile=red-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

In CI the same values come from repository secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Missing secrets never break the
build: the workflow falls back to an unsigned debug APK.

### The system prompt asset

`system_prompt.md` lives at the repository root and is copied into
`app/src/main/assets/` by a Gradle `Sync` task at build time, then **git-ignored**
inside the module so the source of truth is single. Settings › About displays its
SHA-256 so a build can be audited against an expected prompt.

## 7. Repository layout

```
.
├── android/                      Gradle project
│   ├── app/
│   │   └── src/main/
│   │       ├── java/red/crishoper/agent/
│   │       │   ├── MainActivity.kt        drawer, scaffold, routing, toasts
│   │       │   ├── RedViewModel.kt        UiState, intents, coroutine scopes
│   │       │   ├── agent/AgentEngine.kt   the plan-act-observe loop
│   │       │   ├── data/Store.kt          state snapshot + keystore vault
│   │       │   ├── data/RedRepository.kt  persistence façade
│   │       │   ├── net/Providers.kt       OpenRouter / OpenAI / Gemini clients
│   │       │   ├── ui/                    Chat, Settings, Terminal, Onboarding
│   │       │   └── util/Text.kt           formatting helpers
│   │       ├── res/                       themes, launcher icons, splash
│   │       └── AndroidManifest.xml
│   └── {build.gradle.kts, settings.gradle.kts, gradle.properties}
├── system_prompt.md             the operator prompt (asset source)
├── docs/                        architecture · privacy · security model · Heretic
├── ci/workflows/                lint · checks · signed release (inactive — ci/README.md)
└── .github/                     CODEOWNERS · Dependabot · issue & PR templates
```

## 8. Security model

- **Vault.** AES-256-GCM with a non-exportable key in `AndroidKeyStore`
  (alias `red.vault.v1`), random IV per write, blob at `filesDir/red.vault.bin`.
  Key material is never written to disk in the clear.
- **No analytics, no crash reporting, no accounts.** The app has no backend.
- **Least privilege.** `INTERNET` plus nothing else that reads your data; no
  contacts, no location, no SMS, no background services.
- **Cleartext off.** `usesCleartextTraffic=false`, only HTTPS origins allowed.
- **Prompt integrity.** The system prompt hash is displayed and diffable.
- **Destructive gate.** Every irreversible action requires a second, explicit tap.

Full detail, including the threat model and what is *not* protected, is in
[docs/SECURITY-MODEL.md](docs/SECURITY-MODEL.md) and [SECURITY.md](SECURITY.md).

## 9. Building and releasing

The APK in [Releases](https://github.com/srijan7815-bit/crishoper-red/releases) is built from the tagged commit and signed
with the project key — its certificate fingerprint is published with each release
and can be re-checked locally:

```bash
apksigner verify --print-certs crishoper-red-2.0.0.apk
sha256sum -c checksums.txt
```

Workflow definitions for a fully automated pipeline — lint, unit tests, a compile
guard, signature verification, and tag-driven signed releases — are versioned in
[`ci/workflows/`](ci/workflows/) with activation instructions in
[`ci/README.md`](ci/README.md). They are deliberately **inactive**: GitHub only
accepts pushes that create files under `.github/workflows/` from a token carrying
the `workflow` scope, and this repository was published with a narrower one. The
definitions are checked in so the pipeline is reviewable and one `cp` away from
running; nothing in the build depends on them.

Until they are activated, the reproducible path is the local one:

```bash
cd android
./gradlew :app:assembleDebug                    # ~20 s after the first run
./gradlew :app:assembleRelease                  # signed, if keystore.properties exists
./gradlew :app:assembleRelease -Pred.release.minify=false   # low-RAM machines
```

### Build notes for constrained hardware

R8 needs roughly 3 GB of heap on this dependency graph. On a 2 GB container the
`minifyReleaseWithR8` step is where the daemon dies, while `assembleDebug` (which
skips R8) completes comfortably. `-Pred.release.minify=false` produces a larger
but functionally identical release APK; use it on CI runners *only* if you also
cannot allocate the heap, since shrinking is worth having in shipped builds.

## 10. Roadmap

- [ ] On-device summarisation of long operations (local, no upload)
- [ ] Multiple concurrent operations with per-operation sandbox quotas
- [ ] Wear OS companion for run notifications
- [ ] Local model support through MLC on flagship devices
- [ ] Exportable operation archive (Markdown + JSON)

## 11. License

Proprietary — see [LICENSE](LICENSE). Source is published for auditability and
evaluation; redistribution and commercial resale are not granted.

---

<sub>Built for people who would rather hold the keys.</sub>
