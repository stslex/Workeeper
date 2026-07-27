# v3 Redesign — Specification

**Revision 3.** Supersedes all earlier copies, including the Russian drafts. If you find another, delete it.

**Sources:** `documentation/mockups/workeeper-redesign-pass2d.html`, `workeeper-session-v3f.html`.
**Merged:** steps 1–5.

---

## 0. How to read this document

### 0.1 Claims are marked

This arc produced a hard lesson. Every claim this specification made about **what the code does** was wrong or too narrow — seven for seven. Every claim about **what the design should be** held. Cabinet analysis of this repository has now failed for its author as reliably as it failed before.

So claims are marked:

- **[V]** — verified by execution, with the evidence named. Trust it, but note when it was verified: two of four cited call sites went stale inside a single arc, in weeks.
- **[I]** — inferred from reading. **Resolve by preflight before building on it.** An [I] claim that turns out wrong is the expected case, not a surprise.

An unmarked statement is a design decision, which is this document's own authority and does not need verification.

### 0.2 The mockups are reconnaissance, not contract

They were drawn before the type scale was locked and carry twenty font sizes against the locked six.

| Take | Do not take |
|---|---|
| palette, both themes | font sizes — the six-step scale wins |
| motion tokens | raw pixel values — see 0.3 |
| structure and hierarchy | CSS shadows, radii, flex gaps |
| component states | §11 items |

Some text will land on a scale step 1–2px from the mockup. That is correct; do not file it during visual review.

### 0.3 Pixels and dp

An earlier revision claimed a px→dp factor of ~1.15. **Wrong.** The mockups declare `width=device-width`, so 1 CSS px is 1 dp; the 452px frame is a desktop viewport cap above the widest phone.

The work is **rounding onto the existing `AppDimension` ladder**: nearest rung, ties broken toward the value that already has call sites. Worked example — the mockup's 20 gutter was equidistant and resolved to 16dp, which already had 45 call sites. **[V]**

Values may be **derived rather than transcribed** where the mockup's number is a consequence of its parts: the 88dp row height is `2×21 + 4 + 18 + 2×12`, confirmed from golden pixels at 2.75 px/dp as 176 / 242 / 242 px = 64.0 / 88.0 / 88.0 dp. **[V]**

### 0.4 Every mockup colour is measured before adoption

Two for two failed a locked threshold: light `meta` `#69727C` at 3.79:1 on `raise`, and `dim` at 3.91:1 under an 11sp label. Both miss by under 15% and neither is visible to the eye. **Measure, then adopt.** **[V]**

---

# Part I — Foundation

## 1. Principle

The accent is **brightness, not hue**. A session starts visually muted and brightens as it is completed. Completed = maximum contrast against the background, in both themes.

No coloured accent. `molten` marks records only; `rust` marks destruction only. Contrast stops being an accessibility requirement and becomes the load-bearing expressive device — hence the gate.

Measured: completed content scores 13.07–17.77 dark and 14.71–18.96 light against a 7:1 threshold. The light-theme signature inversion worked; the margin is double. **[V]**

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
| `meta` | **`#596169`** — see 2.4 |
| `dim` | `#6B7078` |
| `idle` | `#7C858F` |
| `hair` | `rgba(13,17,20,.07)` |
| `hair-s` | `#D2D7DD` |
| `molten` | `#C2410C` |
| `molten-solid` | `#F97316` |
| `rust` | `#B03B2E` |

**Naming:** these are the mockup's names. The codebase uses `surfaceTier0..4`, `accentTintedBackground`, `textPrimary/Secondary/Tertiary`. There is no `AppUi.colors.raise`. Aligning the two is logged in `tech-debt.md` and is **not** part of this arc — a rename across every call site would drown the visual diff it exists to make reviewable.

### 2.3 `molten` is a four-part role that inverts by theme

Text, solid fill, background, border — and light **darkens** where dark **lightens**: `#F0A22E` → `#C2410C` text, `#F0A22E` → `#F97316` fill.

