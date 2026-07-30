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
| `rust` | `#DF714B` | destructive — corrected to match shipped `AppColors.kt`, see B19 |

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
| `molten` | `#BE3E0C` — corrected to match shipped `AppColors.kt`, see B19 |
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

**`rust` on `raise` in dark measures 4.50:1** (`#DF714B` on `#242B32`; truncated from 4.500986 — the tightest dark pair in the palette, zero headroom either direction). Resolved by moving the foreground, not the backing: see B6.

This line previously read **"`rust` on `raise` in dark = 3.28`... [V]`"** — a claim marked verified-by-execution, measured against `#C4574A`, a colour the app has never shipped in dark (B19). It carried that mark, unchanged, for weeks after `d3f7a3e0` moved the foreground and made the number stale. The wrong number is the smaller failure; the more expensive one is that it wore the one mark that tells a reader not to re-check it. A `[V]` verifies that an execution happened, not that its input is still the input in front of you — see B19 and §27.

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

### 10.6 The shell mockup gate runs in CI

`documentation/mockups/shell_gate.py` — nine checks over `pass2d.html`, two of them rendered in headless Chromium — is wired into `.github/workflows/mockup_gate.yml` and runs on every PR except those into `master` (excluded by branch, not by path: `master` carries no `documentation/mockups/` to baseline against, and check 9 reads `AppColors.kt` too, so a paths filter would miss the drift the check was written for). Three properties matter more than the check list. The baseline is the merge-base against the PR's **base branch**, which is what satisfies §27's "the baseline must not already contain the change under test" for a stacked PR, where `dev` does not. A `:root` token change is declared with an `Allow-root-change:` commit trailer rather than a hard-coded workflow flag, so the declaration is reviewable in the diff, permanent in history, and scoped to the commit that earned it. And the permanent known negative (`--target f52462c7`) runs on every CI run and must go red — asserted on a `[FAIL] 7` row at `width=0px→0px`, not on the exit code alone, because checks 6 and 9 also fail at that ref and would mask check 7 ceasing to discriminate. That is §10.1's liveness rule in different clothing: a green from a detector never shown to fire is not evidence. **[V]**

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

**Stage 5 opens with the shell drawn rather than derived.** The chrome those screens share — the bottom bar, the list row, selection mode, the add action, the paging tails — appears on three to four screens each. Drawn per screen it produces three or four editions of one element, which is the mechanism behind extraction §6.5. It is drawn once instead, in `pass2d.html` (`#s-list`, `#s-nav`), and each screen composes it; the decisions it encodes are §26.

**The eight derived screens, enumerated from the tree** (`AppNavigationHost.kt`) — they had never been listed anywhere in this document: `home` · `all-trainings` · `all-exercises` · `archive` · `single-training` **detail** · `single-training` **editor** · `plan-editor` · `exercise` **editor**. The arithmetic, recorded so no count drifts again: the host registers **12 graphs**; two of them carry two screens each (`Screen.Training` → `TrainingDetailScreen` + `TrainingEditScreen`, `Screen.Exercise` → `ExerciseDetailScreen` + `ExerciseEditScreen`), giving **14 screens**; minus the five designed and rebuilt in stage 4 — live-workout, past-session, **exercise detail**, chart, settings — leaves 9; minus `image-viewer` (`Screen.ExerciseImage`), the ninth screen and **outside the arc**, leaves **8**. `feature/recovery` is in neither count: it is a separate `RecoveryActivity`, not a NavHost destination. Correct any prior count that says otherwise. **[V]**

**The nav-bar deletion perimeter, recorded so it is not recomputed.** Three files die: `core/ui/kit/.../components/bottombar/AppBottomBar.kt` and `.../AppBottomBarDestination.kt` — both already dead, their only references being each other and a preview — and `app/.../bottom_app_bar/BottomAppBar.kt`, the live one. `BottomBarItem` **survives or is replaced one for one**: it carries `screen: Screen.BottomBar` and `getByRoute(route)`, which is routing, not chrome. `app/.../host/BottomBarNavigationListener.kt` is the fourth file in the perimeter — it reads `getByRoute` off an `OnDestinationChangedListener` and moves with `BottomBarItem`, not with the bar. Four things break and are fixed in that same PR: `App.kt:152`, the only call site of `WorkeeperBottomAppBar`; the testTags `WorkeeperBottomAppBar` and `BottomAppBarItem_{NAME}`, **nine** lookups across `ApplicationBottomBarTest` (4) and `NavigationLifecycleRegressionTest` (5) — keep them verbatim, those tests are about navigation lifecycle and rewriting them mixes two changes into one PR; and the `ContrastContract` row `("accentTintedForeground", "accentTintedBackground", META, "AppBottomBar selected icon on indicator pill")`, which after the deletion is a declared pair with no consumer, i.e. a guarantee that cannot fail (§27). **[V]**

`AppDimension.BottomNavBar.height = 72.dp` is a v2 rung and re-derives onto the ladder (§0.2) at the drawn 60. It has **two** consumers, not one: `BottomAppBar.kt:61` takes `heightWithInsets` (height + the system navigation-bar inset) for the bar itself, and `AppNavigationHost.kt:49` pads every bottom-bar destination by the bare `height` — followed in the same chain by `.systemBarsPadding()`, which supplies the same inset on the content side.

**The two are flush — measured, not read.** One draft of this section inferred that the content under-pads the bar by the system inset; the counter-inference that it does not was equally a reading, and modifier-order claims are the class this arc has been wrong about seven for seven. Instrumented instead, as a controlled pair on one emulator whose only delta is the navigation mode, reading the tagged destination node (it sits at the **end** of that modifier chain, so its bounds *are* the padded content region) against the bar's own bounds:

