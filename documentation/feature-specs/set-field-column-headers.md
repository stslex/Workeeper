# Set-field column headers (E-d)

Executor spec for the set-row value-clipping fix in `LiveSetRow` (live-workout) and
`PastSetEditRow` (past-session). Phase 0 discovery ran 2026-08-18; rulings R1–R8 are
locked by Ilya and are not revisited below. `PlanSetCard` is out of scope for change
(measured in §5.4; gated by one new fixture per R3).

Every number in this document is measured — fontTools 4.63.0 on the shipped TTFs,
file:line reads of the layout chain, and a live Paparazzi probe through the real
layoutlib text stack. Where the original task brief disagreed with measurement, the
corrected figure is stated and the delta explained.

## 1. Problem, measured

- `AppNumberInput` lays out `[value Box(weight 1f)] [4dp gap] [suffix Text]` inside a
  `Row(.height(48dp).padding(horizontal = 12dp))` (`AppNumberInput.kt:71-113`). The
  suffix has no weight: it takes intrinsic width first, the value gets the remainder.
  Priority inversion — the column label outranks the data.
- The value is a `BasicTextField(singleLine = true)`. It does not clip in layout: its
  inner text is measured at `maxWidth = Infinity` and the overflow disappears into the
  horizontal-scroll layer. Same data loss on screen as a clip, but structurally
  invisible to `onTextLayout` — see §6 (oracle).
- Archivo `wdth116` + `tnum`: **exactly 0.700 em per digit** (all 20 GSUB
  substitutions advance 700/1000 upem; confirmed empirically — `"12"` at `dataValue`
  lays out at exactly 100.0 px = 36.36 dp on the golden device). IBM Plex Mono:
  exactly 0.600 em, uniform, Cyrillic included. Archivo `.`/`,` = 0.320 em (8.32 dp
  at 26sp), untouched by `tnum`.
- `numeric.title` (= `dataValue`) carries **no letterSpacing** — the −0.39sp title
  tracking is text-family-only (`AppTypography.kt:231-238`). A 26sp digit costs
  **18.20 dp**; two digits need **36.40 dp**. (The brief's 17.81/35.62 baked in the
  phantom tracking; the deficit is worse than briefed.)
- Live weighted row on the 360.73 dp card: reps field = **92.15 dp** (the brief's
  ~96 undercounted one of the four 8dp gaps). Fixed cost inside the field:
  24 dp padding + 4 dp gap + 28.40 dp `повт` = 56.4 dp → value box = **35.75 dp**.
  **Two-digit reps need 36.40 dp. The field cannot fit them at fontScale 1.0** —
  deficit 0.65 dp ≈ 1.8 px, confirmed empirically (100.0 px of text vs a 98.3 px
  box). fontScale ≥ 1.3 turns the deficit from marginal into gross (10 dp+).
- The golden corpus never catches this: `SessionStateGoldenTest` defaults `reps = 5`
  (`:224-236`), its only `reps = 12` case is `setBodyweight` with `isWeighted = false`
  (`:77-91`), and `GOLDEN_DEVICE` pins `fontScale = 1.0` (`GoldenHarness.kt:37-41`).
  Two further blind spots found: bare-row `goldenSubject` frames are 392 dp wide with
  no screen/card context (reps box 60.87 dp — roomy), and the EN locale renders
  "reps" (4 glyphs = same 28.4 dp as `повт`) so RU-specific widths are gated nowhere;
  `повторений` (71.0 dp) has zero golden coverage.
- Stale in-code figures at `AppNumberInput.kt:59-64`: "'102.5' needs ~66dp" is
  actually **81.12 dp**; "the weighted row's value box has ~52" is actually
  **68.38 dp** (weight) / **35.75 dp** (reps). Fixed in this PR per R8.

## 2. Locked decisions (from the task brief)

1. Unit labels move OUT of the field and INTO a column header inside the exercise
   card. The field keeps 48 dp height (as `heightIn(min)`) and the 26sp `dataValue`
   rung. `heightIn`, never `height`: 48 dp is the floor the mockup draws, but above
   roughly fontScale 1.5 the 26sp `dataValue` line height exceeds it and a hard height
   would clip vertically exactly what this component exists to stop clipping
   horizontally.