Two places only: the personal-record accent and the transient wow state (§9). Not a general accent. Say so in KDoc or it will become one.

### 2.4 Light meta deviates deliberately

The mockup's approved `#69727C` fails 4.5:1 on three surfaces of five: `raise` 3.79, `field` 4.12, `sec` 4.32; passes only on `base` 4.56 and `slab` 4.89. `sec` is the most frequent surface. **[V]**

**Resolution: `#596169`** — worst surface 4.88, passes everywhere. Separation from `body` survives (11.93 vs ~5.8 on `base`). Record the reason in KDoc; somebody will otherwise "fix" it back to the mockup.

The correction direction is safely asymmetric: the gate asserts a threshold, so darkening is free and lightening is caught.

### 2.5 Section labels use `textTertiary`, not `dim`

`dim` under an 11sp label gives 3.91:1 against the 4.5:1 owed. Same trap as 2.4. **[V]**

## 3. Contrast thresholds

Threshold depends on **type size**, not the pair alone. WCAG grants 3:1 only for ≥24sp regular or ≥18.66sp bold; `FontWeight.Medium` is not bold.

| Slot | Condition | Threshold |
|---|---|---|
| Archivo Expanded 700, 34 / 26 / 19 | bold ≥18.66sp | **3:1** |
| Archivo Expanded 700, 15 / 12.5 / 11 | — | **4.5:1** |
| IBM Plex Sans Medium, 34 / 26 | regular ≥24sp | **3:1** |
| IBM Plex Sans Medium, ≤19 | — | **4.5:1** |
| `max` on any surface | — | **7:1** |
| `meta` on its backing | — | **4.5:1** |

**`rust` on `raise` in dark measures 3.28** — below 4.5:1 for a destructive label. Either darken the surface beneath destructive text or establish that destructive text never sits on `raise`. Whichever is chosen, the map must encode it so it cannot silently regress. **[V]**

### 3.1 Hairlines carry no threshold

An early revision locked ≥3:1 on the theory that killing cards made hairlines the sole section separator. **Wrong.** `.sgroup { margin-top: 30px }` plus `.sgroup > .label` — every section has thirty pixels of air and a text heading. Sections are separated by space and a label. **[V]**

`hair-s` divides **rows inside** a section — the decorative case, and 1.12–1.52 is legitimate.

**Rule:** gutter and heading separate sections; a hairline is decorative. A line that becomes the sole separator on some future screen is load-bearing and takes 3:1.

### 3.2 The gate is built from a map

A measurement yields pairs; a threshold requires the usage. The same pair holds 3:1 under 26sp and 4.5:1 under 15sp.

- **(a)** a declared map of `(foreground, background, type slot)` triples with thresholds;
- **(b)** an exclusion list of pairs that provably never co-occur, each with a reason;
- **(c)** a test enumerating **all** foreground × surface combinations that fails on any neither declared nor excluded.

Part (c) is the point. Without it a new screen adds an unverified pair in silence.

Also: disabled and inactive colours are **WCAG-exempt** (1.4.3, 1.4.11) — verify inactivity at call sites before exempting. Role aliasing inflates counts; report **distinct measurements** alongside rows. Hairlines are excluded with the 3.1 reason, not asserted as passing.

### 3.3 The map's own integrity is gated

A duplicate key in the roles map resolves last-write-wins, so behaviour is set by declaration order and **neither line reads like what happens**.

This occurred. The resolution is worth recording precisely, because two separate readings of the source disagreed and both were half right: the threshold **was** declared correctly at 3:1, and `borderDefault` had **dropped out of the enumeration entirely**. A correct-looking line created the appearance of coverage where there was none. Established by execution — stripping all five `DECLARED` rows gave exit 0 before the fix and exit 1 after. **[V]**

This is more dangerous than an uncovered pair. Uncovered fails loudly. A silently substituted threshold passes green with the wrong number, inside the gate built to prevent exactly that.

