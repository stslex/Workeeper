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
  `Row(.height(48dp).padding(horizontal = 12dp))` (`AppNumberInput.kt:110-163`). The
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
  tracking is text-family-only (`AppTypography.kt:393-402`). A 26sp digit costs
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
  (`:77-91`), and `GOLDEN_DEVICE` pins `fontScale = 1.0` (`GoldenHarness.kt:81-85`).
  Two further blind spots found: bare-row `goldenSubject` frames are 392 dp wide with
  no screen/card context (reps box 60.87 dp — roomy), and the EN locale renders
  "reps" (4 glyphs = same 28.4 dp as `повт`) so RU-specific widths are gated nowhere;
  `повторений` (71.0 dp) has zero golden coverage.
- Stale in-code figures at `AppNumberInput.kt:98-103`: "'102.5' needs ~66dp" is
  actually **81.12 dp**; "the weighted row's value box has ~52" is actually
  **68.38 dp** (weight) / **35.75 dp** (reps). Fixed in this PR per R8.

## 2. Locked decisions (from the task brief)

1. Unit labels move OUT of the field and INTO a column header inside the exercise
   card. The field keeps 48 dp height (as `heightIn(min)`) and the 26sp `dataValue`
   rung.
2. Header text, Russian: `ВЕС (КГ)` and `ПОВТОРЫ`; bodyweight: `ПОВТОРЕНИЙ`. Casing
   applied by the component via locale-aware `uppercase()`, matching `AppLabel`.
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
- **R8** — the stale KDoc at `AppNumberInput.kt:98-103` is corrected in this PR with
  the §1 figures.

## 4. Delegated decisions, resolved (accepted at GO)

- **D1 — home: `core:ui:kit`**, new `components/setrow/` package. Two real consumers
  after this PR in two different feature modules; the unit strings and the `AppLabel`
  casing pattern already live in kit.
- **D2 — one `Text`, `AnnotatedString` + `SpanStyle`.** A single Text with the unit
  appended after the name truncates tail-first, so the unit is eaten before the name
  by construction; two Texts in a Row cannot guarantee shrink order without a custom
  layout. Base colour `textSecondary`, unit span `textDim`; each segment uppercased
  with locale-aware `uppercase()` before the spans are built. Verified by test
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
- **D4 — `suffix` parameter survives.** Remaining consumers: `PlanSetCard.kt:172,186`
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
  the `BasicTextField` — the `AppTextField.kt:144-153` template. Rows pass the
  localized full unit. Asserted with the repo's PR-gating JVM pattern: Robolectric +
  `runComposeUiTest` (`AccessibilitySemanticsTest` constraints: one `@Test` and one
  composition per class; never `createComposeRule()` — silently undiscovered under
  JUnit 5).

## 5. Measured width budget

Golden device: Pixel 5, 1080 px @ 440 dpi = 2.75 px/dp = 392.727 dp;
`goldenSubject` width 392 dp. All figures at fontScale 1.0 (1 sp = 1 dp).
Glyph costs: Archivo tnum digit 18.20 dp @26sp / 13.30 dp @19sp; `.` 8.32/6.08 dp;
mono @11sp + 0.5sp tracking = 7.10 dp/glyph → `кг` 14.2, `повт`/`reps` 28.4,
`повторений` 71.0 dp.

### 5.1 Live weighted row, in-app (392.727 dp)

| link | value |
|---|---|
| screen edge padding (`LiveWorkoutScreen.kt:325-331`, `screenEdge` = 16) | −32 → card 360.727 |
| `AppActiveSurface` / `liftedSurface` | −0 (no padding, no layout border) |
| `SetsColumn` `padding(h = 12)` (`LiveExerciseCard.kt:404-408`) | −24 → 336.727 |
| row `padding(h = 4)` (`LiveSetRow.kt:99-102`) | −8 → 328.727 |
| 5 children → 4 × 8 dp gaps | −32 |
| index `widthIn(min = 12)` | −12 |
| chip-or-PR-tag (both exactly 34 wide) | −34 |
| `AppCheckmarkButton` touch box | −48 |
| flexible | 202.727 |
| weight field (×1.2/2.2) | **110.58** → value box 110.58−24−4−14.2 = **68.38** |
| reps field (×1/2.2) | **92.15** → value box 92.15−24−4−28.4 = **35.75** |

Bodyweight branch: 4 children → fixed 118 → field 210.727 → value box 111.73 (RU) /
154.33 (EN).

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

### Gate design (R1 + R2 compliant)

1. **Slot capture (closed-loop, R1).** `AppNumberInput` gains a test-only
   `slotWidthProbe: ((Dp) -> Unit)? = null` invoked with the `BoxWithConstraints`
   max width D5 introduces anyway; `LiveSetRow`/`PastSetEditRow` forward it
   (default `null`, production never passes — the `flashAlphaOverride` precedent,
   `LiveSetRow.kt:61-64`). The gate composes the **real row** at the real card
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

## 7. Target and known limits (R4)

Post-fix value boxes: reps 68.15 dp, weight 86.58 dp (live). Hard band 1.0–1.6: all
matrix cells must pass via the D5 ladder. Band 1.6–2.0: ellipsis permitted, clip
forbidden. **Known limit (registered, not worked around):** weight × 5 glyphs at
fontScale 2.0 — "102.5" at the 19sp floor under the non-linear converter is
estimated 89–95 dp vs the 86.58 dp box; the exact measured number is recorded here by
the Phase 2 gate run. Row re-layout for fontScale 2.0 is Phase 7 work, out of scope.

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
   (consumers: `PlanSetCard`, `PersonalRecordHero.kt:144`, `LiveWorkoutMapper.kt:431`,
   `exercise-chart`'s own duplicate). R7 fixture 3 + D3 canary golden.

## 10. Blocker registry

| id | status | note |
|---|---|---|
| B-1 | known limit (R4) | weight/5-glyph/2.0 cell; measured numbers land in §7 at Phase 2 |
| B-2 | follow-up, vetoed here (R5) | `AppNumberInput` lacks `semantics { error() }` unlike `AppTextField` — separate PR |
| B-3 | gated (R3) | D5 lifts `PlanSetCard` ≥4-glyph values back to 26sp where they fit; new 5-glyph fixture pins it |
| B-4 | constraint | fontScale is non-linear (×1.40 at 26sp/2.0); all gate expectations computed from measured output |
| B-5 | fixed in PR (R8) | stale `AppNumberInput.kt:98-103` KDoc figures |
| B-6 | note | EN `unit_reps` == `unit_reps_full` ("reps", `tools:ignore=DuplicateStrings`); they diverge only in RU — header strings must not collapse the pair |
| B-7 | note | new golden test classes must live in `*.golden.*` packages and be recorded, or the module liveness gate fails permanently; the R2 gate deliberately lives outside them |

## 11. Verification discipline

Every gradle invocation: `--rerun-tasks --no-build-cache --no-configuration-cache`
(FROM-CACHE/UP-TO-DATE are not evidence). detekt as a separate serial invocation.
The gate prints its matrix size; zero-input runs fail. Every commit builds and passes
independently. Network-blocked runs are reported as unverifiable, never reasoned
into green.
