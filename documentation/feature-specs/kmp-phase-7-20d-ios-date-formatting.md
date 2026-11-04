# KMP Phase 7.20D — format workout dates on iOS

Proposed for maintainer review on [`dev@f3fc908b4`](https://github.com/stslex/Workeeper/commit/f3fc908b488754c71b94151be016316e235043e6), the merge of [Phase 7.20C, #359](https://github.com/stslex/Workeeper/pull/359).

## Outcome and scope

Give the Apple host a production `buildIosResourceWrapper(): ResourceWrapper` in the `iosMain` source set of `:app:ios`. Home must be able to label a workout relative to the caller's clock. A dated dialog published through the real Metro graph must show the corresponding local date in the admitted shared UI. The same formatter must handle English and Russian, a change of time zone, and pre-1970 epoch milliseconds.

The [Apple parent](../../app/ios/src/iosMain/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraph.kt) already requires `resources` as a caller input. Its [test host](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosGraphFixture.kt) supplies the constants `just now`, `Nov 2, 2026` and `Nov 2`. [7.20C](kmp-phase-7-20c-ios-install-epoch.md#implementation-evidence) delivered the production installation epoch. Of the remaining host services, date formatting has existing screen consumers and needs no permission prompt, media file, Swift entry point or generation coordinator.

### Existing readers on the discovery ref

| Common operation | Shared production readers | Dependency on the result |
| --- | --- | --- |
| `getAbbreviatedRelativeTime` | [HomeUiMapper](../../feature/home/src/commonMain/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/HomeUiMapper.kt), two calls | Both paths pass a captured `nowMillis` so rows share one clock reading. |
| `formatDayMonth` | [Archive paging](../../feature/archive/src/commonMain/kotlin/io/github/stslex/workeeper/feature/archive/mvi/handler/ArchivePagingHandler.kt), [chart readout](../../feature/exercise-chart/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ChartReadoutMapper.kt), [exercise history](../../feature/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/mapper/ExerciseUiMapper.kt) | The chart appends `point.day.year` itself. The label must contain a localized day and full month exactly once, with no year. |
| `formatMediumDate` | [Past Session](../../feature/past-session/src/commonMain/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/mapper/PastSessionUiMapper.kt), [Single Training](../../feature/single-training/src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt), exercise history, [app-dialogs mapper](../../feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/mapper/AppDialogDateMapper.kt) | Five calls, including restore-success and undo-confirmation dates. The [dialog handler](../../feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt) formats before the state copy. |

## Apple formatting contract

Take Unix epoch **milliseconds** as signed values. Use the active formatting locale (language and region) and local time zone at the time of each call. Use a Gregorian calendar for workout-date components on iOS. This keeps the month and day consistent with the ISO year already appended by the chart. Respect localized field order, grammar, numerals and punctuation. User-selected non-Gregorian workout calendars across platforms would require reviewing that shared chart contract separately.

| Operation | Required Apple behavior |
| --- | --- |
| `formatMediumDate(t)` | Foundation medium date style, no time. Convert the instant before extracting the local date. In particular, `-1` ms is still in 1969 in UTC. |
| `formatDayMonth(t)` | Use a localized full-month/day template such as `MMMMd` with the locale set first. For 22 July use `July 22` in `en_US` and `22 июля` in `ru_RU`. Use the grammatical month within a date. |
| `getAbbreviatedRelativeTime(t, now)` | Use the supplied `now` for direction, thresholds and year comparison. Below one elapsed minute use the localized present label. Below one hour use completed minutes, below 24 hours completed hours, in the correct direction. From 24 hours to under seven elapsed days use the signed distance between **local civil dates**. At seven elapsed days and beyond show an absolute local date, omitting the year within the same local calendar year and including it across years. |

The minute floor and week cutover follow the Android call site. The proposed iOS wording uses Foundation's localized relative labels, including a single present label for both sides of the sub-minute interval. Apple supplies abbreviations, plural forms and named days. The test contract pins the meaning and switch points across platforms, and pins exact examples only where the product depends on them. [Shared interface](../../core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/resources/ResourceWrapper.kt) · [Android implementation](../../core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/resources/AndroidResourceWrapper.kt) · [AOSP unit selection](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/main/android-35/android/text/format/RelativeDateTimeFormatter.java) · [Apple date templates](https://developer.apple.com/documentation/foundation/dateformatter) · [Apple relative formatter](https://developer.apple.com/documentation/foundation/relativedatetimeformatter)

Keep the public factory parameterless and the graph argument mandatory. Use a coherent locale/zone snapshot for each synchronous call. Main and worker callers may share one wrapper. Avoid mutable formatter reconfiguration across concurrent callers, a process-lifetime snapshot of the first locale, per-row retained cache entries, and an extra dispatch to Main. A package-internal settings supplier is sufficient for deterministic tests. Production must read Foundation settings. Preserve the subsecond sign when converting to seconds and avoid signed-delta overflow when selecting a unit.

### Android resource IDs

The two integer-ID methods on `ResourceWrapper` represent Android resources. Shared localized copy has a [typed Compose resource path](../architecture.md#shared-kmp-modules-and-which-reader-a-string-takes). Keep Android ID lookup and the shared interface intact. On Apple, an invocation of either integer method must fail immediately with `UnsupportedOperationException` identifying the method and Android IDs. It must not return fallback copy or interpret an integer as an Apple/CMP resource.

**P0.** Before host integration, audit all Apple-reachable production source sets and record the paths and methods, including aliases and callable references. In a disposable worktree mark the two interface methods `@Deprecated("Android ID probe", level = DeprecationLevel.ERROR)` for a production-only Apple compile probe. Compile the real `:app:ios:compileKotlinIosSimulatorArm64` and `:app:ios:compileKotlinIosArm64` dependency closures. Inventory any suppression of the probe diagnostic and show that none conceals a production lookup. Then deliberately invoke each method through a typed `ResourceWrapper` in a disposable Apple source and require both diagnostics to identify the injected call sites. Restore the exact files and rerun. Keep this probe outside the delivered patch. A reachable ID lookup, missing target, or a need to split the shared interface is a **STOP** for a maintainer decision.

On the pinned Kotlin/Native toolchain, also exercise Foundation conversion, both locales and the minimum-resolution/cutover behavior on a simulator, then link `:app:ios:linkDebugTestIosArm64`. Stop on unavailable bindings or a deployment-target or dependency increase. Keep the two syntax diagnostics separate from behavioral test failures.

## Native acceptance

Register these six literal methods of `io.github.stslex.workeeper.app.ios.IosResourceWrapperTest` before reading the new XML. Use test-owned locale/zone inputs for deterministic cases. Get expected values from independently configured Foundation formatters or explicit civil dates and signed components fixed in the test. Never compute an expected string by calling the subject a second time.

| Method | Passing observation |
| --- | --- |
| `absoluteDatesUseLocaleZoneAndEpochMilliseconds` | For `2026-07-22T00:30Z` check July 22 in UTC and July 21 in Los Angeles. Check both complete EN/RU day-month strings, localized medium date including year and no time, and UTC `-1` ms. Include a locale requesting a different calendar, with Gregorian workout components. Preserve raw results if normalizing typographic spaces for comparisons. |
| `relativeTimeUsesSuppliedNowAndMinutePrecision` | Use a fixed historical clock. Cover equality, ±59,999 ms, ±60,000 ms, immediately below and at one hour, and immediately below 24 hours. Check unit, signed direction, truncation and EN/RU output. Hold `t` fixed and move only `now` so the direction reverses. |
| `relativeDaysAndAbsoluteFallbackUseLocalCalendar` | At exactly 24 hours switch to local day distance. In New York, 7 March 2026 noon to 9 March noon spans 47 hours and two civil days. Cover both directions, both sides of the seven-day boundary and a local year boundary where UTC and the selected zone disagree. Require an absolute date with the correct year visibility. |
| `oneWrapperTracksSettingsAcrossCallers` | Use the same wrapper across a controlled change from EN/UTC to RU/Los Angeles. In each stable configuration call from Main and concurrent workers with distinct instants, and assert each independently expected string. Keep system preferences unchanged in the test. |
| `androidResourceIdsFailExplicitly` | Invoke both integer methods with synthetic IDs and arguments. Check the exception type and distinct diagnostic for each. Also prove valid date calls still work on the same instance. |
| `admittedRootRendersPublishedDialogDates` | Supply the public production factory to the real graph. Mount the admitted shared `App`. Publish two uniquely owned synthetic `RestoreSuccess` dialogs with distinct years and `previousVersionAvailable = false` in succession through `AppDialogRepository`. For each, await the visible dialog body containing the independently expected medium date, dismiss it through the repository, and observe disappearance before publishing the next. The wrong date must be absent. A direct mapper call or a repository read alone cannot satisfy this case. |

Replace the graph fixture's fixed `TestResources` with the public factory and retain all seven graph and six epoch tests. For the new dialog case use the fixture's owned simulator, admission, database and teardown conventions. Use synthetic data and clear only the dialogs created by the test. Keep DataStore's live-file ownership and 7.20C's two-process proof intact. The two filtered relaunch cases count only in the separate relaunch receipt.

Run two behavioral mutations independently with the [mutation harness](../mockups/mutation_harness.py). For one, force UTC for day-month formatting and require the Los Angeles date assertion to fail. For the other, substitute a fixed medium date **only at graph-fixture injection** and require the rendered dialog assertion to fail while the standalone formatter remains correct. Record focused GREEN, executed assertion RED and byte-restored GREEN for each. A broken compile or a timed-out simulator boot cannot satisfy a behavioral control.

## Verification and handoff

The [topology check](../../.github/scripts/assert_kmp_ui_source_topology.py) currently pins five host Kotlin files, thirteen factory invocations and the exact `app:ios` Native identity row. Add just the Apple production/test files and six methods to it and the [Native oracle](../../.github/scripts/assert_kmp_ios_smoke.py). Prove that complete fresh fixtures pass and that dropping a new method or backdating its XML fails. Preserve the other twenty modules, full-suite markers, epoch receipts and the existing [CI job](../../.github/workflows/android_build_unified.yml).

For entry and exit, use the same pinned tools and record the exact production input manifests, fresh XML identities and skip states. Prior receipts can serve as entry evidence only with matching relevant input blobs and toolchain. With the inspected baseline, expect **969 retained + 6 new = 975** simulator cases, **19** in `:app:ios`, and **21** fresh IOS device links. The Android reference is 3,398 host cases, 44 Smoke (three known skips) and 90 Regression (zero skips). Compare the seven tracked Room schemas, 456 golden PNGs and both phone `releaseRuntimeClasspath` trees to entry.

Run in Bash from the accepted implementation worktree. Set `ENTRY_SHA` to its base. Create a fresh directory outside the worktree and set `EVIDENCE_DIR` to its absolute path and log each command with its real exit status. Provision a new simulator for each full run using the current CI provisioning step. Export its actual UUID as `WORKEEPER_DATABASE_TEST_SIMULATOR` and boot it. Use a supported, task-owned Android emulator for connected tests.

```bash
set -euo pipefail
: "${ENTRY_SHA:?}" "${EVIDENCE_DIR:?}" "${WORKEEPER_DATABASE_TEST_SIMULATOR:?}"
G=(--rerun-tasks --no-build-cache --no-configuration-cache --console=plain)
export WORKEEPER_INSTALL_EPOCH_NONCE
WORKEEPER_INSTALL_EPOCH_NONCE=$(python3 -c 'import uuid; print(uuid.uuid4())')
export SIMCTL_CHILD_WORKEEPER_INSTALL_EPOCH_NONCE="$WORKEEPER_INSTALL_EPOCH_NONCE"
export SIMCTL_CHILD_WORKEEPER_INSTALL_EPOCH_LAUNCH=full-suite
M=(core:ui:kit core:ui:navigation core:ui:mvi core:ui:start-mode core:ui:plan-editor
   feature:image-viewer feature:plan-editor feature:archive feature:all-trainings
   feature:all-exercises feature:past-session feature:home feature:exercise-chart
   feature:single-training feature:exercise feature:live-workout feature:app-dialogs:impl
   feature:settings app:common core:data:database app:ios)
S=()
D=()
for m in "${M[@]}"; do
  S+=(":$m:iosSimulatorArm64Test")
  D+=(":$m:linkDebugTestIosArm64")
done
./gradlew :app:ios:iosSimulatorArm64Test --tests '*IosResourceWrapperTest*' "${G[@]}"
./gradlew :app:ios:linkDebugTestIosArm64 "${G[@]}"

# Move any pre-existing/filtered results aside before the complete suite.
mkdir "$EVIDENCE_DIR/pre-full"
for m in "${M[@]}"; do
  module_dir=${m//:/\/}
  results="$module_dir/build/test-results/iosSimulatorArm64Test"
  if [[ -d "$results" ]]; then mv "$results" "$EVIDENCE_DIR/pre-full/$m"; fi
done
epoch_dir=app/ios/build/install-epoch-relaunch
if [[ -d "$epoch_dir" ]]; then mv "$epoch_dir" "$EVIDENCE_DIR/pre-full/epoch"; fi
for module_dir in feature/settings app/common core/data/database app/ios; do
  mkdir -p "$module_dir/build"
  touch "$module_dir/build/native-test-start"
done
./gradlew "${S[@]}" "${G[@]}" --no-parallel --continue
python3 .github/scripts/prove_ios_install_epoch_relaunch.py \
  --simulator "$WORKEEPER_DATABASE_TEST_SIMULATOR" --nonce "$WORKEEPER_INSTALL_EPOCH_NONCE"
python3 .github/scripts/assert_kmp_ios_smoke.py
touch core/data/database/build/device-link-start
./gradlew "${D[@]}" "${G[@]}" --continue
linked=0
for m in "${M[@]}"; do
  module_dir=${m//:/\/}
  bin="$module_dir/build/bin/iosArm64/debugTest/test.kexe"
  [[ -s "$bin" && "$bin" -nt core/data/database/build/device-link-start ]]
  xcrun vtool -show-build "$bin" | awk '$1 == "platform" && $2 == "IOS" {ok=1} END {exit !ok}'
  linked=$((linked+1))
done
[[ "$linked" -eq 21 ]]
printf '%d/21 fresh IOS device links\n' "$linked"

./gradlew assembleDebug lintDebug testDebugUnitTest :core:ui:mvi:testAndroidHostTest \
  :app:common:testAndroidHostTest :core:data:database:testAndroidHostTest "${G[@]}" --continue
python3 .github/scripts/assert_mvi_host_identities.py
./gradlew assembleDebugAndroidTest "${G[@]}" --continue
./gradlew verifyPaparazziDebug :lint-rules:test "${G[@]}" --continue
./gradlew detekt "${G[@]}" --continue
ann=-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations
./gradlew connectedDebugAndroidTest "$ann.Smoke" "${G[@]}" --continue
python3 .github/scripts/assert_mvi_device_identities.py
# Archive connected XML before Regression overwrites Smoke. Require a nonempty inventory.
find app core feature -path '*/build/outputs/androidTest-results/connected/*.xml' -print > "$EVIDENCE_DIR/smoke-files"
test -s "$EVIDENCE_DIR/smoke-files"
tar -cf "$EVIDENCE_DIR/smoke-xml.tar" -T "$EVIDENCE_DIR/smoke-files"
./gradlew connectedDebugAndroidTest "$ann.Regression" "${G[@]}" --continue
python3 .github/scripts/assert_kmp_ui_source_topology.py
python3 .github/scripts/assert_mvi_source_topology.py
python3 documentation/personal_data_gate.py -v
python3 documentation/mockups/shell_gate.py --base "$ENTRY_SHA" -v
for app in dev store; do
  ./gradlew ":app:$app:dependencies" --configuration releaseRuntimeClasspath "${G[@]}" \
    > "$EVIDENCE_DIR/$app-release-runtime.txt"
done
```

Archive fresh XML and binaries before subsequent runs, with raw commands, timestamps, target identities, nonzero inputs, test counts, exact skip identities, artifact paths and exit codes. Check `N actionable tasks: N executed` for the load-bearing Gradle invocations. Cached, up-to-date or empty target tasks cannot discharge a gate. Compare resolved dependency sections separately from timing text. Preserve Detekt/Lint baselines, suppressions and all golden bytes.

**Edit boundary.** Apple host implementation, its tests/fixture, the two existing validators and focused documentation/evidence. Media/camera, restart and recovery coordination, Swift/Xcode packaging, actual backup/restore, Room, shared feature logic, Android/Wear production and build pins are separate work. Simulator rendering and device linking have separate receipts. Physical-device behavior and a signed iOS application remain later acceptance.

Start implementation only after this specification lands on `dev` and Ilya gives GO. Stop on failed ownership, a reachable integer-ID method, a new shared API requirement, a changed schema/golden, unexpected identity drift, or an unexecuted mandatory gate. Report the exact failing input and command. Deliver one implementation PR with verified signed Conventional Commits, independent review on its exact head and the remaining manual gates. Ilya owns merge.

## Implementation evidence

Recorded on 2026-11-04 (Moscow) from accepted entry
`10454130f4c10f644dce6ba8cf66cc78a2cb3e31`. PR #360 was merged into `dev`;
its final reviewed head was `f0dae92ef9a268516aea9651074530081b6da46d`.
The reviewed and entry spec blobs both equal `226678acbf4bd11b04718e03b41395235bacec9f`.

The implementation worktree is `.claude/worktrees/kmp-7-20d-ios-date-formatting`.
Raw receipts live in the separate ignored, main-checkout-relative archive
`.claude/worktrees/phase720d-evidence-20261104/`. Each command receipt records its
arguments, UTC times, elapsed time, real exit code, log and relevant environment.
Input manifests and archived XML preserve source identities and raw Foundation output.
The entry's relevant production, test and build sources match the Phase 7.20C
implementation `f710fbc47` exactly; its archived inventories and release dependency
reports supplied the entry comparison.

Host: Apple silicon, macOS 27.0, Xcode 27.0 (27A266a), JVM 21.0.11,
Kotlin/Native 2.4.10 and Gradle 9.6.1. Simulator runtime: iOS 26.5.
The owned full-run simulator was `45887E5F-ED4D-451E-B026-2857915C79E9`.
Long gates ran on AC; the maintainer explicitly authorized continuing with the lid closed.
Measurements below describe debug/test builds.

### Apple contract and P0

`buildIosResourceWrapper()` reads the current Foundation locale/zone identifiers on
each call and constructs call-local Gregorian calendars and formatters. A controlled
internal settings supplier exercises one wrapper from Main and synchronized concurrent
workers across EN/UTC and RU/Los Angeles. Integer lookups throw distinct
`UnsupportedOperationException` diagnostics. The graph still requires the resource
argument and borrows the caller's database and generation lifetime.

The audit covered 827 tracked Apple/common production Kotlin files. Ten date calls in
seven features were found; the two Android-ID methods had no reachable calls, aliases
or callable references. The only production `DEPRECATION` suppression covers
`SetRepositoryImpl.getLastFinishedSet`'s deprecated DAO call and has no resource lookup.
No probe-error suppression was found in production sources or build flags.
See `apple-production-call-sites.txt`, `apple-call-site-audit.md` and `entry-inputs.json`.

The disposable interface probe compiled both real production closures. Both injected
typed calls failed at their two distinct source sites on both targets with
`Android ID probe`. Original interface bytes and mode were restored before recompiling;
the clean probe worktree was removed. Compiler controls contribute zero Native cases.

| Gate | Exact Gradle summary | Observation | Receipt |
| --- | --- | --- | --- |
| Annotated production closures | `346 actionable tasks: 346 executed` | Simulator and device compile | `p0-id-clean.json` |
| Injected ID calls | `346 actionable tasks: 346 executed` | Exit 1; four intended site/target diagnostics | `p0-id-injected.json` |
| Byte-restored closures | `346 actionable tasks: 346 executed` | Both targets compile | `p0-id-restored.json` |
| Foundation P0 | `350 actionable tasks: 350 executed` | Five exact passing cases; no skips | `p0-foundation-main-workers.json` |
| P0 device link | `349 actionable tasks: 349 executed` | IOS executable, deployment target 15.0 | `p0-device-link.json`, `p0-device-platform.json` |
| Final focused contract/root | `350 actionable tasks: 350 executed` | All six cases pass on the new full-run simulator | `exit-focused-six.json` |
| Final focused device link | `349 actionable tasks: 349 executed` | Includes the root/dialog case | `exit-focused-device.json` |
| All 21 simulator tasks | `584 actionable tasks: 584 executed` | 975 passing cases; zero skips/failures/errors | `native-full.json`, `native-oracle.json` |
| All 21 device links | `563 actionable tasks: 563 executed` | 21 fresh, nonempty IOS executables | `native-device-links.json`, `native-device-platforms.txt` |

All Gradle rows use the four forced-execution flags from the verification block.
The full identity comparison is exactly 969 retained plus six added, with 19 in `app:ios`.
The other twenty oracle dictionaries and the original thirteen host identities are unchanged.
Both distinctly owned synthetic dialogs render their independently expected medium-date
bodies through the admitted root, exclude the other and former fixed date, and disappear
before the next publication. Teardown disposes UI/owners, joins lifetimes and closes the database.

The first development concurrency harness explicitly queued Main work while the Native
runner occupied that thread. Its sampled stack and terminated receipt are retained in
`p0-foundation-process.sample.txt` and `p0-foundation-execution.json`; that attempt is invalid
as passing evidence. Direct Main-thread assertions plus synchronized Default workers passed
on rerun. Initial category-import compile diagnostics are also retained and count as no tests.

### Independent controls and relaunch

| Mutation | Executed RED | Restored GREEN | Archive |
| --- | --- | --- | --- |
| Force UTC only for day/month | Los Angeles expects July 21 and receives July 22 | Exact bytes/mode restored; the absolute case passes | `control-utc/`, `control-utc-green-before.*`, `control-utc-green-restored.*` |
| Fix the medium date only at graph injection | Rendered body assertion fails; the standalone absolute case still passes in the same invocation | Exact fixture bytes/mode restored; both cases pass | `control-fixture/`, `control-fixture-green-before.*`, `control-fixture-green-restored.*` |

Every before, mutated and restored Gradle invocation reports
`350 actionable tasks: 350 executed`. The harness requires the intended compiled assertion
failure; each stage has separate raw logs, XML and executable archives.
Filtered controls contribute no additional full-suite cases.

The ordered relaunch helper ran before the Native oracle. Two sequential exited processes
passed the exact public installation-source case on the same owned simulator with nonce
`f1a91f64-e206-47b1-9ec9-6188e092ab1c`; their canonical path and file digest match.
These two executions remain separate from the 975 full-suite cases.
See `native-epoch-relaunch.json` and `native-relaunch-receipts/`.

The validator's copied complete fresh fixture passes. Removing each of the six new methods
individually fails, backdating the new XML fails, and exact restoration passes.
The fixture rebinds only its copied executable invocation path; original execution receipts
remain intact. These fixture checks add zero Native execution cases.
See `validator-controls.json` and `validator-registration-comparison.json`.

### Initial stop-gate state

The initial mandatory Android build/Lint/host command exited 1 during application-plugin
configuration with `ci-smoke.jks is inValid`, reporting
`4 actionable tasks: 4 executed`. No load-bearing Android target ran.
See `setup-failure-20261104T053859Z-98907e/android-build-host.json` and its log.

The task-created synthetic signing key had been moved into the ignored evidence directory,
and its ignored local `keystore.properties` used an absolute `storeFile`. The existing
`Project.getFile` constructs `File(rootProject.projectDir, path)`, which joins that value
beneath the root. The real key exists; the incorrectly joined path does not.
A read-only JVM reproduction proves that the relative value
`../phase720d-evidence-20261104/build-fixtures/ci-smoke.jks` resolves to the existing fixture.
See `signing-fixture-resolution.log`. At this initial checkpoint the local correction had
not yet been applied; the build helper was unchanged.

At this initial checkpoint, work stopped under the failed/unexecuted mandatory-gate rule.
Android build/Lint/host,
device-test compilation, Paparazzi/lint-rules, separate full Detekt, Smoke, Regression,
exit source gates and both exit release-runtime comparisons remained unexecuted.
No implementation commit, push, PR, independent final-head review or final-head CI existed.
The six-file implementation diff and complete Native receipts are retained for resumption.
All seven schemas and 456 golden PNGs still match entry byte-for-byte.
Both owned iOS simulators were removed after their test processes exited; the task-owned
Android emulator was stopped and its AVD retained.

Simulator execution and IOS device linking are the completed platform evidence.
Physical-device behavior and an installed signed iOS application remain separate acceptance.


### Resumed Android and source verification

The maintainer authorized the local fixture correction and resumed the retained worktree.
Only the existing `storeFile` entry in the ignored, task-owned `keystore.properties`
changed to the relative path demonstrated above. Other properties and permissions were
preserved; the synthetic key and all local signing configuration remain outside Git.
The Gradle helper and build pins are unchanged.

The first configured host run exited 1 after `3149 actionable tasks: 3149 executed`.
Its exact entry set contained 3,398 host cases: 3,380 passed and 18 failed, all in
`TrainingRepositoryImplDbTest`. Every failure was Robolectric temporary-directory
creation raising `java.nio.file.FileSystemException: No space left on device`.
The exercise task emitted 222 cases. The complete failure remains in
`android-failure-enospc-20261104T055423Z-a2b3f5/`; the original configuration failure
remains in `setup-failure-20261104T053859Z-98907e/`. Both archive mappings preserve
relative paths, hashes, modes and timestamps.

After a read-only audit and maintainer-managed space reclamation, all six implementation
hashes, each 2,361-file Native input manifest, 19 critical artifacts, 42 executable hashes,
Native XML, relaunch receipts, mutation controls and freshness markers matched again.
The completed Native evidence above therefore carries forward unchanged. No iOS
simulator was recreated. See `post-cleanup-checkpoint-verification.json` and
`pre-browser-resume-checkpoint-verification.json`.

The full Android retry used the original receipt runner and unchanged parallelism.
Available capacity for worker temp, build and evidence was sampled every five seconds
on their shared APFS container, starting before Gradle and ending after its real exit.
It started at 74.64 GiB, reached a sampled minimum of 61.76 GiB and ended at 71.18 GiB;
the explicit 8 GiB reserve was not breached. These are sampled debug/test-run readings.
All former 18 failures are individually recorded as passing in
`android-host-retry-acceptance.json`.

| Gate | Exact Gradle summary | Fresh result | Receipt |
| --- | --- | --- | --- |
| Android build, Lint and host tests | `3149 actionable tasks: 3149 executed` | 3,398 cases, zero skips/failures/errors; exact entry identities | `android-build-host.json` |
| Instrumented-test compilation | `2210 actionable tasks: 2210 executed` | Repository-wide compilation passes | `android-device-compile.json` |
| Paparazzi and custom rule tests | `638 actionable tasks: 638 executed` | 1,938 cases, zero skips/failures/errors; exact entry identities | `paparazzi-lint-rules.json` |
| Separate Detekt | `64 actionable tasks: 64 executed` | Pass; suppressions and baselines unchanged | `detekt-full.json` |
| Connected Smoke | `2171 actionable tasks: 2171 executed` | 44 cases: 41 passed, three known skips; zero failures/errors | `smoke.json` |
| Connected Regression | `2171 actionable tasks: 2171 executed` | 90 cases, zero skips/failures/errors; exact entry identities | `regression.json` |
| Dev release runtime | `5 actionable tasks: 5 executed` | Resolved tree identical to entry | `runtime-dev.json` |
| Store release runtime | `5 actionable tasks: 5 executed` | Resolved tree identical to entry | `runtime-store.json` |

All rows use the four forced-execution flags from the verification block. Connected
runs used the sole verified `emulator-5554`, owned `Workeeper_phase720d_API34`, API 34,
arm64-v8a, after completed boot. The AVD was stopped afterward and its contents retained.
Smoke's only skips are `pendingFeatureRewrite` in `AllExercisesScreenTest`,
`AllTrainingsScreenTest` and `ArchiveScreenTest`. Fresh Smoke XML and identities were
archived before Regression in `smoke-xml.tar` and
`smoke-canonical-xml-archive.json`. Both MVI identity oracles exit 0.

Both source-topology scripts and the privacy gate exit 0. The first rendered mockup
attempt exited 1 because no browser was on PATH, leaving checks 7/8 unmeasured.
Its full artifact set remains in
`source-failure-mockup-browser-20261104T121341Z-d3f5b7/`, with verified archive mapping.
The maintainer then authorized a temporary `chromium` wrapper outside Git, forwarding
all arguments to the installed Google Chrome 155.0.8059.86. Only the mockup commands
received its PATH prefix; no package, global PATH or gate source changed.

The CI negative control `--target f52462c7` exits 1 with check 7 failing on measured
`width=0px→0px`. The retained-worktree gate with `--base 10454130f4c10f644dce6ba8cf66cc78a2cb3e31 -v`
exits 0: 11 passed, zero failed. Check 7 measures `width=129px→129px`, and check 8
measures radius `18px→28px`, stable fill and the glyph change. See
`mockup-ci-negative.json`, `source-mockup.json`, `mockup-browser-controls.json` and
`mockup-chromium-resolution.json`.

Final comparison confirms all seven Room schema hashes and 456 golden hashes unchanged.
Both release-runtime sections are identical to their measured entry trees: 2,277 lines,
SHA-256 `a5599c742c8c786951de31b9819fb682cd5a4f7cf981c5ccb1d6883601546c72`.
Other entry production/test/build inputs are unchanged apart from the admitted fixture
replacement. See `final-input-comparison.json`. Final-head independent review and CI
are reported at handoff. Physical iOS hardware behavior and an installed signed iOS
application remain separate acceptance; Ilya owns merge.