**Requirement:** build the map from a list and **fail at construction on a duplicate key**. Fixing offending lines is not sufficient; the next duplicate arrives just as quietly.

Note on writing such guards: the first attempt keyed on `(fg, bg)` and rejected 14 correct rows, because the list is keyed on the triple. The failed attempt is how it was established that the other lists do not share the hazard. A guard that fails informatively is worth writing even when it is wrong. **[V]**

## 4. Typography

| Family | Slots | Weights |
|---|---|---|
| IBM Plex Sans | all text | 400 / 500 |
| Archivo Expanded | numerals, timer | 700 |
| IBM Plex Mono | units, metadata | 400 / 500 |

Scale: **34 / 26 / 19 / 15 / 12.5 / 11**. The fifteen M3 names are derived aliases over the six steps.

**C1. `fontFeatureSettings = "tnum"` is mandatory on every numeric slot.** Archivo's digits are proportional (0=769, 1=683). With `tnum` the colon columns sit at 214–232 on both lines; without, 216–234 vs 200–218 — a 16px drift. A golden canary catches it. **[V]**

**C2. `numericFontFamily` takes digits and `: . , - + / %` only. Never a translatable string.** Archivo has zero Cyrillic. Verified against 19 `values` plus 18 `values-ru`, 55 Cyrillic characters plus `« » · × — … →`. **[V]**

C2 is closed by **two mechanisms covering different halves**: the Cyrillic golden catches text slots, the detekt rule catches numeric ones. The rule is not redundant.

**C3. `isShrinkResources = true` strips unreferenced fonts.** Verify by sha256 in the packaged APK, not filename — entries obfuscate to `res/8y.ttf`. **[V]**

## 5. Motion

The mockup's tokens do not overlap the old `AppMotion` at any point:

| Token | Value |
|---|---|
| `fast` | 140 ms |
| `base` | 260 ms |
| `slow` | 520 ms |
| `out` | `cubic-bezier(.16, 1, .3, 1)` |
| `spring` | `cubic-bezier(.34, 1.56, .64, 1)` |

`spring` overshoots past 1. **Overshoot is valid on geometry and invalid on colour** — a colour lerp past 1.0 clamps or garbages.

## 6. Session mechanics

### 6.1 Exercise states

Mockup classes: `active` · `fin` · `skip` · `temp`, plus `prfx` transient.

| State | Meaning | Denominator | Plan |
|---|---|---|---|
| **Active** | in progress, expanded, raised | counted | — |
| **Done** | collapsed, muted, result replaces plan | counted | — |
| **Skipped** | "not today" | **excluded** | untouched |
| **One-off** | in session, not in plan, dashed ordinal | counted | not added |
| Deleted | should not be here | excluded | cleaned |

- Skip is reversible in place. **No snackbar** — nothing to undo.
- Delete gets a 5-second undo toast.
- The "only for today" toggle appears **only** on mid-session additions. Off by default.
- Removing from the plan is a separate dialog: "Keep" / "Remove from plan".

**Unfilled sets are a UI draft state, not a persistence state.** An earlier revision claimed a `reps = 0` row "persists forever". **Not reachable:** every production writer of `set_table` gates on reps — `upsert` by `reps > 0`, `update` by `parsed > 0`, and `insert` has no production caller. **[V]**

What the user is told at finish is therefore the count of **visible empty rows**, which is real and non-zero. The persistence-level discard remains as defence in depth, and its KDoc must say it finds nothing today — a guard whose emptiness is undocumented reads as dead code and gets deleted.

### 6.2 `plan-attached`

A property of the exercise↔training **relation**, not of the exercise. Not `adhoc`: `exercise_table.is_adhoc` means "created inline" with its own create/graduate/delete lifecycle. Breaking case: a library exercise with `is_adhoc = 0` added as a one-off today.

Encoding: absence of a `training_exercise_table` row. **No migration.**

**The two axes intersect, and the intersection held a bug.** An inline-created one-off would have been stranded at `is_adhoc = 1` forever by graduation, and leaked by cancel. Any future work touching either axis must check the other. **[V]**

