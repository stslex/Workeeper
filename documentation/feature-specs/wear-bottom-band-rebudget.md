# Wear controller — bottom-band rebudget (PR-A)

Status: specified, not implemented.
Baseline: `dev` @ `254e075f`.
Decision: **C4L** — the unavailability word moves into the disabled `EdgeButton` as its
label, the glyph is dropped in the disabled state, and `UNAVAILABLE_WORD_CLEARANCE` is
removed.

Every number in this document was measured on `wear_192` / `wear_240` emulators or in
the calibrated mockup at `documentation/mockups/wear-bottom-band.html`. Rows marked ᴹ are
model predictions from the mockup's fitted non-linear font-scale multipliers
(×1.28 for 13/14sp, ×1.20 for 16sp), which were confirmed against live device text to
within +2.0px.

---

## §1 Problem

On `!completeEnabled` surfaces the content viewport is **184px (92dp)** while the value
row rests at **186px** (ACTIVE-kind error fixtures) or **203px** (worded kinds). Reps and
weight are clipped out of the rest-state view *and* out of the rest-state accessibility
dump. At font scale 1.24 on DISCONNECTED the status word wraps to two lines and the set
pills and both cards vanish entirely.

Measured effective card height at 192dp today:

| surface class | effective card height | vs 48dp minimum |
|---|---|---|
| ACTIVE, enabled | 47dp | under |
| ACTIVE-kind error fixtures (`field_error`, `weight_error`) | 23dp | under |
| worded kinds (`refresh_required`, `disconnected`) | 14.85dp | under |

Cause: `WearControllerScreen.kt:141-145` adds a fixed `UNAVAILABLE_WORD_CLEARANCE = 24`
(`:873`) to `MEDIUM_EDGE_CLEARANCE = 76` (`:890`) whenever `completeEnabled == false`,
and `.padding(bottom = clearance)` precedes `.verticalScroll(scrollState)` on the content
Column (`:157-163`), so the reservation shrinks the scroll viewport itself. The word it
reserves for is drawn by `UnavailableWord` (`:521-533`) as a **sibling** of the scroll
column — `align(BottomCenter)`, `padding(bottom = 76.dp)`, tag `complete_unavailable` —
i.e. painted *over* the content, not placed in the flow.

The reservation is conditional on one boolean but fixed in amount: the word's measured
box is 32px @1.0 and 41px @1.24, so 24dp reserves more than the word ever needs at 1.0
and still fails at 1.24 when the word wraps.

`completeEnabled` is computed at `WearSurfaceMapper.kt:107` as
`available && commandIdle && numericValid`, independently of kind — so three kinds can
take the branch (ACTIVE, REFRESH_REQUIRED, DISCONNECTED) across four fixtures
(`field_error`, `weight_error`, `refresh_required`, `disconnected`).

---

## §2 Decision

**C4L.** Three changes, all confined to the bottom band:

1. Delete `UNAVAILABLE_WORD_CLEARANCE`. The clearance expression at `:141-145` collapses
   to `MEDIUM_EDGE_CLEARANCE.dp` unconditionally. Viewport returns to **232px (116dp)**
   on every surface. (`SMALL_EDGE_CLEARANCE = 62` is retry-surface only and is untouched.)
2. Delete the `UnavailableWord` sibling overlay. `R.string.control_disabled` becomes the
   `EdgeButton`'s own label in the disabled state.
3. In the disabled state the button renders **label only** — no glyph. The enabled state
   keeps its checkmark glyph and its existing label treatment.

Point 3 is not cosmetic. It is the reason C4L exists rather than C4:

| string | 192dp @1.24ᴹ, glyph + label (C4) | 192dp @1.24ᴹ, label only (C4L) |
|---|---|---|
| «Disabled» (en) | fits, 23.7px/side | fits, ≥46px/side |
| «Недоступно» (ru) | **clips 6.8px/side** | fits, 15.3px/side |

