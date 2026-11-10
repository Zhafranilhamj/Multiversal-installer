# Releases

Two release paths, one per audience. They share the version numbers and the changelogs, and
nothing else.

| | Sideload APK | Google Play |
|---|---|---|
| Workflow | `.github/workflows/release-apk.yml` | `.github/workflows/publish-release.yml` |
| Trigger | `workflow_dispatch` only | push of a `v*` tag **and** `PLAY_DEPLOY_ENABLED=true` |
| Flavor | `opensource` | `play` |
| Output | three signed APKs on a GitHub release | three AABs on a Play track |
| Secrets needed | keystore only | keystore + `PLAY_STORE_CONFIG_JSON` + `GOOGLE_SERVICES_JSON` |

## Version numbers

Per `.claude/skills/upgrade-app/SKILL.md`:

- **One unified `versionName`** across every module bumped in that release.
- **`versionCode` bands** keep the three form factors distinguishable under one Play package name:
  phone 1–999, Wear OS 1000–1999, TV 2000–2999.
- **Bump only modules that changed** since the last tag. All three depend on `:core`, so a `:core`
  change bumps all three.

Current: phone 49 / 1.23.1, TV 2036 / 1.23.1, Wear OS 1043 / 1.23.1.

## Changelogs

Google Play caps a changelog at **500 characters**. Check with `wc -c` before pushing; the bullet
marker `•` is three bytes in UTF-8, so the byte count is the number that matters.

- Phone: `fastlane/metadata/android/en-US/changelogs/<app-versionCode>.txt`
- TV: `fastlane/metadata/android/en-US/changelogs/<tv-versionCode>.txt`
- Wear OS: `fastlane/metadata/android/en-US/changelogs/<wear-versionCode>.txt`

All three live in the *phone* metadata directory: `fastlane/Fastfile` calls `upload_to_play_store`
without a `metadata_path`, so supply defaults to `./fastlane/metadata/android` for every module.
`fastlane_tv/` and `fastlane_wearos/` are leftovers — do not add new files there.

`release_notes_v<versionName>.md` in the same directory is the GitHub release body. It carries one
section per changed form factor (`### 📱 Phone (App)`, `### 📺 Android TV`, `### ⌚ Wear OS`), which
`GEMINI.md` requires, and `release-apk.yml` prefers it over the per-code `.txt` files.

Never put issue numbers in a changelog; it is user-facing text.

## Sideload release (`release-apk.yml`)

Dispatch it from the branch or tag you want to ship — it reads the versions out of the modules'
`build.gradle.kts`, so it names itself correctly from any ref:

```bash
gh workflow run release-apk.yml --ref main -f prerelease=true
```

It runs the five unit-test tasks, builds `:app:assembleOpensourceRelease`,
`:tv:assembleOpensourceRelease` and `:wearos:assembleRelease`, verifies each APK with `apksigner`,
writes `SHA256SUMS` and `signature.txt`, and creates release `v<app-versionName>` with the tag on
the dispatched commit. Dispatching again for the same version replaces the assets (`--clobber`).

`workflow_dispatch` only works for a workflow that exists on the default branch. A brand-new
release workflow has to be merged to `main` before it can be dispatched.

The tag it creates does **not** re-trigger other workflows: it is pushed with `GITHUB_TOKEN`, and
GitHub suppresses workflow runs caused by that token.

## Play release (`publish-release.yml`)

The job is gated on the repository variable **`PLAY_DEPLOY_ENABLED`**:

```
Settings → Secrets and variables → Actions → Variables → PLAY_DEPLOY_ENABLED = true
```

Without it the job is skipped. That is deliberate. The deploy needs `PLAY_STORE_CONFIG_JSON` and
`GOOGLE_SERVICES_JSON`, and a *missing* `GOOGLE_SERVICES_JSON` is worse than a missing secret: the
workflow writes an empty `app/src/play/google-services.json`, which makes `hasFirebaseConfig` true,
applies the google-services plugin against a blank file, and fails the build with no message that
points at the cause. `release-apk.yml` also creates `v*` tags, and those must not start a Play
deploy at all.

Before a Play release, `GEMINI.md` requires checking that `fastlane/Fastfile` and
`publish-release.yml` still agree on the build tasks (`:app:bundlePlayRelease`,
`:tv:bundlePlayRelease`, `:wearos:bundleRelease`) and the artifact paths.

## Signing

Both workflows decode `KEYSTORE_BASE64` to `$RUNNER_TEMP/release.jks` and hand
`STORE_PASSWORD` (falling back to `KEYSTORE_PASSWORD`), `KEY_ALIAS` and `KEY_PASSWORD` to
`.github/scripts/PrepareSigning.java`, which opens the keystore, checks the alias really is a
private key, and writes `key.properties`. It fails with a readable message and prints no
credentials. `key.properties` and `*.jks` are gitignored and removed in an `if: always()` step.

APKs signed with a different key than the installed build cannot upgrade in place — the user has to
back up settings and uninstall first.

## Room schemas

Both databases export their schema (`exportSchema = true`, `room.schemaLocation` set through KSP).
The JSON under `app/schemas/` and `updater/schemas/` is committed, and `build-debug.yml` fails if an
entity changed without it. To regenerate after a schema change, dispatch `build-debug.yml` with
`update_room_schemas=true`; it commits the KSP output back to the dispatched branch.

## CI diagnostics

Runner logs and uploaded artifacts are only readable from a browser session. On failure,
`build-debug.yml` and `release-apk.yml` filter the Gradle log through
`.github/scripts/summarize-gradle-failure.sh` and publish the result as a **check run** plus
`::error::` annotations, which are reachable from the REST API:

```bash
SHA=$(git rev-parse HEAD)
for id in $(gh api "repos/OWNER/REPO/commits/$SHA/check-runs" --jq '.check_runs[].id'); do
  gh api "repos/OWNER/REPO/check-runs/$id/annotations" --jq '.[].message'
done
```