| nav mode | nav inset | bar height | content bottom | bar top | gap |
|---|---|---|---|---|---|
| gestural | 24dp | 96dp | 827.43dp | 827.43dp | **0.00dp** |
| three-button | 48dp | 120dp | 803.43dp | 803.43dp | **0.00dp** |

The inset doubles, the bar grows by exactly that, the content bottom rises by exactly that, and the gap stays zero. **No bug — closed, not carried forward.** The pair discriminates: had the two paddings failed to track, the gap would differ between the rows. **[V]**

**The lists are not a blank slate**, which §26's "Add action" row depends on. Five bottom paddings exist today. `AllTrainingsScreen:171` and `AllExercisesScreen:74` already pass `heightLg + screenEdge` = **56 + 16 = 72dp**, and the second names it "FAB clearance" in a comment. `HomeScreen:121` passes `Space.md` = 12 and `ArchiveScreen:155` / `:195` pass `Space.sm` = 8 — ordinary breathing room, not clearance. Exactly two screens draw a FAB, `all-trainings` and `all-exercises`, and they are the same two. So the 88 lands on an existing 72, not on nothing, and home and archive need no change at all.

> **Carried to the code PR (the only code change this gate found).** `AllTrainingsScreen:171` and `AllExercisesScreen:74`: `bottom = AppDimension.heightLg + AppDimension.screenEdge` is **56 + 16 = 72** and omits the **leading 16** — the gap between the last row and the top of the FAB. Both become `screenEdge + heightLg + screenEdge` = **88** (§26, "Add action"). Two call sites, +16dp each, no other screen affected. Recorded here rather than applied: this PR ships no code. **[V]**

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
| B6 | rust on raise (dark) = 3.28 vs 4.5:1 for destructive text | step-3 measurement | **resolved — by moving the foreground, which is neither option this entry listed.** Not "darken the backing" and not "forbid destructive text on `raise`": `d3f7a3e0` (the v3 palette repaint) shipped `DARK_RUST` at **`#DF714B`** and has carried the reasoning in its KDoc ever since — `#C4574A` met 4.5:1 on *no* dark surface in the palette (base 4.46 / sec 4.16 / field 3.93 / slab 3.59 / **raise 3.28**), so the floor was lower than the one surface this entry flagged, and the foreground moved rather than the backing. **The 3.28 was measured on `#C4574A`, which the app has never shipped in dark** — the value is right for the colour it names and that colour is not in the build (§25 B19). The pair is declared `TypeSlot.BODY` → `NORMAL_TEXT` = **4.5**, and the gate's only comparison is `ratio >= case.declared.typeSlot.threshold` (`ContrastGateTest.kt:61`), so the threshold is not being chosen to suit the pair — §3.3 does not apply here. Verified green, executed not cached (`51 actionable tasks: 51 executed`). **Recorded because it will matter later: the pass has no headroom whatsoever.** `status.error` on `surfaceTier4` measures **4.500986 against a 4.5 requirement** — the gate prints `4.50:1 (needs 4.5:1)` and names it the **tightest dark pair in the palette**. One byte in either direction fails: `#DE714B` → 4.479, `#DF704B` → 4.471, `#DF714A` → 4.499. Anyone nudging `rust` or `raise` in dark breaks five named consumers at once — `ExerciseEditScreen`, `FinishConfirmDialog`, the settings rows, `AppButton.Destructive`, `AppConfirmDialog`'s banner — and the gate will say so, but only a reader who knows *why* the value is exactly this will know it was not slack to spend. **[V]** |
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
| B18 | **The palette has no role for *text* on a `--rust` fill. It does have one for a glyph.** Measured with the gate's own arithmetic (`WcagContrast`, solid-on-solid, no compositing): dark `#C4574A` — `max` **3.98**, `base` **4.45**, `slab` **3.58**; light `#B03B2E` — `max` **3.17**, `base` **5.57**, `slab` **5.98**. A graphical object takes 3:1 (§3.2 / WCAG 1.4.11), so `--base` on `--rust` clears it in both themes with **one declaration and no theme override** — that is the destructive FAB's glyph, and the mockup now draws it that way; the override the first draft needed is deleted. Text takes 4.5:1, and **nothing in the dark palette reaches it**: `base` at 4.45 is the closest and misses. That is the narrowing — as first recorded this read "the palette cannot paint this", and measured it cannot paint *text*. `max`, note, carries its own **7:1** (§3) and is not a candidate either way. Two hazards on record: the pre-measurement estimate for light `max` was "roughly 2.5:1" against an actual 3.17 — 0.67 low, and low in the direction that changes which threshold the argument turns on, a fourth instance of §0.4; and dark `base` is a **hundredths-at-threshold** case of exactly the kind §27 names — the exact ratio is 4.45845, which the gate truncates to 4.45 and ordinary rounding reads as 4.46. Both land on the fail side of 4.5, so nothing turns on it here, but **the gate's quantised arithmetic is the instrument of record**, not a hand calculation on either side. `molten`'s four-slot shape is still the shape of the fix, and text on rust is still unpaintable in dark. **But nothing draws text on a rust fill today** — the only candidate was the count this arc rejected — so B18 blocks nothing and is filed **latent**. **Wake condition restated, wider and cheaper to check:** it wakes on any solid `--rust` **fill** being drawn at all, not on a label being placed on one. Measured on the contract, there are currently **zero** such surfaces: `.fab.del` was the only solid rust fill in the drawing and the D2 amendment **has removed it**, and the three remaining `--rust` uses are text colour (`.srow.rust .srow-t`), a dashed border (`.nu`, itself undrawn) and a frame border (`.clash`). A fill is what a reader can see arriving in a diff; a label placed on one later is not. The measurements below are the reason this stays latent rather than closing — they are what any future rust fill would need, and re-deriving them is the expensive part. That is the opposite of **B6**, which is live and has named consumers: `status.error` and `setType.failureForeground` are *declared* against all five surface tiers including `surfaceTier4` — which is `raise`, the very surface B6 names — for `ExerciseEditScreen`, `FinishConfirmDialog`, settings rows, `AppButton.Destructive` and `AppConfirmDialog`'s banner. A reader comparing the two entries should be able to see that one has consumers and one does not. **[V]** | shell mockup | **latent — no consumer; wakes on any solid `--rust` fill being drawn at all. Zero such surfaces in the contract after D2** |
| B19 | **The mockup draws colours the app cannot produce, and this entry originally undercounted them.** Dark `--rust` is `#C4574A` in the drawing and `#DF714B` in the build. The code side recorded the move when it made it — `d3f7a3e0`'s KDoc says so in as many words ("This is #DF714B, not the spec's #C4574A, and the difference is not taste") — and **three documentation sites never followed**: §2.1's dark-table row for `rust`, still reading `#C4574A` with the role "destructive"; `--rust:#C4574A` in `pass2d.html`, which is the appearance contract the eight derived screens are to be built from; and **§3's "`rust` on `raise` in dark = 3.28", which carries a [V]** — a claim marked verified-by-execution, measured against a colour that was never in the build. §0.3 warns that citations decay; this is one that decayed *while wearing the mark that says it did not*, which is the more expensive failure of the two, because the mark is what stops a reader re-checking.
| B20 | **A tag chip's selected and unselected states resolve to the same colour, in both themes.** `AppTagChip.kt:76` picks `accentTintedBackground` when selected and `surfaceTier4` otherwise; `AppColors.kt:448/454` both map to `DARK_RAISE` and `:506/512` both to `LIGHT_RAISE`, so the two states render an identical fill. The only surviving signal is the label (`:94`, `textSecondary` → `accentTintedForeground`). The contract moves three things at once — `.tag` `--sec` → `.tag.on` `--raise`, label → `--max`, **and** a `--hair-s` border (`pass2d.html:75-76`) — so the drawn distinction is a fill change the code does not make. Not screen-local: every `AppTagChip.Selectable` in the app has it | all-trainings mapping, G2 | **open** |
| B21 | **A failed bulk archive surfaces nothing and leaves the dialog up.** `ClickHandler.processBulkDeleteConfirm` (`:117-147`) calls `launch(onSuccess = { … })` with **no `onError`**, and both the state reset (`selectionMode = Off`, `pendingBulkDelete = null`) and the snackbar live *inside* `onSuccess` — so on failure the user gets no message, selection mode stays on, and `pendingBulkDelete` stays non-null with the confirm dialog still showing. **Same shape as B17** (`loadChart`/`processInit` pass no `onError` and `launchDefault` defaults it to `{}`), which makes it the second instance of one defect class rather than a screen quirk. The shell draws no general error surface either — `.perr` is a paging tail only | all-trainings mapping, G6 | **open** |
| B22 | **First load renders a blank screen.** `isEmptyAndIdle()` (`AllTrainingsScreen.kt:195-199`) requires `refresh`, `append` **and** `prepend` to be `NotLoading`, and the empty state is gated on it (`:74`). During the first page load `refresh is Loading`, so the list has no rows **and** the empty state is suppressed: nothing at all is drawn. Nothing is drawn in the contract for this state either — `.pfoot` is an append footer inside a list that already has rows — so it is absent on both sides | all-trainings mapping, G7 | **open** |
| B23 | **The permanent-delete confirmation on all-exercises is unreachable.** `AllExercisesStore.State.pendingPermanentDelete` is read in three places and written to non-null in **none** — every write in the repository sets it to `null` (`State.init`, `ClickHandler.processConfirmPermanentDelete`, `processCancelPermanentDelete`), there is no `OnPermanentDelete` action, and it is a store field so no other feature can set it. Behind the dead gate sit a dialog, four strings, two `Click` actions, a `Confirm`-tier haptic and `AllExercisesInteractor.permanentlyDelete`. Restoring the trigger is a **behavioural** decision, not a transcription one: the strings promise "This exercise has no history and isn't used", a precondition nothing currently evaluates, and the contract draws no affordance for a permanent delete anywhere. **Second finding, and it is the more general one: the two `ClickHandlerTest` cases covering this path pass by constructing their own precondition.** They set `pendingPermanentDelete` to a hand-built `PendingDelete` and then assert the handler behaves — which it does, faithfully, on a state the application cannot produce. **A test that builds its own precondition cannot tell you whether the precondition is reachable**, so it certifies a surface no user can arrive at while counting as coverage on every report. Same family as a gate that passes by inspecting nothing (§27, the `FROM-CACHE` liveness claim and the golden that photographs a state its name does not describe): in each case the artefact is green, the mechanism ran, and the thing it appears to vouch for was never in scope. The cheap discriminator is to ask what *writes* the state the test reads — here, nothing does, and one `grep` said so | all-exercises delta mapping, 2.3 | **open — decision** |