2. Header text, Russian: `ВЕС (КГ)` and `ПОВТОРЫ`; bodyweight: `ПОВТОРЕНИЙ`. Casing
   applied by the component via `uppercase()`, matching `AppLabel`. That no-arg overload
   is locale-INVARIANT (`Locale.ROOT` mapping), which is the property wanted here: the
   label must not change with the device locale, or a golden and a user's screen disagree.
3. Two-tone header: NAME `textSecondary`, UNIT in parentheses `textDim`. No dimmer
   colour than `textDim` — a caption-rung label owes 4.5:1.
4. Ellipsis order: `(КГ)` truncates before `ВЕС`.
5. Scope: `LiveSetRow` + `PastSetEditRow`. `PlanSetCard` measured and reported only.
6. Goldens are never re-recorded by the executor. Any moved golden stops at Gate 2.
7. Zero detekt suppressions.
8. Target (refined by R4): value clipping forbidden at fontScale 1.0–1.6, hard;
   1.6–2.0 ellipsis permitted, clipping still forbidden; header ellipsis acceptable
   above 1.6.

## 3. Rulings (locked)

- **R1** — the oracle is closed-loop: the gate's slot widths are captured from a
  *rendered* `LiveSetRow` / `PastSetEditRow`, never recomputed from row-budget
  constants inside the gate. Mandatory known-negative: `CHIP_MIN_WIDTH` 34 → 60 must
  turn the gate red; if it stays green the loop is open — stop and report.
- **R2** — the gate is a measurement test: asserts on `multiParagraph.width` vs the
  captured slot width, **no `snapshot()` call**, not in a `*.golden.*` package, zero
  new PNGs. Proven by running it; if `assertGoldenLiveness` fires anyway, the exact
  mechanism is reported before any workaround.
- **R3** — one stepdown mechanism (in `AppNumberInput`), no fork. The resulting
  `PlanSetCard` behaviour change (≥4-glyph values that fit stay at 26sp instead of
  force-stepping to 19sp) is gated by a NEW `PlanSetCard` golden fixture with a
  5-glyph value ("102.5"). New snapshot, not a re-record.
- **R4** — target bands as in §2 item 8. The residual weight/5-glyph/fontScale-2.0
  cell is a known limit, recorded in §7 with measured numbers.
- **R5** — the missing `isError` → `semantics { error() }` on `AppNumberInput` is a
  correct finding for a different PR. Registered in §10; not implemented here.
- **R6** — predicted golden movement: 36 PNGs across exactly 3 classes (§8). At
  Gate 2 the actual diff list is presented; divergence from the prediction in either
  direction is itself a stop-finding.
- **R7** — mandatory new fixtures: weighted row with two-digit reps; RU bodyweight
  header `ПОВТОРЕНИЙ`; the D3 canary (header + rows at 10+ sets).
- **R8** — the stale KDoc at `AppNumberInput.kt:59-64` is corrected in this PR with
  the §1 figures.

## 4. Delegated decisions, resolved (accepted at GO)

- **D1 — home: `core:ui:kit`**, new `components/setrow/` package. Two real consumers
  after this PR in two different feature modules; the unit strings and the `AppLabel`
  casing pattern already live in kit.
- **D2 — one `Text`, `AnnotatedString` + `SpanStyle`.** A single Text with the unit
  appended after the name truncates tail-first, so the unit is eaten before the name
  by construction; two Texts in a Row cannot guarantee shrink order without a custom
  layout. Base colour `textSecondary`, unit span `textDim`; each segment uppercased
  with the locale-invariant `uppercase()` before the spans are built. Verified by test
  (commit 1): constrained-width layout capture asserting the visible line end drops
  the unit characters first.
- **D3 — one resolved index width, computed at the container.** `SetRowGeometry` in
  kit holds the 12 dp minimum once, plus a resolver:
  `max(indexMinWidth, TextMeasurer.measure(largestIndexLabel, mono.meta).width)`,
  measured at current density/fontScale. `SetsColumn` (live) and `CardBody` (past)
  know the set count, resolve once, and pass the same value to the header and every
  row. Rows keep `widthIn(min = passed)` with the default equal to today's 12 dp, so
  bare rows are byte-identical; at 10+ sets header and rows grow together.
  `PastSessionGoldenTest.cardDoubleDigitIndex` is the movement canary.
