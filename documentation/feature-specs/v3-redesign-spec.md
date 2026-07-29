# v3 Redesign — Specification

**Revision 4.** Supersedes all earlier copies. If you find another, delete it.

**Companion (normative):** `documentation/feature-specs/screen-extraction.md` — the appearance contract for all five designed screens. This revision does not duplicate it; it delegates to it. Where the two disagree, **the extraction wins** for appearance and **this document wins** for tokens, thresholds, behaviour and scope.

**Revision discipline, binding from here on:** revisions are **edited, not rewritten**. Revision 3 lost a first-discovery blocker (the missing elevation mechanism) because each revision was a from-scratch rewrite and content survived only by being remembered. This is the last full rewrite; it exists to install the append-only structures (§25, §26) that make future changes incremental. A change to this document is a diff, reviewable as one.

---

## 0. The two contracts

### 0.1 What governs what

- The **mockups** (`documentation/mockups/*.html`) are the contract for **appearance**: layout, structure, component treatment, iconography, affordances, states, strings.
- **This document** is the contract for **tokens, type scale, contrast thresholds, behaviour, and scope**.
- The **extraction** (`screen-extraction.md`) is the mockups made buildable: per-screen structure, colour, type, geometry, states, icons, affordances, strings, interaction — plus delta tables against the code as of its writing.

Revision 3's §0.2 said "mockups are reconnaissance, not contract". That sentence was true of **values** (font sizes, raw px, the §11 scope items) and false of **appearance**, and it is the sentence that shipped a session screen with the design system applied and the design absent. Corrected as above.

### 0.2 Values the mockups do NOT decide

- **Font sizes** — the six-step scale wins (§4). Text may land 1–2px off the mockup; that is correct, not a bug.
- **Raw px as geometry** — round onto the `AppDimension` ladder: nearest rung, ties toward the value with existing call sites (worked example: mockup 20 → 16dp, which had 45 call sites). Values may be **derived rather than transcribed** where the mockup's number is a sum of parts (88dp row = 2×21 + 4 + 18 + 2×12, confirmed from golden pixels). **[V]**
- **CSS mechanics** — shadows, radii, flex gaps transcribe as treatments, not as literal CSS.
- **§11 scope items** — drawn in the mockup, decided here.

### 0.3 Claims are marked

- **[V]** — verified by execution, evidence named. Note the date-fragility: citations have decayed inside weeks, and `design-system.md` went stale **inside a single branch**.
- **[I]** — inferred from reading. **Resolve by preflight before building on it.** This arc's [I] claims about code behaviour failed seven for seven; an [I] claim being wrong is the expected case.

Unmarked statements are design decisions — this document's own authority.

### 0.4 Every mockup colour is measured before adoption

Three for three have failed a locked threshold: light `meta` #69727C (3.79 on raise), `dim` under an 11sp label (3.91), and the `dim` ramp itself (§2.5). None visible to the eye. **Measure, then adopt** — and measure with the gate's own arithmetic (§27, quantisation).

---

# Part I — Foundation

## 1. Principle

The accent is **brightness, not hue**. A session starts muted and brightens as it is completed. Completed = maximum contrast against the background, both themes.

No coloured accent. `molten` marks records only; `rust` marks destruction only. Contrast is the load-bearing expressive device — hence the gate.

Measured: completed content scores 13.07–17.77 dark / 14.71–18.96 light against 7:1. **[V]**

## 2. Palette

### 2.1 Dark

| Token | Value | Role |
|---|---|---|
| `base` | `#0B0D0F` | app background, lifted off zero, cold cast |
| `sec` | `#12161A` | secondary surface (most frequent) |
| `field` | `#171C21` | input fields |
| `slab` | `#1E242A` | section slab |
| `raise` | `#242B32` | the single raised surface — active exercise |
| `max` | `#F1F5F9` | completed, maximum contrast |
| `body` | `#B7C0CA` | ordinary text |
| `meta` | `#8B95A1` | metadata — **and the merged `dim`, see 2.5** |
| `idle` | `#8B95A1` | inactive (WCAG-exempt where genuinely inactive) |
| `hair` | `rgba(255,255,255,.05)` | row divider |
| `hair-s` | `#2B333B` | solid divider |
| `molten` | `#F0A22E` | record, text |
| `molten-solid` | `#F0A22E` | record, fill |
| `rust` | `#C4574A` | destructive |

### 2.2 Light

