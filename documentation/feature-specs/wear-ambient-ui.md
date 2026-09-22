# Wear ambient presentation increment

**Status:** ambient source has executed host acceptance evidence: the 128-cell matrix,
24 named negative controls, restored focused gates and repository gates described below.
This remains the ambient-only PR. Runtime/ongoing integration and physical always-on
acceptance are not claimed by this increment.

[Wear UI completion](wear-ui-completion.md) owns the delivery order. Its PR-D visibility
contract and evidence remain separate. The privacy/transport gate remains closed.

## 1. Platform provider and permission

`AndroidWearAmbientProvider` adapts `AmbientLifecycleObserver` from pinned
`androidx.wear:wear:1.3.0` into the injectable `WearAmbientProvider`. The library declares
minSdk 23 and is compatible with the app's existing minSdk 28; the app minimum does not
change. `WearAmbientController` handles entry/update/exit independently of Android.

Only the Wear app bundle disables language splitting, so every supported locale remains
available offline when the summary uses the model's selected locale; Android lint guards
that packaging requirement.

The pinned observer requires `android.permission.WAKE_LOCK` in the app manifest.
Declaring that library-required permission does not authorize or implement an app-owned
freshness wake lock: this increment acquires no custom PowerManager lock, sets no
keep-screen-on flag, and introduces no foreground service, exact alarm or polling timer.
The provider registers one Activity lifecycle observer and uses system ambient events.

Every ambient event invokes the injected authority-expiry callback synchronously before
publishing its transition. Failure must not expose a resumed interactive branch. Display
wall time is sampled on system events only and never participates in elapsed-realtime
mutation authority. Waking cannot issue a handshake or renew a lease.

## 2. Summary and low-power rendering

Ambient replaces the entire interactive subtree with a noninteractive summary. Buttons,
editor BackHandler, touch actions and rotary handlers are absent from the ambient branch.
The summary shows time, concise context, exact set progress, reps, applicable weight,
status and an explicit unsent-value marker. Full context/progress remain available in
spoken semantics. Only context may ellipsize; numeric rows are not silently shortened.

