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

**Visibility and compact layout.** Compute visible bounds using each node's actual
ancestor clips. The anchored `EdgeButton` is a sibling of the scroll column, so the
column viewport must not clip the button in the oracle. Test known-visible and known-
clipped controls before relying on it. Initial primary content and scroll-reachable
secondary details have separate assertions. Extend the existing read-only fixture
rather than creating a competing one.

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
