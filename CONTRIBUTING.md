# Contributing

Thanks for taking the time. This is a small, opinionated codebase; a little
context up front saves a lot of review round-trips.

## Ground rules

1. **Native only.** No WebView, no bundled JavaScript, no HTML assets in the app
   module. CI enforces this (`checks.yml`).
2. **No new dependencies without a reason.** Every library is attack surface and
   APK size. Say what the library does that the platform cannot.
3. **No telemetry.** Pull requests that add analytics, crash reporting, or ad SDKs
   will be closed without discussion.
4. **Never commit a credential.** Keys belong in the vault at runtime, never in
   source, tests, fixtures or CI logs. `checks.yml` scans for credential shapes.
5. **The system prompt is a single source of truth.** Edit `system_prompt.md` at
   the repository root. Do not create a second copy anywhere.

## Development setup

```bash
git clone https://github.com/srijan7815-bit/crishoper-red.git
cd crishoper-red/android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

JDK 17 and Android SDK 34 are required. On a machine with less than ~4 GB of
available RAM, add `-Pred.release.minify=false` for release builds; R8 needs
headroom that the rest of the build does not.

## Before you open a pull request

```bash
./gradlew :app:compileDebugKotlin   # must pass
./gradlew :app:lintDebug            # should not add warnings
./gradlew :app:testDebugUnitTest    # must pass
```

Then check the four things reviewers always look at:

- [ ] Does it touch a screen the user can reach without a key? (onboarding path)
- [ ] Does it add a destructive action without a confirmation step?
- [ ] Does it log anything derived from a secret?
- [ ] Does it add a string that is not in `strings.xml`?

## Commit messages

Conventional Commits, scoped by area:

```
feat(chat): collapse tool steps into one summary block
fix(vault): rotate IV per write
docs(privacy): describe Drive scope usage
perf(list): key step cards by call id
```

## Pull request shape

Small and single-purpose beats large and complete. If a change touches the design
system *and* the engine, split it. Include a screenshot or a short screen capture
for anything visual — the maintainer reviews on a phone, not a colour-calibrated
monitor.

## Reporting bugs

Use the issue template. The two things that turn a shrug into a fix are the exact
build (`versionName` from Settings › About) and the step stream that led to the
problem — expand the collapsed steps and paste the failing call.

Do not paste API keys, tokens, or full conversation content into an issue. Redact
them; if that makes the report meaningless, use the private channel in
[SECURITY.md](SECURITY.md).

## Code of conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
