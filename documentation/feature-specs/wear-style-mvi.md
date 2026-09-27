# Wear shared style and presentation MVI

## Contract

The controller shares design tokens and bundled fonts with the phone while adapting geometry
to round 192dp and 240dp screens. IBM Plex Sans owns words, Archivo owns numeric fragments with
tabular figures, and IBM Plex Mono owns auxiliary roles. Archivo must never style Russian text.
Wear keeps its deliberate black background. Phone typography, colours and goldens stay unchanged.

The existing project Store/Handler/StoreProcessor owns screen/editor state and ambient/notice
presentation. An interactor exposes coherent runtime snapshots; the UI mapper formats each
snapshot once for its locale. Activity owns platform callbacks and system effects; Compose owns
scroll, focus, swipe and rotary pixel accumulation. There is no second MVI implementation.

The process runtime retains cache, draft, reducer, authority, ongoing activity and Tile ownership.
Expiry is checked synchronously before exposing interactive content after ambient. Relative draft
steps apply serially to the latest draft, preserving the existing bounds and nullable-weight policy.
Protocol, database and mutation-authority contracts are unchanged.

Firebase uses the existing phone SDKs and matching distribution configuration. Crashlytics gets
the custom key `platform=phone` or `platform=watch` at application startup, before graph/runtime
work. Wear telemetry must not serialize workout payloads through action/event descriptions.
The real-phone-payload privacy gate remains closed; validation uses synthetic/debug sources only.

The explicit notification-settings action first opens the public per-app notification page.
If the platform has no handler for that action, it falls back to public system Settings.
If neither route is available, the existing UI-event error boundary reports the failure without
changing notification permission, publishing ongoing activity or terminating the controller.

## Delivery and evidence

Four stacked changes cover shared resources and dependencies, presentation/rotary, final-font
geometry, then system Tile and emulator acceptance. Tile freshness acceptance requires the real
system Tile to update following persisted state changes; a preview is insufficient. ProtoLayout
uses supported system fonts with the shared colour roles. The dated ledger and
[final scoped report](../wear-emulator-acceptance/reports/2026-09-27-style-mvi/README.md) distinguish
completed native observations from asynchronous platform scheduling and remaining hardware limits.

Required matrix: API30/36 × round192/240dp × EN/RU × font1.0/1.24. Values, primary action and
blocking reason must be visible before scrolling; details remain reachable after scrolling.
Back/swipe/focus, ambient, authority and lifecycle contracts remain mandatory.

New guards require named negative controls through the mutation harness. Compiler and
infrastructure failures are INVALID, not RED. Gates run serially with `--rerun-tasks`,
`--no-build-cache`, `--no-configuration-cache`, and Detekt separately. Fresh XML and the exact
`N actionable tasks: N executed` summary are required. Previous acceptance is historical evidence,
not verification of this change. Performance observations must name their build type.

## Geometry

Information screens use a centred scroll viewport inscribed in the round display. For a display
diameter `d`, a square of side `d / sqrt(2)` has its four corners on the circle; subtracting 4dp
keeps an inset for the visible content. The viewport guarantees a safe width at every visible
height, while long instructions remain scrollable. Progress and completion instructions are
centre-aligned. Editor signs use symmetric vector paths centred by an IconButton, with a 48dp
touch target and no position offsets.

Wear's typography is an adapter over the shared families. Card values use the compact numeric
role; editor values use larger numeric spans with separate auxiliary spans for units. Missing
weight is a text role. Actual render/layout acceptance remains mandatory after these changes.

### Card sizing provenance

The unequal card allocation reserves more space for a six-character weight than for
three-digit reps. Units and the full unset-weight wording remain available in accessibility
and the editor. The round-screen matrix must verify the actual bundled-font result.