- **D4 — `suffix` parameter survives.** Remaining consumers: `PlanSetCard.kt:147,186`
  (out of scope) and previews. KDoc notes the set rows moved to headers.
- **D5 — measured stepdown inside `AppNumberInput`.** `BoxWithConstraints` provides
  the slot width ahead of children layout; `TextMeasurer` measures the value at each
  ladder rung; first fit wins. Acyclic by construction: the slot width is
  parent-flex-driven, so the chosen style cannot feed back into the constraint.
  `onTextLayout` remains a test oracle only. **Ladder = [26sp `numeric.title`,
  19sp `numeric.section`], floor contrast-pinned**: below ~18.66sp bold the value
  owes 4.5:1, which the record molten and `textTertiary` pending colours cannot pay.
  `MAX_GLYPHS_AT_FULL_SIZE` is deleted (commit 5).
- **D6 — the unit returns via semantics.** New `accessibilityLabel: String?` on
  `AppNumberInput`, applied as `Modifier.semantics { contentDescription = ... }` on
  the `BasicTextField` — the `AppTextField.kt:82-87` template. Rows pass the
  localized full unit. Asserted with the repo's PR-gating JVM pattern: Robolectric +
  `runComposeUiTest` (`AccessibilitySemanticsTest` constraints: one `@Test` and one
  composition per class; never `createComposeRule()` — silently undiscovered under
  JUnit 5).

## 4a. Column geometry — the resolved widths, as built

D3 closes the leading side. Two more columns behave the same way, and all three must be
resolved once and handed to the header and every row.

- **Trailing slot.** `SetRowGeometry.resolveTrailingSlotWidth()` =
  `max(setTypeSlotWidth, personalRecordTagIntrinsicWidth())`, by measurement.
  `CHIP_MIN_WIDTH` (34 dp) is a *minimum* `AppSetTypeChip` and `PersonalRecordTag`
  share, and the `PR` label outgrows it above roughly fontScale 1.6 — at 2.0 the tag
  measures wider than the chip it replaces. A slot pinned to the minimum leaves a
  RECORD row's fields narrower than its non-record siblings' and than the header's
  columns. Both trailing branches of `LiveSetRow` / `PastSetEditRow` take the one
  resolved width, never their intrinsic widths.
- `personalRecordTagIntrinsicWidth()` measures the label at `prTagTextStyle()`
  (`mono.caption` at `FontWeight.SemiBold`, `PR_TAG_TRACKING` = 1.1.sp) plus
  `AppDimension.Space.xs` × 2, and lives beside the label and style it measures so the
  two cannot drift.
- **Header gutters mirror the rows from the components' own constants**, not from
  copied numbers: live = `AppCheckmarkButtonTouchSize` (48 dp — public for this reason,
  not for callers to size the button; the mockup's `.mark` is 46 px, 48 dp is the rung
  and the minimum touch target); past = `resolveTrailingSlotWidth() +
  AppDimension.Space.sm + DragHandleSize` (24 dp) in `PastExerciseCard.CardBody`.
  Changing either side desynchronises header and rows.
- Every fixture sits at fontScale 1.0, where chip and tag both measure exactly the
  minimum, so no golden and no width gate can see the divergence. The alignment gate's
  fourth axis is what covers it — WHICH trailing component the row draws:
  `Case(label = "1 record set @2.0", setCount = 1, fontScale = 2f, isRecord = true)`.
- **Index column (D3), why measured rather than tabulated:** a fixed 12 dp box clips a
  SINGLE digit at fontScale ~1.6. At 10+ sets header and rows must be handed the same
  resolved value or the rows shift ~3 dp out from under a static header.
- **Card-head ordinal, a third column.** `PastExerciseCard.OrdinalWidth` = 16 dp
  (`.chead .ord { width: 16px }`) is applied as `widthIn(min = ...)` plus
  `maxLines = 1`, the same correction the set row's `indexColumnWidth` carries. 16 dp
  fits a two-digit ordinal only at fontScale 1.0; in a FIXED box Compose `Text` breaks
  the over-wide token at a GRAPHEME boundary rather than overflowing, so "10" stacks
  1-over-0. The wrap is SILENT — the title column (card head) and the 48 dp fields (set
  row) set the row height, so nothing moves.
  `PastSessionGoldenTest.cardDoubleDigitIndex` is the fixture that makes it visible.

