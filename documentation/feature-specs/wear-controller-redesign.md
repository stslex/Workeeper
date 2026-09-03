# Wear controller visual redesign — phase 1a

Status: specification. Supersedes the layout described in
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

**D-A. Fixed palette, no dynamic theming.** `WearAppTheme` currently calls
`dynamicColorScheme(LocalContext.current)`, which sources colour from the active
watch face. It is replaced by a fixed palette derived from `AppColors`. Cost: the
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

**D-E. `Complete set` is anchored to the bottom edge.** It is currently the last
child of a `Column`, which places the primary action below the fold on small
screens.

**D-F. The training name is dropped from the controller.** It costs a line of a
240dp screen for information the user already has and which the Tile also carries.
This changes the §3.2 reading order.

**D-G. Disabled and secondary labels use `#8B95A1`, never `#627587`.**
`#627587` on black is 4.35:1, below the 4.5:1 text threshold. It remains legal as
a stroke, where the 3:1 non-text threshold applies.

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
2. Connection status: a dot plus a word. The dot is **filled** when fresh and
   **hollow** when not — a shape difference, not only a colour one.
3. Exercise name. Up to two lines, then ellipsis.
4. Set scale: one pill per set of the current exercise. Completed pills filled,
   the current one outlined, pending ones `pillPending`. Directly below it the
   same information as words (`Подход 3 из 5`), because §10 forbids relying on a
   visual channel alone.
5. Two value cards side by side: weight and reps. Each card shows a unit-bearing
   label in the header and a bare numeral below it — the column-header convention
   already used on the phone. When the exercise is weightless the weight card is
   absent and the reps card is centred.
6. `Complete set`, anchored to the bottom edge.

When mutation is unavailable the cards lose their fill and keep an outline, their
values move to `textMuted`, and the button inverts from filled to outlined and
gains the word `Недоступно` beneath its label. Both changes are changes of shape
and of text, not of colour alone.

## 5. Full-screen numeric editor

Opened by tapping a value card. One value, large, with increment and decrement
controls placed at the top and bottom arcs. Confirm returns to the controller.

- Reps step 1, weight step `WEIGHT_STEP_HUNDREDTHS_KG` (250, i.e. 2.5 kg).
- Bounds and the `null` weight transition are governed by `WearDraftPolicy`,
  which is unchanged. Controls at a bound are disabled and say so.
- Rotary input drives the value. This is the only screen where rotary is bound
  to anything, so no mode indicator is required.
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

Copy comes from `WearCopy`; no string is introduced by this work.

## 7. Gates

Each gate is stated with the mutation that must turn it red. A gate that cannot be
made to fail is a comment, not a gate.

**G1 — touch targets.** Every semantics node carrying a click action has bounds of
at least 48dp on both axes, and no two such nodes overlap.
*Red when:* the bottom-edge button's height is set to 40dp.

**G2 — no dynamic theming.** No Wear source references `dynamicColorScheme`, and
the colour values reaching the composition are the palette of §3.
*Red when:* `dynamicColorScheme` is reintroduced in `WearAppTheme`.

**G3 — every kind is distinguishable by text.** For all eleven kinds the rendered
semantics tree contains a non-empty status string, and no two kinds produce the
same one.
*Red when:* two kinds are pointed at the same string resource.

**G4 — disabled is not signalled by colour alone.** In every state where
`completeEnabled` is false and the button is present, the semantics tree contains
the disabled label.
*Red when:* the label is removed and only the fill changes.

**G5 — contrast.** Every foreground/background pair used for text meets 4.5:1;
every stroke meets 3:1. Computed from the palette object, not sampled from pixels.
*Red when:* any text role is pointed at `stroke` (`#627587`, 4.35:1).

**G6 — no visual overflow.** At font scales 1.0 and the largest the platform
offers, and with the longest string of each locale, no text node reports visual
overflow except the exercise name, which may ellipsize at its second line.
*Red when:* the status row is given a fixed width narrower than its longest string.

Robolectric is the host for G1, G3, G4 and G6. It is an unreliable oracle for
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
- Availability of `EdgeButton` in Wear Compose Material3 at the pinned
  `wearCompose` version is unverified. If it is absent, the fallback is a
  bottom-anchored container with the same dimensions and the same gates.
- No screenshot testing exists for the Wear module. Adding it would let the
  layout be gated on appearance rather than only on semantics.
