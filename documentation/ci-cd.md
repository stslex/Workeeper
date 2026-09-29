# CI/CD

This document covers every GitHub Actions workflow, the test-reporting actions they use, the
release pipeline (Fastlane + Play Store), required secrets, and the branch model. For local
test commands see [testing.md](testing.md). For lint mechanics see [lint-rules.md](lint-rules.md).

## Workflow inventory

All workflow files live under `.github/workflows/`.

| File | Trigger | Purpose |
|---|---|---|
| `android_build_unified.yml` | push to `master`, every `pull_request`, `workflow_dispatch` | Three jobs: `Build and Unit Tests` (including MVI/shared-UI topology, forced Android-host tests and exact identities; Linux), `Release bundle identity` (both release bundles and the bundle identity gate on each, plus its swap control; Linux) and `KMP iOS kit smoke` (kit, navigation, MVI, start-mode, shared plan-editor UI, image-viewer, and the plan-editor feature Native tests plus exact identities on `macos-26`). Gates PRs. |
| `ui_tests.yml` | weekly `schedule` (Mondays 05:00 UTC, against `dev`), `workflow_dispatch`, `workflow_call` | Smoke / regression UI tests on an emulator. Does not gate PRs; called by `android_deploy_prod.yml` with `test_suite=smoke`. |
| `mockup_gate.yml` | every `pull_request` **except** into `master`, `workflow_dispatch`, `workflow_call` | Runs `documentation/mockups/shell_gate.py` against the v3 shell mockup, plus its permanent known negative. Seconds; no emulator, no JDK, no secrets. |
| `pr_guard.yml` | `pull_request` into `master` only | Fails any PR into `master` whose head branch is not `release/release-v.X.Y.Z`. |
| `cut_release.yml` | `workflow_dispatch` only (`mode`: release / hotfix) | Bumps the version (minor from `dev`, patch from `master`), pushes a `release/release-v.X.Y.Z` branch and opens the release PR. |
| `sync_master_to_dev.yml` | push to `master` | Opens an automated PR propagating `master` (version bumps, hotfixes) back onto `dev`. |
| `android_deploy_beta.yml` | `workflow_dispatch` only | Bumps version, generates a Play Store changelog, uploads to the Beta track via Fastlane, tags `beta-v<version>`. |
| `android_deploy_prod.yml` | `workflow_dispatch` only | Same flow targeting the production track, tags `release-v<version>`, then calls `android_deploy_wear.yml`. |
| `android_deploy_wear.yml` | `workflow_call` from `android_deploy_prod.yml`; `workflow_dispatch` on a `release-v.X.Y.Z` tag | The release's Wear bundle to the Wear OS internal testing track ([Wear deployment](#wear-deployment)). |
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
python3 .github/scripts/assert_mvi_source_topology.py
python3 .github/scripts/assert_kmp_ui_source_topology.py
./gradlew verifyPaparazziDebug --full-stacktrace   # visual gate, before anything can rewrite the tree
./gradlew :lint-rules:test --full-stacktrace       # the custom detekt rules, before detekt consumes them
./gradlew detekt --full-stacktrace
python3 documentation/personal_data_gate.py -v     # no real names/emails in tracked files
./gradlew lintDebug --no-configuration-cache --full-stacktrace
./gradlew :core:ui:mvi:testAndroidHostTest --rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain
python3 .github/scripts/assert_mvi_host_identities.py
./gradlew --stop; pkill -f '[o]rg.jetbrains.kotlin.daemon.KotlinCompileDaemon'   # frees the build daemons' memory (F01)
bash .github/scripts/run_with_resource_samples.sh ./gradlew testDebugUnitTest --full-stacktrace \
  -PwearUnitTestFlavors=store   # the property only on pull_request; other triggers run both Wear flavors
./gradlew :app:wear:testStoreReleaseUnitTest -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false \
  --tests '*ReleaseRuntimeBoundaryTest*' \
  --rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain
```

The last line proves the Wear release boundary on **storeRelease**, the variant that ships. The
two Wear flavors differ only by `app/wear/src/dev/AndroidManifest.xml` (a Firebase Performance
logcat meta-data entry); AGP does not create release unit-test tasks unless
`android.onlyEnableUnitTestForTheTestedBuildType=false` is passed, which is why the step spells
the property out.

`testDebugUnitTest` is not pure Gradle on this repository: the Wear module's alias depends on
`:app:wear:verifyEmulatorAcceptanceRunner`, an `Exec` task that runs
`python3 documentation/wear-emulator-acceptance/run_parser_tests.py`. The step therefore needs
`python3` on PATH (ubuntu-latest ships it; the same holds for every local root gate), and the
runner verifies an exact identity inventory (`EXPECTED_TESTS`, 65 ids): a missing, renamed or
extra parser test fails the task before any test runs, and every listed id must leave a passing
testcase in `app/wear/build/test-results/verifyEmulatorAcceptanceRunner/`.

The Wear module runs its host tests once per flavor. Its two flavors differ by one manifest
meta-data line, and the second run doubled the module's share of the unit-test step, so
`pull_request` passes `-PwearUnitTestFlavors=store` and runs the shipping flavor only. The
property defaults to `dev,store`, an unknown value fails the build, and the dev-flavor Wear unit
tests keep running on `master` pushes, `workflow_dispatch`, `workflow_call` and in every local
root gate. The results publisher behind the **Unit Test Results** comment counts **tests** by unique
name and **runs** by execution. The two Wear flavors share test names, so on pull requests
**tests** is unaffected and **runs** drops by the dev flavor's Wear test executions, by design.
Heap, `forkEvery` and timeouts are unchanged.

For an executed CI gate, dispatch the workflow with `execute_unit_tests=true`: the unit-test step then adds `--no-build-cache` and every test task it owns executes, while a re-run of the same PR restores that PR's build cache and executes only what changed.

The step runs inside `.github/scripts/run_with_resource_samples.sh`, which writes a `[res]` sample
block into the step log every 15 s: `uptime`, `free -m`, one `vmstat` row (si/so/wa/st), `df -h /`,
`du -sh /tmp` under a 5 s `timeout` (the JVM's default temp dir on Linux; a local root gate wrote
~7 GiB of transient temp, with Robolectric's native-runtime extraction as the candidate), PSI for
cpu/memory/io, and the eight largest processes by RSS. A sample whose `du` exceeds 5 s has no
`/tmp` line. It lives in the step log rather than an artifact because a runner that receives a
shutdown signal cancels every later step and `failure()` is false on cancellation. The cause of the
Wear stack's mid-step runner shutdowns is unmeasured; these samples are the instrument for the next
occurrence.

`:app:wear:assembleStoreRelease` is a compile-and-R8 gate, not a release. The Crashlytics Gradle
plugin adds `uploadCrashlyticsMappingFile<Variant>` to `assemble<Variant>` whenever the variant's
`mappingFileUploadEnabled` is true, so the Wear module keeps it off by default and reads
`-PcrashlyticsMappingUpload=true`; the Wear release pipeline that does not exist yet must pass that
property when it assembles the shipping build. The phone application keeps its unconditional
upload, so the one PR job that builds a phone release variant, `Release bundle identity`, excludes
`uploadCrashlyticsMappingFileRelease` and proves the exclusion with a dry run on every run
([Bundle identity gate](#bundle-identity-gate)).

Order is load-bearing twice over. `verifyPaparazziDebug` runs first so the goldens are compared
against the tree as checked out, before any step could rewrite it. `:lint-rules:test` runs before
`detekt`, since detekt is what consumes the jar those tests cover.

Every repo-wide spelling above also covers the KMP-shaped `:core:ui:kit`, `:core:ui:navigation`,
`:core:ui:mvi`, `:core:ui:start-mode`, `:core:ui:plan-editor`, `:feature:image-viewer`, and
`:feature:plan-editor`: the KMP
conventions register `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
`assembleDebugAndroidTest` and `verifyPaparazziDebug` as lifecycle aliases onto the real KMP tasks
(`assemble`, `testAndroidHostTest`, `lint`, `assembleAndroidDeviceTest`,
`verifyPaparazziAndroidMain`), so a converted module cannot silently vanish from these steps.

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

## Bundle identity gate

`.github/scripts/assert_play_bundle.py` proves that an AAB is the bundle its role claims before
anything talks to Play ([wear-release-pipeline.md](feature-specs/wear-release-pipeline.md) §6; G8
from [wear-paired-transport.md](feature-specs/wear-paired-transport.md) §9.2). One bundle per run: `--aab <path> --role phone|wear --toml gradle/libs.versions.toml`.