| Token | Value |
|---|---|
| `base` | `#F6F7F9` |
| `sec` | `#EFF1F4` |
| `field` | `#E9ECF0` |
| `slab` | `#FFFFFF` |
| `raise` | `#DFE3E8` |
| `max` | `#0D1114` |
| `body` | `#2C333A` |
| `meta` | `#596169` — deliberate deviation, see 2.4 |
| `idle` | `#7C858F` |
| `hair` | `rgba(13,17,20,.07)` |
| `hair-s` | `#D2D7DD` |
| `molten` | `#C2410C` |
| `molten-solid` | `#F97316` |
| `rust` | `#B03B2E` |

**Naming:** these are the mockup's names; the codebase uses `surfaceTier0..4`, `textPrimary/Secondary/Tertiary` etc. The Rosetta table is extraction §0.1. Aligning the naming systems is `tech-debt.md`, not this arc.

### 2.3 `molten` — four-part role, inverts by theme

Text, solid fill, background (wash), border. Light **darkens** where dark **lightens**. Two homes only: the record accent and the transient wow state (§9). Not a general accent — KDoc says so.

### 2.4 Light meta — deliberate deviation

Mockup's #69727C fails 4.5:1 on three of five surfaces (worst: raise 3.79). Resolution **#596169** — worst surface 4.88, passes everywhere; separation from `body` survives. **[V]** KDoc carries the reason. Darkening further is free (the gate asserts a threshold); lightening is caught.

### 2.5 `dim` — merged into `meta`. The argument, corrected.

The mockups declare a fourth, weakest ramp step: dark `#6B7078`, light `#98A0A9` (revision 3 had them **swapped** — a token-extraction error where theme blocks were discarded and values assigned by plausibility). **[V]**

As drawn, `dim` fails hard: worst-surface 2.87 dark / 2.05 light, **worst backing is `raise` in both themes** (an earlier claim named `slab` and called light unreachable — wrong: light clears 4.5 at `#5E6670`). **[V]**

**The merge stands on the corrected argument: perceptual collapse.** The legal value collapses onto `meta` in both themes — dark `#8C9198` vs meta `#8B95A1` (redmean 16.3), light `#5E6670` vs `#596169` (redmean 17.0). A fourth step that passes AA is indistinguishable from the third step. **[V]**

Implementation: `textDim` is an **alias of meta** with the full argument in KDoc, including the reinstatement path (a restricted role limited to large type) and the revert point (foundation-fixes C1). The contrast map carries the merge with zero orphaned triples.

### 2.6 `slabtop` — the lifted-surface signature. Built.

One meaning, two mechanisms, inverting by theme (extraction §0.2):

- **dark:** `inset 0 1px 0 rgba(255,255,255,.055)` — a 1dp inner top-edge highlight. There is no darker colour to cast a shadow onto at `#0B0D0F`.
- **light:** `0 1px 3px rgba(13,17,20,.07), 0 6px 18px rgba(13,17,20,.05)` — real shadows. A white highlight on `#FFFFFF` is invisible.

Marks: the active exercise card, the open card, the tab indicator, the segmented-control thumb.

Implementation **[V]**: tokens on `AppElevation` (already palette-derived); treatment as its own `Modifier.liftedSurface`. **Binary, not a level** — all four consumers read one fact, and `AppActiveSurface` is capped at one call site by a detekt rule while lift legitimately has four. Measured off recorded PNGs: dark lifted top band `#2A3035` = slab + 5.5% white exactly; light page darkens to `#E3E4E6` under the card, easing back over ~24px.

The next reader will try to unify the two mechanisms. The KDoc explains why they cannot be.

## 3. Contrast thresholds

Threshold depends on **type size**, not the pair alone. WCAG grants 3:1 only at ≥24sp regular or ≥18.66sp bold; Medium (500) is not bold.

| Slot | Condition | Threshold |
|---|---|---|
| Archivo (`wdth 116`) 700, 34 / 26 / 19 | bold ≥18.66sp | **3:1** |
| Archivo (`wdth 116`) 700, 15 / 12.5 / 11 | — | **4.5:1** |
| IBM Plex Sans below 700, 34 / 26 | ≥24sp | **3:1** |
| IBM Plex Sans below 700, ≤19 | — | **4.5:1** |

