# Wear controller visual redesign — phase 1a

Status: initial redesign implemented in PR #284; bottom-band correction implemented
in PR #285 (`dev` merge `20215640`). This document describes that baseline. The approved
next increments are tracked in [Wear UI completion](wear-ui-completion.md). Supersedes the layout described in
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

`MainActivity` continues to render `SyntheticSurfaceFixtures` with `onAction = {}`.
This work changes how the surface looks, not where its data comes from.

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

## 4. Layout — active state

Reading order, top to bottom:

1. Time of day, pinned at the top arc.
2. Connection status: a dot, and a word beside it in every state **except**
   `ACTIVE`. The dot is **filled** when fresh and **hollow** when not — a shape
   difference, not only a colour one. In `ACTIVE` the filled dot carries the
   message and the word would cost a line of a 192dp screen, so it is spoken and
   not drawn; in every degraded state the word is the whole message and is drawn.
3. Exercise name, one line, then ellipsis. The semantics carry the whole name.
4. Set scale: one pill per set of the current exercise. Completed pills filled,
   the current one outlined, pending ones `pillPending`. The same information in
   words (`Подход 3 из 5`) is the pill row's content description. §10 forbids
   relying on a visual channel **alone**, which this does not: the words are
   still stated, in the accessible channel.
5. Two value cards side by side: weight and reps, **unequal** — the weight takes
   the larger share, because its widest value needs 65dp of content against the
   reps' 36dp at the largest font scale. Each card shows an **icon** identifying
   the field and the value below it: the weight bare, without its unit, and the
   reps bare. The unit and an absent weight (`—`) are stated in the value's
   content description and in the full-screen editor. A textual header is not
   used: one row does not amortise a column header, and the unit is a constant
   in a kilograms-only app.
6. The primary action, anchored to the bottom edge, drawn as a **check glyph**.
   Its wording lives in the button's content description: `Завершить` is one
   unbreakable nine-character word that splits mid-word inside the arc even on
   the largest screen at the default font scale.

