# Privacy Notice

**Short version: there is no Crishoper RED server. Nobody at this project can see
your prompts, your files, your memory, or your keys.**

Last updated: 2026-10-22 · Applies to app version 3.0.0 (`red.crishoper.agent`)

---

## 1. Who processes your data

| Party | What it receives | When |
|---|---|---|
| **Model provider** (OpenRouter by default, or whichever endpoint you configure) | Your prompt, the conversation history sent with it, the system prompt, and tool results needed to continue the run | Every time you press send |
| **Tavily** | The search query the model writes | Only when the model calls `web_search` |
| **E2B** | Commands, file contents and paths the model sends to the sandbox | Only when the model calls `terminal`, `read_file` or `write_file` |
| **Google Drive** | The document you export, written to your own Drive with your own account | Only when the model calls `drive_save` and you confirm |
| **This project** | Nothing | Never |

Each of those is a company with its own privacy policy, and each is optional:
if you do not enter a Tavily key, the model is simply told the tool is
unavailable.

## 2. What stays on the device

- Conversation history and operation names
- Your memory entries
- Settings (model, persona, theme, step budget)
- Diagnostics shown in the terminal pane
- API keys and OAuth tokens — encrypted, see §3

All of it lives in the app's private storage (`/data/data/red.crishoper.agent`)
and is removed when you uninstall the app. Nothing is uploaded for backup: the
app sets `android:allowBackup="false"` and excludes its files from cloud backup.

## 3. How keys are stored

API keys are sealed with AES-256-GCM using a key that is generated inside the
**Android Keystore** and marked non-exportable. The ciphertext is written to
`filesDir/red.vault.bin` with a fresh random IV per write. The Keystore key never
leaves the TEE/StrongBox boundary, so a copy of `red.vault.bin` is useless on
another device.

Consequences you should know about:

- A factory reset destroys the ability to decrypt the vault. Re-enter your keys.
- Restoring a backup to a new phone will not carry the keys over.
- On a device where the OS itself is compromised, nothing on this list holds.

## 4. What we deliberately do not collect

- No analytics SDK, no crash reporter, no attribution library, no ad ID.
- No account, no email, no phone number, no device fingerprint.
- No telemetry ping on first launch. The app's only outbound traffic is the API
  call you asked for.

You can verify this by reading `AndroidManifest.xml` (only `INTERNET`,
`ACCESS_NETWORK_STATE` and `VIBRATE` are declared — none of them reads your data) and by watching the app with a proxy — both are ordinary requests to
the hosts you configured.

## 5. Retention and deletion

| Data | Lives until |
|---|---|
| An operation | You delete it, or clear all operations in Settings |
| Memory entries | You forget them, or clear memory in Settings |
| A key | You press *Forget*, or you uninstall the app |
| Sandbox files | The sandbox is killed — automatically at the end of a run, or on demand |

Deletion is immediate and local. There is no server copy to chase.

## 6. Children

The app is a developer/operator tool and is not directed at children. It requires
API keys that involve billing accounts.

## 7. Changes

Material changes to this notice will be committed to this file with a version
bump and called out in `CHANGELOG.md`. Because the notice ships inside the app,
the version you are running always contains the notice that applied to it.