The Plex rows say **below 700**, not "Medium": since the fonts PR the heading rungs are set in **600**, and 600 does not reach WCAG's bold boundary any more than 500 does, so 400 / 500 / 600 all share one answer. Only a real 700 would move a row — and it would *loosen* the 19sp one, which is the direction the gate cannot catch. `TypeSlot`'s KDoc carries the same table and the same warning.
| `max` on any surface | — | **7:1** |
| `meta` on its backing | — | **4.5:1** |

**`rust` on `raise` in dark = 3.28** — below 4.5:1 for a destructive label. Open: darken the backing or forbid destructive text on `raise`; the map must encode whichever. **[V]**

### 3.1 Hairlines carry no threshold

Sections are separated by a 30dp gutter plus a label, not by a line; `hair-s` divides rows **inside** a section — decorative, 1.12–1.52 is legitimate. **[V]** A line that becomes the sole separator on some future screen is load-bearing and takes 3:1.

### 3.2 The gate is a map

(a) declared `(foreground, background, type slot)` triples with thresholds; (b) exclusions with reasons; (c) a test enumerating **all** foreground × surface combinations that fails on anything neither declared nor excluded. Part (c) is the point — without it a new screen adds an unverified pair in silence.

Disabled/inactive colours are WCAG-exempt (1.4.3, 1.4.11) — verify inactivity at call sites. Report **distinct measurements** alongside rows (role aliasing inflates counts).

### 3.3 The map's own integrity is gated

A duplicate key resolves last-write-wins: behaviour set by declaration order, neither line readable as truth. This occurred — and the two prior readings of the outcome were **both half right**: the threshold *was* declared correctly at 3:1, and `borderDefault` had *dropped out of the enumeration entirely*. Established by execution: stripping all five DECLARED rows gave exit 0 before the fix, exit 1 after. **[V]**

**The map is built from a list and fails at construction on a duplicate key.** The guard is proven live: injecting a duplicate `textDim` key throws, naming both roles. **[V]**

## 4. Typography

| Family | Slots | Weights bundled |
|---|---|---|
| IBM Plex Sans | all text | 400 / 500 / **600** |
| Archivo, cut at `wdth 116 / wght 700` | numerals, timer | 700 |
| IBM Plex Mono | units, metadata | 400 / 500 / **600** |

Scale: **34 / 26 / 19 / 15 / 12.5 / 11**. The fifteen M3 names are aliases over the six steps.

The three heading rungs (34 / 26 / 19) are set in **600**; body / meta / caption in 400. `text.title` carries **−0.39sp** of tracking (`-.015em` at 26sp) and no other `(family, rung)` pair carries any, beyond the pre-existing 0.5sp on caption. The timer has a **name**, `AppTypography.timer`, aliasing `numeric.display` — not a seventh step. All fonts PR (B2–B5). **[V]**

**C1. `tnum` mandatory on every numeric slot** — Archivo's digits are proportional; measured drift without it is 16px on a timer. **[V]**
**C2. `numericFontFamily` takes digits and `: . , - + / %` only** — zero Cyrillic in Archivo; closed by two mechanisms covering different halves (Cyrillic golden → text slots; detekt rule → numeric slots). **[V]**
**C3. `isShrinkResources` strips unreferenced fonts** — verify by sha256 in the APK; entries obfuscate. **[V]**

**B2–B5 closed by the fonts PR** — see §25 for each. Two of the four premises were wrong on inspection and the corrections are recorded there: headings rendered at **400**, not "500 or synthesised"; and the timer is **32px in both mockups**, so `.data-s` carries two roles rather than the timer carrying two tiers. **[V]**

## 5. Motion

| Token | Value |
|---|---|
| `fast` | 140 ms |
| `base` | 260 ms |
| `slow` | 520 ms |
| `out` | `cubic-bezier(.16, 1, .3, 1)` |
| `spring` | `cubic-bezier(.34, 1.56, .64, 1)` |

`spring` overshoots past 1. **Overshoot is valid on geometry, invalid on colour** — a colour lerp past 1.0 clamps or garbages.

## 6. Session mechanics (behavioural contract; appearance is extraction Part 1)

### 6.1 Exercise states

`active` (expanded, lifted) · `fin` (collapsed, muted, result replaces plan) · `skip` (excluded from the denominator, plan untouched, reversible in place, **no snackbar**) · `temp` (one-off: counted, not added to the plan, dashed ordinal) · deleted (excluded, plan cleaned, 5-second undo toast). Transient `prfx`.

