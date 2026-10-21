# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 3.x | ✅ |
| 2.x | ✅ — security fixes only |
| 1.x (legacy WebView build) | ❌ — superseded, please upgrade |

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private reporting channel:

1. Go to the **Security** tab of this repository.
2. Choose **Report a vulnerability**.
3. Describe the issue, the affected build (version + certificate fingerprint if
   you have it), and the shortest reproduction you can manage.

If private reporting is unavailable to you, open a public issue containing only
the sentence *"I need a private channel for a security report"* — no details —
and a maintainer will contact you.

### What to expect

| Stage | Target |
|---|---|
| Acknowledgement | 3 business days |
| Triage & severity call | 7 business days |
| Fix for critical issues | 30 days from triage |
| Public disclosure | after a fixed release is available, coordinated with you |

We credit reporters in the release notes unless you ask us not to.

## In scope

- The shipped Android application (`red.crishoper.agent`).
- Credential handling: the vault, key derivation, and any path where a key,
  token, or prompt could be exposed to another app, a log, or a backup.
- Network handling: TLS validation, host allow-lists, request construction.
- The agent sandbox boundary: anything that lets model output escape into the
  device outside the sandbox or outside an explicit user confirmation.
- Build and release integrity: signing configuration, artifact verification,
  the CI workflows in `.github/workflows/`.

## Out of scope

- Vulnerabilities in OpenRouter, Tavily, E2B, or Google — report those upstream.
- Attacks that require a rooted device with the app's private directory already
  readable (the vault protects against extraction, not against a compromised OS).
- Denial of service against your own API quota.
- Content produced by the model. A model saying something wrong, offensive, or
  unsafe is a model-quality issue, not a vulnerability in this app — but a model
  *action* that bypasses the destructive-action gate is very much in scope.
- Social engineering of the maintainer.

## Hardening notes for operators

- Treat API keys as billing credentials: a leaked OpenRouter key can spend money.
  Use per-provider keys with a spend cap, and rotate them if you suspect exposure.
- The vault is bound to the Android Keystore. Keys do **not** survive a factory
  reset, and restoring an app backup to another device will require re-entering
  them. That is intentional.
- Disable USB debugging on any device holding production keys.
- Verify the APK certificate before installing a build you did not compile:

  ```bash
  apksigner verify --print-certs crishoper-red-2.0.0.apk
  ```

  The expected fingerprint is published with each GitHub Release.
