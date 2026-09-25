# Wear emulator acceptance

This directory records observable behavior of the debug synthetic Wear application. It
produces an acceptance report before application fixes are considered. A failing assertion,
an unavailable emulator capability, and missing evidence remain distinct outcomes; none
permits silently changing the application or weakening the acceptance condition.

The canonical contracts are [Wear UI completion](../feature-specs/wear-ui-completion.md),
[ambient](../feature-specs/wear-ambient-ui.md), and
[lifecycle](../feature-specs/wear-lifecycle-ui.md). Real phone payload transfer remains
privacy-gated. These scenarios do not acknowledge a set on a phone or calibrate a production
reconnect policy.

## Scope and evidence status

The tools and receiver are acceptance instrumentation. Their presence does not mean that
the emulator matrix has run or passed. Use the generated receipts and report for execution
status; retain failed attempts and their raw artifacts.

The required matrix uses round API 30 and API 36 emulators at 192dp and 240dp, EN and RU,
and font scales 1.00 and 1.24. Verify the actual configuration reported inside the test
process rather than inferring it from an AVD name. Every cell must retain all 18 fixture
identities, including extreme numbers, absent weight, blocked actions and informational
states. Four fixtures use the existing static preview: `weight_error`, `retryable`,
`protocol_mismatch` and `loading`. The other 14 use the synthetic runtime. In particular,
`weight_error` is outside the protocol's canonical numeric range; a screenshot of a static
preview does not prove runtime admission.

First-view evidence covers visible values, the primary action and its specific unavailable
reason. Exercise names, set progress and explanations belong to the scrollable details.
Preserve the requested font scale. Rotary, editor return, Back/swipe, ambient restoration
and authority expiry are separate checks, not consequences inferred from a screenshot.

## Isolated emulator setup

Use dedicated AVD storage. The preparation script refuses to overwrite an existing profile
and records the system-image properties and resulting configuration hash. API 30 uses the
official `android-wear` image; API 36 uses `android-wear-signed`. Install those SDK packages
before preparing a profile.

```bash
python3 documentation/wear-emulator-acceptance/prepare_avd.py \
  --sdk "$ANDROID_SDK_ROOT" \
  --avd-home /private/tmp/workeeper-wear-acceptance-avds \
  --api 30 --dp 192
```

Repeat for both APIs and sizes. Use the recorded startup command with the same
`ANDROID_AVD_HOME`. Keep the existing user's AVDs, installed applications and device
settings untouched. Build and install the explicitly selected debug flavor and its test
APK; record their SHA-256 values, package/version, Git HEAD and complete source inventory.
Do not install the Wear APK over the phone application on a physical phone: their
distribution-specific application IDs intentionally match.

Configure each cell before its trial:

```bash
python3 documentation/wear-emulator-acceptance/configure_cell.py \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial "$SERIAL" \
  --locale ru --scale 1.24 --package io.github.stslex.workeeper.dev \
  --output /private/tmp/wear-acceptance-results/api36-192-ru-1.24-setup.json
```

Use a new receipt path for every setup attempt. On the API 36 user image the script uses
platform per-app locales; it does not require root. On API 30 it uses the official
userdebug image's root capability to set the framework locale and restart that isolated
emulator's framework when necessary. Do this before starting a measured trial, since it
restarts processes. The script also simulates an unplugged battery, enables ambient,
disables stay-awake while plugged in, sets a 120-second interactive screen timeout and records the
previous settings. The battery override prevents the Wear OS 3 charging screen from
covering application ambient; it is not an energy measurement. Keep the same unplugged
condition for baseline and active trials. Its result is
`CONFIGURED_NOT_VERIFIED`; only the test Activity's actual locale, font scale, roundness
and dp dimensions establish the cell configuration.

Each instrumentation launch sends SLEEP then WAKEUP before opening the Activity, resetting
the inherited idle interval without replacing the composition or adding a keep-awake window
flag. WAKEUP alone has no effect on an already awake display. The effective screen timeout
is recorded in the Activity configuration. Explicit ambient SLEEP/WAKE checks remain enabled.
Before passive retention, process-death and inactivity-baseline trials, configure the same
cell with `--screen-timeout-ms 15000`; keep that setting equal for the compared trials.
The lifecycle runner rejects a calibration that does not report that measured timeout.