## 5. Measured width budget

Golden device: Pixel 5, 1080 px @ 440 dpi = 2.75 px/dp = 392.727 dp;
`goldenSubject` width 392 dp. All figures at fontScale 1.0 (1 sp = 1 dp).
Glyph costs: Archivo tnum digit 18.20 dp @26sp / 13.30 dp @19sp; `.` 8.32/6.08 dp;
mono @11sp + 0.5sp tracking = 7.10 dp/glyph → `кг` 14.2, `повт`/`reps` 28.4,
`повторений` 71.0 dp.

### 5.1 Live weighted row, in-app (392.727 dp)

| link | value |
|---|---|
| screen edge padding (`LiveWorkoutScreen.kt:309-315`, `screenEdge` = 16) | −32 → card 360.727 |
| `AppActiveSurface` / `liftedSurface` | −0 (no padding, no layout border) |
| `SetsColumn` `padding(h = 12)` (`LiveExerciseCard.kt:375-378`) | −24 → 336.727 |
| row `padding(h = 4)` (`LiveSetRow.kt:77-80`) | −8 → 328.727 |
| 5 children → 4 × 8 dp gaps | −32 |
| index `widthIn(min = 12)` | −12 |
| chip-or-PR-tag (both exactly 34 wide) | −34 |
| `AppCheckmarkButton` touch box | −48 |
| flexible | 202.727 |
| weight field (×1.2/2.2) | **110.58** → value box 110.58−24−4−14.2 = **68.38** |
| reps field (×1/2.2) | **92.15** → value box 92.15−24−4−28.4 = **35.75** |

Bodyweight branch: 4 children → fixed 118 → field 210.727 → value box 111.73 (RU) /
154.33 (EN).

`SetRowGeometry.WEIGHT_COLUMN_FLEX` = 1.2f is a deliberate deviation from the mockup,
which draws `flex: 1` on both fields: weights carry decimals ("102.5") and reps never
do, so the extra fifth softens the budget. Restoring 1f to "match the drawing" narrows
the weight field.

### 5.2 Past weighted row, in-app

Same chain to row inner 328.727; drag handle 24 replaces the checkmark 48 → flexible
226.727 → weight field 123.67 (box 81.47), reps field 103.06 (box 46.66). Bodyweight:
field 234.727, box 135.73 (RU).

### 5.3 Golden contexts

Bare `goldenSubject` rows at 392 dp skip screenEdge + SetsColumn: reps box 60.87 dp —
they do not reproduce the in-app deficit. The two full-frame goldens
(`SessionScreenGoldenTest`, `PastSessionGoldenTest.screenLoaded`) match the in-app
chains exactly (EN suffixes; "reps" and `повт` are both 4 glyphs = 28.4 dp).

### 5.4 PlanSetCard (no change; R3 fixture only)

Trailing cluster 38 dp, ordinal min 22 dp, 4 dp gaps, no row h-padding: weight box
101.8 dp / reps box 63.6 dp in the 16 dp-gutter contexts; inside
`TrainingExerciseCard` weight 88.7 / reps 52.7 dp. No two-digit deficit anywhere; the
only marginal cell is 3-digit reps at 26sp in the 52.7 dp box (−1.9 dp), unreachable
under the current fixtures. Post-D5, values of ≥4 glyphs that fit their box stay at
26sp (today force-stepped to 19sp) — visible in-app change, zero golden movement
(all existing fixtures are ≤2 glyphs); gated by the new R3 fixture.

## 6. Oracle (Phase 2)

Probe findings the design rests on (measured 2026-08-18, throwaway test, deleted):

- `BasicTextField`'s own `TextLayoutResult` **cannot** report visual overflow
  (singleLine measures at `maxWidth = Infinity`; the clip lives in the scroll layer).
  Not a layoutlib infidelity — Compose architecture, identical on device.
- The faithful oracle is `Text(maxLines = 1, softWrap = false,
  overflow = TextOverflow.Clip, onTextLayout = capture)` at the width under test:
  `didOverflowWidth` is exact (`multiParagraph.width` 100.0 px vs an 83 px slot).