When mutation is unavailable the cards lose their fill and keep an outline, their
values move to `textMuted`, and the action inverts from filled to outlined. Its glyph
is replaced by the label **“Disabled” / “Отключено” inside the button**. Both states use
`EdgeButtonSize.Medium`; there is no sibling unavailable-word overlay or extra word
reservation. The disabled label is one line and may ellipsize only in the accepted
RU × 192dp × 1.24 cell. The full action and state remain spoken. See the
[bottom-band decision](wear-bottom-band-rebudget.md#2-decision-and-accepted-copy).

The historical 2dp/10dp card-fill clipping record at 192dp covered **ACTIVE only**.
It does not characterize errors or worded read-only states. Error reasons below the
fold, clipped or absent value nodes on worded states, and the circular-edge boundary
remain open; see [remaining defects](wear-bottom-band-rebudget.md#6-remaining-defects-and-ownership).
PR-A improves the budget but does not establish complete initial visibility. The approved
completion contract requires values, action, and a concrete disabled reason before
scrolling; it will change this reading order and the corresponding gates together.

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
| `ACTIVE` | interactive | `Complete set` |
| `REFRESH_REQUIRED` | read-only | outlined, disabled |
| `DISCONNECTED` | read-only | outlined, disabled |
| `PHONE_ACTION_NO_SETS` | absent | absent |
| `PHONE_ACTION_UNSUPPORTED` | absent | absent |
| `PAYLOAD_TOO_LARGE` | absent | absent |
| `WORKOUT_COMPLETE` | absent | absent |
| `RETRYABLE_ERROR` | absent | `Retry` |
| `PROTOCOL_MISMATCH` | absent | absent |
| `NO_SESSION` | absent | absent |
| `LOADING` | absent | absent |

Copy comes from `WearCopy` where it already exists. A string this design requires
and `WearCopy` lacks — the unit-bearing weight-card header of §4 is one — is
introduced, in both locales, rather than worked around. (An earlier revision
forbade introducing any string, which contradicted §4; the layout requirement
wins.)

## 7. Gates

Each gate is stated with the mutation that must turn it red. A gate that cannot be
made to fail is a comment, not a gate.

The implemented inventory is **G1–G7 and G9–G11: ten numbered gates; G8 does not exist**.
Locale-specific test classes are not additional gate numbers. Unset-weight semantics
have a separate regression test.

**G1 — touch targets.** Every semantics node carrying a click action has layout size of
at least 48dp on both axes, and no two clipped target bounds overlap. The current test
uses `node.size` for minimum dimensions and scrolls before opening editors; it does not
prove that the complete target is initially visible. PR-C/PR-D replace this gap with
explicit ancestor-clip visibility checks, including read-only surfaces.
*Red when:* the bottom-edge button's height is set to 40dp.

**G2 — no dynamic theming.** No Wear source references `dynamicColorScheme`, and
the colour values reaching the composition are the palette of §3.
*Red when:* `dynamicColorScheme` is reintroduced in `WearAppTheme`.

**G3 — every kind is distinguishable by text.** For all eleven kinds the rendered
semantics tree contains a non-empty status string, and no two kinds produce the
same one. Every non-ACTIVE kind also draws its status word. The current test does not
protect the inverse ACTIVE rule or the set-count spoken-only rule; PR-C adds that coverage
before PR-D deliberately revises the layout contract.
*Red when:* two kinds are pointed at the same string resource.

**G4 — disabled is not signalled by colour alone.** In every state where
`completeEnabled` is false and the button is present, the semantics tree contains
the disabled label.
*Red when:* the label is removed and only the fill changes.

**G5 — contrast.** Every foreground/background pair used for text meets 4.5:1;
every stroke meets 3:1. Computed from the palette object, not sampled from pixels.
*Red when:* any text role is pointed at `stroke` (`#627587`, 4.414:1).

**G6 — no visual overflow.** At font scales 1.0 and the largest the platform
offers, and with the longest string of each locale, no text node reports visual
overflow except the exercise name, which is allowed at most two lines by the current
oracle (production draws one), and the one disabled completion-label cell described
in [PR-A G6](wear-bottom-band-rebudget.md#9-gates-and-review-classification). That cell
must positively report one line and overflow; it is not skipped.
*Red when:* the status row is given a fixed width narrower than its longest string.

**G7 — primary hierarchy.** The enabled primary action is never smaller than its
disabled form at either screen extreme.
*Red when:* enabled uses Small while disabled uses Medium.

**G9 — string coverage.** Rendered fixtures exercise every required string resource,
with an explicit allowance list, and the EN/RU resource-ID sets match.
*Red when:* a required user-facing string is added without a fixture that renders it.

**G10 — no mid-word button-label breaks.** Labels in both locales must not split a
word across lines. Single-line ellipsis is governed separately by G6.
*Red when:* the narrow completion lane renders a multi-line “Завершить” label.

**G11 — no text collisions.** Rendered text bounds do not overlap other visible text
bounds. Clipped-away nodes are counted separately; a minimum compared-pair count stops
an empty walk from passing.
*Red when:* the unavailable word is positioned over another rendered text node.

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
  The controller already uses Medium completion and Small retry buttons; no library
  upgrade or substitute is required for rotary work.
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
The numeric step sizes, bounds, null-weight transition, protocol actions, and primary
button dimensions are unchanged. Rotary events must not leak into the covered hierarchy.

Acceptance exercises scroll movement on overflow content in each controller family;
a non-scrollable fixture may consume focus but must not mutate a value. Both editors
must still change their own field using rotary, and each exit path must restore scroll
control. Back/swipe are tested as behaviour: lack of an explicit `interceptBack` argument
alone is not evidence of a defect. Each protective check needs a named negative control,
and host input injection is recorded separately from physical bezel/crown evidence.