| Check | Rule |
|---|---|
| G1 | Exactly one existing, non-empty file at the path (a glob must match exactly one). Prints size and sha256. |
| G2 | The package is `io.github.stslex.workeeper`. |
| G3 | versionCode is the TOML value (phone) or `1_000_000 +` the TOML value (wear). |
| G4 | versionName is the TOML value (phone) or the TOML value plus `-wear` (wear). |
| G5 | Phone: no `uses-feature android.hardware.type.watch`. Wear: exactly one, not `required="false"`. |
| G6 | Wear: the application meta-data `com.google.android.wearable.standalone` is `false`. Not applicable to phone. |
| G7 | Every `lib/armeabi-v7a/*.so` has the same file under `lib/arm64-v8a/` of the same module. Counts per ABI and module are printed; zero native libraries is a valid, reported result. |
| G8 | No `com.google.android.gms.permission.AD_ID` in the base manifest (`uses-permission` or `uses-permission-sdk-23`), for both roles. The apps show no ads: each application manifest removes the permission that `firebase-analytics` brings and sets `google_analytics_adid_collection_enabled` to `false`. `app/dev` ships no store bundle, so review covers it. |

Every check prints what it read, and the last line is `RESULT PASS` or `RESULT FAIL <checks>` with
the number of checks that ran. Exit 0: every check passed. Exit 1: a check failed. Exit 2: the gate
could not run (unreadable TOML, bundletool missing or failing), which a swap control must not
mistake for the failure it expects.

**bundletool.** The manifest dump comes from bundletool pinned in the version catalog
(`bundletool = "1.18.3"`, library `libs.bundletool`): the version AGP 9.3.0 itself resolves
(`./gradlew buildEnvironment`), so bump it with AGP. The root task `./gradlew :bundletoolClasspath`
resolves `libs.bundletool` through the `settings.gradle.kts` repositories like any other dependency
and writes the resolved jars, one absolute path per line, to `build/bundletool/classpath.txt`. The
script runs `java -cp <those jars> com.android.tools.build.bundletool.BundleToolMain dump manifest
--bundle <aab>`, with Java from `JAVA_HOME`, else from `PATH`. Nothing is downloaded outside Gradle's
dependency resolution, and the script exits 2 when the classpath file or any jar in it is missing.
G7 reads the AAB's zip entries directly.

`--self-test` replays `.github/scripts/fixtures/assert_play_bundle/cases.json`: 23 cases over two
manifests trimmed from real `bundletool dump manifest` output, each case applying exact-once text
replacements. It fails on any mismatch and unless every check is shown both PASS and FAIL.

**Where it runs.**

- `fastlane deploy`: `gradle clean :app:store:bundleRelease :bundletoolClasspath`, the gate with
  `--role phone`, then `upload_to_play_store(aab:)` with the same explicit path. `sh` raises on a
  non-zero exit, so a wrong bundle never reaches the upload, and the upload never falls back to
  supply's newest-AAB-by-mtime selection.
- `fastlane deploy_wear`: the gate with `--role wear` on the Wear AAB the lane just built, before
  the lane reads Play.
- Pull requests: the `Release bundle identity` job of `android_build_unified.yml`.

**The pull-request job** builds `:app:store:bundleRelease` and `:app:wear:bundleStoreRelease`, runs
the self-test, runs the gate on both real AABs, and runs the swap control: the Wear AAB checked with
`--role phone` must exit 1 with G5 among the failures, so the control cannot pass on an identity
mismatch alone. It is a job of its own because the phone release bundle compiles every module's
release variant and the build job's worst green run took 47.1 of its 60 minutes. It uses no Gradle
caching, neither `setup-java`'s nor the build cache: the repository's Actions cache is near its
10 GB limit, and on a key change this job would race the build job to save `setup-java`'s entry with
a dependency set that lacks every test library. Every run is therefore a clean, executed build, and
`--no-build-cache` keeps it so if a cache is ever added back.

PR CI must not upload mapping files (F07 above). The phone release variant uploads its mapping
unconditionally, so the job's task list carries `-x :app:store:uploadCrashlyticsMappingFileRelease`,
and a dry run of that exact list runs first: it fails when it schedules zero tasks, when either
bundle task is missing, or when any `uploadCrashlyticsMappingFile*` task is scheduled. The Wear
variant schedules its upload only with `-PcrashlyticsMappingUpload=true`, which the job never passes.

