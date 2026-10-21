## What this changes

<!-- One paragraph. What behaviour is different after this merges? -->

## Why

<!-- The problem this solves. Link the issue if there is one: Closes #123 -->

## How it was verified

- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] Installed and exercised on a device/emulator (state the model and Android version)
- [ ] If visual: screenshot or short capture attached below

## Checklist

- [ ] Native only — no WebView, no bundled JS, no HTML in the app module
- [ ] No new dependency (or: explained below why the platform cannot do this)
- [ ] No telemetry, analytics, or crash reporting added
- [ ] No credential, token or key in source, tests, fixtures or logs
- [ ] Destructive actions still require an explicit confirmation
- [ ] User-facing strings are in `strings.xml`
- [ ] `CHANGELOG.md` updated for user-visible changes

## Notes for the reviewer

<!-- Trade-offs you are unsure about. Say so here rather than in the diff. -->
