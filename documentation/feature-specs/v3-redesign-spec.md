# v3 Redesign — Specification

**Status:** complete. Parts I and II closed.
**Sources:** `documentation/mockups/workeeper-redesign-pass2d.html`, `workeeper-session-v3f.html`.
**Supersedes:** all earlier drafts of this document, including the Russian ones. If you find another copy, delete it — two sources with drifting numbers is the failure mode this file exists to prevent.

---

## 0. Mockup status

The mockups are **reconnaissance, not contract**. They were drawn before the type scale was locked and carry twenty font sizes against the locked six.

| Take from the mockup | Do not take |
|---|---|
| palette, both themes | font sizes — the six-step scale wins |
| motion tokens | raw pixel values — see 0.1 |
| structure and screen hierarchy | CSS shadows, radii, flex gaps |
| component states | units picker, third chart metric, session tonnage — see §11 |

**Consequence to know in advance:** some text will land on a scale step 1–2px away from the mockup. That is correct. Do not file it as a bug during visual review.

### 0.1 Pixels and dp

An earlier draft of this document claimed the mockup frame implied a px→dp scale factor of roughly 1.15. **That was wrong.** The mockups declare `width=device-width`, so 1 CSS px is 1 dp; the 452px frame is a desktop viewport cap sitting above the widest phone, not a device width.

The real work is not conversion but **rounding onto the existing `AppDimension` ladder**. Rule: round to the nearest rung; break ties toward the value that already has call sites. Worked example: the mockup's 20 gutter is equidistant between two rungs and resolved to 16dp, which already had 45 call sites.

Values may also be **derived rather than transcribed** where the mockup's number is a consequence of its parts. The 88dp row height is `2×21 + 4 + 18 + 2×12`, and measurement from golden pixels at 2.75 px/dp confirmed 176 / 242 / 242 px = 64.0 / 88.0 / 88.0 dp.

### 0.2 Every mockup colour is measured before adoption

Two for two so far have failed a locked threshold:

- light `meta` `#69727C` — 3.79:1 on `raise`, against 4.5:1
- `dim` proposed for section labels — 3.91:1 under an 11sp label, against 4.5:1

The mockups were drawn without contrast measurement. **Measure, then adopt.** Never the other way round. Neither failure is visible to the eye — both miss by under 15%, and both were caught only by number.

---

# Part I — Foundation

## 1. Principle

The accent is **brightness, not hue**. A session begins visually muted and brightens as it is completed. Completed = maximum contrast against the background, in both themes.

There is no coloured accent. `molten` marks records only; `rust` marks destruction only. Contrast stops being an accessibility requirement and becomes the load-bearing expressive device — which is why it is gated rather than eyeballed.

Measured on the approved palette: completed content scores **13.07–17.77** in dark and **14.71–18.96** in light against a 7:1 threshold. The signature inversion in light theme worked; the margin is double.

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
| `meta` | `#8B95A1` | metadata |
| `dim` | `#98A0A9` | muted large text |
| `idle` | `#8B95A1` | inactive |
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
| `meta` | **`#596169`** ← changed, see 2.4 |
| `dim` | `#6B7078` |
| `idle` | `#7C858F` |
| `hair` | `rgba(13,17,20,.07)` |
| `hair-s` | `#D2D7DD` |
| `molten` | `#C2410C` |
| `molten-solid` | `#F97316` |
| `rust` | `#B03B2E` |

**Naming note:** these are the mockup's names. The codebase's slots are `surfaceTier0..4`, `accentTintedBackground`, `textPrimary/Secondary/Tertiary` and so on. There is no `AppUi.colors.raise`. Aligning the two naming systems is logged in `tech-debt.md` and is explicitly **not** part of this arc — a rename touching every call site would drown the visual diff it is supposed to make reviewable.

### 2.3 `molten` is a four-part role that inverts by theme

Not one hex. Text, solid fill, background and border — and light **darkens** where dark **lightens**: `#F0A22E` → `#C2410C` for text, `#F0A22E` → `#F97316` for fill.

It appears in exactly two places and nowhere else: the personal-record accent, and the transient wow state (§9). It is not a general-purpose accent. Say so in KDoc, or it will be reused as one.

### 2.4 Light meta deviates from the mockup deliberately

