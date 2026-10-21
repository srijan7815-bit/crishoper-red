# Security Model and Threat Model

This document explains *what the app protects*, *how*, and — just as important —
*what it does not protect*. It is written for someone deciding whether to put
their own API keys and their own data into this build.

Reporting a vulnerability: see [../SECURITY.md](../SECURITY.md).

---

## 1. Assets

| Asset | Sensitivity | Where it lives |
|---|---|---|
| Provider API keys (OpenRouter, Tavily, E2B) | High — billable | Encrypted vault |
| Google OAuth refresh token | High | Encrypted vault |
| Conversation content | Medium — may contain private work | `filesDir/red-state.json` |
| Memory entries | Medium — deliberately personal | `filesDir/red-state.json` |
| System prompt | Low — but integrity matters | APK asset, hash shown in UI |
| Sandbox credentials | Ephemeral | Memory only |

## 2. Trust boundaries

```
┌─ device ────────────────────────────────────────────────┐
│  UI process ── ViewModel ── Engine                       │
│        │           │          │                          │
│        │      Keystore (TEE)  │                          │
│        │           │          │                          │
│   private files   vault     outbound HTTPS               │
└────────────────────────────────┼─────────────────────────┘
                                 │  (TLS 1.2+, cert pinning off —
                                 │   see §6 for why)
                    ┌────────────┴────────────┐
                    │ model / Tavily / E2B /  │
                    │ Google Drive            │
                    └─────────────────────────┘
```

The device is trusted. The network is not. The model is *not* trusted — model
output is input to the tool layer and is validated there.

## 3. Credential protection

- **Algorithm.** AES-256-GCM, 96-bit random IV per record, 128-bit tag.
- **Key.** Generated in `AndroidKeyStore` under alias `red.vault.v1` with
  `setUserAuthenticationRequired(false)` so background work is possible, and no
  `EXPORTABLE` flag. Where the device has StrongBox, the key is created there.
- **At rest.** `filesDir/red.vault.bin` holds a single encrypted blob containing a
  JSON map of secret name → value. Rewritten atomically (temp file + rename).
- **In transit.** Secrets are only ever placed in an `Authorization` header of a
  request to the provider they belong to.
- **In logs.** The app never logs a secret. `RedViewModel` redacts key previews to
  the first four characters. `Log.d` does not receive vault values.
- **In memory.** Strings live in the heap while in use; Kotlin cannot reliably
  wipe immutable strings. This is a known, accepted limitation of the platform.

**Not protected against:** a rooted device with a live attacker, a malicious
keyboard or accessibility service that reads what you type, or a compromised
system image. If your threat model includes those, do not store production keys
on the device.

## 4. Prompt and tool integrity

- The system prompt is shipped as an APK asset and synced from the repository at
  build time. Its SHA-256 is displayed in Settings › About so a user can check
  that a build carries the prompt they expect.
- Tool calls are parsed from the model response with a strict schema check. An
  unknown tool name, a missing argument, or an out-of-range argument is returned
  to the model as an error rather than executed.
- **Destructive-action gate.** Deleting an operation, clearing memory, forgetting
  a key, and killing a sandbox all require an explicit second tap by the human.
  No model output can satisfy that gate, because the gate lives in the UI layer
  and not in the tool layer.
- **Step budget.** Every run is bounded (default 12 tool calls). A runaway model
  cannot spin forever or drain a quota silently.
- **Sandbox.** Code executes in an E2B micro-VM, never on the device. The phone
  holds no terminal emulator; the "Terminal" screen displays what the sandbox ran.

## 5. Network surface

- `usesCleartextTraffic="false"` plus a network security config that keeps
  cleartext closed everywhere except loopback / `*.local` / private LAN ranges,
  which exist only for a self-hosted inference server.
- Host allow-list is enforced by an OkHttp interceptor before any request can
  leave the process: `openrouter.ai`, `api.openai.com`,
  `generativelanguage.googleapis.com`, `api.tavily.com`, `api.e2b.app`,
  `www.googleapis.com`, `oauth2.googleapis.com` — plus the single host you set
  in *Custom endpoint*, and loopback/private-LAN names. A model cannot make the
  app call an arbitrary host.
- Response bodies are size-capped before parsing so a hostile endpoint cannot
  exhaust memory.
- No WebView anywhere in the APK. There is no JavaScript engine to exploit.

## 6. Certificate pinning — deliberately not used

Pinning protects against a mis-issued certificate; it also breaks every user
whose network does TLS inspection (corporate MDM, some carriers) and turns a
routine certificate rotation at a provider into an app-wide outage. For a BYOK
client talking only to well-known API endpoints, standard Android TLS validation
with a current root store is the better trade. This decision is recorded here so
it is a decision and not an oversight.

## 7. Privacy of the run

Every tool call is displayed in the UI while it happens, including the arguments
sent to the sandbox. A model cannot perform a silent action; the operator sees
the step stream and can stop the run at any point.

## 8. Supply chain

- Dependencies are pinned in `android/gradle/libs.versions.toml`; Dependabot
  opens grouped PRs weekly.
- CI runs `checks.yml` on every push: committed-secret scan, prompt-size guard,
  and a guard that fails if any HTML/JS payload reappears in the app module.
- Releases are built from a tag by `release.yml`, signed, and published with
  SHA-256 checksums. The signing key exists only as a base64 secret in the
  repository settings and on the maintainer's machine.

## 9. Known limitations (open, accepted)

1. Operation content is stored in clear JSON inside app-private storage. It is
   protected by the OS sandbox and by full-disk encryption, not by the vault.
2. Memory entries are sent to the provider whenever they are recalled, because
   that is what recall means.
3. The app cannot tell a genuinely user-written prompt from one an attacker typed
   while holding an unlocked phone.
4. `drive_save` writes to whatever Google account the OAuth token belongs to —
   the app does not let you choose per-export.