- fontScale is honored **non-linearly** (Android 14 `FontScaleConverter`): 26sp at
  fontScale 2.0 renders ×1.40 (`"12"`: 100.0 → 140.0 px). All expectations above 1.0
  are computed from measured output, never linear sp math.
- Plain `testDebugUnitTest` renders via the HTML report writer — no golden
  comparison, no `src/` writes; `assertGoldenLiveness` hooks only
  `verifyPaparazzi*`/`recordPaparazzi*` and counts only `*.golden.*` suites.
- Robolectric is false-negative for text metrics, so the header ellipsis sweep
  (`SetColumnHeaderTest`) renders through the Paparazzi measurement harness
  (`OverflowGateSdk`), never a Robolectric composition. ~156 px is the full Russian weight header
  advance at `mono.caption` on the golden device, which is what makes
  `CONSTRAINED_WIDTHS_PX = listOf(220, 150, 130, 110, 90, 70, 50)` straddle the regime
  the test needs — unit truncated while the name still reads in full.

### Gate design (R1 + R2 compliant)

1. **Slot capture (closed-loop, R1).** `AppNumberInput` gains a test-only
   `slotWidthProbe: ((Dp) -> Unit)? = null` invoked with the `BoxWithConstraints`
   max width D5 introduces anyway; `LiveSetRow`/`PastSetEditRow` forward it
   (default `null`, production never passes — the `flashAlphaOverride` precedent,
   `LiveSetRow.kt:48`). The gate composes the **real row** at the real card
   content width (device width minus `screenEdge`×2 minus the sets-column `Space.md`
   ×2 — context tokens referenced from `AppDimension`, the same tokens the screen
   reads; everything inside the row — gaps, index, chip, checkmark/drag, flex split,
   field padding — is production layout, measured, not recomputed). Captured widths
   feed the matrix.
2. **Assertion.** For each matrix cell the gate lays the value string as a proxy
   `Text` with the identical `TextStyle` at the captured slot width and asserts
   `!didOverflowWidth` (hard band) — plus the R4 band rules.
3. **No snapshot semantics (R2).** The gate never calls `Paparazzi.snapshot()`; it
   drives the same SDK seam `Paparazzi.setup()` wraps with a no-op frame consumer, so
   verify/record modes never engage regardless of which gradle task runs it. It lives
   outside `*.golden.*` (proposed: `...feature.live_workout.gate` /
   `...feature.past_session.gate`), adds zero PNGs, and is proven by running it under
   both plain `testDebugUnitTest` and a `verifyPaparazziDebug` invocation. If
   `assertGoldenLiveness` fires anyway, stop and report the mechanism.
4. **Matrix.** {1, 2, 3, 5 glyphs} × {fontScale 1.0, 1.3, 1.6, 2.0} × {weight slot,
   reps slot} per row type. The gate prints the matrix size and fails on zero inputs.
5. **Known-positive (pre-fix, uncommitted proof).** On unmodified rows the captured
   reps slot is 35.75 dp; predicted red cells at fontScale 1.0: reps × {2, 3, 5}
   glyphs (2 glyphs by ~2 px); weight all green at 1.0, red from 1.3 up. The failing
   set must match this prediction; if the gate passes on unmodified code, stop.
6. **Known-negatives (uncommitted, reverted).** (a) `SIZE_TITLE_SP` 26 → 40 must add
   failures; 26 → 12 must clear them. (b) R1: `CHIP_MIN_WIDTH` 34 → 60 must go red
   (the captured slot shrinks by ~11.8 dp; at least the high-fontScale 3-glyph cells
   flip). Green here = open loop = stop.
7. **Commit rule.** The gate lands together with the change that makes its asserted
   band green (bisect-green): the fontScale-1.0 band with commit 2 (suffix removal +
   header), the full R4 matrix with commit 5 (stepdown). No red state is committed.
8. **GUARD — no `onGloballyPositioned` inside `AppNumberInput`** for a test-only
   capture: it dispatches on every scroll frame of a live session. The seam is the
   probe callback (shipped as `valueSlotProbe`), fed from the `BoxWithConstraints` the
   D5 rung choice needs anyway; production never passes it.
