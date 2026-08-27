# CI/CD

This document covers every GitHub Actions workflow, the test-reporting actions they use, the
release pipeline (Fastlane + Play Store), required secrets, and the branch model. For local
test commands see [testing.md](testing.md). For lint mechanics see [lint-rules.md](lint-rules.md).

## Workflow inventory

All workflow files live under `.github/workflows/`.

| File | Trigger | Purpose |
|---|---|---|
| `android_build_unified.yml` | push to `master`, every `pull_request`, `workflow_dispatch` | Two jobs: `Build and Unit Tests` (detekt, Android Lint, build, unit tests, test reporting; Linux) and `KMP iOS kit smoke` (the kit Compose-scene and navigation serializer-registry native tests on `macos-26`). Gates PRs. |
| `ui_tests.yml` | weekly `schedule` (Mondays 05:00 UTC, against `dev`), `workflow_dispatch`, `workflow_call` | Smoke / regression UI tests on an emulator. Does not gate PRs; called by `android_deploy_prod.yml` with `test_suite=smoke`. |
| `mockup_gate.yml` | every `pull_request` **except** into `master`, `workflow_dispatch`, `workflow_call` | Runs `documentation/mockups/shell_gate.py` against the v3 shell mockup, plus its permanent known negative. Seconds; no emulator, no JDK, no secrets. |
| `pr_guard.yml` | `pull_request` into `master` only | Fails any PR into `master` whose head branch is not `release/release-v.X.Y.Z`. |
| `cut_release.yml` | `workflow_dispatch` only (`mode`: release / hotfix) | Bumps the version (minor from `dev`, patch from `master`), pushes a `release/release-v.X.Y.Z` branch and opens the release PR. |
| `sync_master_to_dev.yml` | push to `master` | Opens an automated PR propagating `master` (version bumps, hotfixes) back onto `dev`. |
| `android_deploy_beta.yml` | `workflow_dispatch` only | Bumps version, generates a Play Store changelog, uploads to the Beta track via Fastlane, tags `beta-v<version>`. |
| `android_deploy_prod.yml` | `workflow_dispatch` only | Same flow targeting the production track, tags `release-v<version>`. |
| `github_release_apk.yml` | push of `release-*` tag, `workflow_dispatch` | Builds the store-release APK and creates a GitHub Release with a generated changelog. |
| `claude.yml` | issue / PR review / comment events | Runs `anthropics/claude-code-action@v1` when `@claude` is mentioned. |
| `claude-code-review.yml` | `workflow_dispatch` only | Posts an automated PR review using Claude. |