To reproduce locally. Keep the `-x`: without it a local phone release build uploads its mapping to
Crashlytics.

```bash
./gradlew :bundletoolClasspath :app:store:bundleRelease :app:wear:bundleStoreRelease \
  -x :app:store:uploadCrashlyticsMappingFileRelease
python3 .github/scripts/assert_play_bundle.py --self-test
python3 .github/scripts/assert_play_bundle.py --role phone \
  --aab app/store/build/outputs/bundle/release/store-release.aab
python3 .github/scripts/assert_play_bundle.py --role wear \
  --aab app/wear/build/outputs/bundle/storeRelease/wear-store-release.aab
python3 .github/scripts/assert_play_bundle.py --role phone \
  --aab app/wear/build/outputs/bundle/storeRelease/wear-store-release.aab   # must exit 1
```

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
required context for the Phase-7 native tests. One forced Gradle invocation executes the
`iosSimulatorArm64Test` tasks for `:core:ui:kit` (resource-backed Compose scene),
`:core:ui:navigation` (all 12 routes through the production serialization registry),
`:core:ui:mvi` (lifetime/event/navigation/processor contracts), `:core:ui:start-mode`
(production sheet composition, migrated resources, selected-state semantics, and callback),
`:core:ui:plan-editor` (common reducer coverage plus the production read-only-to-editable scene),
`:feature:image-viewer` (12 common handler cases plus the production resource, branch, Coil, and
action scene), and `:feature:plan-editor` (all 42 portable cases plus the production resource,
branch, and action scene).
It uses `--continue` so one module's failure cannot mask whether the others ran. The job selects
`/Applications/Xcode_26.6.app` explicitly, asserts
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
step actually **started**, red or green. A red native run from a *test* failure is the case where
the per-module XML matters most, and reporting only Gradle's exit code there would not say which
module or which tuple broke; a red run from a compile or simulator-boot failure produces no XML,
and the script says so plainly. It is skipped when the job is cancelled, and when the Gradle step
never ran because an earlier setup step (checkout, Xcode selection, JDK, signing material) failed —
asserting there would bury the real setup failure under a misleading `result directory … does not
exist`. Every module is checked even when an earlier one fails, so a kit-side problem cannot hide
the navigation, MVI, start-mode, shared plan-editor UI, image-viewer, or plan-editor feature
verdict. The image-viewer validator requires
`io.github.stslex.workeeper.feature.image_viewer.ImageViewerSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`
exactly once. The plan-editor feature validator requires all 42 portable tuples and
`io.github.stslex.workeeper.feature.plan_editor.PlanEditorFeatureSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`
exactly once, for exactly 43 target tuples. All seven result directories upload under
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