The glyph pushes the label into a narrower part of the arc. With it, Russian does not
fit the binding cell; without it, every locale × scale × profile cell passes. The KDoc on
`UnavailableWord` already records this configuration failing once before — «Недоступно»
split across two lines mid-word at 192dp@1.24 — so C4L fixes the geometry class, not the
instance.

---

## §3 Change surface

| file | what changes |
|---|---|
| `WearControllerScreen.kt:141-145` | clearance expression collapses to `MEDIUM_EDGE_CLEARANCE.dp` |
| `WearControllerScreen.kt:873` | `UNAVAILABLE_WORD_CLEARANCE` deleted |
| `WearControllerScreen.kt:521-533` | `UnavailableWord` composable deleted |
| `WearControllerScreen.kt:~174` | `CompleteSetButton` renders label-only content when disabled |

Nothing else. Content order is unchanged. The scroll column's children, their order, and
their styling are unchanged. `ScreenScaffold` (`:146`), `rememberScrollState` (`:138`)
and the scaffold's scroll-state wiring (`:147`) are unchanged.

The `complete_unavailable` test tag must survive the move onto the button, or every
assertion that references it must be updated in the same commit — not left dangling.

---

## §4 Behaviour contract

- The disabled state remains stated in **drawn text**, before any scroll. This is the
  commitment the sibling anchoring existed to serve, and C4L keeps it.
- The status word on degraded kinds (`refresh_required`, `disconnected`) is **unchanged**
  and still drawn. C4L does not touch it.
- Semantics: the button's `contentDescription` must continue to convey both the action
  and its disabled state. Losing the glyph must not reduce what a screen reader gets.
- The enabled and disabled buttons remain the same size. `EdgeButtonSize.Medium` fixes
  the height independently of content; both states measure `[0,238][384,378]` on 192dp
  today and must continue to.

---

## §5 Acceptance criteria

All at 192dp unless stated. `v` = value-row clip px at rest, `p` = pills, `e` = error line.
Every criterion is a rest-state measurement — no scrolling before measuring.

**Must hold:**

| cell | before | after |
|---|---|---|
| `field_error` / `weight_error` @1.0 | v39 | **v0** |
| `field_error` / `weight_error` @1.24ᴹ | v54 | **v6** |
| `refresh_required` / `disconnected` @1.0 | v55.8 | **v7.8** |
| `refresh_required` @1.24ᴹ | v80 | **v32** |
| `disconnected` @1.24ᴹ | v120.9 · p19.9 | **v72.9 · p0** |
| `active_boundary` @1.0 | ✓ | **✓ (unchanged)** |
| `active_boundary` @1.24ᴹ | v6 | **v6 (unchanged)** |
| viewport, `!completeEnabled` | 184px | **232px** |

**Also must hold:**

- Effective card height at 192dp rises to **47dp** on the error fixtures and **38.5dp** on
  the worded kinds. Both remain under the 48dp minimum — see §6.
- The value nodes appear in the rest-state accessibility dump on `field_error` and
  `weight_error` at scale 1.0. They are present-but-clipped today; after this change they
  must be present-and-unclipped.
- Arc fit, measured not modelled, on a real `EdgeButton`: «Недоступно» on one line at
  192dp @1.24 with margin on both sides. See §7 item 1 — this is the one criterion that
  cannot be signed off from the mockup.
- 240dp: no cell clips more than it does today, in any locale or scale.

---

## §6 Out of scope — named, not forgotten

These are measured, real, and deliberately not fixed here.

1. **The error line rests below the fold** — `e75.8` @1.0, `e108.9` @1.24ᴹ on
   `field_error` / `weight_error`, on the very surface it disables. It sits after the
   cards in the content order, and content order is out of scope. Unchanged by every
   candidate evaluated, at both profiles.
2. **Universal 6px value clip at 1.24ᴹ** — present on every surface including enabled
   ACTIVE. It is stack height, candidate-independent, and no band change touches it.
3. **Worded-kind residue at 1.24ᴹ** — v32 / v72.9 remain. Closing them requires collapsing
   the status word (candidates C5/C6), which is blocked: see §8.