9. **Edge capture reads the SEMANTICS TREE, never test tags.** The alignment asserts
   address the header label by its text and the field by its accessibility label
   through `ViewRootForTest.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)`
   — the same public access path Paparazzi's own accessibility extension uses under
   layoutlib, so the capture leaves ZERO trace in production composables. GUARD: do not
   add test tags to reach these edges. The gutter/index pair keeps its `onSizeChanged`
   probes only because they fire on size change, never per frame.
10. **Why the read happens in the frame hook.** `OverflowGateSdk.renderView` invokes its
    `onFrame` hook from `PaparazziSdk`'s `onNewFrame` consumer because the frame is the
    one moment the caller's view is attached and its composition live — the only window
    in which a read of the rendered semantics tree is guaranteed valid.

## 7a. Second-round rulings (Gate 2, 2026-08-18)

- **R4 superseded / R11** — bands restated: 1.0–1.6 with domain-reachable values:
  clipping forbidden, hard; 1.6–2.0: ledger entry permitted with measured numbers.
  (The original R4 "ellipsis permitted" clause is void — `BasicTextField` scrolls,
  it cannot ellipsise, per the Phase 0 probe.)
- **R9** — the compact-inset lever: `AppNumberInput` drops its horizontal insets
  `Space.md` → `Space.sm` when the measured field width is under 105dp — a boundary
  between measured populations (must-fire: in-app reps fields 92.15/103.06dp;
  must-not-fire: the ten-set card's golden-width reps 106.36dp, PlanSetCard's
  narrowest 109.1dp). +22px of value budget, no typography or contrast change.
- **R10** — cells a sane domain cap would eliminate enter the ledger tagged
  "resolved by domain cap, follow-up PR"; the missing bound itself is blocker B-8.
- **R12** — a sub-19sp rung gated on value state is REJECTED (state-dependent type
  size is visual noise); the 19sp contrast floor stands.
- **R13** — the width threshold is replaced by an explicit `fieldInset` parameter on
  `AppNumberInput`: the 105dp line was calibrated to a 3.3dp gap in today's geometry —
  a coincidence, not a property, and a silent tripwire. Set rows pass
  `SetRowGeometry.compactFieldInset` (`Space.sm`, uniform across their fields and the
  header's label inset — the parameter change also closed a real 4dp label/value
  drift the threshold had introduced); every other consumer keeps `Space.md`.
- **R14 / R17** — header/column alignment gets direct assertions from the rendered
  layoutlib tree at 1 set and at 10 sets: the header label's LEFT EDGE equals its
  column's value left edge (R17 — the contract itself; width equality was falsified
  inside this PR when the R9 threshold moved the field inset 4dp while the label's
  stayed put, with every width equal through the drift), plus the index gutter equals
  the row index column (R14 — necessary, not sufficient). Known-negatives, both
  proven: a hardcoded 12dp gutter reds the gutter assert at 10 sets (33px vs 40px); a
  divergent header inset reds all four edge asserts (11px) with gutters green. A
  golden records what is; it is not cited as an alignment guarantee.
- **R18** — the first alignment-test cut ran under Robolectric and passed vacuously.
  That is an INSTRUMENT DEFECT, not a font fact: `mono.meta` arithmetic (12.5sp ×
  0.6em) gives "10" = 15dp and "100" = 22.5dp against the 12dp floor, layoutlib
  agrees (measured 14.5dp), and Robolectric laid the 3-digit index 10.5dp under the
  arithmetic — it did not render Plex Mono at all. Recorded as the second confirmed
  Robolectric false-negative in this PR. The drift scenario is REACHABLE in
  production; it is exactly what the gate defends.
- **R15** — the "domain cap" ledger entries are DEBT, not resolution: those cells are
  red in production for anyone entering a five-digit rep count until B-8 ships, and
  the entries are void the moment it does (the inverted gate assertion will fail and
  demand their removal).
- **R16** — the ledger inversion is kept (first live catch: past weight ×5 @2.0
  leaving the ledger under R13 was flagged by the inverted assertion, not by a human).
- **R20** — the never-re-record rule governs BASELINE goldens (the 446 that predate
  this branch). Snapshots first recorded inside this PR are not baseline: amending
  them to the branch's final state pre-merge hides no regression and is not a
  re-record. The three E-d fixtures (`setTwoDigitReps`, `exerciseTenSets`,
  `exerciseBodyweightRu`) were amended under R13 and are reviewed in the contact
  sheet's appendix alongside the 36.

