# Inactive CI definitions

These are the real workflows for this project — lint, unit tests, a compile
guard, a signed release on tag, and Dependabot-friendly caching. They are **not
active** because they live outside `.github/workflows/`.

## Why they are here and not where they belong

GitHub refuses any push that creates or updates a file under `.github/workflows/`
unless the pushing token carries the `workflow` scope. The token used to publish
this repository had `repo`, `admin:org`, `admin:public_key`, `admin:repo_hook`,
`delete:packages` and `write:packages` — deliberately, and not `workflow`.

Rather than ship a README that advertises a green build badge for a workflow that
does not exist, the definitions are versioned here, inactive, and reviewable.

## Enabling them

Either:

**A. Push with a scoped token.** Create a classic PAT with `repo` + `workflow`,
then:

```bash
mkdir -p .github/workflows
cp ci/workflows/*.yml .github/workflows/
git add .github/workflows && git commit -m "ci: activate android, checks, release"
git push
```

…or paste each file's contents into GitHub's *Add file → Create new file* UI at
`.github/workflows/<name>.yml`, which does not require the scope.

**B. Use the GitHub web editor.** Same result, no local token involved.

## What runs once active

| File | Trigger | Does |
|---|---|---|
| `android.yml` | push to `main`, PR, manual | Lint → unit tests → `assembleDebug`/`assembleRelease` → `apksigner` + `aapt2` verification → APK artifact |
| `checks.yml` | every push/PR | prompt-size guard, committed-secret scan, no-web-payload guard, Kotlin compile |
| `release.yml` | tag `v*` | signed release, `sha256sum`, GitHub Release with the APK attached |

## Required secrets for signed releases

| Secret | Contents |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 red-release.jks` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Without them `android.yml` still builds an unsigned debug APK; `release.yml`
fails loudly rather than publishing an unsigned "release".
