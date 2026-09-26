# Shared style / MVI implementation checkpoint — 2026-09-26

**IMPLEMENTATION IN PROGRESS / FRESH HOST GATES ENTERED / FINAL MATRIX NOT RUN**

This report does not replace the 2026-09-25 acceptance or the 2026-09-26 review evidence.
It records only work executed in this implementation stage. Candidate device tests and
system-Tile observations have run; the complete new emulator matrix, physical-watch tests
and performance profiling remain pending. Historical checkpoints below retain their original scope.

## Source boundary

- Worktree: `/private/tmp/workeeper-wear-style-mvi-20260926`.
- Branch: `codex/wear-shared-design-mvi`.
- Base: `1af93d0bbb1fbefaef9f864df3cbac5bbdf1f9dd`, matching the remote PR #291 head
  when implementation began. PR #291 was open over #290 and its Android CI was FAILURE.
- Main `dev` and the source acceptance worktree were clean at entry. The frozen checkout
  was not changed.
- The full prototype remains uncommitted in that worktree. Independently prepared stack commits
  are built in `/private/tmp/workeeper-wear-stack-validation-20260926`.
- [PR #292](https://github.com/stslex/Workeeper/pull/292) contains the foundation commit
  `d2b6288efab7bd1d697b1c14e772b631dd9d5ba8` and review fixes in
  `cef8c949b1b96d46c6a6231c05e7177140ff7ba0`.
- [PR #293](https://github.com/stslex/Workeeper/pull/293) contains presentation MVI and relative
  rotary input in `142c2f4fee41c59cd222e528eea97301bd8de6a9`, stacked on #292. Both PRs remain open.
- The adapted theme is being validated on `codex/wear-shared-style`; its root build/lint/unit
  gate passed, while named controls and device geometry checks are still running.
- The final complete source has not passed the full required acceptance gates.

## Executed evidence

Local artifacts: `/private/tmp/wear-style-mvi-evidence-20260926`.

| Check | Execution | Result |
| --- | --- | --- |
| Shared font identity | Byte comparison against the base | All seven TTFs unchanged; 37 colour constants unchanged; `shared-resource-identity.json` |
| Phone bundled-family and typography contracts | `:core:ui:kit:testAndroidHostTest`, filters `*AppBundledFontsTest` and `*AppTypographyContractTest` | 11 tests, 0 failures/errors/skips; `87 actionable tasks: 87 executed` |
| Rotary defect before the fix | `:app:wear:testDevDebugUnitTest --tests '*WearRotaryDraftTest'`, DevDebug/Robolectric SDK33 | 1 test, 1 assertion failure, 0 errors/skips; `58 actionable tasks: 58 executed` |
| Relative rotary, shared Wear roles and geometry after changes | `:app:wear:testDevDebugUnitTest`, filters `*WearRotaryDraftTest`, `*WearRelativeDraftTest`, `*WearBrand*Test` | 9 tests, 0 failures/errors/skips; `106 actionable tasks: 106 executed`; `wear-ui-first-green/` XML |

Both Gradle commands included `--rerun-tasks --no-build-cache --no-configuration-cache
--no-parallel --console=plain`. The successful font XML is preserved under `shared-fonts-green/`.
The rotary XML is preserved under `rotary-baseline-red/`; its assertion is:

> 96px contains two relative 48px steps ==> expected: <10> but was: <9>

This is a real Compose input event applied through `WatchRuntimeOwner`, not a Python replay.
The relative-step fix was written **after** this failure. Its first GREEN verification is now
recorded above; named negative controls remain pending.
The font run covers the shared-font extraction; it does not establish Wear theme/layout acceptance.

## Implemented but not yet accepted

- Shared design-token module, unchanged font binaries and moved provenance/license files;
  phone adapters consume it. The unused MVI-to-kit dependency is removed.
- The phone Application sets Crashlytics `platform=phone` before initialization.
- Relative editor actions apply every step to the latest runtime draft, preserving the existing
  bounds and nullable-weight transitions. Added runtime and Compose regression coverage.
- Wear typography and shapes use shared families/tokens. Numeric spans are separated from
  localized text. Step controls use centred vector icons and retain 48dp targets.
- Information screens use a round-safe scroll viewport; completion text is centred. SetPill
  and ValueCard modifier chains are stable. Step availability and set-scale preparation moved
  out of composition. Added family, icon-centre and Russian word/geometry guards.

These changes compiled together and passed the nine targeted Wear guards. Existing editor
tests now expect relative actions while retaining their focus, authority and visibility assertions.
The full existing suite and final gates remain separate work. The first two Firebase integration
attempts failed during Gradle configuration (Google Services property API and native-symbol task
configuration); neither is a RED test result. All three Firebase SDKs are now declared and both
applications set their platform key. No Firebase-console delivery claim is made.

## Initial negative-control plan

Run every control through `documentation/mockups/mutation_harness.py` with its `--name`,
`--file`, exactly-once `--find`/`--replace`, `--task` including `--no-configuration-cache`,
and `--expect RED`. Preserve XML/logs and the harness's byte-restoration assertion.

| Name | Mutation | Detector |
| --- | --- | --- |
| `SHARED_TEXT_FONT_SUBSTITUTED` | Sans regular resource replaced with Mono regular in the shared loader | `AppBundledFontsTest` |
| `WEAR_NUMERIC_ROLE_SUBSTITUTED` | `numeralMedium` constructed from the text family | `WearBrandThemeTest` |
| `EDITOR_ICON_OFF_CENTER` | Add 8dp end padding before the glyph test tag | `WearBrandGeometryTest` |
| `INSTRUCTION_VIEWPORT_OUTSIDE_CIRCLE` | Safe-square fraction changed from 0.70710677 to 0.95 | `WearBrandGeometryTest` |
| `ROTARY_BATCH_COLLAPSED` | Dispatch only the sign of the accumulated step count | `WearRotaryDraftTest` |
| `REPS_BATCH_USES_CANONICAL` | Adjust from target reps instead of current draft reps | `WearRelativeDraftTest` |
| `WEIGHT_BATCH_COLLAPSED` | Limit relative weight application to one step | `WearRelativeDraftTest` |
| `WEIGHT_ZERO_LOSES_NULL` | Replace a nullable decrement result with zero | `WearRelativeDraftTest` |

## Remaining work and blockers

1. The owner explicitly confirmed the full phone Firebase set after automatic approval review
   had rejected the SDK patch. That authorization blocker is resolved and the patch is applied.
   Presentation Store/handlers/mapper and Metro graph integration are implemented; final validation remains pending.
2. The owner authorized deletion of unused build files. Compiler intermediates were removed
   from the clean `wear-ambient-fix-20260922` and `kmp-phase-7-7-plan-editor-feature` worktrees.
   No source checkout, AVD, report, XML or APK was removed; 4,188 retained evidence files were
   verified byte-for-byte. Free space rose from about 0.83 GiB to 9.19 GiB across the cleanup
   and intervening build. Exact targets and SHA-256 manifests are in the local evidence directory.
3. Ambient font integration and mapper/runtime separation have targeted passing checks.
   Tile update/layout changes are written; the original defect remains open until fresh system-Tile evidence.
4. Named negative controls are in progress. Root Detekt and the full local Wear host suite
   have executed successfully as recorded below. Lint, required broader builds/unit tests,
   phone goldens/KMP checks and the complete 16-cell emulator matrix remain pending.
   No acceptance claim is made about the current full diff.

Real phone payloads remain prohibited. Protocol, DB and mutation authority are unchanged.
Physical-watch reconnect, power and hardware ambient acceptance remain separate requirements.

## Subsequent targeted execution

Each Gradle invocation below used all three cache-defeating flags and ran serially. Successful
rows each ended with `106 actionable tasks: 106 executed`; their fresh XML is retained under
the named evidence directory. These are local DevDebug tests, not shipping performance data.

| Evidence directory | Tests | Result and scope |
| --- | --- | --- |
| `wear-first-view-fixed-green` | 4 | EN/RU first view, Russian overflow and source-string coverage |
| `raw-runtime-green` | 46 | Raw snapshot publication, runtime authority/draft and mapper compatibility |
| `mvi-existing-ui-green` | 17 | Existing editors, rotary, ambient and notices driven through the project Store |
| `mvi-contracts-green` | 12 | Handlers, mapper cache, retained Metro Store, editor expiry and redacted action descriptions |
| `ambient-fonts-green` | 14 | Shared native font roles, both round sizes/scales/locales, full numeric raster, low-bit, burn-in and system clock preference |

The first full Wear-suite attempt (`wear-before-mvi-all.log`) failed with both real assertions
and a Java heap error. It exposed Russian `Повторы: 1–999` in a fixed-height context container,
and eight Firebase-generated IDs in the source-string inventory. The container now has a minimum
height; source strings remain mandatory and only undeclared, known generated Firebase config IDs
are excluded from that inventory. The test worker has a 2 GiB heap and rotates after 20 classes.
No full-suite success is inferred from these changes.

Ambient attempts one through five and the extra diagnostic run remain archived as failures.
They exposed circular overflow, rounded native glyph advances, oversized font metric extents
and ellipsis rounding. The sixth run passed the unchanged circular-pixel, full-value-raster and
15-percent lit-area guards. No golden image was replaced.

The third Tile/rotary attempt was interrupted after a second Compose test in one Robolectric
class stalled. Its owned worker thread dump is retained as
`rotary-double-composition-thread-dump.txt`; it has no passing gate verdict. Weight and repetitions
now use separate test classes. The second Tile attempt had a test-instrument error from asking a
detached View for screen coordinates; the corrected oracle computes descendant bounds relative
to its actual root. Neither attempt closes real system-Tile acceptance.

Live GitHub refresh during implementation: #290 remains OPEN at `48e46886`, with successful
reported checks; #291 remains OPEN at `1af93d0b`, with Build and Unit Tests FAILURE. The reason for
that older CI failure is still unresolved. These statuses do not verify this uncommitted diff.

## Full Wear host baseline and independent appearance gate

`wear-full-before-controls.log` and `wear-full-before-controls-green/` record 227 tests in
67 suites, with no failures, errors or skips; `106 actionable tasks: 106 executed`. This
baseline includes retained Store rendering, late editor input rejection and the native Tile
status renderer. It precedes the named mutation campaign and is not final device acceptance.

`detekt-third.log` and `detekt-green/` retain the successful separate root Detekt run,
including 55 XML reports; `63 actionable tasks: 63 executed`. Earlier formatting findings
and failed runs were retained. Later device-assertion edits still need a new Detekt run.

**Instrument correction:** the following Headless Shell result is advisory, not an accepted
appearance gate. `.github/workflows/mockup_gate.yml` documents a measured layout difference
and explicitly excludes this packaging as a substitute for the full Chrome instrument.
The local installed Chrome attempt timed out; the required render gate and known negative
remain pending on the workflow's installed Chrome. Raw local results remain preserved below.

The HTML appearance gate passed all 11 checks against base `1af93d0b` using the official
standalone Chrome Headless Shell 154.0.8037.57. The pinned historical `f52462c7` target failed
five content/render checks after its probe completed. Logs are
`shell-gate-headless-shell-green.log` and `shell-gate-headless-shell-negative.log`;
`browser-provenance.json` records the downloaded binary archive hash and origin. Earlier
missing-framework and installed-Chrome timeout attempts are invalid instrument executions,
not application RED controls. No gate condition was relaxed.

## System Tile reproduction on the original APK

The isolated API36/192dp emulator retained the original APK whose hash is
`8722c74d483fd50eeac03eba0f37a09b57c41b77dc8326f3d8ccf2950044d3a7`.
At RU/font1.24, the Activity displayed the admitted synthetic active workout while the
system Tile retained `Workeeper` / truncated `Подключение…`. A subsequent headless
`accepted:refresh` changed the persisted synthetic cache but left that Tile unchanged.
`system-tile-before/observation.md`, acknowledgement, cache bytes, layout and settled PNGs
retain the evidence. These are pre-fix observations, never new-build acceptance.

The old no-session screen showed `Начните тренировку на телефоне` without splitting a word.
The exact reported `тренировки` break has not been reproduced. The only matching static Wear
resource is the ambient `Нет тренировки`; synthetic Russian-name and localized-copy guards
cover word integrity without claiming that they reproduce the original unknown screen.

An additional authorized cleanup removed unused compiler intermediates from clean main `dev`.
All 2,781 report/XML/APK files there were hash-verified unchanged; the exact manifest is
`cleanup-main-builds-manifest.json`. Together with the first two cleanups, 6,969 retained
evidence files were checked. No checkout, AVD or user document was removed. Free space after
this cleanup was 10.53 GiB, before subsequent builds.

## Additional regression and candidate execution

`tile-refresh-required-before-fix/` retains two real-reducer assertion failures with
`106 actionable tasks: 106 executed`. They exposed missing Tile invalidation and missing
refresh-required copy when an unsolicited stale snapshot preserved the display target.
The reducer itself is unchanged; the observer key and renderer now cover that state.

`wear-restored-baseline-and-device-build/` contains 229 passing Wear tests and 65 passing
evidence-parser tests, but its overall gate FAILED because an older instrumented screen test
used the removed UI API. Those calls now use the real Activity/Store. A subsequent graph
compilation attempt also failed and is INVALID, not RED.
`wear-graph-device-build-second/` passed four targeted tests and built both DevDebug APKs,
with `204 actionable tasks: 204 executed`. The candidate APK/source archive and hashes live
in `candidate-graph-apks/`; this is not a final matrix cohort.

The first named-control campaign attempted 41 cases: 38 valid assertion REDs and three invalid
verdicts. Store-retention had a non-compiling mutation; ambient fitting failed the intended
unsafe-ink assertion but the wrapper expected a later message; the Tile column-width mutation
survived the original status-only geometry guard. The last case was a real detector hole.
The native Tile test now checks every visible text line against the circle. The corrected,
new and affected controls are being rerun; no invalid case is counted as RED.

`system-tile-candidate/` retains inspected API36/192dp/RU/font1.24 system-Tile transitions
active -> disconnected -> active, acknowledged headless events, update callbacks and return
to MainActivity. Unlike the old APK, the disconnected Russian status is complete. Startup
logs confirm all three Firebase SDKs initialized, and the local Crashlytics session contains
`platform=watch`. Firebase-console delivery has not been observed. The candidate observation
does not replace final-cohort or API30 evidence.

## Resolved host negative controls

`style-controls-resolved-inventory.json` resolves all 43 current host controls to fresh named
assertion REDs, exact executed-task summaries and byte-restored source. Campaign one, campaign
two and `style-controls-tile-third/` remain separate; failed attempts were not overwritten.

The full-width Tile mutation also survived the first all-line witness: its particular short
title still fit inside the circle. The final witness adds a long valid localized title and
asserts the renderer's actual scaled text size after applying the Robolectric font scale.
The ordinary case passed (`106 actionable tasks: 106 executed`); both one-line status and
full-width column mutations then failed their intended assertions with the same executed-task
summary. This proves those guards detect their named faults; a restored full-suite run follows.

## Restored debug variants and explicit editor shape

`wear-restored-dev-store-and-apks/` passed 534 tests (229 DevDebug, 229 StoreDebug, 65 parser
and 11 shared phone font tests), with `330 actionable tasks: 330 executed`; both debug
app/test APK pairs built. Separate `detekt-before-device/` passed with
`63 actionable tasks: 63 executed` and 55 fresh archived XML reports.

A subsequent native pixel test (`editor-shape-before-fix/`) found the IconButton default
still used its independent circular token. The editor now selects the shared medium radius
explicitly, retaining its 48dp target. `editor-shape-green/` passed; both runs executed 106
tasks. `style-controls-editor-shape/` proves the default-shape and offset-sign controls RED
with exact restoration. The resolved host inventory now contains 44 controls. Restored
device and final repository gates follow this last presentation change.

## Real-Activity baseline and capture verification

`device-rotary-and-controller-third/` passed all five selected real-Activity tests at measured
API36/192dp/RU/font1.24, with `196 actionable tasks: 196 executed`. It retains 45 capture,
semantics, bounds and configuration files. `device-completion-first/` passed its completion
first-view/details test with the same task count and retains 22 artifacts. These targeted
runs are not the full matrix.

The first device attempt found obsolete test tagging and an ActivityScenario Intent identity
mismatch. The test now preserves the launch action/categories while sending fixture extras,
and verifies the exact current localized blocking label. The second attempt passed those
four controller tests but failed the actual-RU assertion because AGP's default cleanup had
uninstalled the package and its per-app locale. The connected tasks now explicitly retain
the test APKs; configuration was reapplied before the successful third run. Both failed
attempts, XML, logs and diagnoses are retained. No application lifecycle contract was changed
for the test repair.

The first image preview incorrectly appeared to omit some content. Decoding the original
files proved all twelve system/composable capture pairs pixel-identical and nonempty. The
preview tool returned PNG bytes with an application/octet-stream MIME prefix; opening the
same bytes as image/png showed the full editor signs and completion instruction. This is
recorded in image-preview-diagnosis.md and candidate-capture-pixel-comparison.json. No emulator
restart, image rewrite or capture-code change was made. The earlier black-frame description
of candidate refreshed.png is explicitly corrected in its observation note.

## Device negative controls and restored lifecycle baseline

`style-device-controls-first/` records five assertion REDs: relative reps/weight batches,
painted minus centring, whole Russian words, and the round-safe completion viewport.
`device-style-restored-rotary/` and `device-style-restored-completion/` each passed their
real-Activity test with `196 actionable tasks: 196 executed`, retaining 45 and 22 artifacts.

The existing six UI/lifecycle controls were adapted to Store-owned editor state and rerun.
The first campaign has four valid REDs and two INVALID natural-expiry attempts: notification
permission was absent after APK reinstall. The system log explicitly marks the Activity
`no-ongoing`, applies a 60-second timer and moves it behind the watch face, so the later
no-Compose-hierarchy exception cannot be the intended expiry assertion. Those attempts
and their raw captures remain in `ui-controls-post-mvi-first/`; the diagnosis and package
permission dump are retained in `ambient-expiry-control-diagnosis/`.

After restoring the canonical lifecycle-positive permission, both
`device-ambient-prerequisite-green-*` and `device-ambient-restored-green-*` passed five tests
across four serial commands: controller/rotary, short editor sleep/wake, natural reps expiry
and natural weight expiry. Every command reports `196 actionable tasks: 196 executed`,
with no skipped/failed/errored test. Between those baselines,
`ui-controls-post-mvi-with-ongoing/` records all six intended assertion REDs and exact
restoration. Together with the 44 resolved host controls, the current inventories contain
55 valid named controls (44 host, 11 device); this is not yet a final full-matrix verdict.

`device-negative-controls-resolved.json` records the concrete assertion messages and
artifact locations. The task-owned API36 emulator was stopped after capture export; its
AVD and prior evidence were retained. Wear also adopts the phone distribution policy
for diagnostic logcat (Dev or Debug enabled, Store Release disabled); the shared logger
sends Firebase breadcrumbs independently of that console flag.

## Independent foundation validation

The first stacked change is assembled separately in
`/private/tmp/workeeper-wear-stack-validation-20260926` on
`codex/wear-shared-foundations`, from `1af93d0b`. It contains shared fonts/tokens,
the existing MVI dependency boundary and complete phone Firebase integration, without
the subsequent presentation, geometry or Tile fixes. The full candidate's results do
not stand in for this intermediate source.

`phase1-build-lint-unit-configured/` passed root `assembleDebug lintDebug
testDebugUnitTest`: 2,835 fresh tests, zero failures/errors/skips, 78 fresh lint XML
reports with no errors, and `2360 actionable tasks: 2360 executed`. The Android-host
MVI identity oracle verified all nine required identities in 26 executed tests.
The initial `phase1-build-lint-unit/` attempt lacked the ignored signing file and
failed configuration before tests; it is retained as setup failure, not a code verdict.
The previously failing CI classes passed locally, which does not establish the cause
of the old CI failure. The additional golden/APK/release/native and Detekt gates follow.

An additional cleanup removed 4.47 GiB of unused compiler/generator intermediates from
the legacy `AndroidStudioProjects/Workeeper` checkout. The cleanup verified 4,462
preserved files byte-for-byte, including both untracked user files, source, APKs and
reports. `cleanup-legacy-builds-manifest.json` records the exact allowlist and hashes.
No AVD, worktree or earlier evidence was removed.

The first two additional-gate attempts used release unit-test task names without AGP's
required opt-in and executed no tests. CI explicitly enables the release source boundary
with `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`; it will run separately
with its exact `ReleaseRuntimeBoundaryTest` identity. The subsequent phase-exit attempt
failed with explicit `No space left on device` errors after the new validation build
directory grew by about 5 GiB. Its `2140 actionable tasks: 2140 executed` line does not
make the failed build green; it produced no completed test XML.

`cleanup-unused-transforms-manifest.json` records removal of 26,758 Gradle transform-cache
entries (10.34 GiB), each older than one day in both modification and access time and
rechecked for open handles immediately before deletion. The downloaded dependency cache,
source, APKs and evidence were excluded. Only this task's verified idle Gradle daemon
was stopped before the fresh retry. These local setup/storage failures do not explain
the unrelated earlier GitHub CI timeout.

## Foundation review and presentation validation

The two foundation review findings were classified before pushing: copied derivations were
correct, and unconditional runtime Logcat was correct-and-new. Colour derivations moved into
`shared-design-tokens.md`, with all 37 values unchanged. Runtime failures use the phone logger,
so Logcat follows the existing debug/dev policy and release failures reach Crashlytics.
`foundation-review-logging-before-fix/` records two real assertion failures; both corresponding
named controls produced valid RED and restored the source. The restored five-test baseline
passed with `106 actionable tasks: 106 executed`.

On `cef8c949`, `foundation-review-build-lint-unit/` records 2,839 tests and 78 clean lint reports:
`2360 actionable tasks: 2360 executed`. StoreRelease and the exact release boundary passed
with `234 actionable tasks: 234 executed`; separate Detekt produced 55 clean reports and
`63 actionable tasks: 63 executed`. The bot completed the current-head re-review without new
findings. The actual Chrome appearance CI and KMP iOS CI passed.

The Android CI on that same head did not pass. Run `36247139763`, first attempt, reported
`UncompletedCoroutinesError` in `SessionRepositoryImplFinishAtomicDbTest` at 14:41 UTC, followed
by `The operation was canceled` at 15:00 UTC. The log and GitHub annotations are retained as
`pr292-reviewed-head-android-ci.log` and `pr292-reviewed-head-ci-annotations.json`. No root cause
is established. A diagnostic rerun of the failed job on the unchanged SHA was requested;
neither local success nor any later repeat removes this failure record. The initial `d2b6288e`
run separately timed out in `LiveSetRowSemanticsTest` and `RestoreDialogChoiceObserverTest`;
its log is also retained. These are distinct from the older #291 failures.

Presentation commit `9291a444` was rebased onto the reviewed foundation, becoming `142c2f4f`.
The archived bundle and range-diff preserve its provenance; validation was repeated on the
updated base. `phase2-validation-index.json` records the new results:

- Root build/lint/unit: 2,881 tests, no failures/errors/skips, 78 clean lint reports;
  `2360 actionable tasks: 2360 executed`.
- All 22 named host controls and both device rotary controls produced the expected assertion
  RED and restored byte-exact source. The device controls observed expected 997 versus actual
  998 repetitions, and expected 99499 versus actual 99749 weight hundredths.
- Restored host tests and repository-wide test APK assembly: 21 tests passed;
  `2181 actionable tasks: 2181 executed`.
- Actual API36 / round192dp / RU / font1.24 Activity: ten baseline tests and one restored rotary
  test passed. Both editors survived short system ambient and closed after genuine elapsed-time
  authority expiry, retaining their unsent values. Each connected invocation reported
  `196 actionable tasks: 196 executed`. Capture directories are unique per invocation.
- StoreRelease and its source boundary: one test passed;
  `234 actionable tasks: 234 executed`.
- Separate Detekt: 55 clean reports; `63 actionable tasks: 63 executed`.
- Source topology, transport self-test/scan and tracked-personal-data checks passed. One external
  orchestration attempt mistakenly supplied an unsupported personal-data self-test option;
  it is retained as INVALID and replaced by the successful qualified source-gate run.

The presentation PR deliberately retains the previous visual layout. Its screenshots prove
Activity behavior, not final-theme acceptance. The bot finished #293's review with no inline
findings. The main, source acceptance and frozen checkouts remained clean at their stated SHAs.

The adapted theme's first root gate then passed 2,887 tests, matching the prediction of three
additional tests in each Wear debug variant. Its 78 fresh lint reports had no errors, and the
summary was `2360 actionable tasks: 2360 executed`. The 12 new host controls, three new device
controls, restored checks and final emulator matrix are separate subsequent evidence.