## 7. Target and known limits — measured ledger (post-R13, final)

Measured with the explicit compact inset on all set-row fields: **58 of 64 cells
pass**. The uniform compact inset resolved past weight ×5 @2.0 (the +6px deficit met +22px
of freed budget — caught by the inverted assertion, as designed) and improved live
weight ×5 @2.0 from +42 to +20px. Every remaining red is 5-glyph:

| cell | text px | slot px | over | tag |
|---|---|---|---|---|
| live reps ×5 @1.3 | 215 | 209 | +6 | DEFERRED to domain cap (B-8) |
| live reps ×5 @1.6 | 250 | 209 | +41 | DEFERRED to domain cap (B-8) |
| live reps ×5 @2.0 | 310 | 203 | +107 | DEFERRED to domain cap (B-8) |
| past reps ×5 @1.6 | 250 | 239 | +11 | DEFERRED to domain cap (B-8) |
| past reps ×5 @2.0 | 310 | 233 | +77 | DEFERRED to domain cap (B-8) |
| live weight ×5 @2.0 | 276 | 252 | +24 | 19sp contrast floor — sanctioned limit |

**The five deferred cells are DEBT, not resolution (R15): until B-8 ships, a user who
enters a five-digit rep count sees a clipped (scrolled-out) value in these bands in
production.** The entries are void the moment B-8 merges — the inverted gate
assertion fails on a fitting ledger cell and forces the cleanup. Converter facts for
the record: 26sp scales ×1.40 at fontScale 2.0, 19sp ×~1.695, mono caption
near-linearly.

## 7b. Superseded first-round ledger (commit 5, pre-lever) — kept for the record

Post-fix value boxes at fontScale 1.0: live reps 187 px / weight 238 px; past reps
214 px / weight 270 px (captured, not computed). The measured stepdown strictly
improves every cell over the glyph heuristic, and the full 64-cell matrix was measured
through the closed-loop gates with the stepdown live. **56 of 64 cells pass. Every
failing cell is ladder-floor-limited**: the value exceeds even the contrast-pinned
19sp section rung, which the non-linear `FontScaleConverter` scales ×~1.375 at
fontScale 1.6 and ×~1.695 at 2.0 (26sp scales less: ×1.40 at 2.0).

| cell | text px | slot px | over | band |
|---|---|---|---|---|
| live reps ×5 glyphs @1.3 | 215 | 187 | +28 | HARD |
| live reps ×5 @1.6 | 250 | 187 | +63 | HARD |
| past reps ×5 @1.6 | 250 | 217 | +33 | HARD |
| live reps ×3 @2.0 | 186 | 184 | +2 | 2.0 |
| live weight ×5 @2.0 | 276 | 234 | +42 | 2.0 — the R4-sanctioned known limit |
| past weight ×5 @2.0 | 276 | 270 | +6 | 2.0 |
| live reps ×5 @2.0 | 310 | 184 | +126 | 2.0 |
| past reps ×5 @2.0 | 310 | 214 | +96 | 2.0 |

**Conflict, reported rather than worked around (task-brief discipline):** the hard
band's "clipping forbidden at 1.0–1.6" is unreachable for reps × 5 glyphs at the 19sp
floor, and the 2.0 band's "clipping still forbidden" fails in four cells where R4
sanctioned one. A 5-digit rep count is typeable (reps has no upper input cap) but not
a physical value class. Options, in Ilya's hands: (a) cap reps input length, taking
5-glyph out of the reps column's value space; (b) permit a below-19sp rung for
non-record/non-pending value states (they pay 4.5:1 fine); (c) extend the known-limit
ledger to all eight measured cells; (d) treat as Phase-7 row re-layout input. Until the
ruling the gates assert the 1.0 band and the matrix above it is measured, not asserted.

## 8. Golden impact (R6) and new fixtures (R7, R3)

Predicted movers — exactly 3 classes, 36 PNGs of 446:

| class | moves | of | why |
|---|---|---|---|
| `SessionStateGoldenTest` | 20 | 30 | direct rows + 3 expanded-card cases: suffix glyphs vanish, header row inserts, "102.5" returns to 26sp |
| `SessionScreenGoldenTest` | 2 | 2 | expanded pe-2 card in frame |
| `PastSessionGoldenTest` | 14 | 30 | expanded cards + direct rows |

