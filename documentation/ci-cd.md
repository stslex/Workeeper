# CI/CD

This document covers every GitHub Actions workflow, the test-reporting actions they use, the
release pipeline (Fastlane + Play Store), required secrets, and the branch model. For local
test commands see [testing.md](testing.md). For lint mechanics see [lint-rules.md](lint-rules.md).

## Workflow inventory

All workflow files live under `.github/workflows/`.

| File | Trigger | Purpose |
|---|---|---|
| `android_build_unified.yml` | push to `master`, every `pull_request`, `workflow_dispatch` | detekt, Android Lint, build, unit tests, test reporting. Gates PRs. |
| `ui_tests.yml` | `workflow_dispatch` only | Optional smoke / regression UI tests on an emulator. Does not gate PRs. |
| `android_deploy_beta.yml` | `workflow_dispatch` only | Bumps version, generates a Play Store changelog, uploads to the Beta track via Fastlane, tags `beta-v<version>`. |
| `android_deploy_prod.yml` | `workflow_dispatch` only | Same flow targeting the production track, tags `release-v<version>`. |
| `github_release_apk.yml` | push of `release-*` tag, `workflow_dispatch` | Builds the store-release APK and creates a GitHub Release with a generated changelog. |
| `version_updater.yml` | `workflow_dispatch` only | Optional version bump and tag without deploying. |
| `claude.yml` | issue / PR review / comment events | Runs `anthropics/claude-code-action@v1` when `@claude` is mentioned. |
| `claude-code-review.yml` | `workflow_dispatch` only | Posts an automated PR review using Claude. |

The `pull_request` event has no branch filter on `android_build_unified.yml`, so the build
runs for PRs targeting any branch. UI tests have to be triggered manually.

## Build and unit-test workflow

`android_build_unified.yml` runs a single `build` job on `ubuntu-latest`.

### Setup steps

1. **Checkout** with `actions/checkout@v4`.
2. **Decrypt the keystore.** The `KEYSTORE` secret is a GPG-encrypted blob; the workflow pipes
   it through `gpg -d --passphrase "$KEYSTORE_PASSPHRASE" --batch keystore.jks.asc`.
3. **Java 21 (Temurin)** with `actions/setup-java@v4` and Gradle cache enabled.
4. **Generate `keystore.properties`** from the `KEYSTORE_KEY_ALIAS`,
   `KEYSTORE_KEY_PASSWORD`, and `KEYSTORE_STORE_PASSWORD` secrets so Gradle can sign the dev
   debug builds it needs for testing.
5. **Decode `google-services.json`** for both variants from the `GOOGLE_SERVICES_JSON_STORE`
   and `GOOGLE_SERVICES_JSON_DEV` secrets (each is a base64-encoded copy of the file).
6. **Copy CI-tuned Gradle properties** from `.github/properties/gradle-ci.properties` to
   `gradle.properties` and `.github/properties/gradle-convention-ci.properties` to
   `build-logic/gradle.properties`. These override local memory settings for CI.
7. **Restore Gradle build cache** via `actions/cache@v4` keyed on
   `settings.gradle.kts`, every `**/build.gradle.kts`, `gradle/libs.versions.toml`, and
   `gradle.properties`.

### Verification steps

```bash
./gradlew detekt --full-stacktrace
./gradlew lintDebug --no-configuration-cache --full-stacktrace
./gradlew build -x test --full-stacktrace
./gradlew testDebugUnitTest --full-stacktrace
```

`lintDebug` is run with `--no-configuration-cache` because the lint integration is not
configuration-cache compatible at the pinned Android Gradle Plugin version.

### Test reporting

Two reporting actions consume the JUnit XML output of `testDebugUnitTest`:

- `EnricoMi/publish-unit-test-result-action@v2` posts a sticky PR comment titled
  **Unit Test Results** with totals, deltas vs. the previous commit, and links to failing
  tests.
- `mikepenz/action-junit-report@v4` writes a job-summary table titled **Detailed Unit Test
  Report** with per-test execution times and stack traces.

Both actions read the same XML from `**/build/test-results/test*/*.xml` and
`**/build/test-results/**/*.xml`.

### Artifacts

- `detekt-reports` — every `**/build/reports/detekt/` plus `detekt.yml` (kept 30 days).
- `lint-reports` — `**/build/reports/lint-results-*.{html,xml}` plus `lint.xml` (kept 30 days).
- PR annotations on lint findings via `yutailang0119/action-android-lint@v4`.

## UI test workflow

