# Changelog

All notable changes to this project are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [3.0.0] — 2026-10-22

### Changed
- **Operator prompt revised.** `system_prompt.md` was rewritten by the operator
  (3,972 bytes, SHA-256 prefix `9f4d814a`) and is embedded into the APK at build
  time by the `Sync` task. Settings › About shows the full hash, so any build can
  be checked against the prompt it claims to carry.
- `versionCode` 3 · `versionName` 3.0.0. Same signing key as 2.0.0 and 1.0.0, so
  it installs over either.

### Unchanged
- No source changes to the agent engine, tools, vault, or UI in this release. The
  only behavioural difference is the prompt the model receives.

### Note on the build
- Shipped without R8 shrinking (`-Pred.release.minify=false`), as in 2.0.0, because
  the publishing machine could not allocate the ~3 GB R8 needs. The artifact is
  signed and verified; it is simply larger than a minified build. `ci/workflows/`
  builds the minified variant on a normal runner.

## [Unreleased]

### Planned
- On-device summarisation for long operations
- Concurrent operations with per-operation sandbox quotas
- Operation archive export (Markdown + JSON)

## [2.0.0] — 2026-10-21

The native rewrite. The 1.x line was a WebView shell around a bundled web UI; it
worked, and it was the wrong architecture for this app. Everything below the
glass is new.

### Added
- **Native Jetpack Compose application.** No WebView, no bundled JavaScript, no
  hybrid bridge anywhere in the APK.
- **Agent engine with a bounded step budget.** Plan-act-observe loop, per-tool
  result truncation, repeated-failure correction, and a stop button that cancels
  in flight.
- **Tools:** `web_search` (Tavily), `terminal` / `read_file` / `write_file`
  (E2B micro-VM), `remember` / `recall`, `drive_save`.
- **Encrypted vault.** AES-256-GCM with a non-exportable Android Keystore key;
  keys are validated with a live probe before they are accepted.
- **Onboarding** with per-key validation and a demo mode that needs no keys.
- **Terminal pane** mirroring sandbox activity, with a kill switch.
- **Operations drawer** — conversations as named, resumable, deletable operations.
- **Adaptive layout** driven by the device's width class, aspect ratio and font
  scale.
- **Ink and paper themes**, both contrast-checked, following the system setting.
- **CI**: lint, unit tests, compile guard, APK build with signature and manifest
  verification, tag-driven releases with checksums.

### Changed
- Settings, memory, and About are now scrollable end-to-end; the previous build
  clipped long panels on small screens.
- The splash animation runs once per process instead of once per resume, so
  reopening the app no longer replays the logo.
- The logo from the brand asset is used for the launcher icon, the splash and the
  About header, replacing the 3D orb.
- Interaction cost: fades and drawer motion only. No shaders, no 3D objects.

### Removed
- The WebView container, the bundled web bundle, and the JS build toolchain.
- The 3D orb renderer and its asset pipeline.

### Fixed
- Credential strings are redacted in every UI surface that previews a key.
- Deleting an operation, clearing memory, and killing a sandbox now require an
  explicit second confirmation.
- The step list is visible during a run and collapses into a single summary block
  in the final answer.

### Known limitations
- Operation content is stored as plain JSON in app-private storage; only the
  credentials go through the vault. See `docs/SECURITY.md` §9.
- Keys do not survive a factory reset or a restore to a different device.

## [1.0.0] — 2026-10-19

- Initial public build: WebView shell, bundled agent UI, BYOK settings for
  OpenRouter, Tavily and E2B. Superseded by 2.0.0.