The "only for today" toggle appears **only** on mid-session additions; off by default. Plan removal is its own dialog ("Keep" / "Remove from plan").

**Unfilled sets are a UI draft state, not a persistence state.** Every production writer of `set_table` gates on reps — a zero-rep row is not creatable through production paths. **[V]** The finish dialog reports the count of **visible empty rows** (real, non-zero); the persistence-level discard is defence in depth whose KDoc says it finds nothing today.

### 6.2 `plan-attached`

A property of the exercise↔training **relation**, encoded by the absence of a `training_exercise_table` row. **No migration.** Not `adhoc` — that flag means "created inline" with its own lifecycle, and **the two axes intersect: the intersection held a bug** (graduation would strand an inline one-off at `is_adhoc = 1`; cancel would leak it). Any work touching either axis checks the other. **[V]**

**The correct frame is every path that joins through `training_exercise_table`, not every path that writes to it** — two readers driving lifecycle writes also join through it. **[V]**

### 6.3 `getPlans`

Distinguishes "no row" from "row with empty plan" — `rows.associate{}` makes a missing pair an absent key, pinned by `TrainingExerciseRepositoryImplDbTest:212-217`; the collapse was at the call site (`map[k]` null-ambiguity), fixed with `containsKey`. **[V]**

### 6.4 Sets

Set-level skip removed. Deletion: "− set", always the last; middle deletion not planned. Adding a set to a completed exercise returns it to incomplete. The done-marker and PR-row treatments are **shipped at the component level** (foundation-fixes C4/C5) **[V]**; the row *geometry* they sit in is rebuilt by the session PR. T2 (value at 26sp + molten tint) is **carried** — blocker B1.

## 7. Disclosure — SUPERSEDED by decision (session-rebuild amendment to #186)

The seven-rule automaton this section used to specify is **retired deliberately**: the coming
logic changes need a bare base without built-in assumptions, and there is intentionally **no
explicit active-set marker**. The complete replacement contract:

1. **Expanded = the card is open.** That is the whole meaning of "active"; the lift keys on it.
2. **On first entry the FIRST card in the list is expanded** — status is not consulted.
3. **Collapse a card → it collapses. Nothing else happens anywhere.**
4. **Expand a card → it expands. Nothing else happens anywhere.**

No auto-advance, no auto-collapse of completed cards, no "exactly one open", no
manual-mutes-auto flag, no skip exception. Multiple open cards are legal and expected.

What survives from the old section: the open set lives in the Store so a plan-editor
round-trip preserves it (the real failure mode was always that round-trip, not rotation —
17 absorbed configChanges make all state containers survive identically **[V]**), and a
mid-session addition opens its own card.

**Past-session has no open state at all** — no `expanded` anywhere in the feature. Its
rebuild starts by building disclosure (this contract, not the retired automaton). **[V]**

## 8. Progress rail

9dp tall, 12dp gap, 22dp top margin; `railmeta` beneath. Degrades **sets → exercises → overall** via `BoxWithConstraints` **inside the single rail component** — the rule is width at the layout point and survives `fontScale` (MainActivity absorbs it without recreation). The extraction records **two** width thresholds (9 and 11), not one — both named constants, both **unverified on a device**. The mockup's presets reach only two of three levels; the golden set adds a constrained-width case for OVERALL. **[V]**

## 9. Wow moments

Two only: **set closure** (circle → 13dp squircle, 38→42dp, tick strokes in over 260ms with 60ms delay, tick colour = `base` on the filled shape, row flashes, rail segment fills) and **record** (molten unfurl; closure automaton takes record as a **parameter** — flash and segment resolve to `molten`/`molten-solid`). Merge, never sequence. Flash and stroke gate on **false→true transitions** — the first-composition-fire defect shipped once. Overshoot on geometry only.

## 10. Gates

### 10.1 Paparazzi

`2.0.0-alpha05` (stable cannot configure on AGP 9.3.0) · Jupiter path · `maxPercentDifference = 0.0` explicit · `useDeviceResolution = true` · real bundled fonts · **liveness assertion finalising both tasks is mandatory** (silent-skip survives Jupiter via task filters; `failOnNoDiscoveredTests` is false repo-wide) · goldens do not run under `testDebugUnitTest` (no comparison happens there) · a filtered run cannot serve as a determinism check · canvas cut to the subject (~22 KB vs ~49 KB). All **[V]**.

### 10.2 What a golden does not guarantee