### 6.3 `getPlans` — resolved

An earlier revision claimed the repository could not distinguish "no row" from "row with an empty plan". **Wrong on both counts.** `getPlans` builds its map with `rows.associate {}` over rows SQL returned, so a missing pair is an absent key — and `TrainingExerciseRepositoryImplDbTest:212-217` already pinned all three states. The collapse existed only at the call site, because `map[k]` returns null for absent-key and present-null alike. Fixed with `containsKey`. **[V]**

Worth keeping as a pattern: the capability was already proven by a test that predated the claim it disproved.

### 6.4 Paths that join through the plan table

An earlier revision named `addExerciseToActiveSession` "the only writer requiring change". True of **writers**, and the wrong frame. Two **readers** that drive lifecycle writes also join through the plan table and would silently skip a one-off; finish-time plan writes would match zero rows. **[V]**

**The correct frame is every path that joins through `training_exercise_table`, not every path that writes to it.**

### 6.5 Sets

Classes `done` · `flash` · `pr`.

- Set-level skip is **removed** — unperformed sets are overwritten next session.
- Deletion is the "− set" button, always the last set. Middle deletion needs a swipe: **not planned**.
- Adding a set to a completed exercise returns it to incomplete; its buttons stay reachable.

## 7. Disclosure

| Rule | Behaviour |
|---|---|
| has progress | always expanded |
| no progress | exactly one — **first by position** among unfinished |
| completed | collapses automatically |
| manually expanded | sticky for the screen session |
| after first manual action | auto rule **stops collapsing anything** until the screen is left |
| completed | manually expandable — its buttons are otherwise unreachable |

Manual mutes auto-collapse; auto never overrides manual.

**Configuration change does not discriminate here.** With 17 absorbed `configChanges`, `remember`, `rememberSaveable` and Store state survive rotation identically. "Surviving rotation" is not the useful test; the failure mode that actually occurred was a plan-editor round-trip resetting expansion, which is not "leaving the screen session". **[V]**

## 8. Progress rail

Geometry: 9dp tall, 12dp gap, 22dp top margin, flex; `railmeta` with two captions beneath.

Degrades by width: **sets → exercises → overall**.

**Mechanism: `BoxWithConstraints` inside a single rail component.** Not `WindowSizeClass`. The rule is local — do N segments fit in **this** rail — which is width at the layout point and survives `fontScale` changes a screen-level computation would not.

**The rail is one component with the rule inside it.** Copies mean a drifting threshold.

The 9dp minimum segment width is **unverified** — taken from a browser. It stays a named constant whose KDoc says so until a device says otherwise.

**The mockup's presets do not cover the ladder.** 2×4, 5×4, 8×4 and 16×5 reach only two of three levels: 16×5 lands on EXERCISES, confirmed at the mockup's own 412px width. OVERALL needs 24 exercises or a constrained rail; the golden set constrains the rail rather than inflating the data. **[V]**

## 9. Wow moments

Two, and only two:

1. **Set closure** — circle morphs to a filled plate, row flashes, rail segment fills.
2. **Personal record** — molten unfurl.

**Merge, do not sequence.** A record almost always *is* a set closure. The structural half cannot be suppressed — the segment must fill, the rail depends on it — and queueing doubles the duration of the app's most frequent action.

**One automaton, record as a parameter:** same morph, flash and segment resolve to `molten` instead of `max`. Overshoot applies to the geometry, never to the colour lerp (§5).

**The flash must be gated to false→true transitions.** Firing on first composition is the defect this arc actually shipped into four goldens before review caught it — see 10.2.

## 10. Gates

### 10.1 Paparazzi

| Condition | State |
|---|---|
| `2.0.0-alpha05` | latest; stable 1.3.5 cannot configure on AGP 9.3.0 |
| JUnit path | **Jupiter.** `setup(TestName)` / `teardown()` touch no JUnit 4 type |
| `maxPercentDifference` | **0.0**, explicit. A whole glyph moved 0.030–0.031% |
| `useDeviceResolution` | **`true`** — hairlines land on whole pixels |
| Fonts | no substitution; goldens render the real families |