The Canvas paints white text on black. Native text antialiasing is disabled in low-bit
mode; subpixel text and dithering are disabled. Burn-in support shifts content by a
bounded two-pixel vector selected from the system-event minute. Content does not animate
between events. Font scale is respected rather than reduced to satisfy a screenshot.
The target is at least 85% black pixels, following
[Android ambient guidance](https://developer.android.com/training/wearables/always-on#recommendations-for-ambient-mode).

Painting tests compare actual numeric rows with independently derived expected strings,
including absent weight and weightless state. They require positive expected ink and
exact row raster equality. A matching semantic description, recomputed layout or merely
nonblank bitmap cannot substitute for the numeric text being painted.

## 3. Editor and controller continuity

`rememberSaveableStateHolder` retains the interactive branch's scroll state while ambient
is visible, and `editingField` survives the branch switch. The ambient entry itself does
not alter model values or create a draft. `hasUnsubmittedDraft` supplies the unsent marker.

On exit, current edit eligibility decides whether to restore the previous reps/weight
editor. Expired authority closes it and returns hierarchy focus to the controller.
A still-authorized editor receives rotary focus again without an extra tap. Controller
scroll position remains the one held before entry. Host integration tests supply mutable
models/actions and cover both editor paths, genuine overflowing content and authority loss.
They do not establish process-surviving draft storage or physical Activity recreation.

## 4. Transitional Activity source in this increment

MainActivity still consumes `WatchProcessState.surface/currentSurface`, and ambient events
call `WatchProcessState.expireAuthority()`. After that synchronous callback, the flow
transform samples the current surface rather than replaying an earlier combine emission.
`WatchProcessState` has no admitted production workout source here and starts read-only.

Explicit debug fixture intents select static preview models; release does not interpret
those fixture IDs. The Activity controller action callback is still a no-op at this stage.
Thus an ambient editor/summary host test with injected model updates is not evidence that
an installed preview submits edits, stores a draft, confirms a set or talks to a phone.
A static debug preview is not a lifecycle-authority owner.

The next sequential PR replaces this temporary wiring with the single runtime/cache/ongoing
owner and notification permission UI. Its coordinator, cache restoration, command-in-flight,
notification deadline and release-boundary claims must be reviewed and proven there. This
ambient PR does not add ongoing retention or establish foreground persistence on Wear OS 5+.

## 5. Executed host evidence and physical boundary

The painting matrix passed **128 cells**: 192/240dp × font scales 1.0/1.24 × low-bit
off/on × burn-in off/on × four models × EN/RU. Models cover maximum reps/weight and
Int.MAX_VALUE progress with unsent values, absent weight with its blocking reason and
unsent values, weightless state, and protocol mismatch. The complete 128 named PNGs and
their hashes are archived from the fresh and restored DevDebug runs and final repository
gate. Pixel assertions check black-pixel budget, low-bit output, safe bounds and complete
numeric-row raster; no physical rendering or visual-inspection claim follows automatically.

| Gate | Archived XML | Executed task summary |
| --- | --- | --- |
| DevDebug ambient + G9 baseline | 12 tests / 6 classes; zero failures, errors or skips | `64 actionable tasks: 64 executed` |
| StoreRelease pure controller/callback baseline | 8 tests / 2 classes; zero failures, errors or skips | `38 actionable tasks: 38 executed` |
| Restored DevDebug ambient + G9 | 12 tests / 6 classes; zero failures, errors or skips | `37 actionable tasks: 37 executed` |
| Restored StoreRelease pure controller/callback | 8 tests / 2 classes; zero failures, errors or skips | `38 actionable tasks: 38 executed` |

Release host coverage above is deliberately limited to the eight pure controller/callback
methods. Compose painting/editor/scroll tests use DevDebug because the host test Activity
manifest is debug-only. `assembleStoreRelease` is a separate build result; it is not a
release-device UI test or an ongoing release-boundary test.

All **24 controls** below produced an intended named assertion RED and byte-exact
restoration. The exact-method validator records **12/12 methods** covered: eleven new
protective methods and the extended G9 check. The two draw-only controls omit WEIGHT/REPS
while retaining production layout and semantics, so those structures alone cannot pass
the painting oracle. Observed mutation task summaries: `37 actionable tasks: 37 executed`.

Assertion excerpts below come directly from each selected run's saved intended-failure
XML. Prepared manifest observables are not execution evidence; the archived full XML
retains any longer message whose first line is abbreviated here.

| Executed control | Protected contract | Observed intended failing method | Actual XML assertion excerpt |
| --- | --- | --- | --- |
| `ambient-enter-bypasses-injected-clock` | Provider display time must be sampled from its injected clock only on platform events. | WearAmbientControllerTest.expiry precedes every published transition and only events read the display clock() | org.opentest4j.AssertionFailedError: expected: &lt;120000&gt; but was: &lt;1790107810431&gt; |
| `ambient-system-update-skips-expiry` | Every system update checks existing authority, including updates received outside ambient. | WearAmbientControllerTest.update outside ambient checks expiry without entering ambient or reading the display clock()<br>WearAmbientControllerTest.wall clock changes cannot supply or extend the monotonic authority deadline() | org.opentest4j.AssertionFailedError: expected: &lt;1&gt; but was: &lt;0&gt;<br>org.opentest4j.AssertionFailedError: expected: &lt;false&gt; but was: &lt;true&gt; |
| `ambient-inactive-update-reads-clock` | An inactive update must not sample the display clock or publish a timestamp. | WearAmbientControllerTest.update outside ambient checks expiry without entering ambient or reading the display clock() | org.opentest4j.AssertionFailedError: An inactive update must not sample the display clock ==&gt; expected: &lt;0&gt; but was: &lt;1&gt; |
| `ambient-wake-publishes-before-authority-check` | Expiry must finish synchronously before publishing isAmbient=false; failure remains ambient. | WearAmbientControllerTest.expiry precedes every published transition and only events read the display clock()<br>WearAmbientControllerTest.failed expiry check never exposes an interactive state() | org.opentest4j.AssertionFailedError: expected: &lt;WearAmbientState(isAmbient=true, timestampMillis=0, deviceHasLowBitAmbient=false, burnInProtectionRequired=false, offset=WearAmbientOffset(xPx=0, yPx=0))&gt; but was: &lt;WearAmbientState(isAmbient=false, timestampMillis=0, deviceHasLowBitAmbient=false, burnInProtectionRequi...<br>org.opentest4j.AssertionFailedError: expected: &lt;[WearAmbientState(isAmbient=false, timestampMillis=null, deviceHasLowBitAmbient=false, burnInProtectionRequired=false, offset=WearAmbientOffset(xPx=0, yPx=0)), WearAmbientState(isAmbient=true, timestampMillis=120000, deviceHasLowBitAmbient=true, burnInProtectionRequire... |
| `ambient-burn-in-offset-exceeds-vector-budget` | The displacement vector, not only each coordinate, must stay within two physical pixels. | WearAmbientControllerTest.burn-in position depends on the event minute and stays within physical pixel bounds() | org.opentest4j.AssertionFailedError: expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-burn-in-offset-frozen` | Burn-in protection must move over event minutes without an owned timer. | WearAmbientControllerTest.burn-in position depends on the event minute and stays within physical pixel bounds() | org.opentest4j.AssertionFailedError: expected: &lt;9&gt; but was: &lt;1&gt; |
| `ambient-offset-enabled-without-capability` | Offsets are conditional on the independent burn-in capability. | WearAmbientControllerTest.devices without burn-in protection retain a zero offset through every event() | org.opentest4j.AssertionFailedError: expected: &lt;WearAmbientOffset(xPx=0, yPx=0)&gt; but was: &lt;WearAmbientOffset(xPx=0, yPx=-2)&gt; |
| `ambient-platform-low-bit-conflated-with-burn-in` | The adapter must forward both platform capability flags independently. | WearAmbientLifecycleCallbacksTest.platform callbacks preserve independent capabilities and drive the injected state provider() | org.opentest4j.AssertionFailedError: expected: &lt;false&gt; but was: &lt;true&gt; |
| `ambient-platform-update-not-forwarded` | Every platform update callback must reach the injectable controller. | WearAmbientLifecycleCallbacksTest.platform callbacks preserve independent capabilities and drive the injected state provider() | org.opentest4j.AssertionFailedError: expected: &lt;60000&gt; but was: &lt;0&gt; |
| `ambient-platform-entry-retains-stale-capability` | Each entry replaces capability details rather than accumulating flags. | WearAmbientLifecycleCallbacksTest.each ambient entry replaces previous device details() | org.opentest4j.AssertionFailedError: expected: &lt;false&gt; but was: &lt;true&gt; |
| `ambient-low-bit-allows-antialiasing` | Native low-bit text must not use antialiasing. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=true, burnIn=false) ACTIVE low-bit pixel at 82,42 ==&gt; expected: &lt;-1&gt; but was: &lt;-15527149&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=true, burnIn=false) ACTIVE low-bit pixel at 82,42 ==&gt; expected: &lt;-1&gt; but was: &lt;-15527149&gt; |
| `ambient-background-is-white` | The actual rendered background must remain black. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE unsafe ink at 0,0 ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE unsafe ink at 0,0 ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-more-than-fifteen-percent-lit` | At least 85% of physical circular screen pixels must remain black, independently of clipping safety. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE lit=8162 screen=28968 ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE lit=8320 screen=28968 ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-renderer-ignores-event-timestamp` | Renderer must use the supplied event timestamp. Frozen time is a deterministic control; no real-clock boundary race. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: Only an event timestamp should advance the displayed time ==&gt; expected: not equal but was: &lt;[03:00. An intentionally long exercise name that remains complete in the spoken ambient description. Set 2147483647 of 2147483647. Weight: 999.99 kg. 999 reps. Ready. Complete set, enabled...<br>org.opentest4j.AssertionFailedError: Only an event timestamp should advance the displayed time ==&gt; expected: not equal but was: &lt;[03:00. An intentionally long exercise name that remains complete in the spoken ambient description. Подход 2147483647 из 2147483647. Вес: 999,99 кг. 999 повт.. Готово. Завершить подход, д... |
| `ambient-renderer-truncates-reps` | Numeric display values must remain complete even at the largest font size. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-draft-marker-removed` | Unsubmitted draft state must be explicit in English/Russian semantics and covered by the actual ambient G9 corpus. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock()<br>WearStringCoverageGateTest.every Wear string resource is rendered by at least one fixture | org.opentest4j.AssertionFailedError: 1 string id(s) are rendered by NO fixture:<br>org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-discards-reps-editor` | Each numeric editor must survive ambient independently, without sending its draft. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | java.lang.AssertionError: Failed to assert the following: (Text + EditableText = [9]) |
| `ambient-discards-weight-editor` | Each numeric editor must survive ambient independently, without sending its draft. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | java.lang.AssertionError: Failed to assert the following: (Text + EditableText = [102.5 kg]) |
| `ambient-discards-controller-scroll` | The interactive subtree must retain saved scroll state when disposed for ambient. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | org.opentest4j.AssertionFailedError: Ambient discarded the controller scroll position ==&gt; expected: &lt;115.0&gt; but was: &lt;0.0&gt; |
| `ambient-expired-editor-remains-eligible` | Wake may restore a retained editor only while current authority still permits editing. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | java.lang.AssertionError: Failed: assertDoesNotExist. |
| `ambient-exit-skips-expiry-integration` | The production screen and injected controller together must retire an expired editor on wake. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | java.lang.AssertionError: Failed: assertDoesNotExist. |
| `ambient-retains-interactive-input-tree` | Ambient must replace interactive composition, not leave hidden focus/actions active. | WearAmbientControllerIntegrationTest.ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() | java.lang.AssertionError: Failed: assertDoesNotExist. |
| `ambient-draw-only-omit-weight` | The actual Canvas raster must draw the complete WEIGHT row independently of preserved layout and semantics. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE WEIGHT actual row must draw full '999.99 kg': expected ink=383, actual ink=0 ==&gt; expected: &lt;0&gt; but was: &lt;383&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE WEIGHT actual row must draw full '999,99 кг': expected ink=332, actual ink=0 ==&gt; expected: &lt;0&gt; but was: &lt;332&gt; |
| `ambient-draw-only-omit-reps` | The actual Canvas raster must draw the complete REPS row independently of preserved layout and semantics. | WearAmbientSummaryRuTest.russianSummaryFitsAndHasNoIndependentClock()<br>WearAmbientSummaryTest.englishSummaryFitsAndHasNoIndependentClock() | org.opentest4j.AssertionFailedError: en/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE REPS actual row must draw full '999 reps': expected ink=341, actual ink=0 ==&gt; expected: &lt;0&gt; but was: &lt;341&gt;<br>org.opentest4j.AssertionFailedError: ru/AmbientProfile(screen=SMALL_ROUND, fontScale=1.0, lowBit=false, burnIn=false) ACTIVE REPS actual row must draw full '999 повт.': expected ink=323, actual ink=0 ==&gt; expected: &lt;0&gt; but was: &lt;323&gt; |

After restoration and `clean`, `assembleDebug detekt lintDebug testDebugUnitTest` reports
**2331 actionable tasks: 2331 executed**. Wear XML: DevDebug: 92 tests in 36 classes; StoreDebug: 92 tests in 36 classes; zero failures,
errors or skips. `assembleDebugAndroidTest verifyPaparazziDebug :lint-rules:test
:app:wear:assembleStoreRelease` reports **2400 actionable tasks: 2400 executed**.
All Gradle gates ran serially with `--rerun-tasks --no-build-cache --no-configuration-cache`.
Final rendered-string result: `G9 string coverage: walked 47 id(s) — 45 required, 2 allowlisted by design, 45 reached, 0 unreached.`.

Evidence run: `20260922T200808.392050Z`, based on `b968fb93ac84ecba0df480773f2498c0ab39af4e` plus the recorded source-byte snapshot.
Logs, command metadata, XML identities, PNG inventories, before/mutant bytes and the final
validator report are retained under `/private/tmp/wear-ambient-evidence/runs/20260922T200808.392050Z` and its selected mutation-record paths.
The table records those observed local controls; it does not assert that their runnable
manifests were committed or shipped with the application. Source bytes were checked
unchanged through the completed pipeline and immediately before this documentation update.

**Physical ambient acceptance remains pending.** Host clock callbacks, injected model
actions, raster assertions and rotary events do not prove target-watch lifecycle callbacks,
Activity recreation, TalkBack speech or automatic return-to-watch-face behavior. Verify
entry/update/exit, device low-bit/burn-in behavior and authority-loss editor exit separately,
recording build type, OS/API and exact scenario. The transitional no-op/static-preview
boundary in §4 remains unchanged. Ongoing retention, calibrated reconnect timing, process-
death notification removal and real phone transport/acknowledgement belong to later
increments and their separate privacy and physical acceptance gates.

### 5.1 Review follow-up: draft projection and system time preference

The original campaign completed its Gradle gates and six negative controls, but its
controller remained **FAILED** during postprocessing of repeated Paparazzi display
labels. A separate proof-only reprocessing validates the original executed artifacts;
it does not rerun Gradle, mutations or source changes. See the provenance below.

The preceding §5 is the original host-evidence ledger published in `4e7581dd`; its
24-control campaign, release-host scope and counts remain historical. This follow-up
records a separate reviewed candidate based on that commit plus the source-byte snapshot
below. It does not relabel the original mutation results as measurements of the new source.

Both review findings were reproduced on unchanged production before applying their fixes
and classified **correct-and-new**. The new regression classes executed three methods:
exactly two intended assertion failures and one passing canonical/targetless control,
with **37 actionable tasks: 37 executed**. Actual archived assertions:

| Unfixed regression | Actual XML assertion excerpt |
| --- | --- |
| WearAmbientTimePreferenceTest.systemTimePreferenceControlsSpokenAndDrawnClockAtSmallScreenBoundaries() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: AmbientClockCase(locale=en, use24Hour=false, timeZone=sun.util.calendar.ZoneInfo[id="UTC",offset=0,dstSavings=0,useDaylight=false,transitions=0,lastRule=null], timestampMillis=0) system time preference must produce '12:00 AM', actual '00:00. Single-arm dumbbell shoulder press. Set 2147483647 of 2147483647. Weight: 999.99 kg. 999 reps. Ready. Complete set, enabled. Not sent' ==&gt; expected:... |
| WearSurfaceDraftProjectionTest.activeDraftProjectionMarksUnsubmittedValues() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: Every projected active draft must retain its unsent marker ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |

`WearSurfaceMapper` now sets `hasUnsubmittedDraft` when its active projection applies a
numeric draft, including explicit absent weight. Clearing the draft clears the marker;
canonical and targetless projections do not claim unsent values. This fixes the model
projection used by the ambient summary without adding transport, acknowledgement or
process-surviving draft storage.

The rendered and spoken clock now use the Android 12/24-hour preference, the selected
locale's time pattern and the current time zone. Recomposition includes the chosen time
pattern in the content key. Display time still comes only from the supplied ambient event
timestamp; there is no independent clock ticker. The existing summary matrix pins the
system setting to 24-hour time and restores it afterward; its pixel assertions and 128
capture names remain intact, and its raster helpers are shared with the new clock test.

| Executed gate | Archived XML | Executed task summary |
| --- | --- | --- |
| Fixed DevDebug ambient + G9 + review regressions | 15 tests / 8 classes; zero failures, errors or skips | `42 actionable tasks: 42 executed` |
| Restored DevDebug focused suite | 15 tests / 8 classes; zero failures, errors or skips | `42 actionable tasks: 42 executed` |
| Repository per-commit gate | testDevDebugUnitTest: 95 tests / 38 classes; testStoreDebugUnitTest: 95 tests / 38 classes; zero failures, errors or skips | `2331 actionable tasks: 2331 executed` |
| Repository phase exit, Paparazzi, lint-rule tests and StoreRelease assembly | Fresh passing archived XML for lint-rule tests and every configured Paparazzi module | `2400 actionable tasks: 2400 executed` |

The fixed baseline, restored focused gate and repository gate each archive a fresh complete
**128-cell** ambient matrix and **24 additional clock captures**. The clock matrix covers
EN/RU × 12/24-hour preference × UTC/GMT+03:00 × timestamps 00:00, 12:00 and 23:59 UTC,
at 192dp and font scale 1.24 with both low-bit and burn-in protection enabled. Its assertions
check the spoken clock, complete native TIME-row raster, circular safe bounds and black
pixel budget. Passing captured pixels are host evidence, not a target-watch rendering claim.

All **six new controls** produced a named intended assertion RED, with byte-exact source
restoration. The exact-method validator covers **3/3 new protective methods**. The draw-only
TIME control preserves semantic text and layout, requiring the actual clock ink to be
painted. Observed mutation execution summaries: `37 actionable tasks: 37 executed`.
The excerpts below come from actual selected XML, not prepared manifest expectations.

| Executed new control | Observed intended failing method | Actual XML assertion excerpt |
| --- | --- | --- |
| `ambient-mapper-drops-projected-draft-marker` | WearSurfaceDraftProjectionTest.activeDraftProjectionMarksUnsubmittedValues() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: Every projected active draft must retain its unsent marker ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `ambient-mapper-marks-canonical-values-unsent` | WearSurfaceDraftProjectionTest.canonicalAndTargetlessProjectionsDoNotClaimUnsubmittedValues()<br>WearSurfaceDraftProjectionTest.activeDraftProjectionMarksUnsubmittedValues() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: expected: &lt;false&gt; but was: &lt;true&gt;<br>org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: Clearing the draft must clear its unsent marker ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `ambient-mapper-marks-targetless-values-unsent` | WearSurfaceDraftProjectionTest.canonicalAndTargetlessProjectionsDoNotClaimUnsubmittedValues() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: A targetless projection does not apply a numeric draft ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `ambient-clock-ignores-system-twelve-hour-preference` | WearAmbientTimePreferenceTest.systemTimePreferenceControlsSpokenAndDrawnClockAtSmallScreenBoundaries() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: AmbientClockCase(locale=en, use24Hour=false, timeZone=sun.util.calendar.ZoneInfo[id="UTC",offset=0,dstSavings=0,useDaylight=false,transitions=0,lastRule=null], timestampMillis=0) system time preference must produce '12:00 AM', actual '00:00. Single-arm dumbbell shoulder press. Set 2147483647 of 2147483647. Weight: 999.99 kg. 999 reps. Ready. Complete set, enabled. Not sent' ==&gt; expected:... |
| `ambient-clock-ignores-selected-time-zone` | WearAmbientTimePreferenceTest.systemTimePreferenceControlsSpokenAndDrawnClockAtSmallScreenBoundaries() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: AmbientClockCase(locale=en, use24Hour=false, timeZone=sun.util.calendar.ZoneInfo[id="GMT+03:00",offset=10800000,dstSavings=0,useDaylight=false,transitions=0,lastRule=null], timestampMillis=0) system time preference must produce '3:00 AM', actual '12:00 AM. Single-arm dumbbell shoulder press. Set 2147483647 of 2147483647. Weight: 999.99 kg. 999 reps. Ready. Complete set, enabled. Not sent... |
| `ambient-clock-draw-only-omits-time` | WearAmbientTimePreferenceTest.systemTimePreferenceControlsSpokenAndDrawnClockAtSmallScreenBoundaries() | org.opentest4j.AssertionFailedError: org.opentest4j.AssertionFailedError: AmbientClockCase(locale=en, use24Hour=false, timeZone=sun.util.calendar.ZoneInfo[id="UTC",offset=0,dstSavings=0,useDaylight=false,transitions=0,lastRule=null], timestampMillis=0) TIME actual row must draw full '12:00 AM': expected ink=296, actual ink=0 ==&gt; expected: &lt;0&gt; but was: &lt;296&gt; |

The original 24 negative controls were not re-executed in this follow-up. Their protected
host tests run in the fresh focused and full suites above; §5 retains their original source
and execution provenance. No additional release-host or release-device UI result is claimed
here. `assembleStoreRelease` remains a build gate, while these painting and mapper tests
are measured through the two debug variants described above.

Evidence run: `20260922T211518.348821Z`; base `4e7581dd074ccea548e7f841516041ee5beb99e8` plus the recorded complete source snapshot.
The snapshot JSON SHA-256 is `e91d7e0d17808efdbffe69026811351be153bbf3a3104692c1045f948e26b966`. Command receipts, archived XML,
128- and 24-PNG inventories, before/mutant bytes and the exact-method validator report
are retained under `/private/tmp/wear-ambient-review-evidence/runs/20260922T211518.348821Z`. The six-control manifest and standalone evidence tools are
local execution artifacts; this ledger does not assert that runnable manifests are shipped
in the repository. All Gradle proof gates execute serially with `--rerun-tasks --no-build-cache
--no-configuration-cache`; preparatory `clean` is separate. The candidate's complete source
bytes were checked unchanged after each stage and immediately before this documentation
append. B/C/D evidence and the original ambient ledger are preserved byte-for-byte.

**Physical acceptance remains pending.** These results do not establish actual watch clock
settings, ambient callbacks, Activity recreation, TalkBack speech, automatic return to the
watch face, foreground retention, reconnect constants or notification removal after process
death. The transitional `WatchProcessState`/no-op/static-preview boundary in §4 and the
closed privacy/transport gate are unchanged. Runtime and ongoing activity remain the next
separate increment.

#### Proof-only reprocessing of phase reports

The original pipeline JSON still records `FAILED` at `root-phase` with
`RuntimeError: Duplicate XML identities`. Its actual phase command exited zero and logged
`2400 actionable tasks: 2400 executed`; the failure occurred when the evidence parser
assumed each `(classname, display label)` denoted a unique method. Parameterized reports
legitimately reuse labels such as `[1] LIGHT` within one XML file, including host tests
in Paparazzi-enabled modules that are not named Golden tests.

Before another build could overwrite them, the original fresh phase reports were copied
byte-for-byte with their modification times and SHA-256 hashes. The separate amended
parser preserves every raw testcase occurrence using the relative XML path and its
one-based ordinal within that file; it also records the supplied classname/display label.
It invents no method names and never deduplicates cases. The exception applies only to
phase reports from the configured Paparazzi modules. Wear, new-regression, mutation and
lint-rule identity validation remains strict.

Reprocessed phase scope: **164 XML files / 1706 testcase occurrences in
13 Paparazzi-enabled modules**, plus **15 lint-rule XML files /
138 tests**. All actual testcase counts match suite counters, with
zero failures, errors or skips and original phase-interval timestamps. The parser retains
61 repeated display-label groups containing 440 occurrences.
The complete source snapshot, all prior executed stage receipts, PNG hashes, six intended
assertion controls and their byte restoration were revalidated unchanged. The exact-method
proof is **COMPLETE for 6/6 controls and 3/3 methods**.

Derived proof: `/private/tmp/wear-ambient-review-derived-evidence/20260922T214313.115398Z` (`DERIVED_PROOF_COMPLETE`). Original raw capture:
`/private/tmp/wear-ambient-review-phase-raw-20260922T211518.348821Z`. Original pipeline SHA-256:
`51e327f804b73aa40ac4c4b363baffaaaa979b75d4506c18f93d2428e73d52fd`; amended proof-only parser SHA-256:
`e9489f1e4da647547e8d375b2ea3e998b8de9b679bacfe4bcab236e30789ea26`. Original campaign and raw-capture artifact inventories
are frozen in the derived record. **No Gradle gate or mutation was re-executed**, and the
original failed controller record was not relabelled COMPLETE. This is a repair of the
proof parser for the already executed gates, not a new build or device measurement.