The mockup's approved `#69727C` fails the locked 4.5:1 threshold on three surfaces of five: `raise` 3.79, `field` 4.12, `sec` 4.32. It passes only on `base` (4.56) and `slab` (4.89). `sec` is the most frequent surface in the set, so the failure is real rather than theoretical.

**Resolution: `#596169`.** The worst surface scores 4.88; it passes everywhere unconditionally. Separation from `body` survives — 11.93 against roughly 5.8 on `base`.

Record this reason in KDoc on the token. Somebody will otherwise "fix" it back to match the mockup.

The correction direction is safely asymmetric: the gate asserts a threshold, so darkening further is free and lightening is caught.

### 2.5 Section labels use `textTertiary`, not the mockup's `dim`

Measured: `dim` under an 11sp label gives 3.91:1 against the 4.5:1 an 11sp label owes. Same trap as 2.4. The fifth tier now has a number attached to it.

## 3. Contrast thresholds

The threshold depends on **type size**, not on the pair alone. WCAG grants the 3:1 allowance only for ≥24sp regular or ≥18.66sp bold; `FontWeight.Medium` (500) is not bold.

| Slot | Condition | Threshold |
|---|---|---|
| Archivo Expanded 700, 34 / 26 / 19 | bold ≥18.66sp | **3:1** |
| Archivo Expanded 700, 15 / 12.5 / 11 | — | **4.5:1** |
| IBM Plex Sans Medium, 34 / 26 | regular ≥24sp | **3:1** |
| IBM Plex Sans Medium, ≤19 | — | **4.5:1** |
| `max` on any surface | — | **7:1** |
| `meta` on its own backing | — | **4.5:1** |

**`rust` on `raise` in dark measures 3.28** — below 4.5:1 for a destructive label. Either darken the surface beneath destructive text, or establish that destructive text never sits on `raise`. Whichever is chosen, the contrast map must encode it so it cannot silently regress.

### 3.1 Hairlines carry no threshold

An early draft locked hairlines at ≥3:1, on the theory that killing cards made them the only section separator. **That was wrong.** The markup shows `.sgroup { margin-top: 30px }` plus `.sgroup > .label` — every section has thirty pixels of air above it and a text heading. Sections are separated by space and a label.

`hair-s` divides **rows inside** a section. That is the decorative case, and the measured 1.12–1.52 is legitimate.

**Rule:** a section is separated by gutter and heading. A hairline is decorative. If a future screen makes a line the sole separator, it becomes load-bearing and takes 3:1.

### 3.2 The gate is built from a map, not from a measurement

A measurement yields pairs. A threshold requires the usage. The same foreground/background pair holds 3:1 under 26sp and 4.5:1 under 15sp — one ratio, two verdicts.

Required shape, three parts:

- **(a)** a declared map of `(foreground, background, type slot)` triples, each with the threshold it must meet;
- **(b)** an explicit exclusion list of pairs that provably never co-occur, each with a one-line reason;
- **(c)** a test that mechanically enumerates **all** foreground × surface combinations and fails on any that is neither declared nor excluded.

Part (c) is the point. Without it a new screen adds an unverified pair in silence. A gate that only checks what it already knows about is a comment.

Additional requirements:

- Disabled and inactive colours are **WCAG-exempt** (1.4.3 and 1.4.11 both carve out inactive components). Do not score them, and verify each candidate is genuinely inactive at its call sites before exempting it.
- Role aliasing inflates counts — `primary`/`secondary`/`tertiary` may all resolve to one colour. Report **distinct measurements** alongside row counts.
- Hairlines are excluded with the reason from 3.1, not asserted as passing.

### 3.3 The map's own integrity must be gated

A duplicate key in the roles map is silently resolved by last-write-wins, which means the gate's behaviour is determined by declaration order and **neither line reads like what actually happens**. This has already occurred once: an entry carrying a reasoned 3:1 justification sat dead beneath an unreasoned `DECORATIVE` entry for the same key, and the net behaviour matched neither.

This is more dangerous than an uncovered pair. An uncovered pair fails loudly — that is part (c). A pair whose threshold has been silently substituted passes green with the wrong number.

**Requirement:** build the map from a list of entries and fail at construction on a duplicate key. Fixing the two offending lines is not sufficient; the next duplicate would arrive just as quietly.

## 4. Typography