**The liveness assertion is mandatory.** The claim that Jupiter removes silent-skip structurally was **disproved**: with the golden package excluded by a task filter, `verifyPaparazziDebug` exits `0 / BUILD SUCCESSFUL` having run zero tests. `failOnNoDiscoveredTests` is `false` repo-wide anyway. **[V]**

**Goldens must not run under `testDebugUnitTest`** — the same mutation gave 6/6 PASSED there and 2 FAILED under verify. The plugin injects `paparazzi.test.verify` only into its own tasks. **[V]**

**A filtered golden run cannot serve as a determinism check** — the liveness gate legitimately fails it (12 executed against 40 committed). **[V]**

Rules: a golden must explicitly paint its background surface, since the window background comes from the `theme` parameter rather than `AppTheme`. A flake is a finding about nondeterminism, never a reason to raise tolerance. An unexplained golden delta is a review stop.

**Cost:** the canvas is the lever. Phone frames averaged ~49 KB; subject-sized canvases ~22 KB. **[V]**

### 10.2 What a golden does not guarantee

A golden locks in what **is**, not what **should be**. Its guarantee is differential — *nothing changes without explanation* — and it says nothing about the correctness of the baseline. Record it with a bug and the gate is locked onto the bug and green forever.

This happened: the flash fired on first composition, and four goldens captured it. Review caught it; the gate could not.

None of the three defences reach this class. `maxPercentDifference = 0.0` catches change. Liveness catches the gate vanishing. "Unexplained delta is a review stop" catches unintended change. **A wrong baseline is none of those.**

Visual inspection cannot reach it either, and the reason is structural: **a transient state captured as a static frame is unfalsifiable by eye.** The frame of a flash that fires always and the frame of a flash that fires correctly are the same frame. "Open and describe the PNG" is powerless here by construction.

**Rule: any state declared transient is captured as a pair** — at rest and mid-transient. The pair asserts a difference; a lone transient golden asserts nothing about when it fires.

**And when a golden delta is a baseline correction, the commit body must say so.** Otherwise it reads six months later as an intentional design change.

### 10.3 Custom detekt rules

**A `detekt.yml` key is required** — with the key removed and a violating fixture present, detekt exits 0 silently. Registration alone is not enough. **[V]**

`:lint-rules:test` now runs in CI. Before that, custom rules were verified **never**. **[V]**

**Invariant rules should be treatment-based, not slot-based.** A rule counting call sites of one composable survives token renames; a rule naming a colour slot breaks on rename — and these slot names are already scheduled to change (§2.2).

### 10.4 Outside the gate

Paparazzi models a single window. **18 out-of-window sites**: `ModalBottomSheet`, ten `Dialog`s, `DatePickerDialog`, five `DropdownMenu`s, `TooltipBox`. A naive grep yields 19 by counting a wrapper's own declaration. **[V]**

Of the stock M3 components that shout default, `DropdownMenu` and dialogs are entirely outside the gate, as are all four picker-sheet implementations — and exercise selection is a central flow.

Outside the gate: everything modal, both animations, AGSL, everything time-based. Verified by hand, every step.

### 10.5 `@Deprecated` is unavailable as a migration signal

Under `build.maxIssues: 0`, deprecation warnings are build failures rather than a worklist. Staged migration must be tracked explicitly in this document or in `tech-debt.md`, never as compiler warnings.

## 11. Scope — three items

| # | What | In code | Cost | Decision |
|---|---|---|---|---|
| 1 | Past-session tonnage | absent — `getBestSessionVolumes` takes top-N since a date, no `sessionUuid` | a Kotlin sum over already-loaded data; no query, no migration | **in** |
| 2 | Chart third metric (per-session volume) | absent — `ChartFolder` folds by day taking one winning set, never sums | new fold + enum value + label | **in** |
| 3 | Units picker | absent | new setting, persistence, and conversion everywhere weight is shown | **out** — its own arc |