**B21 and B22 have a second consumer.** Both were filed against `all-trainings` and both reproduce verbatim in `all-exercises`: `ClickHandler.processBulkDeleteConfirm` there has the same `launch(onSuccess = { … })` with no `onError` and the same state reset and snackbar *inside* `onSuccess` (B21), and `AllExercisesScreen.isEmptyAndIdle()` is the same predicate gating the same empty state (B22). Neither entry changes — what changes is that neither is screen-local, so neither can be closed by a fix on one screen. B22's answer is the same drawing D7 needs (see the all-exercises delta mapping, §6).

**B19, continued.** **"Scope is exactly one token in exactly one theme" was itself an unverified claim, and it was wrong.** That line was "measured, not assumed" only against the dark `:root` block — the light block was never walked the same way, and the light audit below found two more sites the first pass called clean. The corrected count is **three drifted tokens across two themes**, not one in one:

- **Dark `rust`** — as above. `#C4574A` mockup, `#DF714B` shipped.
- **Light `meta`** — `#69727C` mockup, `#596169` shipped (`LIGHT_META`, `AppColors.kt`). The first pass filed this as a **documented deliberate divergence**, grouped with `dim` and `hair-s`, on the reasoning "moved because the drawn value was unmeasured." That reasoning proves the opposite of what it was used for: an unmeasured value that the app then measured and corrected, without the correction reaching the drawing, is the *definition* of drift used everywhere else in this entry — not a different, safer category. The app's own KDoc calls it by name: `LIGHT_META`'s comment reads "deliberately not the mockup's `#69727C`" — deliberate on the code side, silent on the mockup side, which is exactly rust's shape. `dim` and `hair-s` stay correctly classified: both are **non-mappings** (no `AppColors.kt` constant is "meta's fourth step" or "hair-s, but a control outline" — the roles were retired or rerouted, not moved and left behind), confirmed in both themes this pass (`textDim` aliases `*_META` in both `provideDarkAppColors`/`provideLightAppColors`; `borderDefault`/`borderStrong` read `*_CONTROL_OUTLINE` in both, `#627587` dark / `#748396` light, neither matching either theme's `--hair-s`).
- **Light `molten` (text role)** — `#C2410C` mockup, `#BE3E0C` shipped (`LIGHT_MOLTEN`). Found by asking the question B18 raised and B19 didn't: does the mockup's flat `--molten` (used as text colour, `.prtag`/`.label`/`.data-s`) correspond to anything, given §2.3's four-part role? It does, and it drifted for a documented, measured reason: `LIGHT_MOLTEN`'s KDoc — "a 9-unit nudge off the spec's `#C2410C`" — records that `#C2410C` clears 4.5:1 on the surfaces you'd guess and **fails on the one that matters**, `PersonalRecordCard`'s actual composited backdrop (wash-over-page, 4.325 against a 4.5 obligation), while `#BE3E0C` clears every real backdrop (4.52–5.41). `LIGHT_MOLTEN_BORDER` was **not** touched by this fix and still resolves to `#C2410C` + alpha, byte for byte matching the mockup's `--molten-br` — so only the text role drifted, not the token family; a selector that checked "does any `--molten*` var match some `LIGHT_MOLTEN*` constant" would have missed this by matching on the wrong sub-role.