A golden locks in what **is**. Its guarantee is differential; a wrong baseline is green forever. This shipped twice: the first-composition flash (four goldens) and the under-treated PR row (`setPersonalRecord` locked the weaker rendering). **Transient states are captured as pairs** — at rest and mid-transient; a lone transient golden asserts nothing about *when* it fires. Baseline corrections are labelled as such in commit bodies.

### 10.3 Custom detekt rules

A `detekt.yml` key is **required** (keyless registration = silent exit 0). `:lint-rules:test` runs in CI. Rules are **treatment-based, not slot-based** — they survive the scheduled token renames. All **[V]**.

### 10.4 Outside the gate

18 out-of-window sites (sheets, dialogs, dropdowns, tooltip) plus both animations plus everything time-based. **Outside the gate means in scope and verified by hand** — "not coverable" is a statement about verification, never about whether the work is required. Step 5 shipped four unmodified out-of-window surfaces on an otherwise-complete screen because a prompt treated its commit list as the work list.

### 10.5 `@Deprecated` is unavailable

Under `maxIssues: 0`, deprecation warnings are build failures. Staged migrations are tracked in this document or `tech-debt.md`, never as compiler warnings.

## 11. Scope

| # | Item | Decision |
|---|---|---|
| 1 | Past-session tonnage (Kotlin sum over loaded data) | **in** |
| 2 | Chart third metric — per-session volume (new fold + enum value) | **in** |
| 3 | Units picker | **out** — its own arc; the Settings row is omitted, not stubbed |

Weightless chart semantics: the metric toggle is gated `type == WEIGHTED` and stays gated unless a decision says otherwise — do not invent a bodyweight-volume semantic.

## 12. Out of scope

Time as an exercise type (v6→v7 migration + enum across ten sites) · supersets · units (§11.3) · residual-weight scrubbing by migration (rejected: destroys logged data) · module duplication, palette renames, dead dimension scales (`tech-debt.md`).

---

# Part II — Screens

## 13. The appearance contract is the extraction

Per-screen structure, colour, type, geometry, states, iconography, affordances, strings, interaction: **`screen-extraction.md` Parts 1–5.** This document does not restate them. The extraction's delta tables are the rework scope per screen — noting they describe the code **as of the extraction**; foundation-fixes has since landed C1–C6, so re-derive the delta where it matters.

**Provenance (phase-2 addition):** the extraction's "code does" columns may describe the never-merged `feature/v3-screens` branch rather than dev — proven on Part 2 (#187's PF2), recurred as stale component naming in Parts 4–5. Every delta cell is **[I]** against HEAD (§0.3); re-derive before building on it.

| Screen | Extraction | State |
|---|---|---|
| Session | Part 1 | **rebuild** — design-system-applied, design absent; §14 skeleton is retired |
| Past session | Part 2 | **rebuild** — includes building disclosure (§7); 2.8 lists its own rework's defects |
| Exercise detail | Part 3 | not started |
| Chart | Part 4 | **rebuilt** — #190, consolidated in #192; monochrome series closed as drawn (series in `--max`, no palette role) |
| Settings | Part 5 | **rebuilt** — #191, consolidated in #192; the group container is gone (`SettingsSection` deleted) |
| Empty states | pass2d | pattern in kit; placement per screen |
| Eight derived screens | written rule | after the method is proven on Session |

**A screen means its entire surface**: body, topbar, every sheet/dialog/dropdown/menu it reaches, its empty state, every action — including actions that already work and merely look like v2.4. The extraction's sheet inventories supersede any list in any prior revision (§14's list missed a real sheet).

**Kit candidates** are ranked in extraction §6.4; the shared set-row question is settled there, not per-prompt.

**Every screen prompt requires CC to run an element-by-element comparison against the mockup before opening the PR** and to attach the delta. A human found the step-5 gap in one pass of the eyes; the implementer can run that pass first.

---

# Part III — State and registries

## 24. Arc state (as of revision 4)

**Merged into dev:** #177 fonts+theme · #178 PR tiebreak · #179 gate+typography · #182 palette+contrast map · #183 session mechanics · #184 foundation fixes (dim merge, slabtop, done-marker, PR wash, input surface, сет→подход) · extraction committed · this revision · #185 fonts (B2–B5) · #186 session rebuild · #187 past-session rebuild · #188 exercise detail · #189 design-system §Components/4 (B9's live instance). **Stage 4 complete** — the five designed screens are rebuilt (#186–#191); #190 chart + #191 settings ride the consolidated delivery PR (#192, `feature/v3-final`), which merges after the device regression executes against it.