4. **Effective card height stays under 48dp** at 192dp after this change (47dp / 38.5dp).
   PR-A improves it; PR-C is where the gate learns to measure it.

---

## §7 Open items requiring on-device confirmation

1. **Arc content lane.** The 15.3px/side margin was computed against the *drawn arc edge*
   from the framebuffer. A real `EdgeButton` has its own content padding, so the usable
   lane is narrower. 15.3px/side is an **outer bound, not a margin**. One on-device
   measurement of the composed label inside the real button, in ru at 192dp@1.24, is
   mandatory before merge. If it does not fit, the fallback is a copy decision on
   `control_disabled` (ru), not a type-size change.
2. **Pressed-state morph.** Whether the arc's press animation disturbs an in-arc label is
   a real-device question, unanswered.
3. **Disabled affordance becomes text-only.** Whether that needs a gate or a §4 amendment
   to the redesign spec is a design call, not settled here.

---

## §8 Rejected alternatives

| candidate | why rejected |
|---|---|
| **C0** — keep current behaviour | the defect under audit |
| **C1** — word moves inside the scroll column | +48px viewport, but the word then lands below the fold in every tight cell (rest top 259.8px on `disconnected`@1.0): the disabled state becomes invisible until scrolled, surrendering the rationale the anchoring existed for |
| **C2** — clearance derived from the word's measured box | honest, but yields only +16px @1.0 / +7pxᴹ @1.24 and still fails every band cell; strictly dominated |
| **C3** — word removed entirely | +48px at the price of no drawn disabled word anywhere; unavailability becomes outline + semantics only |
| **C4** — glyph + label in the arc | geometry identical to C4L, but «Недоступно» clips 6.8px/side at 192dp@1.24; the code's own KDoc records this configuration failing before |
| **C5** — C4 plus status-row collapse on worded kinds | geometrically the best (v0 @1.0 everywhere, uniform v6 @1.24ᴹ), but **reds G3**: `WearKindDistinctionGateTest` lines 71-77 require every non-ACTIVE kind to draw its status word, not only speak it. Proven by probe — control green, C5-shape red at that clause. Shipping C5 means amending a deliberately-written clause that encodes a locked §4 decision. |
| **C6** — C5 with a shape-distinguished status dot | geometrically free relative to C5 and restores visual kind distinction at zero cost, but reds G3 for the same reason: a slashed ring contributes no `Text`. C6 is the correct *form* of the collapse if the §4 decision is ever revisited; it is a dead letter while the clause stands. |

---

## §9 Gates

| gate | expected effect |
|---|---|
| G3 kind distinction | **unaffected** — the status word stays drawn on degraded kinds |
| G7 primary hierarchy | **unaffected** — `EdgeButtonSize.Medium` fixes height independent of content; a single ≤41px label line cannot outgrow the 140px body. Settled analytically; if an empirical answer is wanted, the probe is a scratch Robolectric test comparing `EdgeButton(Medium)` `node.size` with `Icon` vs `Text` content. |
| G1 touch targets | **still green, still blind** — it measures unclipped `node.size`, so it neither catches today's 14.85dp nor credits the improvement. PR-C, not PR-A. |
| G6 overflow | unaffected in principle; re-run and confirm |
| G4 disabled-not-colour-alone | must be re-confirmed: the disabled affordance loses its glyph, so the assertion's subject changes even if its verdict does not |

Verification discipline: `./gradlew --stop` first, every invocation with
`--rerun-tasks --no-build-cache --no-daemon`, detekt and tests as separate serial
invocations, zero new suppressions, bisect-green per commit, no Paparazzi golden
re-recorded.

---

## §10 Sequencing

- **PR-B** (rotary binding) is independent and can land before or after.
- **PR-A** is this document.
- **PR-C** (teach G1 to measure viewport intersection against ancestor clips, and add a
  read-only fixture at the small extreme) lands **after** PR-A — run against today's
  layout it would red on defects PR-A removes.