The Smoke job then runs `assert_mvi_device_identities.py` even when the Gradle command failed, so
the two required MVI cases cannot disappear behind another module's failure or a green zero-test
task. The original Gradle failure still takes precedence after the identity verdict is captured.

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
- `fastlane deploy` — `gradle clean :app:store:bundleRelease :bundletoolClasspath`, the
  [bundle identity gate](#bundle-identity-gate) on the phone AAB, the
  [listing drift guard](#store-listing-drift-guard) for the phone metadata, then
  `upload_to_play_store(aab: <that AAB>)` (the default production track).
- `fastlane deploy_wear` — `gradle clean :app:wear:bundleStoreRelease :bundletoolClasspath
  -PcrashlyticsMappingUpload=true`, the bundle identity gate with role wear, a read-only Play edit,
  the track decision, then `upload_to_play_store` to `WEAR_TRACK` with the Wear screenshots
  ([Wear deployment](#wear-deployment)).
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

### Wear deployment

`android_deploy_wear.yml` ships a release's Wear bundle to the Wear OS internal testing track
([wear-release-pipeline.md](feature-specs/wear-release-pipeline.md) §7; recovery in
[release-flow.md](release-flow.md) §8.7–§8.9). `android_deploy_prod.yml` calls it after its `deploy`
job has uploaded, tagged and merged, with the pinned SHA, versionName and versionCode from `guard`.
Dispatching it on a `release-v.X.Y.Z` tag is the recovery path; GitHub runs a dispatch only from the
default branch's copy of the file, so that path exists once the file is on `master`.

- **`resolve`** checks out the pinned SHA (call) or the tag (dispatch) and fails unless the TOML at
  that commit carries the expected versionName (and, for a call, versionCode).
- **`deploy`** runs one version at a time (`concurrency: deploy-wear-release-v.<version>`,
  `cancel-in-progress: false`) and has no `environment:`, since every secret it reads is
  repository-scoped. It provisions the keystore, `keystore.properties`, `play_config.json` and
  `app/store/google-services.json`, then asserts each is non-empty, every keystore property has a
  value and both JSON files parse: a secret a job cannot see arrives as an empty string, and every
  provisioning step still succeeds. It then runs `fastlane deploy_wear`.

`fastlane deploy_wear`, in one lane run:

1. `clean :app:wear:bundleStoreRelease :bundletoolClasspath -PcrashlyticsMappingUpload=true`, so
   `uploadCrashlyticsMappingFileStoreRelease` runs; then the bundle identity gate with role wear.
2. `fastlane/play_state.rb` reads every Play track and the configured track's version codes in a
   read-only edit that is always deleted, including on error. The configured track's codes are
   read only when Play lists the track, because supply answers `[]` for a missing one. A track the
   list omits is probed with `edits.tracks.get` in the same edit and recorded as
   `configuredTrackProbe`: `found` (the track is returned, with its releases' codes), `empty` (404
   `trackEmpty`: it exists without releases, and supply uploads into it) or `absent` (404 `Track
   not found`). Any other error raises.
3. `.github/scripts/wear_track_decision.py` first requires a non-public Wear track id: it must start
   with `wear:` and must not be `wear:production` or `wear:beta`, whether it comes from
   `WEAR_TRACK` or a dispatch's `wear_track`. Then it prints every track with its codes and
   decides: no tracks, or an `absent` track → FAIL, printing every id; `1_000_000 +` the TOML
   versionCode already on a listed or `found` track → SKIP; otherwise, an `empty` track included,
   UPLOAD. It also warns when that code sits on another track. `--self-test` (20 cases) covers every
   outcome, the id rule and the malformed states.
4. On UPLOAD, the [listing drift guard](#store-listing-drift-guard) for the Wear screenshots, then
   `upload_to_play_store` to `WEAR_TRACK` in an edit of its own, with the explicit AAB,
   `skip_upload_apk`, the metadata path `fastlane/metadata-wear/android`, and only screenshots not
   skipped. The phone lane's listing text and images are never touched from here.

`WEAR_TRACK` in `fastlane/Fastfile` is the one place the track id is configured (initially
`wear:internal`). A dispatch's `wear_track` input overrides it for that run only: a re-run replays
the pinned commit's Fastfile, so this is how a wrong id gets corrected without a new release. The
id rule of step 3 applies to both, so neither can name a phone track or a public Wear track.

A dispatch's `skip_listing` input makes that run upload the bundle alone and leave the Play
listing as it is, with no drift check: the recovery after a Wear DRIFT whose Console screenshots
were adopted on `dev` for the next release, which a retry of the pinned release commit could not
otherwise ship without overwriting them ([release-flow.md](release-flow.md) §8.10).

**Store screenshots.** `fastlane/metadata-wear/android/en-US/images/wearScreenshots/`, never the
phone tree, where a rejected image would fail the phone release. They are captured by
`documentation/wear-emulator-acceptance/store_screenshots.py` from a 240dp API 36 round AVD made by
`prepare_avd.py`, running the store-flavored debug build on synthetic fixtures (see that directory's
README). `.github/scripts/assert_store_screenshots.py` checks each PNG's chunks: colour type 2 (RGB,
no alpha), no `tRNS`, square, at least 384 px, and names `N_<language>.png` numbered in supply's
lexical upload order. The `Release bundle identity` job runs its `--self-test` and then the
directory, printing the file count; zero files fails.

### Store listing drift guard

supply overwrites every listing text and replaces every image and screenshot type present locally,
inside the same edit as the bundle, so every deploy would silently revert a Play Console edit to
anything the repository also holds ([wear-release-pipeline.md](feature-specs/wear-release-pipeline.md)
§8). Both deploy lanes therefore check the listing before they upload, through
`assert_no_listing_drift` in `fastlane/Fastfile`:

1. `.github/scripts/listing_drift.py plan` lists the compared items: exactly what the lane uploads,
   mirroring supply's own selection. Phone: per language, every text field and every image and
   screenshot type present, so today en-US has 4 text fields, the icon, the feature graphic and
   3 screenshot types, and ru-RU has its 4 text fields (its loose images are never uploaded). Wear:
   the `wearScreenshots` of `fastlane/metadata-wear`. Zero items fails.
2. `fastlane/play_state.rb` reads Play's value of exactly those items in a read-only edit that is
   always deleted: text through `listing_for_language`, images as ordered sha256 lists through
   `fetch_images`.
3. `listing_drift.py decide` compares each item three ways. Base is the metadata at the latest
   `release-v.*` tag reachable from the deployed commit, excluding the tag of the version being
   deployed (the Wear job runs after that tag exists); a path absent there is empty. Local is the
   deployed commit; since supply uploads the working tree, the working tree must hold exactly the
   commit's compared files, with no edits and no untracked or ignored file supply would upload.
   Remote equal to base is OK (a repository change, or none); remote equal to local is OK; anything
   else is DRIFT. Text is compared after CRLF → LF, trailing whitespace stripped per line and
   trailing empty lines dropped. Exit 0: no drift. Exit 1: DRIFT. Exit 2: the check could not run.
4. On DRIFT the lane stops before any upload, printing text diffs and hash lists. Under
   `build/listing-drift/<role>/` it leaves Play's state of the drifted items laid out like the
   metadata tree: text written by `listing_drift.py`, images downloaded by `play_state.rb`, which
   also records each file's sha256 next to the API's in `fetched.json` (evidence for the spec's
   ASM-1). `adopt.json` lists the repository files each drifted image item replaces, and
   `listing_drift.py adopt --out <artifact>` applies the artifact: it deletes those files, then
   copies Play's files in, so an image type Play emptied or shortened is removed locally too. Only
   drifted items are included, so adopting cannot undo a repository change to another item.

`allow_listing_overwrite`, a dispatch input of both deploy workflows passed to the lanes as
`ALLOW_LISTING_OVERWRITE`, turns a DRIFT into a logged warning for that run. It is never the default,
and a reader error always fails. Each workflow uploads its `build/listing-drift/<role>/` directory,
the root that `adopt --out` reads, in its last step under `if: always()`, as
`listing-drift-phone-attempt-<n>` / `listing-drift-wear-attempt-<n>`. The upload is never between
the phone's Play upload and its tag and merge, where the step's own failure would strand a live
release, and it runs with `continue-on-error: true`: a failed diagnostic upload after a completed
phone release would otherwise fail `deploy`, and `deploy_wear` runs only when `deploy` succeeded. The name carries the run attempt because `upload-artifact@v4` fails on
a name the run already holds, and a "Re-run failed jobs" attempt keeps the earlier attempt's
artifacts: a fixed name would turn a successful re-run red at its last step, and a red phone
`deploy` skips `deploy_wear`. Recovery is in [release-flow.md](release-flow.md) §8.10.

`listing_drift.py --self-test` builds a throwaway git repository with two tagged releases and
covers OK, DRIFT, override and FAIL: a Console edit, a repository change, a match with local, a
reordered screenshot set, the override, a normalisation-only difference, the exclusion of the tag
being deployed, a Wear path absent at base, a reader gap, an edited compared file, untracked
eligible files (a screenshot, a new language) against untracked files supply never uploads, and
zero compared items, two adopt round trips (an emptied and a reordered screenshot type) that end
OK against the same Play state, and case-variant names (`images/Icon.PNG`, `images/PhoneScreenshots/`)
compared from the names committed, as supply's case-insensitive glob uploads them.

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
| `PLAY_CONFIG_JSON` | beta / prod / Wear deploy | Base64 of the Play Console service-account JSON used by Fastlane. |
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

## Wear review follow-up registry

Findings of the independent review of the Wear stack (#286–#295) that changed this pipeline. One
row per finding: the commit, the guard that now holds it, and the negative control that proved the
guard can fail. Rows are append-only.

| Finding | Commit | Guard | Control |
|---|---|---|---|
| F01 — unit-test step 6.4 → 11.6 min; five stack heads needed re-runs; mid-step runner shutdowns (cause unmeasured). The Wear test-JVM settings `maxHeapSize = "2g"` and `forkEvery = 20` came in d2b6288e (#292) with no recorded rationale; #291's head (1af93d0b), the last one without them, has no green run (attempt 1 failed, attempt 2 cancelled at the job timeout). Correlation only; cause UNMEASURED. Memory, measured from the `[res]` samples of run 36336137166 under a fixed rule (a RAM-backed `/tmp` → per-task `java.io.tmpdir`; otherwise Gradle + Kotlin daemons holding ≥ 40% of used memory → stop both before the unit tests; otherwise no change): `/tmp` is disk-backed (`/` used 79G → 85G while `/tmp` grew 3.4G → 9.3G, and back to 77G at 1.2G; `free` shared stayed 33–41 MiB). At the four swap-full samples (17:51:19–17:52:39Z) the Gradle daemon held 50.5–53.1% and the two Kotlin daemons 23.0–24.0% of used memory, 73.5–77.1% together (56.1% at 17:54:33Z). Choice: stop both | `ci: run the Wear store flavor only on pull requests and sample runner resources`; `ci: sample /tmp usage and record where the Wear test-JVM settings came from`; `ci: stop the Gradle and Kotlin daemons before the unit-test step` | `wearUnitTestFlavors` (unknown flavor fails the build) + `run_with_resource_samples.sh` in the step log, each block with `du -sh /tmp` + the daemon-stop step before `Run Unit Tests` | anchors: `-PwearUnitTestFlavors=bogus` → BUILD FAILED; stand-in `sleep 40; exit 1` → 3 sample blocks, exit 1; with the `/tmp` line, the stand-in keeps its exit code; daemon stop: YAML parses, the extracted step run locally stops a live Gradle daemon and both Kotlin daemons and exits 0, and exits 0 again with nothing running |
| F01 follow-up — executed measurement. Dispatch run 36351649977 (both Wear flavors, 46 test tasks executed, 0 FROM-CACHE): minimum available 6,622 MiB vs 817 in run 36336137166; swap still went 85 → 3,070 of 3,071 MiB in ~30 s (21:52:21–21:52:54Z); PSI memory some/full 30.62/22.22, io some/full 65.58/18.25; `/tmp` 2.6G → 9.7G in the same window; the `:core:data:database` and `:core:data:exercise` Robolectric workers reached 1.8 GB RSS each against a 512m heap, and `/tmp` fell to 0.2G when they exited. Conclusion: the daemon stop removed the idle-daemon baseline, not the pressure event; the event correlates with Robolectric native-runtime extraction in those two modules, which the stack did not change. Cause UNMEASURED; open item for a separate task | `ci: add an executed unit-test dispatch and name java processes in the samples`; `docs(ci): record the executed unit-test measurement under F01` | reproduce with the `execute_unit_tests` dispatch input (`gh workflow run android_build_unified.yml --ref <branch> -f execute_unit_tests=true`); the sampler names each java process's main class, `-Xmx` and Gradle task | a valid measurement shows `--no-build-cache` in the step command and 0 FROM-CACHE test tasks; run 36347898592 (3 executed, 30 FROM-CACHE) is the invalid case |
| F07 — PR CI uploaded a Crashlytics mapping file for every `:app:wear:assembleStoreRelease` | `build(wear): make the Crashlytics mapping upload opt-in` | `uploadCrashlyticsMappingFileStoreRelease` leaves the `assembleStoreRelease` graph unless `-PcrashlyticsMappingUpload=true` (dry-run pair, no test guard) | anchor: with the property the task is scheduled |
| F10 — `python3` inside the unit-test gate undocumented; parser suite accepted “≥ 20 tests” | `test(wear): pin the acceptance parser suite to an exact identity inventory` | `run_parser_tests.py` `EXPECTED_TESTS` (65 ids) via `:app:wear:verifyEmulatorAcceptanceRunner` | `f10-parser-test-renamed`: one renamed test id → RED |
| F08 — release boundary proven on `devRelease`, not the shipping `storeRelease` | `ci: prove the Wear release boundary on storeRelease` | `ReleaseRuntimeBoundaryTest` on `:app:wear:testStoreReleaseUnitTest` | `f08-release-driver-accepts-scenario`: release `handleDebugScenario` returning `true` → RED |

## Check-name reference

Each reporting action in CI uses a unique `check_name` so the per-suite checks coexist on a PR
without overwriting each other.

| Workflow | Action | `check_name` |
|---|---|---|
| `android_build_unified.yml` | `EnricoMi/publish-unit-test-result-action@v2` | `Unit Test Results` |
| `android_build_unified.yml` | job check run (no reporting action) | `KMP iOS kit smoke` |
| `android_build_unified.yml` | job check run (no reporting action) | `Release bundle identity` |
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