**Queue (stage 5):** eight derived screens · the final device pass.

**Open decisions:** B14 (chart→past-session needs a drawn affordance) · release-notes line for #178. Closed since revision 4: the B6 **encoding** note (§3's "the map must encode whichever") — the pair is encoded as an exclusion; B6's value question itself stays open in §25. The monochrome-series question closed with #190 as drawn (series in `--max`; no palette role needed).

**Device-checklist debt:** accumulated from #177 onward, none executed — status bar 28/34/35+, Cyrillic, empty chart, rail thresholds ×2, fontScale 2.0, done-marker animation, PR variant, lifted surfaces (watch light shadow in 8dp LazyColumn gaps), inputs recessed, «подход» truncation. Grows with every screen PR; the final device pass (§24 end) is the largest single verification item left.

**Golden coverage gaps:** past-session and plan-editor have **zero** goldens — recording them **before** their rework edits is an entry condition of those PRs, so the delta reads.

## 25. Blocker registry — append-only

Entries are never deleted; resolution is recorded in place. New entries append. This registry exists because a rewrite lost B-2025-elevation once already.

| ID | What | Origin | Status |
|---|---|---|---|
| B1 | PR-row value: 26sp `numeric.title` + molten tint as drawn. 19sp passes the gate but inverts the mockup's hierarchy (value is the dominant element). "Doesn't fit" was a fact about the old row. | #184 T2 carry | **open — lands inside the session rebuild** |
| B2 | Weight 600: every mockup h1/h2/h3; only 400/500 bundled. Headings render synthesised or at 500. | extraction §0.4 | **resolved — fonts PR.** IBM Plex Sans + Mono 600 bundled from the same release as the 400/500 (proven: re-downloading the bundled four reproduces their hashes byte for byte). All three heading rungs set in 600, which moves 7 of the 15 aliases. **The premise was wrong on one point:** headings rendered at **400**, not "500 or synthesised" — nothing in the repo asked Plex Sans for Bold, so the synthesis hazard was never live. `.ctitle`, `.btn` and `.prtag` also declare 600 and are deliberately left to their components. Mono 600 ships with **no consumer** (its only mockup selector, `.prtag`, is drawn by a text-family component) — 174 608 bytes on account. **[V]** |
| B3 | Archivo drawn at `wdth 116`; bundled static cut at 125. The tnum measurement was taken on the 125 instance. | extraction §0.4 | **resolved — fonts PR.** Cut at 116 by instancing the upstream VF; 121 532 bytes, **−66 504** against the 125 static it replaces and −537 064 against bundling the VF. 116 has no `fvar` named instance and no `STAT` axis value, so no published artifact exists at that width and provenance becomes **reproducibility**: input hash + `fonttools 4.63.0` + the command, in `licenses/README.md`. The reproducibility rests on `recalcTimestamp = False`, **not** on assigning `head.modified` — `TTFont.save()` overwrites that field from the wall clock unless the flag is cleared, so the assignment alone is a no-op. The PR shipped that no-op first and its two-run check passed only because both runs landed in the same second; re-cut and re-checked across ~15 seconds of wall clock, five runs give one hash. **A reproducibility check whose runs are not separated in time is not a reproducibility check** — logged here because it is a new instance of §27's "green from a detector never shown to fire". Faithfulness proven by a controlled pair — self-instancing at 125 reproduces the published static's advances 23/23. tnum re-measured on 116: colons 203–219 (was 214–232), identical on both lines; the canary's signal narrows from 16px to 10px. The VF route works on minSdk 28 (verified in `ui-text-android`: gated at API 26) and is the only way to draw all three mockup widths — recorded as the reinstatement path. **[V]** |
| B4 | letter-spacing declared in mockups (−0.015em/−0.02em), absent from `AppTypography` | extraction §0.4 | **resolved — fonts PR.** `text.title` only, at **−0.39sp** (`-.015em × 26sp`). The rung's three declarations disagree; `-.015em` wins over `.exhead h2`'s `-.02em` because `.exhead h2` declares no `font-family` and neither does its `<button>` parent, so its tracking was chosen against the UA font (Arial), not Plex. `section`, `body`, `display` and both other families stay at default — their selectors declare none, and inventing tracking there would track every sentence in the app to fix one card title. Positive mono tracking stays component-level. **[V]** |
| B5 | Session timer two-tier: 32px vs 26px elsewhere; scale records one step | extraction §0.4 | **resolved — fonts PR, premise corrected.** Read from the markup rather than the stylesheet, the timer is **32px in both mockups**: `pass2d` inline-overrides `.data-s` back to 32px at L221. The mockups do not disagree about the timer. What is genuinely two-tier is the **class**, which carries two roles — timer at 32px (→ 34 rung) and record-hero value at 26px (→ 26 rung, which is B1). Resolved by naming, not by a step: `AppTypography.timer` aliases `numeric.display`, so it carries `tnum` by construction and moved zero pixels. `TnumCanaryGoldenTest` renders through it, making the alias load-bearing. **[V]** |
| B6 | rust on raise (dark) = 3.28 vs 4.5:1 for destructive text | step-3 measurement | **open — decision** |
| B7 | `donefill` belongs on the **field**, not the row: the row's tier4 wash made even the unit label fail | #184 resisted item | **open — session rebuild** |
| B8 | Past-session open card cannot lift: no disclosure state exists | #184 resisted item | **open — past-session rebuild, first task** |
| B9 | `design-system.md` went stale inside one branch (background tier, missing `isRecord`) — nothing gates prose | #184 report | **open — doc rule §27** |
| B10 | IBM Plex Mono 600 bundled with zero consumers (174 KB); one-line revert available | #185 | **open — revert or first consumer decides** |
| B11 | weightless cluster: prod "0kg/0×N" render (predates the arc) · `set_table` residual weights survive type flips and past-session edits re-persist them · type lives on the exercise, not the set · chart semantics gated WEIGHTED · no UI spec for the weightless row · fixtures historically `weight=null` | multiple | **open — its own arc** |
| B12 | CURRENT/PENDING derived in `StateStatusMapper` but consumed by nothing, **by design** (bare disclosure base); do not wire consumers without a decision | #186 amendment | **open** |
| B13 | hero training-name term needs a `PR_ROW_SELECT` extension — touches the #178 parity surface; the join pattern exists in the history query | #188 | **open — own PR** |
| B14 | chart→past-session navigation died with the tooltip; needs a drawn affordance if wanted | #190 | **open — decision** |
| B15 | Archive counts sub-line: no data source; a hardcode would lie | #191 | **open — small cross-feature data add** |
| B16 | The chart canvas draws **no axis labels**, so a metric switch between proportional datasets is visually a no-op — the normalised shape is identical and only the readout numbers change. Correct as drawn; the question is whether the scale needs a cue | chart animation round | **open — decision** |
| B17 | `loadChart` / `processInit` pass no `onError`, and `launchDefault` defaults it to `{}` — a DB failure emits nothing and `isLoading` latches, leaving a permanent spinner with no retry path and no error surface | chart animation round | **open** |