For historical context, the controller at `1af93d0b`, before shared Wear fonts, recorded
65dp/36dp value widths at font scale 1.24, a 92dp:60dp card split with 76dp/44dp content,
and 60dp content per card under an equal split. Its inline derivation also recorded
`108 + 52 + 8 > 160` for a weight including its unit and 88dp for Russian unset copy.
These are preserved source-recorded measurements of the earlier typography; they are
not measurements or acceptance evidence for the new font roles.

## Implementation ledger

2026-09-26: implementation started in an isolated worktree based on `1af93d0b`. Shared tokens and
font assets are being extracted. New implementation gates have not yet completed. Firebase SDK
wiring awaits resolution of automatic approval review; only the phone platform key is applied.
Physical watches, reconnect, energy use and hardware ambient behaviour remain separate acceptance.

2026-09-26 checkpoint: the shared-font checks executed successfully (11 tests); the pre-fix rotary
regression executed and failed with 9 instead of 10. Relative-step and Wear theme/geometry changes
are written, with GREEN and negative controls pending. Further heavy gates stopped below 1 GiB
free space. See the [implementation evidence](../wear-emulator-acceptance/reports/2026-09-26-style-mvi/README.md).

2026-09-26 continuation: the owner explicitly confirmed the full phone Firebase set and authorized
removal of unused build files. Wear now declares Crashlytics, Analytics and Performance with the
matching phone configuration; both applications set their platform key before application work.
Native-symbol upload stays disabled because this application does not own native symbols or use
the Crashlytics NDK SDK; release JVM mapping upload is enabled. The first combined Wear guard run
passed nine tests with `106 actionable tasks: 106 executed`. The preceding two attempts failed
during Gradle configuration and provide no RED test evidence. Named mutations, MVI and final
acceptance remain pending. Cleanup preserved all source checkouts and verified 4,188 existing
report/XML/APK files byte-for-byte, leaving approximately 9.19 GiB free.

2026-09-26 presentation checkpoint: the Wear graph now uses the existing project Store, handlers
and retained StoreProcessor. The process runtime publishes coherent unformatted snapshots; a
cached presentation mapper owns formatting, notice and ambient presentation. Expiry remains
synchronous at the platform boundary. Twelve new handler/mapper/retention tests and seventeen
existing UI tests executed successfully, each run reporting `106 actionable tasks: 106 executed`.
The subsequent ambient-font matrix passed fourteen tests with the same executed-task count.
These targeted results are not the final acceptance of the full diff; named controls are pending.

Native ambient text uses the same bundled files as Compose. Words use Sans, numeric fragments
Archivo, and their units Mono. Baseline ascent/descent reserve each row; circular chords constrain
the full time and progress strings. If a numeric row exceeds its chord, font fitting measures the
actual glyph advances at each candidate size because low-bit integer advances are not linear.
Only the context name may ellipsize; its complete text remains in the spoken description.

The system Tile now has a process-lifetime snapshot observer outside the screen Store. It
requests platform updates for display/recovery/locale changes, suppresses draft-only and
unchanged wake events. A recoverable requester exception is reported without stopping later
updates. Cancellation stops the observer; fatal errors reach the process exception handler.
Its centred column uses the same inscribed-square geometry; status rows allow two lines and
use shared neutral colour roles. Native system-Tile refresh evidence is still pending.

2026-09-26 guard checkpoint: the complete local Wear host baseline passed 227 tests with
`106 actionable tasks: 106 executed`; separate root Detekt passed with
`63 actionable tasks: 63 executed`. The appearance gate passed its live render checks and
the pinned historical negative failed as expected. The named mutation campaign is in progress;
one non-compiling Store-retention mutation is INVALID and requires a corrected retry.
Device acceptance now includes two rotary steps per event in both directions/fields,
painted sign centring and complete round-safe completion details after scrolling.
No new emulator matrix verdict has been recorded.