None leaves a visible hole when absent: the chart shows two tabs, Appearance keeps Theme, the header loses one figure. Deferring is cheap; touching a screen twice is not.

## 12. Out of scope

- Time as an exercise type — a v6 → v7 migration plus a third enum value across ten declaration sites
- Supersets
- Units (§11.3)
- Scrubbing residual weights by migration — rejected: irreversibly discards logged data
- Module duplication, palette slot renaming, the dead `Icon` / `Button` scales — all in `tech-debt.md`

---

# Part II — Screens

## 13. Inventory

Seventeen screens: five drawn in detail, eight derived by written rule, the rest sheets and overlays.

| Screen | Source | Step |
|---|---|---|
| Session | `session-v3f` | 5 ✅ |
| Past session | `pass2d` | 6a |
| Exercise detail | `pass2d` | 6b |
| Chart | `pass2d` | 6c |
| Settings | `pass2d` | 6d |
| Empty states | `pass2d` | 4 (pattern) + 6 (placement) |
| Archive, backup detail, training detail, editors, multi-select, search-or-create sheet, empty session | written rule | 7 |

## 14. Session — built

**Frame:** `topbar` → `shead` → `rail` → `railmeta` → `cards` → `addex` → `dock`.

Set row: ordinal, two fields each `value` + `unit`, then set-type chip or record tag. States `done`, `flash`, `pr`.

Sheets: exercise menu (one-off toggle, skip, delete), plan-removal confirmation, session menu (add, reorder, cancel), undo toast.

## 15. Past session — step 6a

**Frame:** `topbar` → header → `section-head` → `cards`.

- Header: "Finished · 23 July 2026", duration as `data-hero`, then "5 exercises · 14 sets · 4,820 kg" — tonnage per §11.1.
- `section-head`: "Logged" / "editable" — the right caption declares the mode.
- Cards carry `ord` + title; collapsed ones show a plan-line summary.
- Set rows are editable: ordinal, two fields with units, then chip or record tag.

**First of the four, because it settles the shared set-row question.** `LiveSetRow` and `PastSetEditRow` are separate components in separate modules; whether they become one kit component is decided here, with the session work still fresh.

## 16. Exercise detail — step 6b

**Frame:** `topbar` → tags → record hero → Default plan section → History section → `dock`.

- Record hero: marker dot + "Record", date and context beneath, value "9 × 12" on the right.
- Plan card: ordinal + value rows.
- Section heads carry two captions ("History" / "4 sessions").
- History rows: date plus set summary; the record row carries a tag.
- Dock: "Edit" (ghost) + "Log now" (primary).

Blocked by nothing.

## 17. Chart — step 6c

**Frame:** `topbar` → head → tabs → ranges → readout → chart → three stat rows.

- Tabs: **Weight · Session · Set** with a sliding indicator. Session is §11.2.
- Ranges: `1M · 3M · 1Y · All`.
- Readout: metric name and caption left, value + unit right.
- Stat rows: **Minimum · Maximum · Latest**.

**Open question, to be resolved by measurement rather than by eye:** a monochrome line chart needs something to distinguish series, and a home for `molten` on a record point. This may require a palette role that does not exist. Decide before building, not during.

## 18. Settings — step 6d

**Frame:** `topbar` with title → four groups, each 30dp top margin, a label heading, then rows.

Row variants: navigable (chevron), plain (carries a control), destructive.

| Group | Rows |
|---|---|
| **Appearance** | Theme (segmented control) — Units omitted per §11.3 |
| **Backups** | account, auto-backup + value, AI snapshot (switch), back up now, restore, sign out (destructive) |
| **Data** | Archive + counts |
| **About** | version, source, GPLv3, privacy policy |

## 19. Empty states

One pattern, three applications: glyph → heading → one-sentence explanation → zero, one or two buttons.

