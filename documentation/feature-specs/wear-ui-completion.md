# Wear UI completion after PR-A

Status: implementation authorized on 2026-09-22; PR-A is merged as `20215640`.
This is the delivery and acceptance contract for the remaining Wear UI and lifecycle
work. It does not mark the later increments implemented or the complete Phase 1 accepted.
The [Phase 1 behavioural specification](wear-phase-1-active-workout-tile.md) continues
to govern protocol, authority, cache, and privacy.

## 1. Required user outcome

For **192/240dp × EN/RU × font scales 1.0/1.24**, the initial controller view must show
weight, reps, the primary action, and the specific reason that action is unavailable
without scrolling. Apply the same criterion to numeric extremes, absent weight, stale
or disconnected state, an in-flight command, and each invalid numeric field.

Full exercise name, set scale, and additional explanation remain available below the
primary information in scrollable details. Do not lower the user's font scale to fit.
The completion button has the same dimensions in enabled and disabled states and a
visible touch target of at least 48dp on both axes.

Ambient shows a short, noninteractive summary on predominantly black pixels. Waking
restores the controller or numeric editor and its draft, provided existing authority
rules still permit editing. Unsubmitted values are explicitly described as such.
Ongoing activity provides a way back to the controller; it never blocks user exit.

## 2. Delivery sequence

| Increment | Required change | Exit condition |
| --- | --- | --- |
| PR-A documentation closure | Reconcile copy, 159/211px lane, exact-cell G6 exception and historical evidence; run disabled press-hold probe with enabled control | Claims match implementation and evidence; unmeasured hardware work stays explicit |
| PR-B: rotary and focus | Scroll each controller family with its existing state; transfer rotary focus to editor and back | Both editors and Back/swipe/authority-loss exits work without an extra tap |
| PR-C: measurement contract | Measure actual ancestor clips, separate initial visibility from scrolled details, expand read-only matrix, protect ACTIVE spoken-only invariants | Visible and clipped helper controls distinguish correctly; inventory says G1–G7/G9–G11 |
| PR-D: compact controller | Move context or disabling reason above complete values; details below; compact equal-size action | Mandatory initial-visibility matrix passes with the layout in the same PR |
| Ambient | Add injectable lifecycle provider and summary; retain editor/draft subject to authority | Both editor resume paths, low-bit/burn-in, and authority expiry tested |
| Ongoing | Connect atomic cache to one coordinator and an injectable notification adapter | State-machine tests plus target-watch deadline/removal measurements accepted |

PR-B follows PR-A. PR-C precedes PR-D; geometry changes and their mandatory visibility
gates land together. Ambient and ongoing use separate sequential PRs. Each PR remains
open for CI and review; Ilya merges.

## 3. Implementation contracts

**Visibility and compact layout.** Compute visible layout bounds using each node's
actual rectangular ancestor clips and the simulated screen rectangle. The anchored
`EdgeButton` is a sibling of the scroll column, so the column viewport must not clip
the button in the oracle. Shape outlines, the circular display mask, sibling occlusion,
alpha and painted pixels need separate checks. Test known-visible and known-clipped
controls before relying on the instrument. PR-C separates the initially visible action
from cards reachable after scrolling; it does not grant an exception to §1's strict
initial visibility criterion. PR-D must enforce that criterion with its new layout,
while additional details remain scroll-reachable. Extend the existing read-only
fixtures rather than creating competing ones.

Introduce a typed UI reason with precedence: connection/freshness, then an executing
command, then numeric-field errors. A generic boolean is insufficient to explain the
blocked action. The top area shows concise context when enabled and that specific
reason when disabled. Evaluate Small `EdgeButton` first; retain it only if its actual
visible target and label satisfy the contract. Revise layout instead of weakening the
gate. Update redesign §4 and G3/G4 with the resulting structure. Remove the PR-A G6
exception if its residual disappears. Format weight and reps consistently for the
selected locale outside composition.