2026-09-26 instrument correction: the appearance result in the preceding checkpoint used
Chrome Headless Shell. The canonical workflow explicitly rejects that packaging because its
layout differs from full Chrome. That run remains advisory and is withdrawn as gate evidence;
the local full-Chrome timeout is INVALID. The normal workflow must supply the accepted render
result and its pinned negative. This does not affect Gradle, Paparazzi or emulator results.

2026-09-26 graph boundary: an application-scoped Metro graph provides the existing core MVI
logger/dispatchers/analytics dependencies. Its explicit Wear graph-extension factory owns the
screen-scoped Store and handlers. The child receives WatchRuntime as a bound instance; runtime
cache/authority/ongoing/Tile ownership stays in the process owner. No Activity or Context is
retained by the Store/handlers. The explicit factory avoids publishing an internal Wear scope
as a public AppScope contribution.

Two new real-reducer tests first failed for stale unsolicited refreshes whose displayed target
was unchanged. Tile invalidation now includes refreshRequired, and the renderer displays that
reason for an otherwise active target. Both tests then passed. Candidate API36 system-Tile
evidence shows active -> disconnected -> active after acknowledged headless synthetic events;
final-cohort and API30 observations remain required.

Editor IconButtons explicitly select the shared medium corner radius; their Wear default
uses an independent full-circle token even when the theme supplies shared radii. A native
pixel assertion reproduced that default and now guards the adapted 48dp rounded control.
The screen-edge completion/retry action retains Wear's edge-following geometry.

2026-09-26 device checkpoint: five new brand/rotary device controls and six adapted
controller/ambient controls produced named assertion REDs with exact restoration.
Natural expiry has fresh positive baselines before and after the controls, for both
reps and weight. Two earlier no-Compose-hierarchy failures remain INVALID; the captured
system timer and denied notification permission explain why they could not test expiry
restoration. The final matrix and independent intermediate-PR gates remain pending.
Wear diagnostic logcat follows the phone's Dev/Debug versus Store Release policy;
Firebase breadcrumbs remain enabled independently through the existing shared logger.

2026-09-26 stacked delivery: #292 (`cef8c949`) contains shared resources/Firebase and #293
(`142c2f4f`) contains presentation MVI/relative rotary. #294 (`78f83a5f`) contains the adapted
Wear theme and circular geometry. Its root run executed 2,887 tests with no failures/errors/skips
and `2360 actionable tasks: 2360 executed`; 12 host and 3 device controls reached their intended
assertion failures and restored source. Seven positive DevDebug device checks passed on
API36/round192/RU/font1.24. These targeted observations do not replace the complete matrix.

Two additional real Tile tests reproduced swallowed cancellation and fatal platform failure,
with 2 assertion failures out of 4 tests and `106 actionable tasks: 106 executed`. The observer
now preserves coroutine cancellation and fatal-error propagation while retaining recovery from
ordinary requester exceptions. All 7 Tile tests then passed with the same executed-task count.
Named controls and final-stage root/emulator validation follow this checkpoint.

2026-09-26 foundation: shared design tokens and font assets, the minimal existing MVI dependency
boundary, and the complete phone Firebase SDK set are implemented. Phone and watch set their
Crashlytics platform key before application graph work. The remaining stacked changes implement
presentation MVI/relative input, adapted Wear typography/geometry, and Tile refresh/acceptance.
The real-phone-payload privacy gate remains closed. Physical watches, reconnect, energy use and
hardware ambient behavior remain separate acceptance.

2026-09-26 presentation: the existing retained project Store and typed handlers now own
screen/editor decisions. Runtime snapshots keep process cache, authority and ongoing ownership
outside the Store. The mapper prepares locale-aware values once; relative rotary batches apply
to the latest draft. The following PR adapts Wear font roles and round geometry.

2026-09-26 style: Wear consumes the shared font families and shape/spacing roles. Vector signs
are centred inside explicit 48dp rounded buttons. Information content scrolls inside a round-safe
viewport; ambient uses the same bundled typefaces with measured glyph fitting. New device guards
cover whole Russian words, sign centring, complete instructions and multi-step rotary events.
Tile refresh and the frozen final acceptance cohort follow in the last stacked PR.