## 26. Resolved-decision ledger — append-only

| Decision | Resolution | Where |
|---|---|---|
| `dim` fourth step | merged into `meta`; argument is **perceptual collapse** (redmean 16.3/17.0), not unreachability — the unreachability claim was wrong (worst backing is `raise`; light clears at `#5E6670`) | §2.5, #184 C1 |
| Elevation | `slabtop` built: binary `Modifier.liftedSurface`, per-theme mechanisms | §2.6, #184 C2 |
| Light meta | `#596169`, deviation from mockup on record | §2.4 |
| Hairline threshold | none; gutter+label separate sections | §3.1 |
| Unfilled sets | UI draft state; finish-dialog count = visible empty rows | §6.1 |
| `plan-attached` encoding | absent plan row; no migration; axes checked jointly | §6.2 |
| Wow merge | one automaton, record as parameter | §9 |
| PR-value size | 26sp, not 19sp — hierarchy over gate-minimum | B1 |
| Units | out of arc | §11 |
| dim/meta reinstatement path | restricted large-type role; revert point #184 C1 | §2.5 |
| Archivo: derived instance vs published static | **derived at `wdth 116`** — no published artifact exists at that width, so provenance is reproducibility (pinned input hash + tool version + command), not identity. Variable font evaluated and rejected on 537 064 bytes plus an unverified layoutlib path; recorded as the reinstatement route for the three-width treatment | B3, `licenses/README.md` |
| Heading tracking value | **−0.015em, not −0.02em** — the −0.02em declaration renders in the mockup's UA button font, not in Plex | B4 |
| Timer slot | **a name, not a step** — `AppTypography.timer` aliases `numeric.display` (34 rung); the 26px sibling role is B1's | B5 |
| Scope of 600 | the three **heading rungs** only. `.ctitle` / `.btn` / `.prtag` declare 600 but are component treatments on body and caption rungs; moving their aliases would drag `titleSmall` and `labelMedium` against `.tabs button` and `.mitem.on`, which are 500 | B2 |
| Disclosure model | **bare open/closed by decision** — the §7 automaton retired (deleted, not bypassed); expanded = open, first card opens on entry, toggle does nothing else, no active-set marker. The mockup's `isOpen`/`nextSlot` JS no longer binds. | §7, #186 amendment |
| Consent strings | strings carrying consent/legal semantics are **behaviour**, not appearance — the mockup never wins those; origin: #191's AI-export caption, reverted in phase 1 | §0.1 |
| Composite symbols | a glyph outside the numeric charset renders as a mono/text span composed into the value, never as a charset extension; origin: `×` in #188's hero | §4 C2 |
| Overshoot on value-encoding geometry | **forbidden** — a data point driven past its target draws a reading the data never contained, which is a lie with a number attached. Data morphs use `out` at `base`; `spring` stays legal only on geometry that encodes nothing (scrub bar, tab indicator). Companion finding: a value morph must interpolate in a **resolved normalised space** — renormalising half-interpolated values every frame makes the tween track whichever endpoint is numerically larger, and cancels it outright when the two datasets are proportional (measured 0.0000dp of movement over 520ms). Origin: the chart animation round on `feature/v3-final` | §5, §9 |