| Family | Slots | Weights |
|---|---|---|
| IBM Plex Sans | all text | 400 / 500 |
| Archivo Expanded | numerals, timer | 700 |
| IBM Plex Mono | units, metadata | 400 / 500 |

Scale: **34 / 26 / 19 / 15 / 12.5 / 11**. The fifteen M3 style names are derived aliases over the six steps.

**C1. `fontFeatureSettings = "tnum"` is mandatory on every numeric slot.** Archivo's digits are proportional (0=769, 1=683). Measured: with `tnum` the colon columns sit at 214–232 on both lines; without, 216–234 against 200–218 — a 16px drift. A golden canary catches this.

**C2. `numericFontFamily` takes digits and `: . , - + / %` only. Never a translatable string.** Archivo has zero Cyrillic coverage. Verified against the real corpus: 19 `values` plus 18 `values-ru`, 55 Cyrillic characters plus `« » · × — … →`. Plex Sans and Mono cover it completely.

C2 is closed by **two mechanisms covering different halves**: the Cyrillic golden catches text slots, the custom detekt rule catches numeric slots. The rule is not redundant — do not conflate them.

**C3. `isShrinkResources = true` strips unreferenced font resources.** Verify presence by sha256 in the packaged APK, not by filename: entries are obfuscated to `res/8y.ttf`.

## 5. Motion

The mockup's tokens do not overlap the existing `AppMotion` at a single point. `AppMotion` is retokenised wholesale:

| Token | Value |
|---|---|
| `fast` | 140 ms |
| `base` | 260 ms |
| `slow` | 520 ms |
| `out` | `cubic-bezier(.16, 1, .3, 1)` |
| `spring` | `cubic-bezier(.34, 1.56, .64, 1)` |

`spring` overshoots past 1. There is no equivalent in the current set; in Compose this is `spring` with a tuned `dampingRatio` or `keyframes`, not `tween` with an `Easing`.

**Overshoot is valid on geometry and invalid on colour.** Scale, translation and size may overshoot; a colour lerp past 1.0 clamps or produces garbage. This constrains the wow-moment automaton in §9.

`AppMotion` already sits behind `LocalAppMotion`, so replacing values does not touch plumbing.

## 6. Session mechanics

### 6.1 Exercise states

Mockup classes: `active` · `fin` · `skip` · `temp`, plus `prfx` as a transient.

| State | Meaning | Progress denominator | Training plan |
|---|---|---|---|
| **Active** (`active`) | in progress now, expanded, on `raise` | counted | — |
| **Done** (`fin`) | collapsed, muted, result replaces plan | counted | — |
| **Skipped** (`skip`) | "not today" | **excluded** | untouched |
| **One-off** (`temp`) | in the session, not in the plan, dashed ordinal | counted | not added |
| Deleted | "this should not be here" | excluded | cleaned |
| **Unfilled** | set row created, `reps = 0` | **does not survive** | — |

- Skip is reversible in place. **No snackbar** — there is nothing to undo.
- Delete gets a 5-second undo toast; after that there is no way back.
- The "only for today" toggle appears **only** on exercises added during the session. Mockup caption: "stays in this session but will not be added to the training plan." Off by default.
- Removing an exercise from the plan is a separate dialog — "Remove from the training plan?" with "Keep" / "Remove from plan".

**Unfilled is new.** `reps` is a non-null `Int` and zero acts as a "not entered" sentinel (`LiveSetRow.kt:97` renders `reps.takeIf { it > 0 }`). Validation blocks **editing** to ≤0, but the row is **created** with zero — finish a session with an unfilled set and the zero persists forever.

**Resolution: unfilled sets are discarded at session finish, with a line in `FinishConfirmDialog`.** Not silently. The reason is not data hygiene: a progress rail that counts sets which never happened lies about progress, and the principle in §1 rests entirely on that count being honest.

This also affects `totalSets = exercises.sumOf { it.sets.size }` (`PastSessionUiMapper:27-31`).

Confirm the real population on live data before writing release notes — unfilled rows versus deliberate zeroes.

### 6.2 `plan-attached`

The axis is named explicitly. **Not `adhoc`**: `exercise_table.is_adhoc` is a property of the **exercise** ("created inline", create/graduate/delete lifecycle, arrived in v6). One-off is a property of the **relation** between exercise and training. Breaking case: a library exercise with `is_adhoc = 0` added as a one-off today.