`ui_tests.yml` is `workflow_dispatch`-only and exposes a `test_suite` choice
(`smoke` / `regression` / `all`). Two parallel jobs (`smoke-tests` and `regression-tests`) gate
their own execution with `if: inputs.test_suite == 'smoke' || inputs.test_suite == 'all'` (and
similarly for regression). Both jobs:

1. Enable KVM permissions on the runner.
2. Set up JDK 21 and the Android SDK via `android-actions/setup-android@v3`.
3. Decrypt the keystore, write `keystore.properties`, decode both `google-services.json` files.
4. Restore the Gradle build cache and the AVD snapshot cache (keyed on
   `api-level/target/arch`).
5. Use `reactivecircus/android-emulator-runner@v2` to boot an emulator
   (API 34, `google_apis`, `x86_64`) with `-no-window -gpu swiftshader_indirect -noaudio`.
6. Capture `adb logcat` to a file in the background.
7. Run `./gradlew connectedDebugAndroidTest` filtered by the `Smoke` or `Regression` annotation
   (see [testing.md](testing.md#running-tests) for the exact `-P` argument).

### Reporting

The smoke job publishes:

- **Smoke UI Test Results (API 34)** — `EnricoMi/publish-unit-test-result-action@v2`.
- **Detailed Smoke Test Report (API 34)** — `mikepenz/action-junit-report@v4`.

The regression job publishes the analogous **Regression UI Test Results (API 34)** and
**Detailed Regression Test Report (API 34)**.

### Artifacts

- `smoke-test-reports-api-34` / `regression-test-reports-api-34` — the full HTML report tree
  and raw XML (kept 30 days).
- `logcat-smoke-api-34` / `logcat-regression-api-34` — the captured logcat (kept 7 days).
- `screenshots-smoke-api-34` / `screenshots-regression-api-34` — `connected_android_test_additional_output`
  uploaded only on failure (kept 14 days).

## Release pipeline

### Fastlane

Configuration: `fastlane/Appfile`, `fastlane/Fastfile`, `fastlane/metadata/`. The Ruby
toolchain comes from the root `Gemfile` (which only declares the `fastlane` gem). Lanes:

- `fastlane test` — runs `gradle test`.
- `fastlane crashlytics` — `gradle clean :app:store:assembleRelease` then a `crashlytics` step.
- `fastlane beta` — `gradle clean :app:store:bundle`, then
  `upload_to_play_store(track: 'beta')`.
- `fastlane deploy` — `gradle clean :app:store:bundle`, then `upload_to_play_store` (the
  default production track).
- `fastlane build` — `gradle clean :app:store:bundle`.

`Appfile` reads the Play Console service-account JSON from `./play_config.json` and pins the
package name to `io.github.stslex.workeeper`.

### Beta and production deployments

Both `android_deploy_beta.yml` and `android_deploy_prod.yml` are manually triggered. They share
this flow:

1. Decrypt the keystore.
2. Run `./.github/scripts/update_versions.sh` to bump `versionName` / `versionCode` in
   `gradle/libs.versions.toml`.
3. Read the new version values back from the TOML.
4. Resolve the previous tag (`beta-v*` first if it exists, otherwise `release-v*`, otherwise
   the first commit).
5. Run `./.github/scripts/generate_changelog.sh "$FROM_TAG" "$TO_TAG" play "$VERSION_CODE"` to
   write Play Store metadata (the `play` mode lays out the changelog under
   `fastlane/metadata/`).
6. Set up Ruby 3.3, install bundled gems with cache.
7. Set up JDK 21, write `keystore.properties`, decode `play_config.json` and the store
   `google-services.json`.
8. Run `bundle exec fastlane beta` or `bundle exec fastlane deploy`.
9. Commit the version bump and changelog under the `github-actions[bot]` identity.
10. Create an annotated tag `beta-v<version>` or `release-v<version>` and push using the
    `PUSH_TOKEN` secret.

### GitHub APK release

`github_release_apk.yml` triggers on either a manual dispatch (with optional `tag_name` input)
or a push of any `release-*` tag. The job:

1. Validates the Gradle wrapper with `gradle/wrapper-validation-action@v2`.
2. Builds `:app:store:assembleRelease`.
3. Locates the resulting APK under `app/store/build/outputs/apk/release/`.
4. Resolves the current and previous `release-*` tags, then runs
   `./.github/scripts/generate_changelog.sh ... github` to format a Markdown changelog.
5. Uses `softprops/action-gh-release@v2` to create a GitHub Release named after the tag and
   attaches the APK. Releases whose tag contains `alpha`, `beta`, or `rc` are flagged
   pre-release.

### Version updater (no deploy)

`version_updater.yml` is `workflow_dispatch`-only with `channel` (`beta` / `release`) and
`bump` (`true` / `false`) inputs. When `bump=true` it runs the version-bump script; either way
it generates the changelog, commits, tags `beta-v<version>` or `release-v<version>`, and
pushes. Use this when the deploy needs to be split from the version bump.

### Changelog scripts

- `.github/scripts/update_versions.sh` increments `versionName` and `versionCode` in
  `gradle/libs.versions.toml`.
- `.github/scripts/generate_changelog.sh <from-tag> <to-tag> <mode> [<version-code>]` produces
  either a Play Store metadata file or a Markdown body, depending on `<mode>`
  (`play` / `github`).

## AI-integration workflows

- `claude.yml` — runs `anthropics/claude-code-action@v1` when an issue, PR review, PR review
  comment, or issue comment contains `@claude`. Pulls the OAuth token from
  `CLAUDE_CODE_OAUTH_TOKEN`.
- `claude-code-review.yml` — `workflow_dispatch`-only at present (the `pull_request` trigger is
  commented out). Posts a structured PR review via `gh pr comment`.

Neither workflow is required for normal contribution.

## Required secrets and config files

Configured under repository secrets in GitHub:

| Secret | Used by | Purpose |
|---|---|---|
| `KEYSTORE` | every job that signs | GPG-encrypted Android keystore (`keystore.jks.asc`). |
| `KEYSTORE_PASSPHRASE` | every job that signs | Passphrase for the GPG decrypt. |
| `KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`, `KEYSTORE_STORE_PASSWORD` | every job that signs | Written into the generated `keystore.properties`. |
| `GOOGLE_SERVICES_JSON_STORE`, `GOOGLE_SERVICES_JSON_DEV` | build / UI / release | Base64 of the per-variant `google-services.json`. |
| `PLAY_CONFIG_JSON` | beta / prod deploy | Base64 of the Play Console service-account JSON used by Fastlane. |
| `PUSH_TOKEN` | beta / prod deploy / version updater | Token used to push the version-bump commit and the release tag back to the repo. |
| `CLAUDE_CODE_OAUTH_TOKEN` | `claude.yml`, `claude-code-review.yml` | Auth for `anthropics/claude-code-action`. |

Generated at build time on CI (never committed):

- `keystore.jks` (decrypted from `KEYSTORE`).
- `keystore.properties` (built from the keystore secrets).
- `app/dev/google-services.json` and `app/store/google-services.json`.
- `play_config.json` (deploy jobs only).

CI Gradle property overrides live under `.github/properties/`:

- `gradle-ci.properties` is copied over `gradle.properties` to tune memory and parallelism.
- `gradle-convention-ci.properties` is copied over `build-logic/gradle.properties`.

For local development, `keystore.properties` and the `google-services.json` files are not
checked in; see [README.MD](../README.MD#requirements) for the local setup steps.

## Check-name reference

Each reporting action in CI uses a unique `check_name` so the per-suite checks coexist on a PR
without overwriting each other.

| Workflow | Action | `check_name` |
|---|---|---|
| `android_build_unified.yml` | `EnricoMi/publish-unit-test-result-action@v2` | `Unit Test Results` |
| `android_build_unified.yml` | `mikepenz/action-junit-report@v4` | `Detailed Unit Test Report` |
| `ui_tests.yml` (smoke job) | EnricoMi | `Smoke UI Test Results (API 34)` |
| `ui_tests.yml` (smoke job) | mikepenz | `Detailed Smoke Test Report (API 34)` |
| `ui_tests.yml` (regression job) | EnricoMi | `Regression UI Test Results (API 34)` |
| `ui_tests.yml` (regression job) | mikepenz | `Detailed Regression Test Report (API 34)` |

When adding new reporting jobs (e.g. for additional API levels or test types), pick a unique
`check_name` for both the EnricoMi `check_name` and `comment_title` and the mikepenz
`check_name` to avoid clobbering existing checks.

## Branch model

- `master` is the long-lived main branch. Pushes to `master` retrigger the unified build.
- `dev` is used for ongoing development; PRs typically open against `dev`. The unified build
  runs for any PR target.
- Release tags follow `beta-v<version>` and `release-v<version>` and are produced by the deploy
  workflows. Pushing a `release-*` tag triggers `github_release_apk.yml` automatically.
- The pre-commit hook (`.githooks/pre-commit`, installed via `setup-hooks.sh`) is currently
  disabled at the script level — the hook returns early without running checks. CI is the
  enforcement point for detekt and lint until the hook is re-enabled. See
  [lint-rules.md](lint-rules.md#pre-commit-hook) for details.