**Ambient.** Add an injectable platform provider compatible with the current minSdk.
Use system ambient transitions/updates, respect low-bit and burn-in capabilities, and
avoid actions, animations, a per-second ticker, or an app wake lock in ambient. Preserve
navigation/editor state and the draft across ambient transitions; expiry invalidates
editing under the existing rules. See the [Android always-on guidance](https://developer.android.com/training/wearables/always-on).

**Ongoing.** Use the existing `wear-ongoing` dependency and the atomic-cache/reducer
contract in [Phase 1 §8](wear-phase-1-active-workout-tile.md#8-lifecycle-and-ongoing-surface).
Start only from the authorized fresh active lifecycle. Persist an absolute monotonic
stop deadline and compute `timeoutAfter` from its remaining interval on every post or
update. Disconnect may shorten it; repeated events, cache reads, and process restart
cannot extend it. Terminal states cancel immediately with the specified crash ordering.
Return to the same Activity. Notification permission denial leaves ordinary UI usable
and does not promise ongoing retention. Treat Wear OS 5+ retention and behaviour on
older supported versions as separate device observations. See [Ongoing Activity](https://developer.android.com/training/wearables/notifications/ongoing-activity).

## 4. Evidence and release boundary

Run Gradle checks serially with `--rerun-tasks --no-build-cache`; retain XML identities,
counts, and exact `N actionable tasks: N executed` summaries. Protective tests need a
named negative control through the mutation harness and byte-exact restoration.
A host pass does not establish physical input or notification-removal behaviour.

The matrix includes accessibility before scrolling, reachable details, numeric bounds,
absent weight, every disabling reason, rotary handoff, Back/swipe, ambient from both
editors, authority expiry, permission denial, and process death. Disabled-button pixel
comparison must have an enabled positive control and exclude unrelated clock changes.

The privacy gate remains **closed**. Lifecycle development uses synthetic sources
restricted to tests/debug; it is not evidence of real workout transfer. Do not change
phone protocol, database schema, mutation authority, or public privacy-policy files.
N10 (equal-position phone ordering) remains a separate phone-side investigation.

The reconnect interval and `ONGOING_TIMEOUT_TOLERANCE_MS` are **pending physical-watch
measurement**, following Phase 1 §8. Do not publish guessed constants as measured or
mark ongoing accepted without that probe. The target-watch process-death test must
show system removal by the declared deadline/tolerance without app restart. Paired
transport, phone acknowledgement, and final Phase 1 acceptance follow the owner's
privacy disclosure and transport decisions and real phone/watch validation.

## 5. PR-B implementation and evidence

All three controller families bind `rotaryScrollable` to the same `ScrollState` as
their content. `requestFocusOnHierarchyActive` owns focus on both controller and
numeric editor; the editor retains the existing accumulator, steps, and bounds.
The host test sends native rotary input through the Android root, rather than calling
a node's handler. It checks both scroll directions on overflowing active/instruction
fixtures, editor actions, and focus recovery after Back, swipe, and authority loss.
The retry fixture checks focus and absence of draft changes; it has no forced overflow.

The Back/swipe paths execute successfully with the existing `BackHandler`. N3 is not
classified as a reproduced defect solely because `interceptBack` is absent. Physical
rotary and system navigation on a watch remain a separate acceptance observation.

Fresh focused baseline: `:app:wear:testDevDebugUnitTest` selected
`WearRotaryControllerTest` and `WearDisabledPressGateTest`: two named tests, zero
failures/skips; **37 actionable tasks: 37 executed**. The press test first lets the
scaffold indicator settle, then compares its button region during a 500ms hold.
The [PR-A press ledger](wear-bottom-band-rebudget.md#72-pressed-state-behaviour) states
the limits of host raster evidence.

Named negative controls executed through `mutation_harness.py`:

| Mutation | Observed failing assertion | Executed tasks |
| --- | --- | --- |
| `active-scroll-without-rotary` | Clockwise input no longer advances the active scroll | 37/37 |
| `instruction-scroll-without-rotary` | Clockwise input no longer advances the instruction scroll | 33/33 |
| `retry-without-rotary-focus-binding` | Retry controller is not focused | 33/33 |
| `authority-loss-does-not-reclaim-focus` | Read-only controller is not focused after the editor closes | 33/33 |
| `disabled-button-accepts-touch` | Disabled physical click emits completion | 37/37 |
| `disabled-button-reacts-visibly-while-held` | Disabled hold changes button-region pixels | 33/33 |

Every mutation produced one named assertion failure, rather than a compile failure;
the harness restored the source byte-for-byte after each run. The pixel mutation
enables press interactions and applies a pressed alpha of 0.25, proving that the
capture comparison detects a visible response without depending on native ripple
rasterization. These controls do not add a production animation or change its timing.

The restored Wear module passed assemble (both debug flavors, both instrumented test
APKs, and store release), Detekt, lint, and both unit-test variants:
**299 actionable tasks: 299 executed**. Each flavor's XML contains 57 tests in 23
classes, with zero failures, errors, or skips. This includes both new protective tests.

After `clean`, the repository-wide `assembleDebug detekt lintDebug testDebugUnitTest`
run passed with **2331 actionable tasks: 2331 executed**. The separate
`assembleDebugAndroidTest verifyPaparazziDebug :lint-rules:test` run passed with
**2346 actionable tasks: 2346 executed**. Both used `--rerun-tasks --no-build-cache`
and `--no-configuration-cache`. Transport-gate self-tests (32 cases) and source scan
passed. The local shell mockup render remained unmeasured because the installed
headless Chrome did not return; its PR workflow must supply the browser result.

## 6. PR-C measurement contract and evidence

`WearVisibleBounds` preserves the declared layout size for diagnostics, obtains
Compose's actual ancestor-clipped `boundsInRoot`, and intersects it with the explicit
simulated screen rectangle. Dimensions are converted from pixels using the measured
node's density. A fully off-screen result is normalized to `Rect.Zero`. The helper
requires an attached, placed node. The scroll column's viewport is never applied to
its sibling anchored button.

The oracle's seven fixed geometric controls are:

| Fixture | Expected visible width × height | Meets the 48dp minimum |
| --- | --- | --- |
| Visible target | 48 × 48dp | Yes |
| Clipping parent | 40 × 40dp | No |
| Nested clipping parents | 28 × 28dp | No |
| Smaller non-clipping parent | 64 × 64dp | Yes |
| Clipped sibling viewport | 64 × 64dp | Yes |
| Partial screen intersection | 32 × 32dp | No |
| Fully outside screen | 0 × 0dp | No |

These controls establish rectangular layout geometry only. They do not measure a
shape's outline, round-screen raster clipping, sibling occlusion, alpha, text glyphs,
or expanded minimum-touch hit areas. No host pass under this contract proves the
physical screen's complete visual or touch behaviour.

G1 now runs one shared contract under EN and RU resource contexts at 192/240dp and
font scales 1.0/1.24. It reuses the existing weighted ACTIVE, weightless ACTIVE,
`REFRESH_REQUIRED`, `DISCONNECTED`, and retry fixtures, plus both numeric editors.
The controller's anchored action must meet the visible 48dp minimum before any
scroll. Each card must meet it after scrolling that card into view; the editor's
increase and decrease controls must meet it without scrolling. Every click node also
retains the declared-size minimum and pairwise ancestor-clipped non-overlap check.
A loading transition removes the preceding scaffold before each controller case,
so its initial check cannot inherit a previous scroll position.

This intentionally permits cards below the initial fold in PR-C. It proves they are
reachable, not that both values, a disabled reason, and the action fit initially.
Those strict assertions belong to PR-D and must land together with its compact
layout. Additional details must remain reachable after the primary block.

G3 keeps distinct nonempty statuses for all eleven kinds on both screen sizes.
ACTIVE's tagged row has no text and speaks its exact localized status; degraded
rows retain their text. Target-bearing fixtures speak exact localized set progress
on `set_scale` and have no text on that row or its descendants. Exact localized
ACTIVE and set-progress labels are additionally forbidden across the entire unmerged
semantics tree, catching a separately rendered sibling label as well. This protects
the present §4 spoken-only contract before PR-D changes it deliberately.

Fresh focused verification runs `WearVisibleBoundsTest`, `WearTouchTargetGateTest`,
`WearTouchTargetGateRuTest`, and `WearKindDistinctionGateTest`: four named tests,
zero failures, errors or skips. With Wear Detekt, the restored run reports
**42 actionable tasks: 42 executed**.

Named negative controls ran through `mutation_harness.py`, each with
**37 actionable tasks: 37 executed** and byte-exact source restoration:

| Mutation | Named failing contract |
| --- | --- |
| `ignore-ancestor-clips` | Clipped fixture dimensions in `WearVisibleBoundsTest` |
| `ignore-screen-bounds` | Partial-screen fixture dimensions in `WearVisibleBoundsTest` |
| `draw-active-status` | ACTIVE spoken-only status in `WearKindDistinctionGateTest` |
| `remove-spoken-set-progress` | Exact spoken progress in `WearKindDistinctionGateTest` |
| `shrink-readonly-cards` | Read-only 48dp minimum in both EN and RU G1 classes |
| `sibling-active-status` | Globally absent ACTIVE label in `WearKindDistinctionGateTest` |
| `sibling-set-progress` | Globally absent progress label in `WearKindDistinctionGateTest` |

The two sibling controls were rerun after the strengthened G3 baseline passed.
The read-only-card control was rerun after extracting the matrix's card helper to
meet Detekt's nesting limit. Each RED contains an assertion failure in the named
XML report, not a compile failure. The restored tree then passed the full gates.

After `clean`, repository `assembleDebug detekt lintDebug testDebugUnitTest` reports
**2331 actionable tasks: 2331 executed**. Each Wear flavor's XML contains 59 tests
in 25 classes, with zero failures, errors or skips. The separate
`assembleDebugAndroidTest verifyPaparazziDebug :lint-rules:test` run reports
**2346 actionable tasks: 2346 executed**. Both runs used `--rerun-tasks`,
`--no-build-cache`, and `--no-configuration-cache`.

The gate inventory remains G1–G7 and G9–G11; oracle fixtures and locale variants do
not introduce a G8. PR-B evidence above applies to its own recorded tree. This
increment changes measurement and semantics protection; it does not close PR-D's
initial-visibility criterion or substitute for physical-watch acceptance.