**No migration required.** The flag is encoded by the absence of a row in `training_exercise_table`; `performed_exercise_table` has no FK to it, and the loader already tolerates a missing plan.

### 6.3 The `getPlans` seam — verify before implementing

`plan_sets` on an existing row is nullable. At `LiveWorkoutInteractorImpl.kt:95-103`, "no row" and "row with an empty plan" both collapse into `trainingPlans[it] == null`. If `getPlans` returns a map keyed by uuid there is nothing there to distinguish them, and the repository must expose the flag explicitly rather than through plan nullability.

### 6.4 The writer that has to fork

`addExerciseToActiveSession` (`SessionRepositoryImpl.kt:386-400`) atomically writes **both** a plan row and a performed row. Adding an exercise mid-session therefore permanently edits the saved template today. This is the only writer requiring change.

### 6.5 Sets

Mockup classes: `done` · `flash` · `pr`.

- Set-level skip is **removed entirely** — unperformed sets are overwritten by the next session anyway.
- Deletion is the "− set" button, always the last set. Deleting from the middle requires a row swipe and is **not planned**.
- Adding a set to a completed exercise returns it to incomplete; the add/remove buttons stay reachable on a completed exercise.

## 7. Disclosure

| Rule | Behaviour |
|---|---|
| has progress | always expanded |
| no progress | exactly one — the **first by position** among unfinished |
| completed | collapses automatically |
| manually expanded | sticky, holds for the screen session |
| after the first manual action | the auto rule **stops collapsing anything** until the screen is left |
| completed | can be expanded manually — otherwise its buttons are unreachable |

Expanded means active, and carries the accent. The automaton is initialisation plus advance-on-completion; manual expansions are additive and sticky. There is no conflict because manual action mutes auto-collapse, not the reverse.

## 8. Progress rail

Mockup geometry: `height: 9px`, `gap: 12px`, `margin-top: 22px`, flex distribution. Below the rail sits `railmeta` with two captions.

Degrades by width: **sets → exercises → overall**.

**Mechanism: `BoxWithConstraints` inside a single rail component.** Not `WindowSizeClass`.

- The rule is local — do N segments fit in **this** rail. That is width at the layout point, not a window class.
- No new dependency and no global plumbing, which sidesteps `AppDimension` not being a CompositionLocal.
- `MainActivity` absorbs `fontScale` without recreating the Activity, so a value computed once at screen level goes stale; computed at the layout point it does not.

**Condition: the rail is one component with the rule inside it.** Copies mean a drifting threshold.

The mockup ships the degradation ladder as toggles: **2×4 · 5×4 · 8×4 · 16×5** (exercises × sets) — 8, 20, 32 and 80 segments. Ready-made test cases and a ready-made golden set.

## 9. Wow moments

Two, and only two:

1. **Set closure** — the circle morphs into a filled plate, the row flashes (`flash`), the rail segment fills.
2. **Personal record** — a molten unfurl (`prfx`), standing in for AGSL.

Everything else uses default transitions.

**Merge, do not sequence.** A record almost always *is* a set closure. The structural half cannot be suppressed — the segment must fill, the rail depends on it. Queueing doubles the duration of the most frequent action in the app.

**One automaton, record as a parameter.** Same morph; the flash and segment resolve to `molten` instead of `max`. Note the constraint from §5: overshoot applies to the geometry, not to the colour lerp.

Infrastructure is already in place: `SharedTransitionLayout` is threaded into five graphs with all five receivers marked `@Suppress("UnusedParameter")`.

## 10. Gates

### 10.1 Paparazzi — conditional, working

| Condition | State |
|---|---|
| `2.0.0-alpha05` | latest; stable 1.3.5 cannot configure on AGP 9.3.0 (`BaseExtension` removed) |
| JUnit path | **Jupiter.** `Paparazzi.setup(TestName)` / `teardown()` are public and touch no JUnit 4 type |
| `maxPercentDifference` | **0.0**, set explicitly. A whole glyph moved 0.030–0.031% of the frame; 0.1 would have waved it through |
| `useDeviceResolution` | **`true`.** Removes resampling noise; hairlines land on whole pixels |
| Fonts | no substitution — goldens render the real families |