## 27. Verification discipline — append-only

**Execution, not reading**
- `--rerun-tasks` always; `FROM-CACHE` is not executed.
- `--stop` before measuring anything built in the same invocation (stale daemon jar → false green).
- detekt and tests as **separate invocations** (parallel run → false RED via interruption; a false RED is worse — the response is fixing what isn't broken).
- Bisect-green in a **clean worktree, seeded**: six gitignored files (local.properties, keystore.properties, keystore.jks, three google-services.json) are copied by the harness with a post-copy assert. An unseeded worktree fails at plugin-apply **before any commit's code is read** — which is why a harness defect makes all commits look identically red. **[V]**
- **The bisect seed set is SEVEN files** — `play_config.json` joined at #188; the six-file line above predates it. Harnesses assert all seven post-copy. **[V]**
- A failed gate explained plausibly is **not a passed gate**. The explanation is verified by a controlled pair (same commit, one delta) before the gate's verdict is trusted. **[V]**

**Gates**
- Prove both directions: fires on violation, silent on clean.
- Gate the gate's configuration as strictly as its content (§3.3).
- A guarantee that cannot fail is a comment.
- A gate proves what changed, not what is right (§10.2).
- Known-negative mutations must **compile** and must discriminate — a build-breaking mutation proves nothing.
- **Quantise like the gate**: the contrast gate quantises composites to 8-bit before measuring; hand calculations that don't will disagree by hundredths exactly at thresholds, where hundredths decide. **[V]**
- **A determinism check whose runs are not separated in time proves nothing.** Two runs of a font-instancing command produced the identical hash and were read as "byte-reproducible"; they were reproducible only because both landed inside the same wall-clock second, and the line meant to pin the timestamp was a no-op. Separate the runs by more than the granularity of whatever varies. **[V]** (fonts PR, B3)

**Claims**
- Behavioural claims from reading: seven for seven wrong. Mark [I]; resolve by preflight.
- Citations decay in weeks; prose decayed **inside one branch**. Rule: documentation touched by an API change is updated in the same commit or explicitly marked stale (B9).
- Empty multi-agent results are not clean results: count started vs completed; mismatch is RED. (A 17-started/1-returned fan-out was correctly discarded.)
- A failed attempt can produce information — a guard written on the wrong key established that the other lists were safe.
- **Combinations of fixes multiply**: C3's justifying picture only appeared on a lifted light card — two fixes producing a visible effect neither showed alone. Verify fix-pairs, not only fixes.

**Autonomy**
- A monitoring session fixes **breakages** forward and **stops on findings**. A verdict that changes a decision is not a build failure.
- One CC session per checkout. Three concurrent sessions produced commits on the wrong branch.
- Artifacts that live only in sessions die with them. Deliverables land in the tree.