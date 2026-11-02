# KMP Phase 7.19A — move the recovery launch to the Android host

Proposed for review. Discovery base `c06871dd134f7770f08da62a8ac54d589257fa20` on
`dev`, 2 November 2026. This document specifies one Android preparation PR for sharing
`:app:common`. Runtime checks below are acceptance criteria for that PR.

## Goal

Remove the direct `:app:common` dependency on `:feature:recovery`. An admitted Android
UI generation must still open the existing recovery Activity once when its navigation bus
delivers `NavCommand.OpenRecovery`. The host supplies the operation. The composition root
and command processor can then be compiled for another platform without importing this
Android feature. Converting the composition root to KMP is a separate step.

The twelve screen entries and the root-mounted app-dialogs feature already publish shared
factories. Settings has a production iOS handler that reports backup unavailable. Real iOS
backup is planned after migration. An incompatible database at startup has a separate
recovery policy even on a device that has never used backup.
[Settings implementation](https://github.com/stslex/Workeeper/pull/349) ·
[Phase 4 readiness](kmp-phase-4-app-common.md) · [Recovery contracts](backup-recovery.md)

## Two Android launch paths

| Trigger | Observed behavior at the discovery base | Contract for 7.19A |
| --- | --- | --- |
| Startup before composition | `MainActivity.onCreate` first checks `recoverySurfaceRequired` (InterruptedRestore), then `StartupCheck.RouteToRecovery` (StartupMigration). It stamps the intent, starts the recovery Activity, calls `finish()` and returns. | Preserve ordering, stamp, Continue decision, finish and early return. Preserve the existing preflight and DB-free recovery Activity. |
| Command from a mounted UI | `NavigatorExt.processCommand` obtains a Context from composition and starts `RecoveryScenario.intent(context, StartupMigration)`. The factory supplies `FLAG_ACTIVITY_NEW_TASK`, an explicit scenario and `allowContinue=false`. This branch leaves the caller Activity and navigation stack in place. | Invoke the current host callback exactly once on receipt. The Android host starts the same explicit intent with the same flags and extras. |

The only `feature.recovery` import in the 13 production Kotlin files of `:app:common` is in
`NavigatorExt.kt`. Its Gradle edge is `implementation(project(":feature:recovery"))`.
`:app:app` already declares its own `api(project(":feature:recovery"))`.
[Navigation bridge](../../app/common/src/main/kotlin/io/github/stslex/workeeper/navigation/NavigatorExt.kt) ·
[Activity startup](../../app/app/src/main/java/io/github/stslex/workeeper/MainActivity.kt) ·
[Intent factory and parser](../../feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/RecoveryScenario.kt)

## Implementation contract

1. Require an explicit `onOpenRecovery: () -> Unit` on the composable `App`.
   Forward the callback through `AppGenerationContent` to `NavigationEventBusSetup`.
   The production call in `MainActivity.setContent` supplies the launch operation using
   the current Activity and `RecoveryScenario.intent(this, RecoveryScenario.StartupMigration)`.
   The three direct root mounts in `UiAdmissionRaceTest` supply an explicit test callback
   that fails on an unexpected launch. Search all source sets for other callers before editing.
2. Keep the collector keyed on `navigatorHolder`. Read the latest callback on delivery,
   for example with `rememberUpdatedState`, so a callback change during recomposition
   takes effect without restarting collection of a replay-zero flow. Call it only in the
   `OpenRecovery` branch. Preserve command ordering, result publication, telemetry and all
   other stack operations. The callback's lifetime is the host composition. Store, navigator,
   application scope and generation graph must not retain the Activity.
3. Remove the direct recovery edge from `:app:common` and its recovery import. Keep
   the existing `:app:app` ownership of the Android feature. The common module stays on
   its current Android convention in A. A required callback avoids an empty production
   default that would silently discard a request.
4. Adjust the existing topology assertions that spell out `AppGenerationContent(deps)` and
   its declaration. Preserve their proof of generation admission before dependency resolution,
   and check the callback crosses the same admitted region. Add a narrow check for the
   removed recovery project edge/import and the real Android wiring. Keep the previous
   shared-feature checks active. There is no new Native module or CI job in this step.

The Android preflight branch precedes `setContent` and is out of the edit region. Also
outside this change are Settings availability, startup/restore coordinators, scenario and
undo formats, Room/schema/files, `AppRuntime`, `AppReinitializer`, app-graph and process
ownership, architecture-wide platform abstractions, navigation routes and UI redesign.
There is no iOS recovery callback to implement until a real iOS host is designed.

## Change boundary

| Owner | Permitted edits |
| --- | --- |
| `app/common` | `build.gradle.kts`, `App.kt`, `navigation/NavigatorExt.kt` |
| `app/app` | `MainActivity.kt` at the composition entry and its local launch action, `UiAdmissionRaceTest.kt` at its three call sites, one targeted Android regression test file |
| Source gate | `.github/scripts/assert_kmp_ui_source_topology.py`, scoped to the root and recovery edge |
| Documentation | This specification's execution evidence, and a narrow ownership correction in `documentation/architecture.md` if needed |

Use existing Android runner and test dependencies. Keep `feature/recovery/**`, resources,
golden images, the manifest, version catalog, Gradle conventions, general CI workflows and
the Phase 7.18 evidence unchanged. If another production caller actually needs an edit
outside the table, name it and establish the smallest amendment before changing it.

## Acceptance

Record a clean entry baseline against the implementation branch point, including current
test identities, source paths and the recovery module tree hash. The source count at
discovery is 37 test methods across four app/common host suites and 96 across eight recovery
host suites. Compare the actual baseline and exit JUnit identities. Counts alone cannot
establish preservation.

Two new Android regression cases close the gap between the existing bus-emission test
and the existing recovery Activity test:

- `mountedAppOpensStampedStartupRecovery` exercises the Android composition through
  `MainActivity` and its real generation-owned bus. Wait for the composition/collector,
  emit `openRecovery()`, and observe exactly one Activity launch. Inspect the explicit
  component, flags and scenario/Continue extras. Compare extras including key presence
  against the canonical intent. The parser defaults a missing scenario to StartupMigration,
  so checking the decoded enum alone would miss an unstamped intent. Confirm the caller
  remains alive and the prior destination/stack is preserved on return. Use a test-isolated
  graph on a supported emulator. Avoid touching user data.
- `recomposedBridgeUsesCurrentRecoveryCallback` mounts the production bridge, changes its
  callback while retaining the same holder, then emits a command. Verify that only the new
  callback runs once and that the command collector was not replaced. Use a controlled
  receiver/collection counter as necessary. No timing sleeps.

Keep the three `UiAdmissionRaceTest` cases and
`RecoveryActivityDbFreeTest.recoveryActivityScenariosLaunchWithoutOpeningTheDatabase`.
The latter actually warms the Activity with a SQLite driver that throws on connection.
The two new cases do not replace this tripwire. Keep the existing bus, generation and
navigation device suite assertions intact.

Run independently reversible, compile-valid negatives for the discarded host callback,
wrong scenario or missing explicit stamp, stale callback capture, and restored recovery
edge. For each, identify the expected failing test/gate, restore bytes and mode, rerun green.
Use the [mutation harness](../mockups/mutation_harness.py) for supported Gradle mutations.
Use a disposable source fixture or byte/mode snapshot with guaranteed restoration for a
Python source-gate control. Compilation failure is not an accepted behavioral negative.

Follow [AGENTS.md](../../AGENTS.md) and [testing](../testing.md) for per-commit and
phase-exit gates. Load-bearing Gradle commands use all of
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
Keep Detekt separate. Required evidence includes:

- `python3 .github/scripts/assert_kmp_ui_source_topology.py` and
  `python3 .github/scripts/assert_mvi_source_topology.py`.
- `:app:common:testDebugUnitTest`, `:feature:recovery:testDebugUnitTest`,
  `:app:app:testDebugUnitTest`, repository assemble/lint/unit/device-test compilation
  and separate Detekt. Record the Gradle summary and fresh nonempty XML.
- The Android Regression run with both new cases, the DB-free test and the admission tests
  explicitly present. Run the standard Smoke set and its device identity check. Save
  Smoke output before Regression overwrites it. Document baseline skips separately.
- A diff against the entry SHA proving the removed edge, allowed paths, intact recovery
  source tree, and existing startup routing. Check signatures for every created commit.
  Existing Native CI covers previously shared modules. This step adds Android evidence only.

Hand off a signed implementation PR to `dev`, with the commands, summaries, exact
identities, controls and limitations. A separate reviewer checks both launch paths.
The maintainer owns merge. Stop for a changed startup decision, an unintended database
open, a dropped command, loss of admission/test coverage, a new suppression or any
required out-of-scope change. Record missing device/tooling as pending work.

## Following checkpoint

With the direct recovery edge removed, scope 7.19B against its own branch point. Inventory
the two Application-holder lookups in `App.kt` (including when deps may first resolve),
`DisplayCornerShape` and Android window insets, EN/RU bottom-bar resources,
`NavigatorEventBus`'s concurrent result-channel map, Android lifecycle/Nav3 decorators
and their Native-compatible dependency variants. Probe registry concurrency, generation
admission, result delivery and back-stack restoration on the new targets. That checkpoint
owns the actual KMP conversion of `:app:common`.

## Execution evidence

Implementation entry: `origin/dev` at `ad97f5e5df43c28785b55e864ec2bf5889020133`,
the merge of [specification PR #350](https://github.com/stslex/Workeeper/pull/350).
The accepted file's blob was exactly `01c2dc31673295d4283877df2f654492edf1e01f`.
Implementation branch: `codex/kmp-7-19a-recovery-launch`, in its own repository worktree.
The entry tree was clean. Recovery tree: `3b883587d6032b9d66eb3db1baccaad18287367c`.

All Gradle runs below use
`--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
Runs also use `--continue` unless they are mutation-harness controls. Each run has its
own log, start/command/PID receipt and copied fresh JUnit XML. Device:
`Pixel_6_API_34`, `emulator-5554`, API 34, `arm64-v8a`; test graphs use in-memory Room.

| Check | Actual result |
| --- | --- |
| Entry host suites: `:app:common:testDebugUnitTest :feature:recovery:testDebugUnitTest :app:app:testDebugUnitTest` | PASS: `622 actionable tasks: 622 executed`; 37 identities / 4 suites, 96 / 8, 200 / 23, respectively; fresh XML, no failures or skips. |
| Entry UI and MVI source topology | PASS, both scripts. |
| Updated UI and MVI source topology | PASS, both scripts; previous shared-feature checks retained. |
| Restored direct recovery edge negative | RED: `app:common must not depend directly on feature:recovery`; exact bytes/mode restored, then topology GREEN. |
| Callback mount moved outside admission negative | RED: `dependencies and callback must cross the admitted region`; exact bytes/mode restored. |
| Initial targeted device run | FAILED: `755 actionable tasks: 755 executed`; callback-recomposition, three admission identities and DB-free tripwire passed; mounted launch test timed out awaiting the Activity. Superseded by the corrected run below. |
| Corrected targeted device run | PASS: `755 actionable tasks: 755 executed`; all six required identities below passed, fresh XML, no skips. |
| Behavioral negative controls and restored green | Discarded host callback, missing explicit intent extras, and stale capture: each RED with the expected fresh failing identity. After each exact byte/mode restoration, the relevant regression is GREEN (the last green includes all six identities). Every control and restored run: `755 actionable tasks: 755 executed`. |
| Standard Smoke and MVI device identity oracle | PASS: `2116 actionable tasks: 2116 executed`; 44 identities, 0 failures, 3 baseline skips. XML copied before Regression. MVI oracle: 2 exact identities executed, 0 skipped/failed/errored. |
| Android Regression, including all six targeted identities | PASS: `2116 actionable tasks: 2116 executed`; 90 identities, 0 failures/skips. All six required identities occur exactly once; no missing baseline identities in the standard phone task. |
| Repository assemble/lint/unit | PASS: `2919 actionable tasks: 2919 executed`; 3395 fresh XML identities, 0 failures/skips. |
| Repository device-test compilation | PASS: `2156 actionable tasks: 2156 executed`; existing Android/KMP test compilation and classpath checks retained. |
| Separate Detekt | PASS: `63 actionable tasks: 63 executed`; no auto-correct, new suppression or baseline change. |
| Exit host identities, allowed-path/startup/recovery-tree comparison | PASS: exact entry/exit identity multisets match for all 333 host cases; exactly nine allowed paths; startup before `setContent` byte-identical; recovery tree unchanged. |
| Commit signatures and implementation PR | Implementation commit `fd487e152a9e9d510cbedc43dec3e84f7d274045`: local SSH signature valid; GitHub `verified: true`, reason `valid`. [Implementation PR #351](https://github.com/stslex/Workeeper/pull/351) targets `dev`; the signed evidence attestation is verified in the PR handoff. |
| Remote CI and maintainer review | At publication, CI and requested bot review were pending. Live results are tracked in [PR #351](https://github.com/stslex/Workeeper/pull/351); the maintainer owns merge. |

The first baseline attempt stopped at configuration (`keystore.jks is inValid`): the
fresh worktree lacked the existing ignored signing file. Copying the local ignored
configuration fixed that setup issue; no tracked configuration changed.

Independent source review covered both launch paths. Its monitor-lifetime finding was
classified **correct**, reproduced against Android API 34 `Instrumentation.java`, and
fixed: `waitForMonitorWithTimeout` unregisters the monitor; the test now waits on the
monitor itself and unregisters it only in `finally`. The failed mounted test also
exposed a test synchronization boundary: Compose's v2 rule uses `StandardTestDispatcher`,
and `runOnIdle` drains before its action, so the emitted command needs a subsequent
`waitForIdle` before a blocking Android monitor wait. The initial log contained the bus
emission without bridge collection; this is not evidence of a production command drop.
The corrected device run passed all six cases and established the mounted launch behavior.
Final independent review of the restored source approved both launch paths with no remaining
actionable findings; the reviewer independently checked the six-case XML.

Exact targeted identities (package `io.github.stslex.workeeper.app`):

- `RecoveryLaunchBoundaryTest.mountedAppOpensStampedStartupRecovery`
- `RecoveryLaunchBoundaryTest.recomposedBridgeUsesCurrentRecoveryCallback`
- `UiAdmissionRaceTest.admittedGeneration_composesTheRegion_andResolvesItsDependencies`
- `UiAdmissionRaceTest.retiredGeneration_composesNothing_andResolvesNothing`
- `UiAdmissionRaceTest.retirementBetweenPublicationAndFrame_resolvesNothing`
- `RecoveryActivityDbFreeTest.recoveryActivityScenariosLaunchWithoutOpeningTheDatabase`

Commands, with the common execution flags above appended to each Gradle invocation:

```bash
./gradlew :app:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RecoveryLaunchBoundaryTest,io.github.stslex.workeeper.app.UiAdmissionRaceTest,io.github.stslex.workeeper.app.RecoveryActivityDbFreeTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke
python3 .github/scripts/assert_mvi_device_identities.py
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression
./gradlew clean
./gradlew assembleDebug lintDebug testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew detekt
python3 .github/scripts/assert_kmp_ui_source_topology.py
python3 .github/scripts/assert_mvi_source_topology.py
```

Each behavioral control used `documentation/mockups/mutation_harness.py` in one-shot mode
with `--expect RED`. Its `--task` selected `:app:app:connectedDebugAndroidTest` with
`-Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.RecoveryLaunchBoundaryTest#<method>`
and `--no-configuration-cache --console=plain`; the harness supplies the other two forced
execution flags. An outer byte/mode snapshot asserted exact restoration, and fresh failing
XML established the named test rather than relying only on the harness's exit verdict.

| Mutation | Expected failing identity or gate |
| --- | --- |
| `MainActivity`: `App(onOpenRecovery = ::openRecovery)` → `App(onOpenRecovery = {})` | `mountedAppOpensStampedStartupRecovery`: no Activity launch. |
| `MainActivity.openRecovery`: canonical factory → explicit `RecoveryActivity` intent with `FLAG_ACTIVITY_NEW_TASK` but no extras | `mountedAppOpensStampedStartupRecovery`: required explicit extras absent, despite the parser's StartupMigration fallback. |
| Bridge delivery: `onOpenRecovery = currentOnOpenRecovery` → `onOpenRecovery = onOpenRecovery` | `recomposedBridgeUsesCurrentRecoveryCallback`: old callback count becomes 1. |
| Append `implementation(project(":feature:recovery"))` to `app/common` | UI topology rejects the direct project edge. |
| Move `AppGenerationContent` outside the admitted body | UI topology rejects the admission placement. |

Python controls used byte/mode snapshots and `finally` restoration. Both topology scripts
passed again on the restored source.

The three unchanged Smoke skips are `pendingFeatureRewrite` on
`feature.archive.ArchiveScreenTest`, `feature.all_trainings.AllTrainingsScreenTest`, and
`feature.all_exercises.AllExercisesScreenTest` (package prefix `io.github.stslex.workeeper`).
They remain separate from the 41 passing Smoke identities.

Entry and exit host identity fingerprints match. Each SHA-256 covers sorted
`classname<TAB>name<LF>` records, including the exact JUnit method spelling:

| Module | Suites / identities | Entry = exit SHA-256 |
| --- | --- | --- |
| `app/common` | 4 / 37 | `c20c35d302a5abdb468640e9ef955b41aed08c395f01f468951d8608b0047899` |
| `feature/recovery` | 8 / 96 | `c18c97cb6f3de3bcf35f77937247ac361a35b4dface13b9a0a60938c8a148eef` |
| `app/app` | 23 / 200 | `83d2d85411832781f540ec94e4868ec511224055cb51526d264349ca1fa6c975` |

All existing device-test assertions are unchanged: the complete tracked device-test diff
is the three explicit callbacks in `UiAdmissionRaceTest`; the new regression file adds
the two required cases. `clean` was preparation, not validation evidence: its absent
test-output cleanup tasks reported `94 actionable tasks: 42 executed, 52 up-to-date`.
The validation runs in the table have fully executed summaries.

The startup block before `setContent` is byte-identical to entry; `feature/recovery/**`
has no diff and retains its entry tree hash. No Native module, workflow or dependency
version was added. Native execution is outside this Android-only checkpoint.