The `pull_request` event has no branch filter on `android_build_unified.yml`, so the build
runs for PRs targeting any branch. `mockup_gate.yml` runs on every PR *except* those targeting
`master`, and `pr_guard.yml` runs *only* on those. UI tests run on the weekly schedule, on
manual dispatch, or inside the production deploy — never on a PR.

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
./gradlew assembleDebug --full-stacktrace
./gradlew assembleDebugAndroidTest --full-stacktrace   # compiles the instrumented tests; running them still needs a device
./gradlew verifyPaparazziDebug --full-stacktrace   # visual gate, before anything can rewrite the tree
./gradlew :lint-rules:test --full-stacktrace       # the custom detekt rules, before detekt consumes them
./gradlew detekt --full-stacktrace
python3 documentation/personal_data_gate.py -v     # no real names/emails in tracked files
./gradlew lintDebug --no-configuration-cache --full-stacktrace
./gradlew testDebugUnitTest --full-stacktrace
```

Order is load-bearing twice over. `verifyPaparazziDebug` runs first so the goldens are compared
against the tree as checked out, before any step could rewrite it. `:lint-rules:test` runs before
`detekt`, since detekt is what consumes the jar those tests cover.

Every repo-wide spelling above also covers the KMP-shaped `:core:ui:kit`: the KMP conventions
register `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` and
`verifyPaparazziDebug` as lifecycle aliases onto the real KMP tasks (`assemble`,
`testAndroidHostTest`, `lint`, `assembleAndroidDeviceTest`, `verifyPaparazziAndroidMain`), so a
converted module cannot silently vanish from these steps.

`lintDebug` is run with `--no-configuration-cache` because the lint integration is not
configuration-cache compatible at the pinned Android Gradle Plugin version. That flag is about the
*configuration* cache and says nothing about the build cache — the two are independent.

### Test reporting

Two reporting actions consume the JUnit XML output of `testDebugUnitTest`:

- `EnricoMi/publish-unit-test-result-action@v2` posts a sticky PR comment titled
  **Unit Test Results** with totals, deltas vs. the previous commit, and links to failing
  tests.
- `mikepenz/action-junit-report@v4` writes a job-summary table titled **Detailed Unit Test
  Report** with per-test execution times and stack traces.

Both actions read the same XML from `**/build/test-results/test*.xml` and
`**/build/test-results/**/*.xml` (the second glob is the one that matches Gradle's
per-task `test*/` output directories; the first is flat-file belt-and-braces).

### Artifacts

- `detekt-reports` — every `**/build/reports/detekt/` plus `detekt.yml` (kept 30 days).
- `lint-reports` — `**/build/reports/lint-results-*.{html,xml}` plus `lint.xml` (kept 30 days).
- PR annotations on lint findings via `yutailang0119/action-android-lint@v4`.

## Mockup appearance gate

`mockup_gate.yml` runs `documentation/mockups/shell_gate.py`, which gates
`documentation/mockups/pass2d.html` — the appearance contract the eight screens of the v3 arc are
built from. Nine checks: `:root` unchanged against a baseline unless declared, no undefined
`var()`, no new hex literal, tags balanced, the section switcher complete, exactly one default
screen, two **render** checks driven through headless Chromium, and token parity between the
mockup's `:root` and `AppColors.kt`.

One job, `mockup-gate`, on `ubuntu-latest`, `timeout-minutes: 10`. It needs no keystore, no
`google-services.json` and no secret of any kind, so it also runs on fork PRs. Five things about
it are deliberate and are commented at length in the workflow itself:

- **`fetch-depth: 0`.** Checks 1 and 3 read a baseline blob with `git show <base>:<path>`, and the
  known negative reads two historical commits. Under the default depth-1 clone every one of those
  dies as `fatal: invalid object name` before a check runs. This is the one setting the job must
  not copy from `android_build_unified.yml`, which passes only `ref:`.
- **The baseline is `git merge-base origin/<github.base_ref> HEAD`**, falling back to `dev` outside
  a pull request. Not `dev` unconditionally: for a stacked PR the base branch is the branch below,
  and a `dev` baseline pulls the parent PR's own reviewed `:root` change into the diff of the PR
  being gated. The script's header explains at length why the baseline must never already contain
  the change under test.
- **A `:root` change is declared in git, not in the invocation.** The workflow reads an
  `Allow-root-change: rust, meta, molten` trailer off the commits in the range and passes those
  names to `--allow-root-change`. A flag hard-coded into the workflow would allow every future
  change silently. The script still requires the actual diff to match the declared names exactly,
  in both directions.
- **The known negative runs every time and must go red.** `--target f52462c7` reproduces a real
  escape — a nav indicator measuring zero width while six structural checks certified the file.
  The step asserts exit 1 *and* that check 7 is the failure *and* that it failed at
  `width=0px→0px`, because checks 6 and 9 also fail at that ref and an exit-code-only assertion
  would survive check 7 quietly ceasing to discriminate.
- **The browser is Google Chrome installed from Google's deb, and it is asserted to be the one
  used.** Every *unpacked* build hangs under the probe's flags and dies on the script's 90s cap —
  measured on a runner: the image's `/usr/bin/chromium` snapshot, Chrome for Testing 150 and 151,
  and Chromium snapshot 153 all hang; the deb completes in 1.4s. Packaging is the discriminator,
  not version. `chrome-headless-shell` completes too but lays the page out differently (pill 113px
  against 129px everywhere else), so it is not an acceptable substitute for an appearance gate.
  Because the image's hanging `chromium` is the *first* name the script looks for and the deb lands
  third, a `$GITHUB_PATH` shadow points `chromium` at the deb — without it the job times out rather
  than failing quietly. An assertion step fails if the resolved binary is not the installed deb. A
  missing browser is a FAIL in the script by design; there is no `continue-on-error` and no skip
  input anywhere in this workflow.

Trigger scope is by **branch, never by path**. PRs into `master` are excluded because `pr_guard.yml`
already restricts those to `release/release-v.X.Y.Z` roll-ups of commits reviewed on `dev`, and
because `master` carries no `documentation/mockups/` at all, so the baseline blob cannot be read. A
paths filter is separately wrong: check 9 reads `AppColors.kt` as well as the mockup, and the drift
it was written for came from the palette moving in Kotlin while the drawing stayed still.

To reproduce a CI result locally:

```bash
# PR_BASE is the branch the PR targets — the branch below you if the PR is stacked, not `dev`.
PR_BASE=dev
python3 documentation/mockups/shell_gate.py --base "$(git merge-base "origin/$PR_BASE" HEAD)" -v
python3 documentation/mockups/shell_gate.py --target f52462c7   # must exit 1
```

Whether `Mockup Appearance Gate` is required to merge is a branch-protection setting, not a
property of the workflow.

## KMP iOS kit smoke job

The unified workflow's second job (`KMP iOS kit smoke`, `runs-on: macos-26`) is the stable
required context for the Phase-7 native tests. One forced Gradle invocation executes
`:core:ui:kit:iosSimulatorArm64Test` (the non-vacuous Kotlin/Native Compose-scene test that
renders a resource-backed kit composition) and `:core:ui:navigation:iosSimulatorArm64Test`
(the fixed-catalog round trip of all 12 routes through the production
`screenSavedStateConfiguration` registry), with `--continue` so one module's failure cannot
mask whether the other ran. The job selects `/Applications/Xcode_26.6.app` explicitly, asserts
`xcodebuild -version` and the presence of an iOS simulator runtime before Gradle, and provisions
an ephemeral throwaway JKS with `keytool` (the repository configuration reads signing material
at configuration time; no production secret is used).

After Gradle, `.github/scripts/assert_kmp_ios_smoke.py` parses each module's JUnit XML
structurally. Per module it requires: the result directory exists with at least one parseable
`TEST-*.xml` and at least one `<testsuite>`; the declared aggregate `tests` equals the number of
parsed `<testcase>` elements and is at least one; aggregate `skipped` / `failures` / `errors` are
zero and no case carries a `<failure>`, `<error>` or `<skipped>` child; and the expected
normalized `(classname, name)` tuple occurs **exactly once**. Additional *passing* cases are
allowed, so a module can grow a second native test without editing the script — nothing weakens,
because every extra case must still pass and still be counted, and a suite declaring more cases
than it emitted is inconsistent XML rather than evidence. A repo-wide total or a substring match
could not vouch for a test that vanished; a classname from one case paired with a method name
from another cannot forge an identity.

The assertion step is bound to the Gradle step's id (`native_tests`) and runs on
`!cancelled() && steps.native_tests.outcome != 'skipped'` — that is, whenever the Native Gradle
step actually **started**, red or green. A red native run is exactly when the per-module XML is
worth reading, and reporting only Gradle's exit code there would not say which module or which
tuple broke. It is skipped when the job is cancelled, and when the Gradle step never ran because
an earlier setup step (checkout, Xcode selection, JDK, signing material) failed — there is no
fresh XML to judge then, only a previous run's leftovers. Both result directories upload under
`if: always()` regardless.

The job builds no Xcode app, signs no Apple bundle and uploads no framework. See
[kmp-phase-7-1-ui-kit.md](feature-specs/kmp-phase-7-1-ui-kit.md) §9 for the context's origin and
required-ruleset status, and
[kmp-phase-7-2-navigation.md](feature-specs/kmp-phase-7-2-navigation.md) §9 for the expanded
payload and §19 for the hardening evidence.

## UI test workflow

`ui_tests.yml` triggers three ways: a weekly `schedule` (cron `0 5 * * 1` — Mondays
05:00 UTC), `workflow_dispatch` exposing a `test_suite` choice (`smoke` / `regression` /
`all`), and `workflow_call` taking `test_suite` plus a `ref` to test.
`android_deploy_prod.yml` calls it with `test_suite=smoke` as a deploy gate, skippable on
retries via its `skip_ui_tests` input.

GitHub evaluates `schedule:` only from the workflow file on the DEFAULT branch
(`master`), so the cron activates once the file reaches `master` with a release. A
scheduled run checks out `dev` — where the work is — and runs both suites; because
`github.sha` on a cron run is the default branch's tip rather than the tree under test,
each job resolves the tested commit (`git rev-parse HEAD`) and the result publishers
attach to that SHA. The weekly cadence bounds assertion-level rot at 7 days (rationale:
[nav3-stage-1-3.md §5](feature-specs/nav3-stage-1-3.md)).

Two parallel jobs (`smoke-tests` and `regression-tests`) gate their own execution with
`if: github.event_name == 'schedule' || inputs.test_suite == 'smoke' || inputs.test_suite
== 'all'` (and similarly for regression). Both jobs:

1. Enable KVM permissions on the runner.
2. Set up JDK 21 and the Android SDK via `android-actions/setup-android@v3`.
3. Decrypt the keystore, write `keystore.properties`, decode both `google-services.json` files.
4. Restore the Gradle build cache (with `save-always: true`, so a run warms the cache it
   depends on even when a test goes red) and the AVD snapshot cache (keyed on
   `api-level/target/arch`).
5. Assemble everything **before the emulator exists** (`./gradlew assembleDebug
   assembleDebugAndroidTest`), then stop the Gradle daemons — compiling the androidTest
   legs concurrently with a 4 GB emulator is what killed runners; with the APKs prebuilt,
   the connected phase is installs + instrumentation with near-zero compile.
6. Use `reactivecircus/android-emulator-runner@v2` to boot an emulator
   (API 34, `google_apis`, `x86_64`) with `-no-window -gpu swiftshader_indirect -noaudio`.
7. Capture `adb logcat` to a file in the background.
8. Run `./gradlew connectedDebugAndroidTest` filtered by the `Smoke` or `Regression`
   annotation (see [testing.md](testing.md#running-tests) for the exact `-P` argument),
   with a small heap for the connected phase
   (`-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2`) so the emulator keeps its headroom.

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

### Version updater (no deploy) — removed

`version_updater.yml` no longer exists: the release-flow migration deleted it, and its
version-bump role now lives in `cut_release.yml` (which bumps on the release branch it cuts).

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
| `PUSH_TOKEN` | beta / prod deploy, cut release, master→dev sync | Token used to push the version-bump commit and the release tag back to the repo. |
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
| `android_build_unified.yml` | job check run (no reporting action) | `KMP iOS kit smoke` |
| `android_build_unified.yml` | `mikepenz/action-junit-report@v4` | `Detailed Unit Test Report` |
| `ui_tests.yml` (smoke job) | EnricoMi | `Smoke UI Test Results (API 34)` |
| `ui_tests.yml` (smoke job) | mikepenz | `Detailed Smoke Test Report (API 34)` |
| `ui_tests.yml` (regression job) | EnricoMi | `Regression UI Test Results (API 34)` |
| `ui_tests.yml` (regression job) | mikepenz | `Detailed Regression Test Report (API 34)` |

When adding new reporting jobs (e.g. for additional API levels or test types), pick a unique
`check_name` for both the EnricoMi `check_name` and `comment_title` and the mikepenz
`check_name` to avoid clobbering existing checks.

## Toolchain pins

- The root `build.gradle.kts` `buildscript` block forces `org.jetbrains:annotations:23.0.0`
  (`resolutionStrategy`): AGP requires `annotations:23.0.0` while Gradle's embedded Kotlin pins
  `annotations:13.0` **strictly**, and forcing the higher version is what resolves that conflict.
  Removing the force reintroduces it. Recorded against AGP 9.1.0 / Gradle 9.3.1.

## Branch model

- `master` is the long-lived main branch. Pushes to `master` retrigger the unified build.
- `dev` is used for ongoing development; PRs typically open against `dev`. The unified build
  runs for any PR target.
- Release tags follow `beta-v<version>` and `release-v<version>` and are produced by the deploy
  workflows. Pushing a `release-*` tag triggers `github_release_apk.yml` automatically.
- The pre-commit hook (`.githooks/pre-commit`, wired via `setup-hooks.sh` setting
  `core.hooksPath`) runs `./gradlew detekt` on every commit with staged Kotlin files; its early
  `exit 0` sits AFTER the detekt block and only skips the Android Lint half, which stays
  CI-enforced. See [lint-rules.md](lint-rules.md#pre-commit-hook) for details.