**The liveness assertion is mandatory.** An earlier claim that Jupiter removes the silent-skip mode structurally was **disproved by execution**: with the golden package excluded by a task filter, `verifyPaparazziDebug` exits `0 / BUILD SUCCESSFUL` having run zero tests. `failOnNoDiscoveredTests` does not help and is `false` repo-wide anyway. The assertion finalises both Paparazzi tasks.

**Goldens must not run under `testDebugUnitTest`.** Measured: the same mutation gave `testDebugUnitTest` 6/6 PASSED and `verifyPaparazziDebug` 2 FAILED. The plugin injects `paparazzi.test.verify` only into its own tasks; under a plain run there is no comparison at all.

Rules:

- a golden must explicitly paint its background surface — the window background comes from Paparazzi's `theme` parameter, not from `AppTheme`, so without an explicit paint the dark and light goldens silently share a background;
- a flake is a finding about render nondeterminism, **never a reason to raise tolerance**;
- a golden change must be intentional and explained in the commit body; an unexplained delta is a review stop.

**Cost:** the canvas is the lever, not compression. Phone-frame goldens averaged ~49 KB; subject-sized canvases average ~22 KB. Cut the canvas to the subject. Full-screen goldens only where whole-composition is the thing under test, and justify that in the commit body.

### 10.2 Contrast test

JVM, runs under `testDebugUnitTest`. No layoutlib, no prerelease dependency. Catches regressions **in the token**, not in its rendering — earlier and more precisely than a screenshot.

### 10.3 Custom detekt rules

**A `detekt.yml` key is required.** Proven by execution: with the key removed and a violating fixture in the tree, detekt exits 0 and says nothing. Registration alone is not enough.

`:lint-rules:test` now runs in CI. Before that, custom rules were verified **never**: `:lint-rules` is a plain JVM module and the pipeline's only test invocation was `testDebugUnitTest`, which does not exist for it.

**Invariant rules should be treatment-based, not slot-based.** A rule that counts call sites of a single composable stays correct across token renames; a rule naming a colour slot breaks the moment the slot is renamed — and the slot names in this codebase are already scheduled to change.

### 10.4 Outside the gate