2026-09-26 localized-number review: [#294 comment 4111846578](https://github.com/stslex/Workeeper/pull/294#discussion_r4111846578)
is correct-and-new. A fresh Kotlin test reproduced Arabic `١٢٨` taking one text run instead
of separate numeric/unit roles. Ambient fragments now recognize Unicode decimal digits and
Arabic decimal/grouping separators; the complete number keeps its numeric role and units
keep their auxiliary role. The guard includes formatter-produced Arabic values, Persian
digits and Arabic grouping. English/Russian roles and platform fallback for missing glyphs
remain unchanged. Named controls remove Unicode digits and localized separators independently.

2026-09-26 bidirectional-text review: [#294 comment 4111957516](https://github.com/stslex/Workeeper/pull/294#discussion_r4111957516)
is correct-and-new. A native Compose raster regression of an Arabic 12-hour clock reproduced
385 differing pixels against an independently styled platform paragraph, with one assertion
failure and `106 actionable tasks: 106 executed`. Ambient now keeps directional text in one
platform paragraph with the numeric and auxiliary typeface spans. Its advance and ascent/descent
participate in centring and circular fitting; paragraph construction remains in the remembered
layout, outside drawing. The unchanged LTR path retains its existing raster contract. Named
controls bypass paragraph layout and force the wrong base direction independently.
The first forced-LTR control was non-discriminating for the Arabic clock alone and remained
INVALID, with the passing mutated XML preserved. The raster guard also includes a mixed RTL
exercise name with a Latin unit so paragraph base direction has an observable effect.

2026-09-27 final-cohort closure: source `5920e1ee` completed collection with 69 PASS,
two BLOCKED and one FAIL. Three separately recorded replacements qualify strict process-absence
and conservative ten-minute duration observations without overwriting the original receipts.
Native API30/36 Tile observations now show progress, disconnect and natural freshness expiry;
the system scheduler does not promise immediate refresh delivery. API30 notification restoration
remains FAIL: the actual app button threw `ActivityNotFoundException` for the unsupported
per-app Settings action. The frozen original results are retained in the final report.

2026-09-27 notification Settings correction: a real Kotlin/Robolectric regression reproduced
that missing-handler failure on API30/33 (two assertion failures; 106 executed tasks).
The platform adapter now falls back to public system Settings and uses the existing UI-event
error boundary if both routes fail. Eight targeted tests pass with 106 executed tasks.
The final corrected APK and its UI matrix require separately bound fresh evidence; the
`5920e1ee` timed observations must not be relabelled as results from the corrected APK.

2026-09-27 corrected-source acceptance: application `6164203f` passed the fresh serial root
build/lint/unit gate (2,917 tests; 2,360 executed tasks), repository-wide test APK build (2,169 executed tasks),
signed StoreRelease/runtime guard (234 executed tasks) and separate Detekt (63 executed tasks). Three named
Settings controls reached assertion RED and restored exact bytes; 11 restored tests passed.
The corrected UI matrix selects 16 cells / 352 invocations with native/Compose/bounds and sampled
motion review. One additional 22-invocation attempt retains BLOCKED native visual status because
system Hello obscured six captures; a separate complete repeat supplies that cell.

Actual notification denial/Settings/Back/restoration, StoreDebug's declared nine-invocation subset
and release boundaries passed on both APIs. Three full corrected inventories remain BLOCKED
because they do not contain every declared subject. Source `5920e1ee` timed and native
Tile observations retain their original 69 PASS / 2 BLOCKED / 1 FAIL and qualified repetitions; 256 selected
runtime/UI/test/resource files are byte-identical across the two application versions. They are
not represented as source `6164203f` reruns. Full phone Firebase SDKs and platform keys remain enabled;
only synthetic/debug data was used. See the final scoped report for raw hashes, limits and CI.