All 37 other classes (410 PNGs) are predicted stable — the header goes only into the
two feature files; `.height(48)` → `.heightIn(min = 48)` is pixel-neutral corpus-wide
(content 32 dp < 48 dp at fontScale 1.0, no consumer imposes intrinsic min-height);
the D3 default is byte-compatible. At Gate 2 the full corpus runs; the actual moved
list is presented against this table and any divergence stops the work.

New fixtures (new snapshots, first recording — not re-records):

1. Weighted row, two-digit reps (`reps = 12`, `isWeighted = true`) — the case the
   corpus never had (R7).
2. RU bodyweight card with the `ПОВТОРЕНИЙ` header (`locale = LOCALE_RU`) — 71 dp
   suffix-successor coverage, gated nowhere today (R7).
3. D3 canary: expanded live card with 10 sets — header + rows aligned at the grown
   index column (R7; complements `cardDoubleDigitIndex` on the past side).
4. `PlanSetCard` with a 5-glyph value "102.5" — pins the D5 behaviour change (R3).

## 9. Commit plan (one PR off `dev`, every commit bisect-green)

1. Kit: `SetRowGeometry` (D3 source + resolver) + `SetColumnHeader` (D1, D2) + unit
   tests for ellipsis order and casing. No consumer change → zero golden movement.
2. Gate + field change: `slotWidthProbe` seam; suffix removed from the six set-row
   call sites; header wired into `SetsColumn` + `CardBody`; `.height` →
   `.heightIn(min)`; R8 KDoc correction; gate asserting the fontScale-1.0 band;
   known-positive/known-negative proofs recorded in the PR body. R7 fixture 1.
3. Bodyweight branch: single column, `ПОВТОРЕНИЙ`; R7 fixture 2.
4. Semantics (D6) + Robolectric `runComposeUiTest` test asserting the unit is
   announced.
5. Adaptive stepdown (D5); delete `MAX_GLYPHS_AT_FULL_SIZE`; extend the gate to the
   full R4 matrix; record the §7 known-limit numbers; R3 fixture 4.
6. Strings audit: `values/` + `values-ru/` additions; old unit strings stay
   (consumers: `PlanSetCard`, `PersonalRecordHero.kt:131`, `LiveWorkoutMapper.kt:412`,
   `exercise-chart`'s own duplicate). R7 fixture 3 + D3 canary golden.

## 10. Blocker registry

| id | status | note |
|---|---|---|
| B-1 | known limit (R4) | weight/5-glyph/2.0 cell; measured numbers land in §7 at Phase 2 |
| B-2 | follow-up, vetoed here (R5) | `AppNumberInput` lacks `semantics { error() }` unlike `AppTextField` — separate PR |
| B-3 | gated (R3) | D5 lifts `PlanSetCard` ≥4-glyph values back to 26sp where they fit; new 5-glyph fixture pins it |
| B-4 | constraint | fontScale is non-linear (×1.40 at 26sp/2.0); all gate expectations computed from measured output |
| B-5 | fixed in PR (R8) | stale `AppNumberInput.kt:59-64` KDoc figures |
| B-6 | note | EN `unit_reps` == `unit_reps_full` ("reps", `tools:ignore=DuplicateStrings`); they diverge only in RU — header strings must not collapse the pair |
| B-7 | note | new golden test classes must live in `*.golden.*` packages and be recorded, or the module liveness gate fails permanently; the R2 gate deliberately lives outside them |
| B-8 | follow-up (R10) | reps and weight have NO upper input bound (`InputHandler.kt:30`, `PlanDraftReducer.kt:35` coerce only at zero): 99999 reps is enterable and storable — a data-domain defect; the cap is new product behaviour for its own PR, and five of the seven §7 ledger cells resolve with it |

## 11. Verification discipline

Every gradle invocation: `--rerun-tasks --no-build-cache --no-configuration-cache`
(FROM-CACHE/UP-TO-DATE are not evidence). detekt as a separate serial invocation.
The gate prints its matrix size; zero-input runs fail. Every commit builds and passes
independently. Network-blocked runs are reported as unverifiable, never reasoned
into green.