Paparazzi models a single window. **18 out-of-window sites**: `ModalBottomSheet`, ten `Dialog`s, `DatePickerDialog`, five `DropdownMenu`s, `TooltipBox`. (Counted carefully — a naive grep yields 19 by including a wrapper's own declaration.)

Of the stock M3 components that shout default, two — `DropdownMenu` and dialogs — are entirely outside the gate. So are all four picker-sheet implementations, and exercise selection is a central flow.

Outside the gate: everything modal, both animations, AGSL, everything time-based. The theatrical half is verified by hand, at every step.

### 10.5 `@Deprecated` is unavailable as a migration signal

Under `build.maxIssues: 0`, deprecation warnings are build failures rather than a worklist. The standard tool for staged migration is therefore unavailable in this repository. Migrations must be tracked as an explicit worklist in the spec or in `tech-debt.md`, not as compiler warnings.

## 11. Scope — three items need a decision

The mockups contain three things that exist in neither the code nor the locked decisions.

| # | What | In the code | Cost |
|---|---|---|---|
| 1 | **Chart: three metrics** — Weight / Session / Set | `ChartMetricDomain` knows `HEAVIEST_WEIGHT` and `VOLUME_PER_SET`. Per-session volume does **not** exist: `ChartFolder:72-85` folds by day, taking one winning set, never summing | new fold + enum value + label. No migration |
| 2 | **Settings: "Units · Kilograms"** | no unit system; `"kg"` was a hardcoded literal, since moved to `strings.xml` | new setting + persistence + display conversion |
| 3 | **Past session: "… · 4,820 kg"** | per-session tonnage does not exist: `getBestSessionVolumes` takes top-N since a date and accepts no `sessionUuid`; the summary counts only | new query by `sessionUuid`, **or** a Kotlin sum over already-loaded data. Cheapest of the three. No migration |

Each is either a scope expansion or "the mockup draws something that arrives later". All three are due by the screen step; the earlier steps do not depend on them.

## 12. Out of scope

- Time as an exercise type — a v6 → v7 migration plus a third enum value across **ten** declaration sites
- Supersets
- Scrubbing residual weights by migration — rejected: it irreversibly discards genuinely logged data
- Module duplication (`core/ui/plan-editor` vs `feature/plan-editor`, four picker sheets) — logged in `tech-debt.md`
- Renaming palette slots to the mockup's names — logged in `tech-debt.md`
- Deleting the dead `Icon` / `Button` dimension scales kept alive by `@Suppress("unused")` — worth doing, but as its own PR

---

# Part II — Screens

## 13. Inventory

Seventeen screens. Five are drawn in detail, eight derive from the kit by written rule, the rest are sheets and overlays.

| Screen | Source |
|---|---|
| Session | `session-v3f` |
| Chart | `pass2d` |
| Exercise detail | `pass2d` |
| Past session | `pass2d` |
| Settings | `pass2d` |
| Empty states | `pass2d` |
| Archive, backup detail, training detail, training and plan editors, multi-select mode, search-or-create sheet, empty session | written rule |

## 14. Session

**Frame:** `topbar` (leading + trailing icons) → `shead` → `rail` → `railmeta` → `cards` → `addex` → `dock`.

- `shead`: `h2` training name plus `meta`; on the right `data-s`, the timer — Archivo, `tnum` mandatory.
- `rail`: 9px tall, 12px gap, 22px top margin, flex. Degradation per §8.
- `railmeta`: two captions beneath the rail.
- `cards`: exercise cards, states `active` / `fin` / `skip` / `temp`, transient `prfx`.
- `addex`: "Add exercise", below the list.
- `dock`: "Finish session", pinned.

**Set row** (`set`): ordinal `set-i`, two `field`s each carrying `data-l` plus `unit` ("kg", "reps"), then `tchip` (set type) or `prtag` (record). States `done`, `flash`, `pr`.

**Sheets:**

1. Exercise menu — a toggle row "Only for today" with caption, divider, "Skip exercise", "Delete exercise" (`rust`).
2. Confirmation — "Remove from the training plan?" with description, button stack "Keep" / "Remove from plan" (`danger`).
3. Session menu — "Add exercise", "Reorder", "Cancel session" (`rust`).
4. `toast` with an "Undo" button, 5 seconds, for deletion.

## 15. Chart

**Frame:** `topbar` → `exhead` (name + `swap`) → `tabs` → `ranges` → `readout` → `chartwrap` → three `statrow`s.

- `tabs`: **Weight · Session · Set** with a sliding indicator. Positions 2 and 3 are §11.1.
- `ranges`: `1M · 3M · 1Y · All` chips, active marked `on`.
- `readout`: metric name and caption on the left, `data-hero` value plus `unit` on the right.
- `statrow` ×3: **Minimum · Maximum · Latest**, each `meta` plus `val`.

**Open:** a monochrome line chart — what distinguishes series, and where `molten` lives on a record point. May require a palette role that does not exist. Resolve by measurement at the screen step, not by eye.

## 16. Exercise detail

**Frame:** `topbar` → tags → `prhero` → `section-head` Default plan → `plancard` → `section-head` History + count → `list` → `dock`.

- `prhero`: on the left a `label` with a `mdot` marker plus "Record", beneath it `meta` with date and context; on the right `data-s` — "9 × 12" with an `x` separator.
- `plancard`: `planline` rows, each `ord` plus `val` in "7 × 12" form.
- `section-head` carries two captions, left and right ("History" / "4 sessions").
- `list`: rows with `row-name` (date) and `meta` (set summary); the record row carries `prtag`.
- `dock`: "Edit" (ghost) plus "Log now" (primary).

## 17. Past session

**Frame:** `topbar` → header → `section-head` → `cards`.

- Header: `label` "Finished · 23 July 2026", `data-hero` duration "56:08", `meta` "5 exercises · 14 sets · 4,820 kg" — tonnage is §11.3.
- `section-head`: "Logged" / "editable" — the right caption declares the mode.
- `card` with `chead` (`ord` + `title`; collapsed cards carry a `plan-line` summary) and `cbody` → `inner` → `sets`.
- Set row in edit mode: `set-i`, two `field`s with `data-l` plus `unit`, then `tchip` or `prtag`. Fields are editable.

## 18. Settings

**Frame:** `topbar` with `h1` → four `sgroup`s.

Each `sgroup`: 30px top margin, a `label` heading, then `srow`s.

`srow` has three variants: navigable (chevron), `plain` (not navigable, carries a control), `rust` (destructive).

| Group | Rows |
|---|---|
| **Appearance** | Theme (`plain` + segmented control), Units (navigable — §11.2) |
| **Backups** | account (`plain`), Auto-backup + `val`, AI assistant snapshot (`plain` + switch), Back up now, Restore from backup, Sign out (`rust`) |
| **Data** | Archive + counts |
| **About** | Workeeper + version (`plain`), Source code, GPLv3 licence, Privacy policy |

Controls: segmented, switch, trailing value, chevron by default.

## 19. Empty states

One pattern, three applications: `glyph` → `h4` heading → `p` explanation → `btns` (zero, one or two buttons).

| Where | Heading | Buttons |
|---|---|---|
| Training list | Your trainings will appear here | Create training · Start empty (ghost) |
| Chart | Nothing to show yet | none |
| Exercise list | Your exercises will appear here | Add exercise |

The explanation is always one sentence saying what to do next, never one stating that the list is empty.

The pattern moves into the kit with the structural step; placement on screens comes with the screen step.

## 20. Sheets

Common anatomy: scrim → sheet with a grab handle → `h3` → content.

Three forms: a `mitem` list (menu), a button stack (confirmation), free content with a "Close" button (informational).

The exercise picker sheet: `h3` "Select exercise" plus a `mitem` list, active item marked `on`.

**All sheets are outside the screenshot gate** — see 10.4.

## 21. Golden inventory

| Category | What | Goldens |
|---|---|---|
| Kit primitives | buttons, fields, chips, rows, tags | per component × 2 themes |
| Canaries | tnum, hairline, Cyrillic | 3 × 2 |
| Rail | 2×4, 5×4, 8×4, 16×5 | 4 × 2 |
| Exercise states | active, fin, skip, temp, prfx | 5 × 2 |
| Set states | plain, done, pr | 3 × 2 |
| Empty states | three applications | 3 × 2 |
| Screen sections | per §§14–19, per component | settled at the screen step |

**Sizing rule:** the canvas is cut to the subject, not to a phone. This measured 22 KB per golden against a 49 KB phone-frame baseline.

---

## 22. Execution order

Numbering is the execution numbering — the one CC prompts use. Earlier drafts of this document used a separate 0–5 stage numbering that drifted out of sync; that scheme is retired.

| Step | Content | Entry condition |
|---|---|---|
| 1 | Visual gate: Paparazzi wiring, baseline goldens | — |
| 2 | Typography: three families, six steps, C2 rule, `:lint-rules:test` in CI | 1 |
| 3 | Palette both themes, `molten` as a role, `AppMotion` retokenised, contrast map and gate | 2 |
| 4 | Kit structure: sections, hairlines, fixed row height, single raised surface, empty-state pattern, invariant rules | 3 |
| 5 | Session: `plan-attached`, unfilled state, disclosure automaton, rail, merged motion automaton | 4, §6.3 verified |
| 6 | Screens: Chart, Exercise, Past session, Settings, picker sheet, empty states in place — **and the `AppCard` migration** | 4, three decisions from §11 |
| 7 | Eight derived screens by written rule, no mockup | 4 |

**Cards die at step 6, not step 4.** Every real `AppCard` consumer is a feature screen, so the migration cannot happen inside a kit-only step. Step 4 establishes the invariant and the gate; step 6 carries the worklist. This is the correction that retired the old numbering.

Steps 6 and 7 parallelise with each other.

**One session per checkout.** Three concurrent CC sessions in a single working directory have already produced commits on the wrong branch.

## 23. Verification discipline

Accumulated by this arc; applies to everything that follows.

- `--rerun-tasks` always. `FROM-CACHE` is not executed.
- **`--stop` before measuring any rule or plugin built in the same invocation.** A stale jar in the daemon produces a false green that reads like a valid finding.
- Every gate is proven in **both** directions: it fires on a violation and stays silent on a clean tree. One direction is not enough.
- A gate's own configuration needs the same treatment as its content — see 3.3. Silent last-write-wins is worse than an uncovered case.
- An empty result from a multi-agent check is not a clean result. Count agents started against agents completed; a mismatch is red, not silence.
- A guarantee that cannot fail is a comment, not a gate.
- Discovery citations about the build and CI have gone stale repeatedly during this arc. They are grounds to re-check, never grounds to conclude.
- Measure mockup values before adopting them (§0.2). Two for two have failed a threshold that the eye cannot see.
