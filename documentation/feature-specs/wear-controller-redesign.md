# Wear controller visual redesign — phase 1a

Status: PR-D compact controller implemented with **executed host geometry, accessibility
and negative-control evidence**. The initial redesign landed in PR #284 and PR-A in
PR #285 (`dev` merge `20215640`); their observations remain historical evidence for
those layouts. Physical-device and Phase 1 acceptance remain open. Delivery and fresh
evidence are tracked in [Wear UI completion](wear-ui-completion.md#7-pr-d-compact-controller-contract-and-evidence).
Supersedes the layout described in
`wear-phase-1-active-workout-tile.md` §3.2 where the two disagree; the behavioural
contract in that document is unchanged and still governs.

## 1. Scope

In scope: the visual and interaction design of the Wear controller activity
(`WearControllerScreen`), the Wear colour source (`WearAppTheme`), and the test
infrastructure required to gate them.

Out of scope, with the reason each is excluded:

| Excluded | Reason |
| --- | --- |
| Exercise switching / cursor | `WearSurfaceModel` carries `exerciseName: String?`, a single name. A list is a protocol change, and payload expansion is behind the same privacy review as the transport. |
| Tile layout | Blocked on the `requestUpdate` throttling probe. |
| Transport, `onAction` wiring | `WearDataLayerApiRule` and the CI transport gate block it pending privacy review. |
| Durable drafts, live session | Separate step; phone-side, no Wear code. |
| Removal of the mutation lease | Belongs to the variant-A protocol rewrite. |

`MainActivity` accepts `SyntheticSurfaceFixtures` only in debug and otherwise displays
the existing process-state surface; the action bridge and real transport remain gated.
PR-D changes presentation, local formatting and typed unavailability reasons, not
phone protocol, database schema or mutation authority.

## 2. Recorded decisions

**D-A. Fixed palette, no dynamic theming.** `WearAppTheme` uses a fixed palette
derived from `AppColors`, replacing watch-face-sourced dynamic colour. Cost: the
app does not adapt to the watch face. Accepted because the Workeeper palette is
monochrome, which is the least likely thing to clash with an arbitrary face, and
because the Tile (raw ProtoLayout) sets no colours at all — dynamic theming
already produced two surfaces of one app coloured from different sources.

**D-B. Screen background is pure `#000000`,** not `surfaceTier0 #0B0D0F`. Wear
app-quality guidance asks for black backgrounds; the screen is lit for the length
of a workout on an OLED panel. The delta from `surfaceTier0` is not visually
meaningful.

**D-C. No accent colour.** In `AppColors` the accent *is* `textPrimary`
(`#F1F5F9`) — accent is brightness, not hue. `molten #F0A22E` is reserved for
personal-record moments, and phase 1 has none on the watch. The watch is therefore
monochrome. This is a consequence of the existing design system, not a new choice.

**D-D. Inline `− value +` steppers are replaced** by two value cards that open a
full-screen numeric editor. Three 48dp targets per value do not fit twice across a
240dp round screen, and an inline stepper contends with scrolling for the rotary
input. A full-screen editor has nothing to scroll, so rotary binds to the value
with no mode.

**D-E. `Complete set` is anchored to the bottom edge.** A static sibling button
keeps the primary action available while the controller content scrolls.

**D-F. The training name is dropped from the controller.** It costs a line of a
240dp screen for information the user already has and which the Tile also carries.
This changes the §3.2 reading order.

**D-G. Disabled and secondary labels use `#8B95A1`, never `#627587`.**
`#627587` on black is 4.414:1, below the 4.5:1 text threshold. It remains legal
as a stroke, where the 3:1 non-text threshold applies. (An earlier revision said
4.35:1; the WCAG 2.x formula gives 4.414:1 — the conclusion is unchanged.)

## 3. Colour contract

A single Wear palette object. Every value is copied from `AppColors` dark unless
noted; no new hues are introduced.

| Role | Value | Source |
| --- | --- | --- |
| `screen` | `#000000` | D-B |
| `card` | `#1E242A` | `surfaceTier2` |
| `cardInactive` | `#0B0D0F` | `surfaceTier0` |
| `pillPending` | `#242B32` | `surfaceTier4` |
| `textPrimary` | `#F1F5F9` | `textPrimary` |
| `textSecondary` | `#B7C0CA` | `textSecondary` |
| `textMuted` | `#8B95A1` | `textTertiary` |
| `stroke` | `#627587` | `borderDefault` — stroke only, never text |
| `onAccent` | `#0B0D0F` | `onAccent` |
| `error` | `#DF714B` | `status.error` |

The accent surface is `textPrimary` on `onAccent` (D-C).

## 4. Layout — primary information and scrollable details

This is the implemented PR-D reading order. Its nominal dimensions below are the design
budget; the mandatory matrix separately measures actual visibility before scrolling.

1. Time of day remains pinned at the top arc.
2. The primary context shows the exercise name, abbreviated on at most two lines,
   while completion is enabled. If completion is blocked, this slot instead shows
   the specific typed reason, fully visible without clipping or ellipsis.
3. Weight and reps cards follow immediately, with both values and field icons fully
   visible before scrolling. Weighted exercises retain the unequal card widths;
   weightless exercises show only the reps card. Cards contain bare numeric values,
   with units and the explicit absent-weight wording in their accessible descriptions.
   An absent weight is drawn as `—`.
4. A Small `EdgeButton` is anchored independently of the scroll column. Both enabled
   and disabled states draw the same check glyph and have exactly equal dimensions,
   with a visible target at least 48dp on each axis. Enabled is filled; disabled is
   outlined and exposes disabled action semantics. The explicit reason above the
   cards makes its availability understandable without colour or a generic label.
5. Scrollable details follow the primary block in the same column. They contain the
   connection status, complete exercise name, set scale and additional field-error
   explanation. The full name has no line cap or ellipsis. Rotary scrolls this
   column; opening an editor transfers focus as specified in §10. The button stays
   anchored while either the primary block or details move through the viewport.

The details status retains the connection dot: filled for ACTIVE, hollow for degraded
states. ACTIVE's status word is spoken only; degraded status words remain drawn in
**details**, while the concrete completion reason occupies the initial primary slot.
The set scale contains at most eight pills. For totals up to eight, each represents
one set. Larger totals use eight contiguous set ranges covering the entire sequence:
a range wholly before the current set is filled, exactly one range containing the
current set is outlined, and later ranges use `pillPending`. `WearSetScale` uses Long
arithmetic for range products so protocol Int bounds cannot overflow or trigger an
unbounded number of composables. Exact localized set progress remains the scale's
content description, without a duplicate drawn sentence; buckets never replace the
spoken ordinal/total. These semantics remain accessible after scrolling.

`CompletionUnavailableReason` replaces inference from `completeEnabled` alone. Its
precedence is disconnection, then stale/refresh-required state, then an outstanding
command, then remaining unavailable authority, then numeric validation. A fresh pending
command owns AttemptBound authority and is explained as sending; idle missing authority
requires refresh. Rep validation precedes weight validation under the protocol's
existing rules. An actionable completion has no reason, and every blocked target-bearing
surface must have one. Invalid fields leave editing available when existing authority
permits it.

| Typed reason | EN primary copy | RU primary copy |
| --- | --- | --- |
| `DISCONNECTED` | Phone offline | Нет связи |
| `REFRESH_REQUIRED` | Refresh needed | Обновите данные |
| `COMMAND_IN_FLIGHT` | Sending… | Отправка… |
| `INVALID_REPS` | Reps: 1–999 | Повторы: 1–999 |
| `INVALID_WEIGHT` | Invalid weight | Ошибка веса |

Weight and reps use `WearValueFormatter` with the same selected locale, outside
composition. Values are formatted when the presentation model is built or copied;
copying a changed value or locale recomputes them. Weight uses exact hundredths,
zero-to-two fractional digits and no grouping. Reps use the same locale's digits.
Cards and editors consume these prepared strings; localized unit/absence wording
remains in UI resources. Clearing a draft weight keeps it null rather than restoring
the snapshot value.

The nominal 192dp budget reserves 26dp for the top arc, 42dp for the 128dp-wide
context slot, a 4dp gap, a minimum 54dp card row, and 62dp for the anchored Small
button including its outer padding. At the minimum card height the row ends at 126dp;
the scroll viewport ends at 130dp. These are design dimensions, not pixel measurements.
The fresh 80-cell first-view matrix verifies actual card growth, both locales/scales,
bounds, absent weight and every reason. No user font-scale reduction is allowed.

The historical PR-A 159/211px Medium-button lanes and RU × 192dp × 1.24 disabled-word
exception do not certify this Small glyph layout. PR-D removes that word and its G6
exception, with the layout and strict host checks verified together. The
[PR-A ledger](wear-bottom-band-rebudget.md) remains
an account of its original implementation and observations.

## 5. Full-screen numeric editor

Opened by tapping a value card. One value, large, with increment and decrement
controls placed at the top and bottom arcs. Each step immediately updates the draft;
Back or swipe dismissal returns to the controller.

- Reps step 1, weight step `WEIGHT_STEP_HUNDREDTHS_KG` (250, i.e. 2.5 kg).
- Bounds and the `null` weight transition are governed by `WearDraftPolicy`,
  which is unchanged. Controls at a bound are disabled and say so.
- Rotary input drives the value. Controller rotary scrolls content; opening an editor
  transfers rotary focus to numeric stepping, so no user-selected rotary mode is needed.
  This controller binding is the PR-B contract below, not part of the PR-A baseline.
- The editor emits the existing `ControllerAction.SetReps` / `SetWeight`. It
  introduces no new action type and no new state.

## 6. State inventory

All eleven `WearSurfaceKind` values render. Each carries a distinct status string;
none is reachable without one.

| Kind | Cards | Bottom edge |
| --- | --- | --- |
| `ACTIVE` | editable when existing authority and command state permit | Small check glyph; enabled or outlined/disabled with a primary reason |
| `REFRESH_REQUIRED` | read-only | Small outlined check glyph; refresh reason above cards |
| `DISCONNECTED` | read-only | Small outlined check glyph; connection reason above cards |
| `PHONE_ACTION_NO_SETS` | absent | absent |
| `PHONE_ACTION_UNSUPPORTED` | absent | absent |
| `PAYLOAD_TOO_LARGE` | absent | absent |
| `WORKOUT_COMPLETE` | absent | absent |
| `RETRYABLE_ERROR` | absent | `Retry` |
| `PROTOCOL_MISMATCH` | absent | absent |
| `NO_SESSION` | absent | absent |
| `LOADING` | absent | absent |

Copy uses existing `WearCopy` resources for status/details and the five bilingual
`complete_reason_*` resources for the primary reason. `control_disabled` remains an
accessible card state; removing its old completion-label rendering does not make the
resource unused. String coverage must follow actual rendered and semantic roles,
including the in-flight fixture, instead of retaining the old label's corpus entry.

## 7. Gates

Each gate is stated with the mutation that must turn it red. A gate that cannot be
made to fail is a comment, not a gate.

The numbered inventory remains **G1–G7 and G9–G11: ten gates; G8 does not exist**.
PR-D revises the following contracts; its executed evidence is in Wear UI completion §7.
Locale-specific test classes are not additional gate numbers. Unset-weight semantics
have a separate regression test.

**G1 — touch targets.** Every semantics node carrying a click action has layout size of
at least 48dp on both axes, and no two ancestor-clipped target rectangles overlap.
The anchored completion/retry action must also have a visible rectangle of at least
48dp on both axes before scrolling. Each value card must offer that visible minimum
after `performScrollTo`; both editor controls are checked without scrolling.
The matrix is 192/240dp × EN/RU × font scales 1.0/1.24, including the existing
`REFRESH_REQUIRED` and `DISCONNECTED` read-only fixtures, weighted and weightless
ACTIVE, retry, and both numeric editors. Each controller starts with a fresh scaffold
so it cannot inherit a previous fixture's scroll position.

`wearVisibleBounds` uses Compose `boundsInRoot` to apply the node's actual rectangular
ancestor clips, then intersects the result with the simulated screen rectangle. It
does not intersect the anchored button with its sibling scroll viewport. Seven oracle
fixtures cover visible, clipped, nested-clipped, non-clipping-parent, sibling-viewport,
partially off-screen, and fully off-screen targets. Shape outlines, the round-display
mask, sibling occlusion, alpha and painted pixels are outside this instrument.
PR-C's G1 card check proves scroll reachability. PR-D adds mandatory first-view
checks in `WearFirstViewGateTest` and `WearFirstViewGateRuTest`: a fresh subtree with
scroll position zero, complete card rectangles inside the ancestor/screen clips,
whole field icons and text line rectangles inside the modeled round-screen boundary,
all values and blocking reasons without overflow, and identical action dimensions
across enabled/disabled fixtures. The anchored action's visible rectangle must meet
48dp and its glyph must be wholly inside the round screen. After that initial check,
the full name and set scale must remain reachable by scrolling.

The first-view matrix covers 192/240dp × EN/RU × 1.0/1.24, numeric minima/maxima,
weightless and absent-weight states, all five typed reasons, and set ordinal/total
at `Int.MAX_VALUE`: ten fixtures make 80 cells. The circle check uses
layout/text line rectangles, not rasterized pixels or a physical hit-test trace.
Those limits do not weaken the requirement for full initial values, reason and action.
*Red when:* the primary inset clips the initial cards, a card is forced to 40dp, the
reason slot truncates its text, or the bounds oracle ignores an actual ancestor clip.

**G2 — no dynamic theming.** No Wear source references `dynamicColorScheme`, and
the colour values reaching the composition are the palette of §3.
*Red when:* `dynamicColorScheme` is reintroduced in `WearAppTheme`.

**G3 — every kind is distinguishable by text.** All eleven kinds retain distinct
nonempty status strings. On target-bearing surfaces, the status and set scale belong
to `controller_details`, after the primary values. The details status draws every
non-ACTIVE word; ACTIVE speaks its exact localized status without drawing it. Set
progress is the exact localized `set_scale` content description, with no text on that
row or its descendants. Retain PR-C's global unmerged-tree absence checks for the
exact ACTIVE status and set-progress labels, so separate sibling labels cannot bypass
these assertions. This semantics contract is separate from initial geometry and does
not claim raster absence.
*Red when:* statuses are shared, either spoken-only sentence is drawn separately,
set progress loses its description, or status/scale leave the details subtree.

**G4 — blocked completion explains its reason.** Every target-bearing disabled
completion has a `CompletionUnavailableReason`, its exact localized reason is drawn
in the primary context slot, and the action exposes both disabled state and the full
disabled action description. The enabled surface instead shows exercise context and
has no blocking reason. Fixtures must cover every enum member. G1's first-view matrix
proves initial reason visibility in both locales/scales; G4 does not equate semantics
presence with visible text. The outlined action and explicit reason preserve a
non-colour distinction without the old generic word inside the button.
*Red when:* a reason maps to another condition's copy, is removed, or the action is
incorrectly enabled. Separate mapper tests protect precedence and completeness.

**G5 — contrast.** Every foreground/background pair used for text meets 4.5:1;
every stroke meets 3:1. Computed from the palette object, not sampled from pixels.
*Red when:* any text role is pointed at `stroke` (`#627587`, 4.414:1).

**G6 — no visual overflow.** Across EN/RU, both screen sizes and font scales 1.0/1.24,
all text layouts must be unellipsized and report no visual overflow except the compact
`exercise_context`, which may abbreviate on at most two lines. The full exercise name
in details, every blocking reason, values, status text and editor copy take the strict
branch. PR-D removes the disabled completion word and the RU-only exact-cell G6
exception; it does not replace it with a broader exemption. Historical PR-A positive
ellipsis assertions remain in its ledger, not in the new layout's acceptance.
*Red when:* the full name is restricted to an ellipsized line, or a reason/status
width or height is made too small for its text.

**G7 — equal primary dimensions.** The completion action has exactly the same size
in enabled and disabled states at every first-view matrix cell. Both use Small and
the same glyph. The older no-smaller-than comparison remains a useful regression
check; PR-D's first-view gate adds equality.
*Red when:* one completion state uses Medium and the other Small.

**G9 — string coverage.** Rendered fixtures exercise every required string resource,
with an explicit allowance list, and the EN/RU resource-ID sets match. Refresh the
corpus for `complete_reason_*`, the in-flight fixture, and semantic card/action state.
Remove references to the old `complete_unavailable` text node; keep `control_disabled`
where it is still spoken. Corpus/allowlist updates must follow actual uses and must
not simply lower the count to hide lost coverage.
*Red when:* required copy is added without a fixture that renders or exposes its role.

**G10 — no mid-word text breaks.** The existing structural walk checks every rendered
text layout in both locales and scales, including primary reasons, details, retry and
editors. Completion is glyph-only in both states and therefore has no button text to
wrap. A compact-context ellipsis is governed by G6; it is not a general exemption
from word-break checks. PR-D visits 248 text nodes per locale, with 54 EN and 70 RU
legal space wraps and no mid-word splits; the nonempty-walk floor remains enforced.
*Red when:* a reason or details label is narrowed until a line boundary splits one
word. The historical “Завершить” split is not a present completion label.

**G11 — no text collisions.** Rendered text bounds do not overlap other visible text
bounds. Clipped-away nodes are counted separately; a minimum compared-pair count stops
an empty walk from passing.
*Red when:* a primary reason or details text is positioned over another rendered text node.

Robolectric is the host for composition gates. It is an unreliable oracle for
transactional and concurrent semantics; text layout and semantics trees are
neither, so it is used here deliberately and within that limit.

## 8. Verification protocol

- Every gate is proven in both directions before the work is considered done:
  green on the intended implementation, red under the named mutation, with the
  mutation reverted afterwards.
- Before trusting any gate, anchor it on the base branch: a known-positive and a
  known-negative run whose outcomes are already known.
- `--rerun-tasks --no-build-cache` on every verification run. `FROM-CACHE` is not
  evidence of execution.
- Detekt and tests are separate invocations. Running them in parallel has produced
  false reds in this repository.
- Zero detekt suppressions. `autoCorrect = false`.
- Each commit is independently green.

## 9. Follow-ups

- Exercise switching, once the payload carries a list (§1). The choice between a
  tap-opened picker and a vertical swipe is open. A swipe requires a proven
  guarantee that the active screen never scrolls, at every font scale and screen
  size; that guarantee must exist as a gate before the swipe is built.
- `EdgeButton` is present and used at the pinned Wear Compose version `1.6.2`.
  PR-D selects Small for completion and retry; the PR-A baseline used Medium for
  completion. No library upgrade is required for this change.
- Initial visibility, circular-edge clipping, and pressed-state appearance need the
  targeted geometry/device checks in the completion plan. Text and semantics gates
  alone do not establish visual acceptance.

## 10. Controller rotary and hierarchy focus (PR-B)

PR-B follows merged PR-A because both edit the same controller columns. It binds
rotary scrolling to the existing `ScrollState` on all three controller families:
active/read-only, retry, and instruction-only states. Use the pinned Wear Compose
rotary APIs and hierarchy-aware focus request; do not create a parallel scroll state.

The active hierarchy owns exactly one rotary destination. Entering either numeric
editor transfers focus to its existing value-step handler. Back, swipe dismiss,
and loss of edit authority return focus to the controller without an extra tap.
PR-B left numeric step sizes, bounds, null-weight transition, protocol actions and
primary dimensions unchanged. PR-D deliberately changes the primary button to Small
while retaining that rotary/focus contract. Rotary events must not leak into the covered hierarchy.

Acceptance exercises scroll movement on overflow content in each controller family;
a non-scrollable fixture may consume focus but must not mutate a value. Both editors
must still change their own field using rotary, and each exit path must restore scroll
control. Back/swipe are tested as behaviour: lack of an explicit `interceptBack` argument
alone is not evidence of a defect. Each protective check needs a named negative control,
and host input injection is recorded separately from physical bezel/crown evidence.

## 11. Ambient presentation boundary

Ambient replaces the interactive controller/editor subtree with the summary specified
in [Wear ambient UI](wear-ambient-ui.md). Its branch has no click, rotary or editor Back
handler. Saveable interactive state retains scroll and the selected editor; a current
loss of editing authority closes the editor before returning to that branch. The model
supplies the unsent-value marker, and spoken context/progress remain complete.

This increment uses the pinned minSdk-compatible provider, its required manifest
permission, native low-bit rendering and bounded event-driven burn-in offsets. It does
not change the interactive PR-D contract, its G1–G7/G9–G11 evidence, or font scaling.
The separate [ambient ledger](wear-ambient-ui.md#5-executed-host-evidence-and-physical-boundary)
records the executed 128-cell matrix, 24 controls and fresh host gates. Transitional
WatchProcessState and no-op debug-preview actions describe the ambient-only increment;
the [runtime/ongoing contract](wear-lifecycle-ui.md) now supersedes that temporary wiring.
Ambient rendering alone is not foreground-retention proof.