If the selected emulator reports the sensor HAL abort
`activationOnChangeSensorEvent:231: unexpected sensor type: 26`, retain its crash log and
sensor subscribers before changing settings. `configure_cell.py --disable-tilt-to-wake`
records and disables the system `ambient_tilt_to_wake` setting while keeping ambient on.
This is an optional emulator workaround: verify HAL stability and genuine key-driven
sleep/wake again and identify the workaround in the report. Wrist-gesture wake is not
covered by those results. The [Goldfish HAL](https://android.googlesource.com/device/generic/goldfish/+/main/hals/sensors/multihal_sensors.cpp)
and the [platform setting](https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/provider/Settings.java)
describe the affected sensor path and system option; retain the actual image revision
and observed abort because those source branches can change.

## Run and archive the matrix

Finish all source and tooling edits before creating a cohort. The runner freezes Git
HEAD/branch, tracked and unignored source bytes, and both APK hashes. It rejects drift;
start a new cohort after an application or test change. Evidence must live outside the
checkout. The runner never builds, installs or starts an AVD: install the recorded APKs
on the explicitly selected, already prepared emulator first.

```bash
python3 documentation/wear-emulator-acceptance/adb_acceptance.py plan \
  --cohort /private/tmp/wear-acceptance-results/cohort-01 \
  --apk app/wear/build/outputs/apk/dev/debug/wear-dev-debug.apk \
  --test-apk app/wear/build/outputs/apk/androidTest/dev/debug/wear-dev-debug-androidTest.apk

python3 documentation/wear-emulator-acceptance/adb_acceptance.py run-cell \
  --cohort /private/tmp/wear-acceptance-results/cohort-01 \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial "$SERIAL" \
  --cell api36-192-ru-1.24

python3 documentation/wear-emulator-acceptance/adb_acceptance.py run-lifecycle \
  --cohort /private/tmp/wear-acceptance-results/cohort-01 \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial "$SERIAL" \
  --api 36 --scenario death-disconnect --repetition 1

python3 documentation/wear-emulator-acceptance/adb_acceptance.py run-ambient-expiry \
  --cohort /private/tmp/wear-acceptance-results/cohort-01 \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial "$SERIAL" \
  --api 36 --editor reps
```

Confirm the actual output filenames from the build before supplying them. `plan` defaults
to the Dev package and `<package>.test`; pass `--package` and, if needed, `--test-package`
for a different installed variant. Setup may clear data in the isolated test application,
set its API 36 locale and grant notification access for a positive trial. Keep denial and
restoration observations in their separate manual case.

Run all 16 cells. Each executes 22 named instrumentation invocations: 18 fixture views,
one rotary/editor/press flow, and ambient entry/exit from the controller and both editors.
The runner requires the exact named AndroidJUnitRunner result, a fresh invocation receipt
and the actual `config.json`. Each capture includes a composable PNG, a system PNG,
merged/unmerged semantics dumps and bounds JSON. Fixture captures use the `first-view`
prefix. The rotary/editor/press flow retains eight capture groups: `first-view` plus
`reps-upper`, `weight-upper`, `weight-unset`, `expired`, `rotary-retryable`,
`rotary-complete` and `submitted`;
ambient retains `before-sleep`, `system-ambient` and `after-wake`.
A static preview cannot satisfy an interaction or lifecycle invocation.
Inspect the screenshots and bounds for each cell before entering its visual verdict.
Rotary must move an overflowing controller in both directions. A controller that fits the
240dp viewport still checks focus ownership and unchanged numeric values; its receipt records
zero scroll range and `motionObserved=false`, rather than requiring artificial overflow.

Run the four natural ambient-expiry cases separately: both editors on each API at
192dp/RU/1.24. They wait for genuine elapsed-time authority expiry and require the editor
to close at wake while preserving the displayed draft. The short ambient trials instead
require the still-eligible editor to return. Unobserved system ambient is `BLOCKED`.
For diagnosis, `run-cell --only fixture:active_boundary` can collect a selected invocation,
but the cell remains incomplete/`BLOCKED`; it cannot satisfy the full matrix row.

The report has 72 required rows: 16 UI cells, 18 lifecycle trials, four natural
ambient-expiry cases and 34 operator-reviewed cases. The manual IDs are
`manual/visual/<cell>` and `manual/api<30|36>/<subject>`, where subject is `system-tile`,
`notification-denial-restoration`, `ongoing-return`, `release-boundary`, `store-debug-parity`,
`process-death-restoration`, `inactivity-baseline`, `disconnect-terminal` or
`ambient-capabilities`.
Each manual verdict requires existing, nonempty evidence files, retained with hashes:

- `visual`: inspect every fixture's initial pixels, round-edge clearance, semantic values
  and scrollable details at the recorded configuration; retain any contrary frame.
- `system-tile`: use the installed system Tile, not the preview Activity. Record its
  displayed values, read-only behavior and actual return affordance.
- `notification-denial-restoration`: deny available notification access, observe the
  ordinary UI and disabled retention claim, then restore access. Restoration alone must
  not publish or renew ongoing activity; a later admitted fresh scenario can do so.
  API 30 has no POST_NOTIFICATIONS runtime prompt; record the applicable app/channel path.
- `ongoing-return`: leave to the watch face and use the actual ongoing affordance to
  return. Record Activity/task identity and eligible editor/draft before and after;
  launching MainActivity from ADB is not this return observation.
- `release-boundary`: record the release APK hash and actual installed package. Confirm
  read-only behavior and that the acceptance receiver is absent. Keep this installation
  separate from debug trials and restore the pinned debug APKs before continuing them.
- `store-debug-parity`: execute the fixed smoke subset below with separately pinned
  StoreDebug APKs. This comparison does not replace the declared full matrix.
- `process-death-restoration`: follow the draft-and-restart procedure below. The initial
  draft must differ from the canonical cache so that its disappearance is observable.
- `inactivity-baseline`: observe ten minutes without an active ongoing workout using the
  procedure below, then compare with retention on the same image and settings.
- `disconnect-terminal`: use the acknowledged headless events and capture notification,
  UI and absolute-deadline changes. Repeated disconnect must not extend the deadline;
  a terminal state cancels the ongoing notification. Preserve measured timing bounds.
- `ambient-capabilities`: observe a genuine system ambient update and inventory the
  low-bit and burn-in capabilities using the procedure below. Unsupported capabilities
  can be `N/A` within the row; unknown capabilities or an unobserved update are `BLOCKED`.

Use the matrix cell's existing screenshot/semantics files for a visual verdict and raw
system observations for lifecycle verdicts. Record unsupported sub-capabilities explicitly.

### Draft restoration after ordinary process death

Run this on each API with the pinned debug APK. Use a fresh `active_boundary` scenario
and keep the entire death/reopen sequence within the recorded cache validity interval.

1. Record the actual API/configuration, boot ID, package/APK hash, PID and process birth
   identity. Capture the canonical values and copy `no_backup/synthetic_watch_snapshot`
   through `run-as`; retain its SHA-256. Record the current notification key and absolute
   deadline before editing.
2. Open the reps editor and decrease 999 to 998 without completing the set. Capture the
   editor value and verify that the saved canonical value is still 999. The unsent label
   belongs to the ambient summary, not the interactive editor: if also checking that label,
   capture a short ambient entry and wake before killing. If draft and canonical values
   are equal, the trial has not established its precondition.
3. Press HOME to return to the watch face before killing. Resolve the system HOME component
   and retain a fresh `dumpsys activity activities`: HOME must be resumed for the tested user,
   while the target Activity must be `STOPPED`, `mVisibleRequested=false` and `mVisible=false`.
   Missing or ambiguous records are `BLOCKED`. Confirm that PID/birth, notification key and
   absolute deadline did not change across this transition; do not keep a Workeeper Tile
   displayed during the death interval. Re-read `/proc/<pid>/stat` through `run-as` immediately
   before signalling and confirm the same birth identity.
   Kill that process with `run-as <package> kill -9 <pid>`. Record elapsed-time brackets
   around the command and evidence that the old process ended. Do not use force-stop,
   clear application data, reinstall, refresh, or select another fixture.
4. Open the application from its launcher with no fixture/scenario extra. Record the launch
   route, actual Activity intent, new PID/birth identity and first rendered screen; a replayed
   debug fixture intent cannot establish cache restoration. Expect the cached canonical 999,
   disabled editing/completion and no restored 998 draft. If inspecting ambient afterward,
   expect no unsent marker. Capture the post-restart
   cache and notification state: startup must not create a new authority grant, extend the
   old absolute deadline or recreate a notification that has already expired.

Retain before/after screenshots, a short video spanning edit/death/reopen, raw cache bytes,
hashes, boot/PID/birth observations and notification/deadline dumps. This trial intentionally
reopens the app; the separate `death-fresh` and `death-disconnect` trials must observe timeout
removal without reopening it. If death, freshness or cache validity cannot be established,
record `BLOCKED` rather than treating a blank or newly seeded screen as successful recovery.

After each timed `death-fresh` or `death-disconnect` observation has ended and its receipt
has been saved, also launch normally without fixture/scenario extras. Capture the launch
intent, first screen, accessibility state, new PID and notification state. Only the cache
state still permitted at that time may appear; editing/completion must remain unavailable,
and startup must not renew the expired deadline. Keep these captures outside the completed
trial directory and attach them, identified by trial ID, to the corresponding
`process-death-restoration` manual row. Reopening during the timed interval invalidates it.

### Fixed StoreDebug smoke subset

On each API, use 192dp/RU/font 1.24 and record the actual configuration, StoreDebug package
`io.github.stslex.workeeper`, application/test APK hashes and source inventory. Use a separate
StoreDebug cohort when invoking the runner so its APK checks remain meaningful. Its partial
matrix result stays `BLOCKED`; attach the relevant receipts to the main cohort's manual
comparison, whose scope is precisely this subset:

1. Capture and inspect the initial view and reachable details for `active_boundary`,
   `weightless`, `refresh_required`, `disconnected` and `no_session`.
2. Execute the rotary/editor/press flow, retaining all eight capture groups and its actual
   instrumentation result. Execute short system ambient entry/exit from the controller,
   reps editor and weight editor, with the same restoration criteria as the Dev trials.
3. Seed a new active scenario, deliver acknowledged headless `refresh`, `disconnect` and
   `terminal` in order, and retain notification/deadline/UI evidence for each transition.
4. Execute the unsent-draft process-death restoration procedure above with StoreDebug.

Steps 1 and 2 can use a separately planned StoreDebug cohort with these selections; repeat
with `api30-192-ru-1.24` on API 30. The command intentionally returns partial/`BLOCKED` for
its UI matrix row, so review all selected invocation results before attesting the smoke:

```bash
python3 documentation/wear-emulator-acceptance/adb_acceptance.py run-cell \
  --cohort /private/tmp/wear-acceptance-results/store-smoke \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial "$SERIAL" \
  --cell api36-192-ru-1.24 \
  --only fixture:active_boundary --only fixture:weightless \
  --only fixture:refresh_required --only fixture:disconnected --only fixture:no_session \
  --only rotary --only ambient:none --only ambient:reps --only ambient:weight
```

Compare each observation with the matching Dev trial, recording discrepancies rather than
assuming flavor equivalence. Attach the selected raw receipts, screenshots and transition
videos. Restore the pinned Dev APKs and reverify their installed hashes before continuing
Dev trials. A StoreDebug smoke pass is not a complete StoreDebug matrix or a release result.

### Ten-minute inactivity baseline

Use the same API, AVD/image, package build, cell configuration, screen timeout, charging and
stay-awake settings as the compared retention trial. Start in `no_session`, confirm that no
ongoing notification exists, and record the initial resumed Activity and elapsed time. Leave
the screen untouched for ten minutes: do not refresh, wake, tap, rotate, send lifecycle
events or maintain an instrumentation session. Sample elapsed time, boot/process identity
and notification absence every 250ms, rejecting polling or command intervals above the same
500ms resolution budget as retention. Record resumed Activity and power/display state
separately once per second. Preserve the raw samples and actual return-to-watch-face
transition, or record that no return was observed during the entire interval.

Compare the resulting timeline with the ten-minute active-retention trial on that image.
The baseline records platform behavior; it does not require all OS versions to return at a
particular time. Missing samples or an active notification invalidate the comparison. After
the passive interval, separately record a deliberate user exit and verify that the app
allows it. Capture short video around an observed automatic return and the deliberate exit;
use timestamped samples, not video playback duration, for timing claims.

### Ambient updates and platform capabilities

On each API, record the image/build, actual configuration, always-on setting and independently
reported low-bit and burn-in capability values with their raw evidence source. Do not infer
capabilities from a black screenshot or the emulator model name. An explicitly unsupported
capability is `N/A` with its evidence; an unavailable or ambiguous capability observation is
`BLOCKED` for that sub-check.

Enter system ambient from the active controller without substituting a provider or advancing
the debug clock. Observe it continuously for 90 seconds, retaining timestamped system PNGs
and a video spanning at least one displayed minute change. Record entry, the changed clock
while still ambient, and wake; the summary must remain noninteractive. This demonstrates an
observable system-driven update, not an inferred callback from the initial ambient frame.
Retain callback diagnostics if the platform exposes them. If no update or continuous ambient
state can be established, this required row remains `BLOCKED`.

When the reported low-bit flag is supported and enabled, inspect the original ambient PNGs
for the black/white text output. When burn-in protection is supported and enabled, compare
static glyph positions across successive update frames and check that the painted content
remains within the screen. Record these sub-results separately; do not force either flag or
claim an unsupported emulator measured it. Attach capability evidence, original PNGs, video
and the timestamped observation log. Unsupported sub-capabilities do not waive the required
entry/update/wake observation. Even a passing emulator row leaves the physical low-bit,
burn-in and lifecycle checks in [the ambient contract](../feature-specs/wear-ambient-ui.md#5-executed-host-evidence-and-physical-boundary)
and [lifecycle §7](../feature-specs/wear-lifecycle-ui.md#7-physical-and-final-phase-1-acceptance)
open.

### Video evidence

Keep short, unedited recordings for rotary/Back/swipe/press behavior, ambient entry/update/
wake, return through the watch-face ongoing affordance, notification denial/restoration and
the unsent-draft death/reopen sequence. The emulator window or a supported system screen
recorder may supply the video; record which one, its configuration and start/end timestamps.
An external window recording must not send input or keep the emulated screen awake. Use the
same recording setup for compared baseline/retention observations and record any interference.

Retain the original recording with SHA-256 and attach it using `--artifact` to the relevant
manual case; do not replace the original PNGs, semantics, bounds, raw JUnit output or timing
logs. If recording is unavailable or misses the decisive transition, record the missing
visual evidence explicitly and keep that manual observation `BLOCKED`. Screenshots can
support static visual inspection, but cannot stand in for the requested transition video.

```bash
python3 documentation/wear-emulator-acceptance/adb_acceptance.py record-manual \
  --cohort /private/tmp/wear-acceptance-results/cohort-01 \
  --case manual/api36/system-tile --status BLOCKED \
  --reason "System Tile observation is incomplete; see the attached capture log." \
  --artifact /private/tmp/wear-acceptance-results/system-tile-capture.log

python3 documentation/wear-emulator-acceptance/adb_acceptance.py report \
  --cohort /private/tmp/wear-acceptance-results/cohort-01
```

Use a reason that describes the actual observation and attach its actual evidence; the
example does not create or imply a Tile result. Case directories are never overwritten.
Retain `plan.json`, every `cases/<id>/receipt.json`, `commands.jsonl`, raw command output,
instrumentation captures, elapsed-time observations and the final `report.json`.
The report verifies artifact hashes and returns a nonzero status for `FAIL` or `BLOCKED`.

## Headless lifecycle events

`AcceptanceScenarioReceiver` exists only in `src/debug`. Its exported manifest component
requires `android.permission.DUMP`, held by the shell and eligible privileged callers.
It has no intent filter and requires its exact explicit component, action and an ordered
broadcast. It calls the existing `WatchRuntimeFactory` driver in the existing process;
it neither starts an Activity nor advances the debug clock.

The only accepted scenario names are `refresh`, `disconnect` and `terminal`. First seed a
valid fixture using the application's existing debug launch path. A cold process or a
terminal scenario can leave the driver without a current fixture; a subsequent refresh
then fails visibly instead of creating a workout.

```bash
adb -s "$SERIAL" shell am broadcast \
  -n io.github.stslex.workeeper.dev/io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver \
  -a io.github.stslex.workeeper.wear.ACCEPTANCE_SCENARIO \
  --es scenario refresh
```

For StoreDebug, use `io.github.stslex.workeeper` before the slash. A successful delivery
must end with ordered result code `-1` and data `accepted:refresh` (or the exact requested
`disconnect`/`terminal`). Rejection has result code `0` and data
`rejected:invalid_action`, `rejected:invalid_component`, `rejected:invalid_scenario`, or
`rejected:driver_rejected`. A shell exit code alone is insufficient. Unordered broadcasts
perform no operation and provide no acknowledgment. `expire`, `stop_ongoing`, fixture
selection and missing/unknown scenarios are not accepted by this receiver.

Record elapsed time immediately before and after the acknowledgment. Use genuine elapsed
time for expiry and notification-removal measurements. Synthetic clock-offset events
cannot establish those measurements.

## Lifecycle observations and limits

For each API, record three repetitions of retention, process death with a fresh deadline,
and process death after disconnect. Retain the boot ID, PID plus process birth identity,
notification key, recorded absolute deadline and before/after elapsed-time bounds. A
notification disappearing because the application was force-stopped is not evidence that
its original platform timeout survived ordinary process death.

The runner compares the Activity's actual `SystemClock.elapsedRealtime()` with the
external boot-time observation bracket. Retention and death observations target 250ms polls
and reject polling or command intervals above 500ms. Scheduled refreshes retain a separate
two-second lateness budget; death observations have a five-second removal watchdog. Resumed
Activity state is sampled separately once per second, and its cadence is recorded in the
retention receipt. A missing, delayed or incompatible observation
is inconclusive rather than evidence of acceptable timing. These instrument bounds do
not calibrate the application's production reconnect or retention policy.

The notification must survive until its persisted deadline. A disappearance proved earlier
is `FAIL`; an interval that cannot establish removal before the five-second watchdog is
`BLOCKED`. `/proc/uptime` has 10ms printed precision: the conservative absence upper bound is
the observed end plus 10ms, and the report retains this precision and the full removal interval.
This rounding bound is part of the observer, not a production timing tolerance.

Record permission denial and restoration, return from the ongoing notification to the
same Activity, and the system Tile separately. Granting permission alone must not create
a notification or renew authority. The ordinary UI remains available while notification
access is denied. A missing notification is not proof of its removal deadline unless its
earlier presence and identity were captured.

API 36 and API 30 observations must be reported separately. Emulator watch-face behavior
and process scheduling do not establish retention on a physical Wear OS 5+ watch or on
older supported watches. Device reconnect measurements, notification removal after
process death, physical return/retention behavior and calibrated policy constants remain
open physical acceptance work. The current reconnect interval is explicitly uncalibrated
and debug-only.

## Protective host checks

`AcceptanceScenarioReceiverTest` exercises acknowledgment, actual driver transitions,
rejection of time-offset/fixture events, unordered rejection and absence of Activity
launches. `AcceptanceScenarioBoundaryTest` checks the merged debug receiver's exported
flag and DUMP permission; its release execution checks that both class and manifest entry
are absent. The existing `ReleaseRuntimeBoundaryTest` also checks both absences, so the
workflow's explicit release boundary command includes the receiver. Host manifest checks
do not themselves prove on-device permission enforcement.

Run the focused debug tests and a separately enabled release test task with fresh Gradle
flags. Release commands require
`-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` on the pinned AGP version.
Run Gradle commands sequentially:

```bash
./gradlew :app:wear:testDevDebugUnitTest \
  --tests '*AcceptanceScenario*' --rerun-tasks --no-build-cache --no-configuration-cache

./gradlew :app:wear:testDevReleaseUnitTest \
  --tests '*ReleaseRuntimeBoundaryTest*' --tests '*AcceptanceScenarioBoundaryTest*' \
  -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false \
  --rerun-tasks --no-build-cache --no-configuration-cache
```

Each protective check needs its named mutation and an intended assertion failure; compile
errors, cached tasks and missing XML are not negative-control success. Mutation execution
must use [the byte-restoring harness](../mockups/mutation_harness.py). The seven
[receiver controls](receiver_mutations.json) specify the exact source replacement, task
and expected JUnit identity. They cover acknowledgment, the scenario/action allowlists,
unordered rejection, Activity launch, debug permission and the release manifest boundary.

The [parser controls](parser_mutations.json) protect notification/process observations,
configuration and artifact validation, complete result inventories and assertion classification.
Run their baseline, controls and restored baseline serially:

```bash
./gradlew :app:wear:verifyEmulatorAcceptanceRunner \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain
python3 documentation/wear-emulator-acceptance/run_mutations.py \
  --cases documentation/wear-emulator-acceptance/parser_mutations.json \
  --output /private/tmp/wear-acceptance-results/parser-controls
./gradlew :app:wear:verifyEmulatorAcceptanceRunner \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain
```

Use the same wrapper with `receiver_mutations.json` after its debug/release baselines.
The wrapper requires fresh XML for the exact named method, assertion-only failures,
fully executed Gradle tasks and byte-exact restoration. Inspect each failure to confirm its
intended assertion; an unrelated assertion in the same method is not the intended control.
Preserve every attempt in a new output directory. Rebuild both acceptance APKs after mutations:
restoring source bytes does not replace the APK produced by the mutated build.

The six [instrumented UI controls](ui_mutations.json) use an API 36, 192dp, EN, font-1.24
emulator. Configure the platform per-app locale to EN before each invocation and retain
the setup receipt. A connected Gradle task may reinstall the application and reset its
per-app locale; this control profile also has global EN. The Activity's actual resource
configuration assertion remains required. First execute the identical unmutated command
successfully, then its named negative control. This narrower control profile does not
replace the full EN/RU acceptance matrix.
Run each UI control separately with `--only <name>` and repeat setup before its GREEN and RED.
Connected Gradle tests can uninstall the target APK on completion; reinstall it before
clearing its data or granting permissions for the next setup. After the control campaign,
rerun the restored UI baselines and archive their XML before starting the acceptance cohort.

## Reporting

### Recorded infrastructure findings

The first frozen attempt at `bcb8466c` completed the two API30/240dp/EN cells: 44
invocations, 40 PASS and four FAIL. Keep those original verdicts and artifacts; they do
not become passing acceptance evidence after the runner changes. The evidence bundle
contains `attempt-bcb8466-analysis.md` and the review reproductions below. Application
sources under `app/wear/src/main` were unchanged from the PR #290 baseline.

| Finding | Observed evidence | Classification and correction |
| --- | --- | --- |
| A1: framework readiness | PackageManager was available after an API30 locale restart while SettingsService still returned exit 20. | Infrastructure setup failure; wait for both services. Preserve the failed setup receipt. |
| A2: inherited display idle | Three fixture failures had an ambient-only bounds tree; two earlier captures in the same invocation were still interactive. | Mixed capture states, not proof of a missing interactive controller. Reset display idle before launch and use the recorded interactive timeout. Passive trials retain their separate 15-second setting. |
| A3: a controller that fits | At 240dp/font1.0, `after-weight-back` had zero scroll range; the same path at font1.24 moved 0 → 1 → 0px. | Invalid overflow precondition; retain focus, numeric invariants and mandatory bidirectional movement wherever range is positive. |
| R1: premature notification removal | At deadline 10000ms, the observer incorrectly accepted an absence bracket ending at 1220ms. | [Correct and new](https://github.com/stslex/Workeeper/pull/291#discussion_r4104491191); reject proved early disappearance and account for printed clock precision at both deadline bounds. |
| R2: instrumentation execution errors | Named status −1 with `IllegalStateException`, and −2 with `RuntimeException`, both became application FAIL. | [Correct and new](https://github.com/stslex/Workeeper/pull/291#discussion_r4104491201); require a named top-level assertion for FAIL, otherwise record BLOCKED. |

Changed source or tooling requires a new frozen cohort and APK provenance. Successful
retries supplement these records; they never overwrite the original outcomes.

### Acceptance ledger

Every required row must report `PASS`, `FAIL` or `BLOCKED` with raw artifacts and a reason.
Capability-level `N/A` must be explicit and cannot replace an entire required matrix row.
Keep pending visual inspection separate from automated assertions. Preserve source/APK
hashes, exact commands, timestamps, actual test identities, raw output, screenshots and
configuration evidence so a later reviewer can distinguish observations from assumptions.
Report application defects before preparing a separate application fix.

After recording the case receipts, generate the validated human-readable report and summary XML:

```bash
python3 documentation/wear-emulator-acceptance/render_report.py --cohort "$COHORT"
```

This writes `report.md`, `report.html` (with links to original PNGs) and `report.xml` at the
cohort root. Summary XML is a presentation of the acceptance ledger; raw instrumented output
and Gradle JUnit XML remain the execution evidence. Missing rows are errors, never skips.