| Where | Heading | Buttons |
|---|---|---|
| Training list | Your trainings will appear here | Create training · Start empty (ghost) |
| Chart | Nothing to show yet | none |
| Exercise list | Your exercises will appear here | Add exercise |

The explanation always says what to do next, never that the list is empty.

## 20. Sheets

Scrim → sheet with grab handle → title → content. Three forms: item list (menu), button stack (confirmation), free content with Close (informational).

**All sheets are outside the screenshot gate** (§10.4).

## 21. Golden inventory

| Category | Goldens |
|---|---|
| Kit primitives | per component × 2 themes |
| Canaries (tnum, hairline, Cyrillic) | 3 × 2 |
| Rail — 2×4, 5×4, 8×4, 16×5, plus a constrained-width case for OVERALL | 5 × 2 |
| Exercise states | 4 × 2 |
| Set states | 3 × 2 |
| Transient states — **at rest and mid-transient, as pairs** (§10.2) | per state × 2 × 2 |
| Empty states | 3 × 2 |
| Screen sections | settled per screen |

**Sizing:** the canvas is cut to the subject, not to a phone. 22 KB against a 49 KB phone-frame baseline.

---

## 22. Execution order

| Step | Content | State |
|---|---|---|
| 1 | Visual gate: Paparazzi wiring, baseline goldens | ✅ |
| 2 | Typography, C2 rule, `:lint-rules:test` in CI | ✅ |
| 3 | Palette, `molten` as a role, `AppMotion`, contrast map and gate | ✅ |
| 4 | Kit structure: sections, hairlines, row height, raised surface, empty-state pattern, invariant rules | ✅ |
| 5 | Session: `plan-attached`, disclosure automaton, rail, merged motion automaton | ✅ |
| 6a | Past session — settles the shared set-row question | next |
| 6b | Exercise detail | — |
| 6c | Chart — needs the monochrome decision | — |
| 6d | Settings | — |
| 7 | Eight derived screens by written rule | — |

**Step 6 is four PRs, not one.** The screens are independent; four screens in one diff is unreviewable.

**`AppCard` migration is distributed, not a task.** Its eight consumers are different screens, each migrating with its own step. The PF1 inventory is a map, not a worklist. The invariant rule from step 4 is what makes distributed migration safe: a new violation is caught, an old one waits for its screen.

**One session per checkout.** Three concurrent CC sessions in one working directory produced commits on the wrong branch.

## 23. Verification discipline

Accumulated by this arc. Applies to everything after it.

**Execution, not reading**
- `--rerun-tasks` always. `FROM-CACHE` is not executed.
- `--stop` before measuring any rule or plugin built in the same invocation — a stale daemon jar produces a false green that reads like a valid finding.
- Re-verify bisect-green in a **clean worktree**, not from the state at commit creation.
- Run detekt and tests as **separate invocations**. A parallel run reported a spurious test failure when detekt failed first and interrupted the test task — a false RED, and more dangerous than a false green, because the response is to fix something that is not broken.

**Gates**
- Prove every gate in **both** directions: fires on violation, silent on a clean tree.
- Gate the gate's own configuration as strictly as its content (§3.3).
- A guarantee that cannot fail is a comment.
- A gate proves what changed, not what is right (§10.2).
- A known-negative mutation must **compile**, and must discriminate by what is on screen rather than failing wholesale. A mutation that breaks the build proves nothing.

**Claims**
- Behavioural claims about this codebase have failed seven for seven when made from reading. Mark them [I] and resolve by preflight.
- Citations decay inside weeks, including citations into this arc's own artifacts: two of four cited call sites had gone stale by the time they were re-checked.
- An empty result from a multi-agent check is not a clean result. Count agents started against completed; a mismatch is RED, not silence.
- A failed attempt can produce information. A guard written on the wrong key is how it was established that the other lists did not share the hazard.

**Autonomy**
- A monitoring session fixes **breakages** forward and **stops on findings**. A verdict that changes a decision is not a build failure and must not be repaired autonomously.