So: **two confirmed drifts, not one** (dark rust, light molten-text), **one reclassified from non-mapping to drift** (light meta), **two confirmed non-mappings** (dim, hair-s, both themes). Consequences of the corrected numbers, computed on the shipped values rather than inherited: B18's ratios were computed on `#C4574A` and stay as originally recorded there — B18 is latent and restates on its own wake, not here — but §26's "FAB in selection mode" row, which cited B18's numbers directly, is restated on `#DF714B`: `--base` **6.11** (not the **6.12** this entry itself originally wrote — that used ordinary rounding on 6.119648, not the gate's floor-truncation; corrected here, another instance of the class §27 already names), `--max` **2.90**, so the locked decision — glyph in `--base` — **survives and improves**, while `--max` would now fail the 3:1 a glyph carries. §26's "Bottom navigation" row, which cited the mockup's own then-current `--meta`, is restated from 4.31 to **5.55** on `#596169`. **Nothing saw any of this before this pass** — `shell_gate.py` read HTML, the contrast gate reads `AppColors.kt`, and every drift lived in the seam between them. **[V]** | shell gate, stage-5 token audit; light-theme re-audit, token parity PR | **resolved — token parity PR.** All three sites corrected in `pass2d.html` and §2.1/§2.2; §3's stale `[V]` rewritten to say plainly that it decayed while wearing the mark; the seam is now gated (`shell_gate.py` check 9: every `#rrggbb` in the mockup's `:root`/`body.light` resolves to an `AppColors.kt` constant, exceptions — `dim`, `hair-s` — named individually with citations, not a loosened rule). No palette *value* changed; this PR made the drawing match what already ships. |

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
| Bottom navigation | **Both existing bars die, one replaces them.** `--sec` track under a hairline, active = lifted slab pill (`--slab` + `--slabtop`), the system's only drawn marker for "selected among siblings" (`.tabs .ind`, `.mseg button.on`, `.card.open`). Height 60, matching `.topbar`. Icons for trainings and exercises are the drawn empty-state glyphs verbatim; **home is the one new mark**. Inactive icons are **`--meta`, not `--dim`**. Collapsed from three drawn variants to this one on the stage-5 gate-0 device pass: the reviewer picked this variant because the light theme read short of contrast, and named the missing track as the cause. **The reason was right, the diagnosis wasn't** — measured, `--dim` on this track is 3.64 dark / **2.33 light**, below the 3:1 a glyph carries, and on the untracked variant's `--base` it is 2.46 light, also below: the track never touched the number, because every tier step in this palette sits 1.05–1.16 apart and separation is carried by the hairline, not by contrast. `--meta` clears both: 5.98 dark / **5.55 light** (restated on the shipped `#596169`; the mockup's own `#69727C` gave 4.31, see B19). The two rejected variants — a lifted pill with no track, and a caption-only bar with no container at all — are undrawn; this row is their recorded reason, not the track | mockup `#s-nav`, device pass (stage-5 gate 0) |
| Nav pill motion | Slides 340ms `--e-out`, and stretches by travel distance: `scaleX` peak = `1 + 0.30 × (Δ / bar width)`, `transform-origin` on the leading edge so the tail lags. Offset and stretch live on different elements — one `transform` cannot carry both. Legal under the overshoot rule: the pill encodes no value. Now built on the `--sec`-track variant, which is the better citation of the two it has stood on: a track holding a lifted thumb is literally `.tabs`, where this sliding indicator was drawn and built first | mockup `#s-nav`, §5, §26 overshoot row |
| Bottom chrome coexistence | A screen that draws `.dock` (extraction §1.8, §3.6 — this document has no §1.8) **does not show the nav bar**. Stacked they read as two panels and cost 152px | mockup `#s-nav` |
| List row | One skeleton — 88dp ruled row, name clamped to two lines with ellipsis, single-line meta, chevron — carrying four different payloads. `min-height` holds every row to one size; neither a long name nor extra tags move it | mockup `#s-list` |
| Meta-line order | **Information first, tags last.** The line does not wrap, so what truncates is always the tail, and the tail is tags. Exercise type is the first token. In-row tag chips **rejected**: `.tag` as drawn (14px, 8/13) does not sit in an 88dp row, and a smaller chip would be a new treatment. The full tag set lives in `TagFilterRow` above the list — the row confirms tags, it does not enumerate them | mockup `#s-list` |
| Leading media in list rows | **None.** `imagePath` and the type icon stay on the detail screen; the row has no media slot | mockup `#s-list` |
| Selection mode | Selected row rises to `--slab` + `--slabtop`; the mark is the check glyph from `.mitem.on` in the picker sheet, the only drawn "selected" mark. Unselected rows **lose the chevron, not the slot** — in this mode a row does not lead anywhere and has nothing to promise, but the trailing slot is **fixed-width** and keeps its place. The promise argument is unchanged; what the amendment removes is the reflow. Collapsing the slot moved every row on entering selection mode and one row on every toggle, against a two-line clamped name — measured on the drawing at the 452px shell, `.row-body` came back **338 / 336 / 370px** for chevron / check / nothing, and with the slot it is **336px across all 16 rows**. The slot is 20px, the wider of the two glyphs (`.chk`); `.chev` centres in it. Amended after the selection frame was drawn without the width question being decided. The topbar is replaced whole: count plus actions | mockup `#s-list` |
| Add action | FAB retained. The bar's inset stays where it already is — global, on the navigation host. The list adds only FAB clearance, `16 + 56 + 16` = **88**, and only on the two screens that draw a FAB. Without it the tail sits under the button permanently, and on a paged list the tail is live | mockup `#s-list` |
| FAB in selection mode | **Morphs to the archive action, icon only** — squircle 18 opens into circle 28, and that is the whole morph: the fill stays `--max` and the content `--base`, the FAB's ordinary treatment. **Amended.** This row read "morphs to **destructive**", with a `--rust` fill, and that word was the origin of the error rather than a description of it: the action is archive (`archiveTrainings` → `BulkArchiveResult`; "will move to archive. Restore from Settings → Archive"; "Reversible · history preserved"), archive is not destruction, and §1 makes `rust` mark destruction only — so the fill promised irreversibility for a reversible act, and the trash glyph followed the word. The glyph is **archive, not trash** — drawn as `M4 8h16M6 8v11h12V8M10 12h4` on class `.garch`: lid line, body, pull, the same three-subpath grammar as the deletion mark it replaces, so the morph reads as a substitution and not a change of vocabulary. Stroke, `fill:none`, inheriting `.fab svg`'s 2.1 and `.icon-btn svg`'s 1.7 — **no filled mark exists anywhere in this drawing**, which is why matching `feature/archive`'s filled `Icons.Filled.Inventory2` was rejected: it would import a filled Material glyph into a system that has none, the same category of import the nav-bar rebuild exists to remove. `feature/archive` is **owed the correction the other way** — recorded, not done. The same correction applies to the selection topbar's trash and to `Icons.Filled.Delete` on both `all-trainings` and `all-exercises`: one correction applied twice, no divergence between the screens, no ledger row owed for it. The shape morph was always what announced the mode change; the colour was carrying a claim the action does not support. Shape morph is the inverse of the drawn set-closure grammar (circle → 13dp squircle, 38→42, §9); spring legal, it encodes nothing. `--base` on `--max` measures **17.77 dark / 17.69 light** (the gate's own arithmetic, reproduced first against this row's former 6.11/5.57 before being trusted on a new pair), far over the 3:1 a glyph carries — and it is no longer an inversion *borrowed* from `.btn`, it **is** `.btn`'s treatment, unchanged from the resting FAB. **No count on the button**, and that rejection keeps **both** of its grounds. The surviving one is stronger than when it was written: the selection topbar already states the count, and a count before an irreversible act is arguable redundancy where before a reversible one it is plainly unnecessary. The contrast ground beside it — the count is text, 4.5:1 binds, and no dark-palette entry clears it on rust — existed only because the fill was rust and goes with it; `--base` on `--max` clears 4.5:1 with room. The rejected alternative — a pill widening on the `.tabs .ind` grammar to carry «Удалить N» — is recorded here and **drawn nowhere**, per the rejection rule below | mockup `#s-list`, §1, §9, B18 |
| Drawn rejections | **A screen section draws only what ships; rejected alternatives live in this ledger with their reasons.** An append-only ledger is the one of the two that a rewrite cannot quietly lose (§25's own preamble), and a contract that draws both answers stops being a contract. Applied to the three rejections this shell carries: the count-bearing FAB (reason in the row above, and B18) and in-row tag chips (reason in "Meta-line order") are recorded and undrawn — but the **topbar `+` is undrawn with no reason on record anywhere.** "Add action" says the FAB was retained; nothing says what the `+` lost on. That reason is owed, and its absence is exactly the failure this rule exists to prevent — deleting the drawing is only half of the rule. The single section that drew alternatives was `#s-nav`, which is **not a screen** — it was a labelled comparison of one component over identical content, and said so in its own hint, until the device review it existed for collapsed it to the chosen variant (track and pill; reason in the "Bottom navigation" row) | mockup, §0.1 |
| Paging tails | Three states, two drawings. Loading = footer spinner. Exhausted = **no footer at all** — "end of list" states only what is already visible. Error = reason plus **Повторить**, because a silently truncated list is indistinguishable from a finished one | mockup `#s-list` |
| Haptics | No new vocabulary; the repo already uses four constants. `SegmentTick` on nav tab change (already shipped), `LongPress` entering selection, `ContextClick` toggling an item, `Confirm` after a **confirmed** destructive action. The FAB morph fires nothing — it follows the long press that already fired, and two in a row read as a fault. Page load fires nothing — the user did not ask for it | mockup `#s-list` |
| FAB over a topbar `+` | The selection topbar is replaced whole — close, count, actions. Put "add" in the topbar and "delete" lands there too, immediately beside the control that exits the mode: an irreversible action adjacent to a dismiss control is a misclick surface. The FAB keeps the primary action in one fixed place across both modes, and the morph is what announces the mode change at that place. Cost is 88dp of list clearance on two screens, which the code already very nearly pays. **Reasoning found after the decision, not the reason it was taken** — at the time the FAB was retained because the morph requirement presupposed one | mockup `#s-list`, §26 FAB rows |
| Empty state | **The drawn strings and both CTAs are contract, not suggestion.** `#s-empty`'s first `.empty` is all-trainings': glyph tile 52×52 radius 16 with a **1px dashed** `--hair-s` border, mark `M4 12h3l2.5-7 5 14L17 12h3` at 22px/1.6 — **byte-identical to the nav bar's trainings icon**, which is the coupling the Bottom navigation row already relies on, so changing one changes two; headline «Здесь появятся тренировки» 18px/600 `--max` → 19 rung; sentence «Собери шаблон заранее или начни пустую и добавляй упражнения по ходу.» 14.5px `--meta`, `max-width:274px` → 15 rung / 272dp; and **two** stacked actions, primary first, 50px tall, radius 16 — «Создать тренировку» (`.btn`) and «Начать пустую тренировку» (`.btn.ghost`). Confirmed as contract because it was governed by drawing alone: nothing in this ledger named it, §13 delegates only "placement per screen", and the extraction's §4.8 documents the **chart** variant while citing a `§19` that does not exist. Placement stays delegated; the strings, the mark and the pair do not | mockup `#s-empty`, §13 |
| Tag filter band | **Drawn above the list, and it is what makes the row's tag rejection hold.** `.tagrow` — `display:flex`, gap 8, `12px var(--gutter)`, `overflow-x:auto` with the scrollbar hidden, and a `--hair-s` bottom rule, the same hairline grammar `.bulk` / `.perr` / `.nb.track` use to separate bands. Chips are `.tag` **as drawn** (14px, `8px 13px`, radius 10, `--sec` on `--meta`; `.on` → `--raise` on `--max` with a `--hair-s` border) — the treatment the Meta-line order row cites when it rejects in-row chips, so the two rows now stand on the same drawn object. **Selection is MULTIPLE, and the drawn precedent is not.** The treatment is borrowed from `#s-chart`'s range picker, whose `pickTag` is a radio group — single-select — and that grammar **does not transfer**: `activeTagFilter` is a set, two chips are drawn `.on` together, and a reader must not infer single-select from the treatment's origin. Drawn multi-select deliberately, because a screen section draws only what ships. The shipped chip is 11sp caption in a 6dp shell and both of its states resolve to one colour (B20) — neither the drawn size, the drawn radius, nor a drawn distinction | mockup `#s-list`, §26 Meta-line order, B20 |

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
- **A gate that reads a file is not a gate that the file works.** The shell gate checked tags, tokens and hex literals and passed green while `#s-nav`'s indicator measured **0px wide** — it is placed once at load, and it was placed while its own section was `display:none`. It had been correct only because that section happened to be the default screen; the moment another section took that role the demo died silently and not one check noticed. §10.2 exactly — the gate proved what changed, not that anything rendered. Resolved by a headless render probe (Chromium `--dump-dom` after a scripted click), which is the cheapest instrument that can see this class at all, and which also turned five drawn states into measured ones (row heights 88/88/88/88, meta overflow 97px, both FAB morphs, the locked FAB's tokens in both themes, `slabtop` resolving per theme). **[V]** **The probe has its own blind spot, and it reads as a finding rather than as noise.** ~~Under `--virtual-time-budget`, *geometry* transitions settle (width 56→172, radius 18→28 both read correctly) but *colour* ones do not.~~ **Corrected on measurement — the original claim above was inferred from which readings happened to come back right, and it is wrong in the direction that matters: it declares a whole class of reads safe.** What is actually true: **every transitioned property is affected, geometry included.** Measured on the shell gate's own probe — `border-radius` still reporting `18px` **8.8 virtual seconds** after the class that sets it to `28px` was applied, and a 340ms `transform` transition still reporting `matrix(1,0,0,1,7.6446,0)` **twelve virtual seconds** in, while the inline target had long since read `translateX(273px)`. Root cause isolated rather than guessed: **animation frames do not run** — a `requestAnimationFrame` self-loop ticked **12 times in 22 virtual seconds** — so a transition is sampled once, on demand, during a style recalc, and then never advances. The corollary is the sharp edge: **any `getComputedStyle` / `offsetWidth` / `getBoundingClientRect` read taken before or during the mutation arms the transition and freezes it at that sample**, poisoning every later read of that property. Whether an untouched reading comes back correct is therefore a matter of incidental read ordering — which is exactly why the earlier "geometry settles" reading looked solid and was not. Two readings were wrong this way, each looking like a real defect, until `style.transition='none'` isolated it; and the danger is that the value returned is the element's true *pre*-interaction style, so it reads as a legitimate measurement rather than as an instrument failure. **Kill the transition on every property you intend to read, before you read a baseline — not colour only. 10/10 sequential and 8/8 parallel runs reproduce both halves.** **[V]** (corrected by `shell_gate.py`, stage-5 gate 0)
- **A gate that runs after the merge certifies; it does not gate.** `f52462c7` put the shell mockup on `dev` by direct commit — one parent, no PR — and the gate was written to read its baseline from `dev`, so by the time it ran its baseline **was** the thing under test: **0 added lines inspected instead of 178** (`git diff --numstat 9139d8c8 f52462c7 -- documentation/mockups/pass2d.html` → `178 3`; an earlier revision of this line said 146, which reproduces nothing — re-measured rather than left standing, because an unreproducible number in *this* section costs more than the same number anywhere else), and two of six checks vacuous by construction. Pin the baseline to a ref taken *before* the change (keep `BASE`/`TARGET` parameterised, and put the reason in the script header so the next reader does not simplify it back), and run the gate **in the PR, before the merge**. That ordering is the only one in which a gate is a gate. **[V]** (`.github/workflows/mockup_gate.yml` is what now performs it — §10.6. Its baseline is the merge-base against the PR's **base branch**, which satisfies the rule this bullet is really about, *the baseline must not already contain the change under test*, without pinning a ref by hand; the script header now distinguishes that from the `BASE=dev` failure so the next reader does not read the warning as forbidding both. "Two of six checks" is the count as of `f52462c7`; the script now carries nine.)
- **A determinism check whose runs are not separated in time proves nothing.** Two runs of a font-instancing command produced the identical hash and were read as "byte-reproducible"; they were reproducible only because both landed inside the same wall-clock second, and the line meant to pin the timestamp was a no-op. Separate the runs by more than the granularity of whatever varies. **[V]** (fonts PR, B3)
- **A negative control asserted only on its exit code proves that something failed — not that the intended detector fired.** Sibling of the compile-and-discriminate rule above: that one governs the mutation, this one governs the assertion. `shell_gate.py --target f52462c7` is red for **three** reasons at once, only one of which is the point: check 6 (`#s-list` is the default screen at that ref), check 7 (the zero-width nav pill — the escape the control exists to reproduce), and check 9 (historical palette drift, expected there and documented as allowed). On the mockup gate's **first real CI run the browser hung and never returned**, so check 7 produced no verdict at all and exit 1 arrived from 6 and 9 alone. An `if [ "$STATUS" -ne 0 ]` assertion would have certified a dead detector as a live one — the precise failure the control is there to make impossible. The same control had already been weakened once before, when an id-based probe selector turned "the pill measures 0px wide" into "no pill element found": same colour, weaker proof. So the assertion pins the reason, not the colour — exit 1 **and** a `[FAIL] 7` row **and** the `width=0px→0px` measurement. Generalised: **when a negative control can go red for more than one reason, assert which one, or it decays into a liveness check on the exit code.** **[V]** (mockup gate CI, run `30529700516` — the witness)
- **The instrument's packaging changes its answer, and unpacked Chromium does not answer at all.** Diagnosed on the runner because it could not be reasoned about. Same machine, same flags (`--headless --disable-gpu --no-sandbox --virtual-time-budget=8000 --window-size=420,900 --dump-dom`), each candidate given 45s: image `/usr/bin/chromium` **150.0.7871.0 → HANG**; Chrome for Testing **150 → HANG**; Chrome for Testing **151 → HANG**; Chromium snapshot **153 → HANG**; image deb `google-chrome` **150.0.7871.128 → OK, 10.7s**; in-job deb **151.0.7922.71 → OK, 1.4s**. **Packaging, not version** — the 151 deb completes where the 151 zip hangs; the hanging builds sit in GCM registration and never reach `--dump-dom`. `apt-get install chromium` is not an escape either: on noble it is a transitional package pulling the snap, and it overwrites the image's working symlink on the way. **And `chrome-headless-shell` completes while quietly answering a different question:** it lays the page out at **113px** where every deb and a local distro Chromium agree on the documented **129px**, and checks 7/8 assert "nonzero and tracking", never exact pixels — so it substitutes silently and passes. An appearance gate read through a browser that renders the page differently is not measuring the drawing; §10.2's differential-guarantee argument applies to the *instrument*, not only to the baseline. **[V]** (mockup gate CI, runs `30530079592` / `30530450055`)
- **A base retarget inside a squash-merged stack is always conflicted, and a conflicted PR has no recomputable `refs/pull/N/merge`.** `pull_request` reads the workflow definition *from that ref*, so while a PR is conflicted **no run of any workflow can be created for it** — which means a `types: [… edited]` guard, added precisely so a base retarget re-measures the baseline, cannot fire in the one situation a stack reliably produces. Measured merging #193 → #194 → #195: after retargeting #195 onto `dev` with **no push** (head unchanged at `3cc4360e`), the API reported `mergeable=false mergeable_state=dirty rebaseable=false`, and `refs/pull/195/merge` stayed frozen at parents `d9d06c51 3cc4360e` — stamped `10:09:03Z`, against a `12:25:56Z` retarget. The guard is **not** broken and the distinction matters: `edited` is live, witnessed by runs `30533558625` and `30533717666` sharing head SHA `3cc4360e` two and a half minutes apart with **no push between them** (a PR-body edit). It covers a clean retarget. The conflicted case is covered instead by `dirty` blocking the merge, and by every route out of `dirty` — rebase force-push, "Update branch" — firing `synchronize`, which does re-run the gate. **Record that mitigation as incidental, because it is:** it rests on platform behaviour this repository neither controls nor tests, and it protects the *merge* while the PR page still displays a green measured against a baseline that no longer applies. **[V]** (mockup gate CI, stage-5 stack merge)
- **Merging with `--delete-branch` while a dependent PR still points at that branch CLOSES the dependent instead of retargeting it.** GitHub's auto-retarget loses the race with the deletion, and the result is a deadlock: a closed PR **cannot** be retargeted, and it **cannot** be reopened while its base branch is gone. Recovery is to push the old base SHA back to recreate the branch — still reachable as the dependent's parent, so nothing is lost unless that has also been pruned — then reopen, retarget with `gh pr edit --base`, and delete the branch again. Measured on #194. **In a stack, delete a branch only after the dependent has been retargeted.** Note the corollary: *not* deleting means no auto-retarget happens at all, so the retarget becomes a deliberate manual step rather than something to wait for. Squash compounds both — the dependent still carries the pre-squash commits and must be rebased (`git rebase --onto origin/dev <old-parent-tip>`, after confirming `git diff <old-parent-tip> origin/dev` is empty), and its pre-rebase green was measured on a SHA that no longer exists. **[V]** (stage-5 stack merge)
- **A golden image gates only what a single static frame contains.** Anything whose evidence needs a second frame, a gesture or a scroll is invisible to it, and will pass a mutation silently — the gate stays green because the change never reaches a pixel. **The witness is the 88dp list clearance:** `contentPadding.bottom` moves nothing in an unscrolled frame, so reverting it to the old 72 left **every golden byte-identical** — and the one number §24 carried into the code PR turned out to have no gate at all. Measured twice, at two suite sizes, because the count moved between them: **28 of 28 at `2f436e81`**, the suite as it stood when the mutation was run, and **30 of 30 at `443f4db9`** (`tests=30 skipped=0 failures=0` in the golden suite's own results XML, in the mutated run). The two added in between are `rowClamped`'s pair — recorded *because of the same proof run*, which caught the two-line clamp untested for the same reason. At 30 the build does go red, and that is the point: the red comes from `AllTrainingsClearanceTest`, not from a golden. The visual gate is blind at either size. It was found by mutating, not by reading — a golden set that looks exhaustive can be blind to a value it renders every time. **What else sits in the class on this arc:** scroll behaviour of every kind, including whether a tail is reachable above the FAB; the paging footers, which depend on a load state one frame cannot enter; and **motion generally**, where §10.2's transient pair pins the two endpoints and says nothing whatever about the transit between them. **The mitigation is to lift the value into a named constant and assert it directly** — `LIST_BOTTOM_CLEARANCE` with `AllTrainingsClearanceTest`, which asserts both the 88.dp and that it is composed of the three drawn parts rather than a literal.

**AMENDED — the mitigation has two halves, and this entry originally prescribed only the first.** For the paging footers it said: lift them out of the `LazyListScope` block so they can be photographed as subjects. That is necessary and it is not sufficient, and the shortfall is not theoretical — it was **measured on `all-exercises` after following this entry to its stated end.** With both footers photographed and 30 goldens green, deleting `is LoadState.Error ->` from the screen's tail block left **all 30 byte-identical**, and `all-trainings` had the identical hole. A silently truncated list was one deleted line from being indistinguishable from a finished one again — the precise failure the error footer exists to prevent, undone without moving a pixel.

The reason is the mechanism, not an oversight in the fix: **splitting a surface out so it can be photographed is exactly what makes its selector invisible.** Before the split, the footer and the condition that shows it were one expression; after it, the picture covers the drawing and nothing at all covers the branch that chooses it. The split does not create the blindness, but it moves the untested part somewhere a golden set can look complete without it.

So the mitigation reads, both halves together: **split the surface out so it can be photographed, AND lift the selector into a named, directly-asserted value — because the split is what makes the wiring invisible.** The witness is `pagingTailKind(LoadState): PagingTailKind` with `PagingTailKindTest`, which is the same shape as `LIST_BOTTOM_CLEARANCE` with `AllTrainingsClearanceTest`: a pure function out of the composable, `pagingTail` reduced to dispatch, and every branch asserted — **including the absence**, since "no footer" is the one outcome a missing branch produces by accident and therefore the case where a green test proves least unless it is written deliberately. Generalised: for any state-driven surface, a subject golden covers *what it looks like* and never *when it is shown*. Those are two gates, and only one of them is a picture.

**The failure mode this entry exists to name is assuming the visual gate covers the visual.** It covers the visible-in-one-frame, which is a smaller set, and the difference is exactly where a mutation survives. **[V]** (all-trainings rebuild, stage 5 group 1; amended by the all-exercises rebuild, stage 5 group 2)

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
- **A whole-screen golden named for a state can photograph a different state entirely, and it will look like coverage.** `screenEmpty` on both list screens shows a **blank** screen — top bar, tag band, FAB, nothing between — not the empty state. `isEmptyAndIdle()` wants `refresh`, `append` and `prepend` all `NotLoading`, and in the single frame Paparazzi renders `refresh` has not settled, so the rows and the empty state are suppressed *together*. Found by mutation, not by looking: swapping the empty state's glyph and deleting its CTA both went red on the **component** golden while the screen-level picture named after it sat byte-identical through changes to the thing it was named for. It is the same root as the bullet above — a single unadvanced frame cannot enter a paging state — and the same lesson one level up: **the name of a golden is a claim, and a claim in a filename is never checked by anything.** Renamed to `screenNoRows` on `all-exercises`; on `all-trainings` the name was left and the suite KDoc's claim corrected, because churning golden filenames under a running device pass buys nothing. Incidentally, this is what **B22** looks like, and the registry entry is now backed by a picture rather than only by a reading. **[V]** (all-exercises rebuild, stage 5 group 2)