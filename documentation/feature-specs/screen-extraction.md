# v3 — Screen Specification, extracted from the mockups

**Status:** investigation output. No code was written, no branch created, no build run.
**Method:** both mockups read in full (style + markup + script), the spec read for tokens/scale/
thresholds, and the current Compose implementation read to fill every "code does" column.

**Sources — the paths in circulation are stale, again:**

| Cited as | Actually |
|---|---|
| `documentation/mockups/workeeper-session-v3f.html` | `documentation/mockups/session-v3f.html` |
| `documentation/mockups/workeeper-redesign-pass2d.html` | `documentation/mockups/pass2d.html` |

The spec's own §"Sources" header carries the wrong pair too.

---

# Part 0 — The foundation every screen depends on

Read this once. Every screen section below refers to it rather than repeating it.

## 0.1 Token Rosetta stone — mockup name → codebase name

This mapping is the single most dangerous thing in the redesign, because **the tier numbers are
not in the mockup's order**:

| Mockup var | Dark | Light | Codebase slot | Role in the mockups |
|---|---|---|---|---|
| `--base` | `#0B0D0F` | `#F6F7F9` | `surfaceTier0` | page background |
| `--sec` | `#12161A` | `#EFF1F4` | `surfaceTier1` | resting card |
| `--field` | `#171C21` | `#E9ECF0` | **`surfaceTier3`** | input fields, **sheets**, ghost buttons |
| `--slab` | `#1E242A` | `#FFFFFF` | **`surfaceTier2`** | **active/open card**, tab indicator, segmented-control thumb, toast |
| `--raise` | `#242B32` | `#DFE3E8` | `surfaceTier4` | progress-rail pill track, selected tag |
| `--max` | `#F1F5F9` | `#0D1114` | `accent` / `textPrimary` | completed, primary button fill |
| `--body` | `#B7C0CA` | `#2C333A` | `textSecondary` | ordinary text |
| `--meta` | `#8B95A1` | `#69727C` → **`#596169`** | `textTertiary` | metadata |
| `--dim` | `#6B7078` | `#98A0A9` | **NONE — see 0.3** | captions, units, ordinals, chevrons |
| `--idle` | `#8B95A1` | `#7C858F` | `textDisabled` | inactive |
| `--hair` | `rgba(255,255,255,.05)` | `rgba(13,17,20,.07)` | `borderSubtle` | intra-card row divider |
| `--hair-s` | `#2B333B` | `#D2D7DD` | *(no direct slot)* | solid divider, unchecked mark ring, dashed borders |
| `--molten` | `#F0A22E` | `#C2410C` → **`#BE3E0C`** | `molten.text` / `record.textPrimary` | record, as text |
| `--molten-solid` | `#F0A22E` | `#F97316` | `molten.solid` | record, as fill |
| `--donefill` | `rgba(255,255,255,.05)` | `rgba(13,17,20,.06)` | *(none)* | completed field wash |
| `--grid` | `rgba(255,255,255,.07)` | `rgba(13,17,20,.09)` | *(none — `borderSubtle` is nearest)* | chart gridlines |
| `--flash` | `rgba(241,245,249,.13)` | `rgba(13,17,20,.09)` | *(none)* | set-closure flash wash |

**Two live values differ from spec §2's tables.** Both are already correct in code and wrong in the
spec document:

- light `molten` is **`#BE3E0C`** in code, not §2.2's `#C2410C`. `AppColors.kt` documents the
  measurement that forced it.
- light `meta` is **`#596169`** per §2.4, not the mockup's `#69727C`.

## 0.2 `--slabtop` — the active-surface signature

```css
/* dark  */ --slabtop: inset 0 1px 0 rgba(255,255,255,.055);
/* light */ --slabtop: 0 1px 3px rgba(13,17,20,.07), 0 6px 18px rgba(13,17,20,.05);
```

An **inset 1px top highlight** in dark; a **drop shadow** in light. It is applied to every "lifted"
surface: `.card.active`, `.card.open`, `.tabs .ind`, `.mseg button.on`. This is the mockup's entire
elevation vocabulary and the codebase currently has no equivalent (`AppElevation.shadow` is `0.dp`).

## 0.3 BLOCKER — the palette has no `dim`, and the mockups use it heavily

`--dim` is a **fifth text tier** between `meta` and `idle`. The codebase has four
(`textPrimary`/`Secondary`/`Tertiary`/`Disabled`) and `textTertiary` is already `meta`. Elements the
mockups paint in `--dim`:

`.label` · `.unit` · `.set-i` · `.ord` · `.ordchip` (resting) · `.sub` · `.plan-line` · `.chev` ·
`.tchip` text · `.val .x` · `.scrub` · `.empty .glyph svg` · `.tempbadge` border · `.mini` (resting)

`AppSectionHeader`'s KDoc already records the substitution to `textTertiary` and the reason:

> dark `--dim` `#6B7078` on `base` `#0B0D0F` measures **3.91:1** against the 4.5:1 an 11sp label owes.

**I re-measured all four combinations, and the spec's palette table has `dim` swapped between
themes.** Both mockups agree with each other; §2.1/§2.2 disagree with both:

| | mockup (both files) | measured on `base` | spec §2.1/§2.2 | measured on `base` |
|---|---|---|---|---|
| dark `dim` | `#6B7078` | **3.91:1** | `#98A0A9` | 7.36:1 |
| light `dim` | `#98A0A9` | **2.47:1** | `#6B7078` | 4.65:1 |

The spec's own §2.5 reasoning ("`dim` under an 11sp label gives 3.91:1") is derived from the
**mockup's** dark value, i.e. §2.5 and §2.1 contradict each other and §2.5 matches the mockups.

**Consequence, and this is the part that needs a decision:** taken as drawn, light `dim` is
**2.47:1** — a severe failure, worse than the dark case that already forced the substitution. So the
mockups' two-tier `meta`/`dim` distinction **cannot be shipped as drawn in either theme**, and
collapsing both onto `textTertiary` (current behaviour) silently erases a distinction the mockups
make on every screen — e.g. a finished card's title is `meta` while its subtitle is `dim`.

**REPORTED, NOT RESOLVED.** Options are: add a fifth tier with a measured value, accept the
collapse and document it, or exempt these as decorative. All three are §2 palette decisions.

## 0.4 Type — the mockup's declarations vs the locked scale

Settled: the six-step scale (34/26/19/15/12.5/11) beats the mockup's sizes. Rounding for reference:

| Mockup | Rung | Used by |
|---|---|---|
| 9.5px | 11 | `.tempbadge` |
| 10px | 11 | `.prtag` |
| 11px | 11 | `.label` |
| 11.5px | 11 | `.unit`, `.ordchip` |
| 12px | 12.5 | `.set-i`, `.sub`, `.plan-line`, `.tchip`, `.setbar` |
| 12.5px | 12.5 | `.meta`, `.ord` |
| 14px | 15 | `.tabs button`, `.tag` |
| 14.5px | 15 | `.sheet p`, `.toast span`, `.empty p` |
| 15px | 15 | body default, `.addex`, `.sheet .desc` |
| 16px | 15 | `.btn`, `.mitem`, `.srow-t`, `.row-name` |
| 16.5px | 15 | `.ctitle`, `.chead .title` |
| 18px | 19 | `.empty h4` |
| 19px | 19 | `.sheet h3` |
| 20px | 19 | `.topbar h1` |
| 22px | 26 | `.shead h2` (session) |
| 24px | 26 | `.exhead h2` (chart) |
| 25px | 26 | `.data-l` |
| 26px | 26 | `.data-s` (pass2d) |
| 32px | 34 | `.data-s` (session timer) |
| 38px | 34 | chart readout value |
| 44px / 52px | 34 | `.data-hero` |

### BLOCKERS in this area — report only

**All four are closed by the fonts PR (spec §25, B2–B5). Two of the four premises below were
wrong, and the corrections are recorded in place rather than in the resolution only** — an
extraction that keeps asserting a disproved premise is how the next reader inherits it.

1. **Weight 600 is not bundled.** `.ctitle`, `.chead .title`, `.sheet h3`, `.topbar h1`,
   `.shead h2`, `.exhead h2`, `.empty h4`, `.btn`, `.prtag`, `.row-name` (500) and `.mitem.on` (500)
   all declare 600. Only 400/500 ship. **Every heading on every screen is affected.**
   **CORRECTED:** headings rendered at **400**, not at 500 and not synthesised — `text.*` was
   built at `FontWeight.Normal` throughout and nothing in the repo ever asked Plex Sans for
   Bold. The delta was a two-step jump. Resolved: 600 bundled, the three heading rungs set in
   it; `.ctitle` / `.btn` / `.prtag` deliberately left to their components.
2. **Archivo width axis.** Drawn at `"wdth" 115` (`.data-l`), `116` (`.data-s`), `122`
   (`.data-hero`) — three different widths. The bundled static instance is cut at **125**. Every
   numeral is affected, and the mockup's own three-width treatment is unreproducible with one static
   cut.
   **Resolved at 116**, the width the timer and the record value are drawn at, by instancing
   the upstream variable font. The three-width observation stands and is unimplemented: only
   bundling the VF reproduces all three, which costs 537 064 bytes. Recorded as the
   reinstatement path in `core/ui/kit/licenses/README.md`.
3. **The session timer is a second tier.** `.data-s` is **32px** in `session-v3f` and **26px** in
   `pass2d`. Same class name, two sizes. The spec records neither.
   **CORRECTED:** the class is two-valued, the *timer* is not. `pass2d` inline-overrides
   `.data-s` back to `font-size:32px` at **L221**, exactly at the timer, so the timer is 32px
   in both files and the mockups do not disagree about it. The 26px reading belongs to the
   record-hero value at `pass2d` L274. Those are two rungs — 34 and 26 — and the second is
   **`v3-redesign-spec.md` §25 B1**, not this document's own §6.2 row (see the numbering note
   there: `B<n>` is §25's alone, and this document's series is now `E<n>`). Resolved by naming
   the first: `AppTypography.timer`.
4. **letter-spacing is declared throughout and absent from `AppTypography`.** `.label` `.14em`,
   `.prtag` `.1em`, `.tempbadge` `.12em`, `.setbar` `.06em`, `.toast button` `.08em`, `.ctitle`
   `-.01em`, `.shead h2` `-.015em`, `.exhead h2` `-.02em`, `.data-hero` `-.02em`, `.topbar h1`
   `-.015em`. Negative tracking on headings and positive on mono labels — a deliberate, systematic
   treatment that currently cannot be expressed.
   **Partially resolved.** The scale can express it now, and the screen-title rung carries
   **−0.39sp** (`-.015em` at 26sp). `-.02em` was *not* taken even though `.exhead h2` declares
   it: that selector declares no `font-family` and neither does its `<button>` parent, so in
   the mockup it renders in the UA button font, not in Plex — tracking chosen against another
   typeface is not evidence about this one. The positive mono values remain **unimplemented by
   design**: every one of them is mono *and* uppercase *and* component-specific, so they belong
   on the components, the way `AppSetTypeChip` and `PersonalRecordBadge` already carry theirs.
   `.ctitle`'s `-.01em` also remains open — it is a card-title treatment sharing the body rung
   with untracked body text, and it needs a slot the six-step scale does not have.

## 0.5 Geometry ladder

`--gutter: 20px` → **16dp** (§0.3's worked example; 45 existing call sites).
`--row-h: 88px` → **88dp** (already verified from golden pixels).

| Mockup | Rung | Notes |
|---|---|---|
| 2px / 2.5px / 3px | 2dp `xxs` | pstrip gaps, rail pill gaps |
| 4px / 5px / 6px | 4dp `xs` | |
| 8px / 9px / 10px / 11px | 8dp `sm` | card gap 10, chead gap 11, set gap 9 |
| 12px / 13px / 14px | 12dp `md` | rail gap 12, field padding 12 |
| 16px / 18px | 16dp `lg` | chead padding 16, prhero padding 18 |
| 20px / 22px / 24px / 26px | 24dp `xl` | rail top 22, cards top 26 |
| 30px / 32px | 32dp `xxl` | sgroup/section-head top margin |
| 44px / 46px / 48px | 48dp | icon-btn 44, mark 46 |

Radii: 5→4, 8→8, 9→8, 10→8, 11→12*, 12→12*, 13→12*, 14→16, 16→16, 18→16, 20→16, 26→32.
*`AppDimension.Radius` has no 12 rung (4/8/16/32/64/128) — 11/12/13px land between 8 and 16.
**Reported:** the mockup's most common radius (12px, on `.field`, `.set`, `.mitem`, `.icon-btn`) has
no rung. Either it rounds to 16 (visibly rounder than drawn) or a rung is added.

## 0.6 Motion

`--d-fast: 140ms` · `--d-base: 260ms` · `--d-slow: 520ms` (pass2d only)
`--e-out: cubic-bezier(.16,1,.3,1)` · `--e-spring: cubic-bezier(.34,1.56,.64,1)`

Matches spec §5 exactly. `@media (prefers-reduced-motion: reduce)` collapses every duration to 1ms
in both mockups — a requirement the codebase should be checked against.

## 0.7 Chrome that is NOT part of the app

Two classes exist only to drive the mockup and **must not be implemented**:

- `.tools` — the fixed bottom bar with preset buttons (`2×4`/`5×4`/`8×4`/`16×5`) and a theme toggle.
- `.hint` — the dashed explanatory note at the bottom of several pass2d screens.

## 0.8 Shared components — kit candidates

Elements appearing on 2+ screens, with the screens that use them:

| Element | Screens | Kit today? |
|---|---|---|
| `.topbar` + `.icon-btn` | all five | ✗ — each screen uses stock `TopAppBar` |
| `.label` (uppercase mono caption) | all five | partial — private inside `AppSectionHeader` |
| `.section-head` (label + trailing label) | past, exercise | ✓ `AppSectionHeader` |
| `.card` / `.chead` / `.cbody` disclosure | session, past | ✗ — two separate implementations |
| `.mini` (34dp icon button) | session, past | ✗ |
| `.ordchip` / `.ord` | session, past, exercise | ✗ |
| `.field` (value + unit) | session, past | ✓ `AppNumberInput` (**wrong tier — see 0.1**) |
| `.tchip` / `.prtag` | session, past | ✓ `AppSetTypeChip` / `PersonalRecordTag` |
| `.mark` (done marker) | session | ✓ `AppCheckmarkButton` (**wrong shape — see Session**) |
| `.rail` / `.pill` / `.pstrip` | session | ✓ `AppProgressRail` (pstrip is separate) |
| `.btn` / `.btn.ghost` / `.btn.danger` | session, exercise, empty | ✓ `AppButton` (**ghost + danger differ**) |
| `.sheet` / `.grab` / `.mitem` / `.mrow` / `.msep` | session, chart, settings | ✓ `AppSheetLayout` |
| `.sw` (switch) | session sheet, settings | ✗ — settings uses stock M3 `Switch` |
| `.mseg` (segmented control) | settings | ✓ `AppSegmentedControl` |
| `.toast` | session | ✓ `AppSnackbar` |
| `.empty` | chart, lists | ✓ `AppEmptyState` |
| `.tag` | exercise, chart ranges | ✓ `AppTagChip` |
| `.row` (88dp list row) | exercise history, lists | ✓ `AppSectionRow` |

---

# Part 1 — SESSION
`session-v3f.html` · code: `feature/live-workout/`

The reviewed screen. Everything below is what the mockup says; §1.11 is the delta against code.

## 1.1 Frame

```
.topbar         back (lead) · spacer · ⋮ (trail, opens sh-session)
.session        padding 0 gutter
  .shead        h2 + .meta   |   .data-s (timer)
  .rail         the progress rail
  .railmeta     .label (mode)  ·  .label (counts)
.cards          the exercise cards
.addex          dashed "Добавить упражнение" button
.dock           sticky "Завершить сессию"
```

The screen body sits directly on `--base`. **There is no card, no Scaffold surface, and no
container around the header.** `.session` is padding only.

## 1.2 `.topbar`

- `min-height: 60px` → **48dp** rung. `display:flex; align-items:center; gap:6px; padding:6px gutter`.
- `.icon-btn`: **44×44** (→ 48dp), radius 12px, `color: --meta`.
  SVG **21×21**, `stroke-width: 1.7`, `fill:none`, round caps and joins.
  Hover: `background: --sec`, `color: --max`.
  `.lead { margin-left: -12px }` · `.trail { margin-right: -12px }` — the icons hang into the gutter
  so their **glyphs** align to the 16dp edge, not their touch targets.
- Left icon: chevron-left, path `M15 5l-7 7 7 7`.
- Right icon: **vertical three-dot** — three `<circle r="1.4">` at cy 5, 12, 19. Opens `sh-session`.
- **No title text in the session topbar.** The `<span style="flex:1">` is an empty spacer.

## 1.3 `.shead` — the session header

```html
<div class="shead">
  <div><h2>верх (с подтягиваниями)</h2><div class="meta" style="margin-top:6px" id="sMeta"></div></div>
  <div class="data-s">12:04</div>
</div>
```

`display:flex; justify-content:space-between; align-items:flex-start; gap:16px`.

| Element | Colour | Type | Notes |
|---|---|---|---|
| `h2` | `--max` / `textPrimary` | 22px → **26 rung**, IBM Plex Sans **600**, `letter-spacing: -.015em` | training name |
| `.meta` | `--meta` / `textTertiary` | 12.5 mono 400 | `margin-top: 6px` → 8dp |
| `.data-s` | `--max` | **32px** → 34 rung, Archivo `wdth 116`, `wght 700`, `line-height:1`, tabular | elapsed timer |

**`.data-s` is 32px here and 26px in pass2d — see 0.4 blocker 3.**

**Meta string, built in JS:**
`{fin} из {act} упражнений · {d} из {t} подходов`, plus ` · пропущено {sk}` only when skipped > 0.
`fin` counts exercises where every set is done; `act` excludes skipped; `d`/`t` count sets over
non-skipped exercises only.

**THIS IS NOT A CARD.** The step-5 build rendered it as one. No background, no border, no radius, no
elevation — three text elements on the page background.

## 1.4 `.rail` and `.railmeta`

```css
.rail{display:flex;margin-top:22px;height:9px;gap:12px}
.rail.m-groups{gap:6px}  .rail.m-single{gap:0}
.rail .grp{flex:1;display:flex;gap:3px;min-width:0;position:relative}
.rail .pill{flex:1;background:var(--raise);border-radius:4px;overflow:hidden;min-width:0}
.rail .pill b{display:block;height:100%;width:0;background:var(--max);border-radius:4px;
              transition:width 420ms var(--e-out)}
.rail .pill.pr b{background:var(--molten-solid)}
.rail .grp.skip .pill{background:transparent;border:1px dashed var(--hair-s)}
.rail .grp.temp::after{content:"";position:absolute;left:0;right:0;bottom:-5px;
                       border-bottom:1px dashed var(--dim)}
.railmeta{display:flex;justify-content:space-between;margin-top:13px}
```

- Height **9px → 8dp**. Top margin 22px → **24dp**. `.railmeta` top 13px → **12dp**.
- Track `--raise` / `surfaceTier4`; fill `--max`; **PR fill `--molten-solid`**; radius 4px → 4dp.
- Fill transition **420ms** — a fourth duration, not in `--d-*` and not in §5.
- **Skipped group**: pills transparent with a **1px dashed `--hair-s`** outline.
- **One-off group**: a **1px dashed `--dim` underline 5px below the group** (`::after`).

**Three modes, chosen by measured width** (`railMode()`):

```js
if ((w - (nEx-1)*12 - (total-nEx)*3) / total >= 9)  return 'sets';
if ((w - (LV.length-1)*6) / LV.length  >= 11)       return 'groups';
return 'single';
```

**Two thresholds — 9px for sets and 11px for groups.** Spec §8 records only the 9 and calls it
unverified; the 11 is undocumented entirely.

`.railmeta` left label, by mode:
`детализация: подходы` / `детализация: упражнения` / `детализация: общая`
Right label: `{n} упражнений · {t} подходов`, plus ` · разовых: {tmp}` when one-offs exist.
Both are `.label` (11px mono 500, `.14em`, uppercase, `--dim`).

## 1.5 `.card` — the exercise card, all five states

```css
.cards{padding:0 var(--gutter);margin-top:26px;display:flex;flex-direction:column;gap:10px}
.card{background:var(--sec);border-radius:18px;overflow:hidden;position:relative}
.card.active{background:var(--slab);box-shadow:var(--slabtop)}
.card.skip{opacity:.5}
```

Radius 18px → **16dp**. Gap 10px → **8dp**. Top margin 26px → **24dp**.

**There is no border in any state.** Active is marked by **surface change `sec` → `slab` plus
`--slabtop`** (§0.2). Nothing else.

### Head

```
.chead  (padding 16 → 16dp, gap 11 → 8dp, align-items: flex-start, cursor: pointer)
  .ordchip
  .chead-body
    .ctitle-row   .ctitle + .tempbadge
    .sub
    .pstrip
  .chead-act      [.mini.info] .mini.menu .mini.rot
```

**`.ordchip` — 24×24, radius 8px, mono 11.5px. This is the state indicator:**

| Card state | background | colour | border | content |
|---|---|---|---|---|
| resting | transparent | `--dim` | none | the number |
| `.active` | **`--max`** | `--base` | none | the number, weight 500 |
| `.fin` | **`--donefill`** | `--meta` | none | **a checkmark SVG — the number is hidden** |
| `.skip` | transparent | `--dim` | **1px dashed `--hair-s`** | the number |
| `.temp` | transparent | `--dim` | **1px dashed `--dim`** | the number |
| `.temp.active` | transparent | `--max` | 1px dashed `--max` | number, weight 500 |
| `.temp.fin` | transparent | `--meta` | 1px dashed `--meta` | number |

Checkmark: `<svg width=13 height=13 stroke=currentColor stroke-width=3 fill=none>` path
`M4 12.5l5 5L20 7`. `.card.fin .ordchip svg{display:block}` + `.card.fin .ordchip i{display:none}`.

**"Done" for an exercise is: a `donefill` chip whose number is replaced by a checkmark, plus the
title going `--meta`/weight-500. It is NOT an opacity change.**

`.ctitle` — 16.5px → **15 rung**, weight **600**, `--max`, `letter-spacing:-.01em`, line-height 1.25.
- `.card.fin .ctitle` → `color:--meta; font-weight:500`
- `.card.skip .ctitle` → `color:--meta; text-decoration:line-through; decoration-color:--dim`

`.tempbadge` — the one-off marker, `display:none` until `.card.temp`.
mono **9.5px → 11 rung**, `.12em`, uppercase, `color:--body`, **1px dashed `--dim`**, radius 5px → 4dp,
padding 2px 6px. String: **`разовое`**.

`.sub` — mono **12px → 12.5**, `--dim`, `margin-top:5px`, single line + ellipsis.
Content is **always the plan** (mockup comment: "подстрочник всегда план — длина стабильна, карточка
не едет"), or the literal `пропущено` when skipped.
`planText`: weighted → `{w}×{r} · {w}×{r}`; bodyweight → `{r} повт · {r} повт`.

`.pstrip` — a per-exercise miniature of the rail. `height:4px; gap:2.5px; margin-top:10px`.
Segments `--raise`, fill `--max`, PR fill `--molten-solid`, radius 2px, transition **380ms** (a
fifth duration). Skipped: transparent segments, 1px dashed `--hair-s`, no fill.

`.chead-act` — `gap:2px; margin:-6px -6px 0 0` (hangs into the padding).
`.mini` — **34×34**, radius 9px → 8dp, `color:--dim`, SVG **17×17** `stroke-width:1.8`.
Hover: `color:--body; background:--hair`.

Three buttons, in order:
1. `.mini.info` — **only when the exercise has a description.** `<circle cx=12 cy=12 r=9/>` +
   `<path d="M12 11v5M12 7.6v.1"/>`. Opens `sh-desc`.
2. `.mini.menu` — vertical three dots. Opens `sh-ex`.
3. `.mini.rot` — **chevron-right** `M9 6l6 6-6 6`, with
   `.card.active .mini.rot svg{transform:rotate(90deg)}` over `--d-base --e-out`.
   **The expand affordance is a rotating chevron-right — not a caret-down, not `ExpandMore`.**

Tapping `.chead` toggles open, **except** on `.info` or `.menu`.

### Body

`.cbody{display:grid;grid-template-rows:0fr;transition:grid-template-rows 260ms --e-out}` and
`.card.active .cbody{grid-template-rows:1fr}` — a **grid-row height animation**, i.e. a smooth
expand/collapse, not a visibility toggle. `.sets{padding:0 12px}` → 12dp.

## 1.6 `.set` — the set row

```css
.set{display:flex;align-items:center;gap:9px;padding:9px 4px;
     border-top:1px solid var(--hair);border-radius:12px}
.set:first-child{border-top:0}
.set-i{font-family:mono;font-size:12px;color:var(--dim);width:13px;flex:none}
.field{flex:1;background:var(--field);border-radius:12px;height:52px;padding:0 12px;
       display:flex;align-items:center;justify-content:space-between}
.set.done .field{background:var(--donefill)}
.set.done .data-l{color:var(--max)}
.set.pr .field{background:var(--molten-bg)}
.set.pr .data-l{color:var(--molten)}
```

Order: **`.set-i` → field(s) → `.tchip` or `.prtag` → `.mark`**.

- `.set-i` — mono 12px → 12.5, `--dim`, **width 13px → 12dp**, `flex:none`.
- `.field` — **`--field` = `surfaceTier3`**, height **52px → 48dp**, radius 12px, padding 0 12px,
  `justify-content:space-between` so value sits left and unit right.
  - `.data-l` — Archivo `wdth 115` `wght 700`, 25px → **26 rung**, `line-height:1`, tabular.
    **Resting colour is `--idle`**; `.set.done` promotes it to `--max`. A pending value is dimmer
    than a completed one — §1's brightness principle in miniature.
  - `.unit` — mono **11.5px → 11 rung**, `--dim`.
- **Weighted**: two fields — `{w}` `кг` and `{r}` `повт`.
- **Bodyweight**: **one field with `style="flex:2"`**, unit **`повторений`** (full word, not `повт`).
- Trailing chip: `.tchip` (`·`) **or** `.prtag` (`PR`) — never both.
  Shared geometry `min-width:34px; height:32px; border-radius:9px`, mono.
  `.tchip` — 1px `--hair-s`, `--dim`, 12px. `.prtag` — 1px `--molten-br`, `--molten`, 10px,
  weight **600**, `.1em`, padding 0 6px.

### `.mark` — the done marker. **Specify exactly; step 5 got this wrong.**

```css
.mark{width:46px;height:46px;position:relative;display:grid;place-items:center}
.mark .shape{position:absolute;inset:4px;border-radius:50%;border:2px solid var(--hair-s);
             background:transparent;
             transition:border-radius 260ms --e-spring, background 260ms --e-out,
                        border-color 260ms --e-out, inset 260ms --e-spring, transform 260ms --e-spring}
.mark .pulse{position:absolute;inset:4px;border-radius:50%;border:2px solid var(--max);opacity:0}
.mark svg{position:relative;width:19px;height:19px;stroke:var(--base);stroke-width:2.7;fill:none}
.mark svg path{stroke-dasharray:26;stroke-dashoffset:26;transition:stroke-dashoffset 260ms --e-out 60ms}
.set.done .mark .shape{border-radius:13px;background:var(--max);border-color:var(--max);inset:2px}
.set.done .mark svg path{stroke-dashoffset:0}
.mark:active .shape{transform:scale(.9)}
.set.pr.done .mark .shape{background:var(--molten-solid);border-color:var(--molten-solid)}
```

| | resting | done |
|---|---|---|
| shape | **circle** (radius 50%), 38×38 (inset 4) | **squircle, radius 13px**, 42×42 (inset 2) |
| fill | transparent | **`--max`** (or `--molten-solid` if PR) |
| ring | **2px `--hair-s`** | 2px, same as fill |
| tick | present but **fully undrawn** (`dashoffset:26`) | **stroked in** over 260ms, 60ms delay |
| tick colour | — | **`--base`** — the page colour, on the filled shape |

Pressed: `scale(.9)`. **The morph is circle → rounded-square and the checkmark draws itself in.**
Not a checkbox, not a colour swap, and the resting ring is dim `--hair-s`, not an accent.

### Transients

```css
.set.flash{animation:rf 620ms var(--e-out)}
@keyframes rf{0%{background:var(--flash)}100%{background:transparent}}
.set.flash .mark .pulse{animation:rp 560ms var(--e-out)}
@keyframes rp{0%{opacity:.75;transform:scale(1)}100%{opacity:0;transform:scale(1.95)}}
.card.prfx::after{content:"";position:absolute;inset:0;pointer-events:none;
  background:linear-gradient(105deg,transparent 32%,var(--molten-bg) 44%,rgba(249,115,22,.3) 50%,transparent 70%);
  animation:sweep 900ms var(--e-out) forwards}
@keyframes sweep{from{transform:translateX(-120%)}to{transform:translateX(120%)}}
```

- **Row flash** — 620ms wash from `--flash` to transparent.
- **Mark pulse** — a `--max` ring expanding to 1.95× while fading, 560ms.
- **PR sweep** — a 105° molten gradient traversing the **card** (not the row), 900ms, class removed
  after 1000ms.
- Haptics: `navigator.vibrate(pr ? [12,40,22] : 10)`.

Fired **only on the false→true transition**, inside the click handler — never on render. That is
exactly the §10.2 defect the goldens locked in.

## 1.7 `.setbar` — set add/delete. **Missing entirely from the build.**

```css
.setbar{display:flex;border-top:1px solid var(--hair)}
.setbar button{flex:1;background:none;border:0;color:var(--meta);font-family:mono;font-size:12px;
               letter-spacing:.06em;text-transform:uppercase;padding:15px 0 14px}
.setbar button:hover{color:var(--max)}
.setbar button+button{border-left:1px solid var(--hair)}
.setbar button:disabled{opacity:.35;cursor:default}
```

Two equal-width buttons at the foot of every expanded card: **`+ подход`** and **`− подход`**,
separated by a 1px `--hair` rule, with a 1px `--hair` rule above the pair.
**`− подход` is disabled when `sets.length <= 1`.**
Add: appends a set copying the last one's `w`/`r`, un-collapses the card if the user had closed it,
toasts `Подход добавлен`. Delete: `pop()`, toasts `Подход удалён`. Both snapshot for undo.

## 1.8 `.addex` and `.dock`

`.addex` — a **dashed** full-width button below the cards.
`height:60px → 48dp; margin:12px gutter 0; border:1px dashed var(--hair-s); border-radius:16px;
color:--meta; font-size:15px`, centred, `gap:9px`, plus icon 17×17 `M12 5v14M5 12h14`.
Hover: `border-color:--dim; color:--body`. String **`Добавить упражнение`**.

`.dock` — `position:sticky; bottom:0; padding:16px gutter 20px`, background
**`linear-gradient(to top, var(--base) 62%, transparent)`** so content scrolls out under it.
One `.btn`: **`Завершить сессию`**.

`.btn` — height **56px → 48dp**, radius 16px, `background:--max; color:--base`, IBM Plex Sans
**16px → 15 rung, weight 600**, `:active{transform:scale(.985)}`.
`.btn.ghost` — `background:--field; color:--body`.
`.btn.danger` — `background:none; color:--rust; font-weight:500` — a **text** button.

## 1.9 Sheets — four, plus a toast

**§14 lists three. There are four.** `sh-desc` is absent from the spec.

All sheets: `background:--field` (**`surfaceTier3`**), `border-radius:26px 26px 0 0` → 32dp,
`padding:10px 20px 32px`, entering by `translate(-50%,110%) → 0` over 260ms `--e-out`.
`.grab` — 36×4, radius 2, `--hair-s`, `margin:0 auto 20px`.
`.sheet h3` — 19px, weight **600**, `--max`, `margin:0 0 12px`.
Scrim `--scrim` = `rgba(4,5,6,.66)` dark / `rgba(13,17,20,.34)` light.

| id | Title | Content |
|---|---|---|
| `sh-ex` | the exercise name | `.mrow` one-off switch **(only when `adhoc`)** + `.msep`, then `.mitem` skip, then `.mitem.rust` delete |
| `sh-del` | `Удалить из плана тренировки?` | body `<p>`, then `.stack` of `.btn.ghost` **`Оставить`** and `.btn.danger` **`Удалить из плана`** |
| `sh-desc` | the exercise name | `.desc` free text, then `.btn.ghost` **`Закрыть`** |
| `sh-session` | *(none)* | `.mitem` **`Добавить упражнение`**, `.mitem` **`Изменить порядок`**, `.mitem.rust` **`Отменить сессию`** |

`.mitem` — full width, `padding:15px 4px`, `gap:13px`, 16px → 15 rung, `--body`, radius 12px,
leading SVG 19×19 `stroke-width:1.8`. `.mitem.rust` → `color:--rust`.
`.mrow` — `padding:12px 4px; gap:14px`; `.t` is 16px `--max`; sub-line is `.meta`.
`.msep` — `height:1px; background:--hair; margin:6px 0`.

`.sw` — **46×28**, radius 14px, track `--hair-s` → **`--max`** when on; knob 22×22 circle at
`top:3px left:3px`, `background:--meta` → **`--base`** when on, `translateX(18px)`, `--e-spring`.

**Strings:**
- one-off row: **`Только на сегодня`** / sub **`останется в этой сессии, но не попадёт в план тренировки`**
- skip item: **`Пропустить упражнение`** ⇄ **`Вернуть в сессию`** when already skipped
- delete item: **`Удалить упражнение`**
- `sh-del` body, **adhoc**: `«{name}» было добавлено в этой сессии. Записанные подходы пропадут.`
- `sh-del` body, **planned**: `«{name}» исчезнет из плана тренировки и не появится в следующих
  сессиях. Если не хочешь делать его только сегодня — лучше пропустить.`

`.toast` — `bottom:118px`, `background:--slab`, **1px `--hair-s` border**, radius 16px,
`padding:14px 16px`, `box-shadow:0 14px 40px rgba(0,0,0,.4)`, auto-dismiss **5000ms**.
Text 14.5px `--max`; action mono 12px `.08em` uppercase **`--molten`**, label **`Отменить`**.
Toast strings: `Подход добавлен` · `Подход удалён` · `«{name}» добавлено` · `«{name}» удалено из плана`.
Names truncated to **24 chars + `…`**.

## 1.10 Disclosure automaton (JS, verbatim in behaviour)

```js
isOpen(e): skip → false; userOpen===true → true; userOpen===false → false;
           isDone → false; hasProgress → true; else slot===e.id
nextSlot(): first e where !skip && !isDone && !hasProgress && userOpen!==false
```
`hasProgress` = some set done **and not all**. `isDone` = non-empty and all done.
Matches spec §7. Auto-collapse on completion is deferred **420ms**
(`setTimeout(apply, isDone(e) ? 420 : 0)`) so the fill animation finishes first.

## 1.11 DELTA — mockup vs `feature/live-workout/` as it stands

| Element | Mockup says | Code does | Verdict |
|---|---|---|---|
| Session header | three texts directly on `--base` | rendered as a **card** | **differs** |
| Active card | `--slab` + `--slabtop`, **no border** | `surfaceTier1` always + **1dp `accent` border** | **differs** |
| Done card | ordchip → `donefill` + checkmark; title → `meta`/500 | **alpha fade** (`DONE_ALPHA`) | **differs** |
| Skipped card | `opacity:.5` + strikethrough title | alpha fade, no strikethrough | partial |
| One-off card | dashed `--dim` ordchip + `разовое` badge + dashed rail underline | — | **missing** |
| `.ordchip` | 24dp chip, 7 state variants, checkmark replaces number | plain `Text` ordinal | **missing** |
| Done marker | circle → **13px squircle**, fill `--max`, tick **strokes in**, ring `--hair-s` | `CircleShape` in both states, static `Icons.Filled.Check`, ring **`accent`** | **differs** |
| Expand icon | **chevron-right rotating 90°** in a 34dp `.mini` | — | **differs** |
| Info button | `.mini.info` when a description exists → `sh-desc` | — | **missing** |
| `.setbar` | `+ подход` / `− подход`, delete disabled at 1 set | `+ Добавить сет` only | **missing** |
| `.field` surface | `--field` = **`surfaceTier3`** | `AppNumberInput` paints **`surfaceTier2`** | **differs** |
| Done field | `--donefill` wash on the **field** | **whole row** → `surfaceTier4` | **differs** |
| `.data-l` resting | `--idle`, promoted to `--max` when done | — | **differs** |
| Bodyweight row | **one** field, `flex:2`, unit `повторений` | two fields | **differs** |
| `.pstrip` | per-exercise 4px micro-rail in the card head | — | **missing** |
| `.sub` | always the plan, single line, ellipsis | status text varies by state | **differs** |
| `sh-ex` | one-off switch (adhoc only) · skip · **delete** | Изменить план · Сбросить сеты · Пропустить | **differs** |
| `sh-desc` | exercise description sheet | — | **missing** |
| `sh-del` | two-button plan-removal sheet, adhoc/planned copy | — | **missing** |
| Skip | menu item, reversible in place, **no confirmation** | confirmation **dialog** | **differs** |
| **Set noun** | **`подход`** | **`сет`** | **differs** |
| Rail thresholds | **9px** sets / **11px** groups | 9dp only | **differs** |

### The string finding, separately

The app says **`сет`**; the mockup says **`подход`**. `подход` is the standard Russian term and is
already what `feature/past-session` uses (`%d подход/подхода/подходов`, `Подходы не записаны`).
The two screens currently disagree with each other, and live-workout is the one that disagrees with
the mockup. Affected: `feature_live_workout_add_set`, `feature_live_workout_set_count`,
`feature_live_workout_status_set_count`, `feature_live_workout_status_progress_format`,
`feature_live_workout_reset_*`, `feature_live_workout_finish_stat_sets`, and the finish-dialog body.

Also present in code and **absent from the mockup entirely**: `Изменить план` (edit plan) and
`Сбросить сеты` (reset sets) as exercise-menu actions, and the reset confirmation dialog.
Report only — removing them is a scope decision.
---

# Part 2 — PAST SESSION
`pass2d.html` §`s-past` · code: `feature/past-session/`

**This screen was reworked on `feature/v3-screens` (2 commits) from the seven-line §15 skeleton, not
from the mockup. The delta below is deliberately critical of that rework.**

## 2.1 Frame

```
.topbar         back (lead) · h1.sm "низ — 2" · ⋮ (trail)
div             padding 0 gutter
  .label        "Завершена · 23 июля 2026"
  .data-hero    "56:08"            (margin-top 10px, font-size 44px inline override)
  .meta         "5 упражнений · 14 подходов · 4 820 кг"   (margin-top 10px)
.section-head   .label "Записано"   ·  .label "можно править"
.cards          the exercise cards
```

No dock. No FAB. The header is **plain content on `--base`**, exactly as on Session.

## 2.2 `.topbar`

Same `.topbar` / `.icon-btn` as §1.2, but **with a title**:

```html
<h1 class="sm">низ&nbsp;— 2</h1>
```

`.topbar h1{font-size:20px;font-weight:600;color:var(--max);flex:1;letter-spacing:-.015em}`
`.topbar h1.sm{font-size:17px}` → **15 rung** (17 is nearer 15 than 19).

Trailing button is the **vertical three-dot** overflow, same glyph as Session. It is not drawn as a
delete icon.

## 2.3 The header block

| Element | String | Colour | Type | Geometry |
|---|---|---|---|---|
| `.label` | `Завершена · 23 июля 2026` | `--dim` | mono 11 → 11 rung, weight 500, **`.14em`**, **uppercase** | — |
| `.data-hero` | `56:08` | `--max` | Archivo `wdth 122` `wght 700`, **44px inline → 34 rung**, `line-height:1`, `letter-spacing:-.02em`, tabular | `margin-top:10px` → 8dp |
| `.meta` | `5 упражнений · 14 подходов · 4 820 кг` | `--meta` | mono 12.5 → 12.5 | `margin-top:10px` → 8dp |

Note the base `.data-hero` rule is **52px** and the past screen overrides it inline to **44px**. Both
round to the 34 rung.

**Tonnage** is the third term of the `.meta` line (§11.1, in scope). The mockup groups it with a
**narrow no-break space**: `4 820 кг`.

## 2.4 `.section-head`

```css
.section-head{padding:0 var(--gutter);margin:32px 0 12px;display:flex;
              justify-content:space-between;align-items:baseline}
```

Two `.label`s: **`Записано`** and **`можно править`**. They are peers — same class, same style. The
right one declares the mode. Margin 32px → **32dp** top, 12px → **12dp** bottom.
`align-items: baseline`, not centre.

## 2.5 `.card` — two states only

```css
.card{background:var(--sec);border-radius:18px;overflow:hidden}
.card.open{background:var(--slab);box-shadow:var(--slabtop)}
.cards{padding:0 var(--gutter);margin-top:26px;display:flex;flex-direction:column;gap:10px}
```

**An open card lifts to `--slab` (`surfaceTier2`) with `--slabtop`.** Same signature as Session's
`.card.active`. There is no border in either state.

### Collapsed

```html
<div class="card"><div class="chead">
  <span class="ord">2</span>
  <div class="chead-body">
    <div class="title">приседания с мешком</div>
    <div class="plan-line">10×15 · 10×15 · 10×15</div>
  </div>
  <svg class="chev" style="margin-top:4px"><path d="M9 6l6 6-6 6"/></svg>
</div></div>
```

- `.ord` — mono **12.5px**, `--dim`, **width 16px → 16dp**, `flex:none`.
  **This is `.ord`, not Session's `.ordchip`** — a bare number, no chip, no background, no states.
- `.chead .title` — **16.5px → 15 rung**, weight **600** (inherited from `.title`), `--max`,
  `line-height:1.25`, `letter-spacing:-.01em`.
- `.plan-line` — mono **12px → 12.5**, `--dim`, `margin-top:4px` → 4dp.
- `.chev` — a **bare 18×18 SVG**, `stroke:--dim`, `stroke-width:1.8`, `flex:none`,
  `margin-top:4px`. **Not a button, not in a `.mini`, and it does not rotate.**

### Open

```html
<div class="card open"><div class="chead">
  <span class="ord">1</span>
  <div class="chead-body"><div class="title">разведение ног</div></div>
</div><div class="cbody">…</div></div>
```

**The open card has no `.plan-line` and no chevron at all.** The summary is replaced by the rows
themselves, and the affordance disappears. `.chead` is still the tap target.

## 2.6 `.set` — the logged set row

```html
<div class="set">
  <span class="set-i">1</span>
  <div class="field"><span class="data-l" style="color:var(--max)">49</span><span class="unit">кг</span></div>
  <div class="field"><span class="data-l" style="color:var(--max)">15</span><span class="unit">повт</span></div>
  <div class="tchip">·</div>
</div>
<div class="set pr">
  <span class="set-i">3</span>
  <div class="field"><span class="data-l">77</span><span class="unit">кг</span></div>
  <div class="field"><span class="data-l">15</span><span class="unit">повт</span></div>
  <span class="prtag">PR</span>
</div>
```

Identical geometry to Session's `.set` (§1.6) minus the `.mark`. Note precisely:

- **Ordinary rows carry an inline `style="color:var(--max)"` on `.data-l`.** The base `.data-l`
  colour is `--idle`; on a *past* session every set is complete, so the value is at full contrast.
  An implementer must render logged values at `--max`, not at the resting `--idle`.
- **PR rows do NOT carry that override** — `.set.pr .data-l{color:var(--molten)}` wins, and
  `.set.pr .field{background:var(--molten-bg)}` washes both fields molten.
- The trailing slot is `.tchip` **or** `.prtag`, never both. Same 34×32 / radius 9px geometry.
- **There is no drag handle drawn.** See the delta.
- **There is no `.mark`** — nothing to complete.

## 2.7 Surfaces reachable

| Surface | Source | In §15? |
|---|---|---|
| topbar overflow `⋮` menu | mockup markup (no target drawn) | ✗ |
| Delete-session confirmation | code `DeleteConfirmDialog` | ✗ |
| PR explainer | code `PrExplainerDialog`, opened from the PR tag | ✗ |
| deleted / save-failed snackbars | code `Event.DeletedSnackbar`, `SaveFailedSnackbar` | ✗ |
| error state + retry | code `ErrorContent` → `AppEmptyState` | ✗ |

§15 describes a frame, not a surface. Every one of these is undocumented there.

## 2.8 DELTA — mockup vs code **after** the `feature/v3-screens` rework

| Element | Mockup says | Code does (post-rework) | Verdict |
|---|---|---|---|
| Header container | plain on `--base` | plain on `--base` — `AppCard` removed | **matches** |
| Header label | uppercase mono, `--dim` | uppercase `mono.caption`, `textTertiary` | matches (tier collapse, §0.3) |
| Duration | Archivo 44px → 34 rung | `numeric.display` | **matches** |
| Tonnage | third term of `.meta` | third term of `totalsLabel` | **matches** |
| `.section-head` | `Записано` / `можно править` | `AppSectionHeader(label, trailingLabel)` | **matches** |
| **Open card surface** | **`--slab` + `--slabtop`** | **`surfaceTier1` always, no elevation** | **differs** |
| **Collapsed chevron** | bare 18dp `.chev`, `--dim`, **static** | 18dp icon that **rotates 90°** | **differs** |
| **Open card chevron** | **absent entirely** | still rendered, rotated | **differs** |
| **Open card plan-line** | **absent** | correctly hidden | matches |
| Ordinal | `.ord`, 16dp, mono 12.5, `--dim` | `mono.meta`, 16dp, `textTertiary` | matches |
| Card radius | 18px → 16dp | 16dp | matches |
| Set row order | `set-i · field · field · chip-or-tag` | same | **matches** |
| Units in fields | `кг` / `повт` | `кг` / `повт` via `suffix` | **matches** |
| **Logged value colour** | **`--max`** (inline override) | `AppNumberInput` default `textPrimary` | matches |
| **PR row field wash** | **`--molten-bg` on both fields** | no wash — only the tag changes | **differs** |
| **PR row value colour** | **`--molten`** | unchanged `textPrimary` | **differs** |
| `.prtag` | 1px `--molten-br`, `--molten`, 10px/600/`.1em` | `PersonalRecordTag`, `mono.caption`, 1.1sp tracking | matches |
| **`.field` surface** | **`--field` = `surfaceTier3`** | `AppNumberInput` paints **`surfaceTier2`** | **differs** |
| **Drag handle** | **not drawn** | rendered, gesture live | **differs — kept deliberately, flagged** |
| Topbar trailing | **`⋮` overflow** | a **Delete icon** tinted `status.error` | **differs** |
| Topbar title size | 17px → 15 rung | `headlineSmall` M3 alias | **differs** |
| Disclosure default | first open, rest closed | first open, rest closed | matches |

### What my own rework got wrong, stated plainly

Three things, all traceable to building from §15 rather than the mockup:

1. **The open card does not lift.** The mockup's whole disclosure signal is `sec → slab` plus
   `--slabtop`; the rework keeps `surfaceTier1` in both states and signals only via the chevron.
2. **The chevron is wrong twice** — it should be a static bare glyph on collapsed cards and
   **absent** on open ones; the rework animates a rotation and keeps it in both states.
3. **The PR row is under-treated.** The mockup washes both fields `--molten-bg` and turns the values
   `--molten`; the rework changes only the trailing tag. The `setPersonalRecord` golden therefore
   locks in a weaker treatment than drawn — a live example of §10.2.

Item 3 also means the contrast pair `record.textPrimary` on `record.background` (already declared in
`ContrastContract` for `PersonalRecordCard`) gains a second call site.
---

# Part 3 — EXERCISE DETAIL
`pass2d.html` §`s-ex` · code: `feature/exercise/ui/ExerciseDetailScreen.kt` (470 lines)

## 3.1 Frame

```
.topbar        back · h1.sm "Отведение гантелей через стороны" · ⋮
div            tag row — padding 0 gutter, display flex, gap 8px, margin-bottom 16px
.prhero        the record block
.section-head  .label "План по умолчанию"
.plancard      4 × .planline
.section-head  .label "История"  ·  .label "4 сессии"
.list          3 × .row
.dock          .btn.ghost "Изменить"  +  .btn "Записать сейчас"
```

## 3.2 Tag row

```html
<div style="padding:0 var(--gutter);display:flex;gap:8px;margin-bottom:16px">
  <span class="tag" style="cursor:default">С весом</span>
  <span class="tag" style="cursor:default">верх</span>
</div>
```

```css
.tag{padding:8px 13px;border-radius:10px;background:var(--sec);color:var(--meta);
     font-size:14px;cursor:pointer;border:1px solid transparent}
.tag.on{background:var(--raise);color:var(--max);border-color:var(--hair-s)}
```

Padding 8/13 → **8dp / 12dp**. Radius 10px → **8dp**. Type 14px → **15 rung**.
On this screen the tags are **`cursor:default`** — display only, not filters. The `.on` variant is
not used here (it belongs to the chart's range chips).
Strings: **`С весом`** (the exercise type) and **`верх`** (a muscle-group tag).

## 3.3 `.prhero` — the record block

```css
.prhero{margin:6px var(--gutter) 0;border:1px solid var(--molten-br);background:var(--molten-bg);
        border-radius:20px;padding:18px;display:flex;justify-content:space-between;
        align-items:center;gap:16px}
```

```html
<div class="prhero">
  <div><div class="label" style="color:var(--molten)"><span class="mdot"></span>Рекорд</div>
       <div class="meta" style="margin-top:7px">12 июля 2026 · верх (с подтягиваниями)</div></div>
  <div style="text-align:right"><span class="data-s" style="color:var(--molten)">9<span class="x" style="color:var(--molten)">×</span>12</span></div>
</div>
```

| Property | Value | Rung |
|---|---|---|
| border | **1px `--molten-br`** (`rgba(240,162,46,.42)` dark / `rgba(194,65,12,.34)` light) | 1dp |
| background | **`--molten-bg`** (`rgba(240,162,46,.09)` / `rgba(249,115,22,.11)`) | — |
| radius | 20px | **16dp** |
| padding | 18px | **16dp** |
| margin | `6px gutter 0` | 8dp / 16dp |

- **`.mdot`** — `width:9px;height:9px;border-radius:50%;background:var(--molten-solid);
  display:inline-block;margin-right:7px;vertical-align:middle`. A **9dp molten dot** preceding the
  word. → 8dp rung, `margin-right` 7px → 8dp.
- Label **`Рекорд`** — `.label` (mono 11, `.14em`, uppercase, weight 500) but **colour overridden to
  `--molten`**, not `--dim`.
- Sub-line — `.meta` (mono 12.5, `--meta`), `margin-top:7px` → 8dp.
  String: `12 июля 2026 · верх (с подтягиваниями)` — date · the training it was set in.
- Value — `.data-s` (Archivo `wdth 116` `wght 700`, **26px → 26 rung**, tabular),
  **colour `--molten`**, right-aligned.
  - **`.x`** — the `×` separator, `color:var(--dim); margin:0 1px`. Here it is **also overridden to
    `--molten`**. Elsewhere (`.val .x`) it stays `--dim`.
  - Format: `9×12` — weight × reps.

**Contrast note:** `molten.text` on `record.background` composited over `--base` is the pair the
spec already measured at 4.33:1 (page) and forced the light molten value to `#BE3E0C`. This block is
the `PersonalRecordCard` call site already declared in `ContrastContract`.

## 3.4 Default-plan section

`.section-head` with a **single** label: **`План по умолчанию`** (no trailing label).

```css
.plancard{margin:16px var(--gutter) 0;background:var(--sec);border-radius:18px;padding:6px 16px}
.planline{display:flex;align-items:center;justify-content:space-between;padding:13px 0;
          border-top:1px solid var(--hair)}
.planline:first-child{border-top:0}
```

- `.plancard` — `--sec` / `surfaceTier1`, radius 18px → **16dp**, padding `6px 16px` → 8dp/16dp.
- `.planline` — `padding:13px 0` → **12dp**, separated by 1px `--hair`, **no rule above the first**.
- Each line: `.ord` on the left (mono 12.5, `--dim`, width 16px), `.val` on the right.
- **`.val`** — `font-family:mono; font-size:15px; font-weight:500; color:var(--body);
  tabular-nums`. → **15 rung**, `textSecondary`.
- `.val .x` — the `×`, `color:var(--dim); margin:0 1px`.
- Content in the mockup: four identical lines `7×12`.

**Note the two numeric treatments on one screen:** the record hero uses **Archivo** (`.data-s`), the
plan lines use **IBM Plex Mono** (`.val`). They are not the same family and must not be unified.

## 3.5 History section

`.section-head` with **two** labels: **`История`** · **`4 сессии`**.

```css
.list{border-top:1px solid var(--hair-s)}
.row{display:flex;align-items:center;gap:14px;padding:0 var(--gutter);min-height:var(--row-h);
     overflow:hidden;border-bottom:1px solid var(--hair-s);cursor:pointer}
.row:hover{background:var(--sec)}
.row-body{flex:1;min-width:0;display:flex;flex-direction:column;justify-content:center;gap:6px}
.row-name{font-size:16px;font-weight:500;color:var(--max);line-height:1.25;
          -webkit-line-clamp:2;max-height:2.5em;overflow:hidden}
.chev{width:18px;height:18px;stroke:var(--dim);stroke-width:1.8;fill:none;flex:none}
```

- `.list` has a **1px `--hair-s` rule above the first row**; each `.row` has one **below**. So the
  list is fully ruled top and bottom — unlike `AppSection`, which forbids exactly this.
  **Conflict with spec §3.1 / `AppSection`'s "separation is air and a label, never a line".**
- `.row` — **`min-height: 88px`** (`--row-h`) → **88dp**, gap 14px → 12dp, no radius, no background.
- `.row-name` — 16px → **15 rung**, weight **500**, `--max`, clamped to **2 lines**.
- Sub-line — `.meta` (mono 12.5, `--meta`), `gap:6px` → 8dp.
- Trailing: `.chev` **or** `.prtag`.

Three rows drawn:

| Date | Sub-line | Trailing |
|---|---|---|
| `22 июля` | `7×12 · 7×12 · 7×12 · 7×12` | `.chev` |
| `12 июля` | `5×12 · 6×12 · 9×12 · 7×12` | **`.prtag` "PR"** |
| `23 июня` | `5×12 · 5×12 · 5×12 · 5×12` | `.chev` |

`.row-name` here uses `style="font-size:15px"` inline, overriding the class's 16px → **15 rung**
either way.

**The PR row replaces the chevron with the tag** — same "chip or tag, never both" rule as the set
rows, and it means the record row loses its navigation affordance while remaining tappable.

## 3.6 `.dock`

```css
.dock{position:sticky;bottom:0;margin-top:auto;padding:16px var(--gutter) 20px;
      background:linear-gradient(to top,var(--base) 62%,transparent);display:flex;gap:12px}
.btn{flex:1;height:56px;border:0;border-radius:16px;font-size:16px;font-weight:600;
     background:var(--max);color:var(--base)}
.btn.ghost{background:var(--field);color:var(--body)}
```

Two buttons, `gap:12px` → 12dp:
- **`Изменить`** — `.btn.ghost` with `style="flex:0 0 130px"` → a fixed **130px → 128dp** width.
- **`Записать сейчас`** — `.btn` (primary), taking the remaining space.

Note the pass2d `.dock` is `display:flex` with a gap; the session `.dock` is not. Same class, two
layouts.

## 3.7 Surfaces reachable

| Surface | Source |
|---|---|
| topbar `⋮` overflow | mockup (no target drawn) |
| history row → past session | mockup `.row` is `cursor:pointer` |
| `Изменить` → edit screen | mockup dock |
| `Записать сейчас` → start session | mockup dock |
| image viewer / image source dialog | **code only** (`ImageEditRow`, `ImageSourceDialog`) |
| tag picker | **code only** (`TagPickerInline`) |
| delete / archive confirmations | **code only** |

**The mockup does not draw the exercise image, the tag editor, or archiving.** They exist in code.
Report only — whether the mockup is incomplete or the features are out of the redesign is Ilya's call.

## 3.8 DELTA — mockup vs `feature/exercise/`

| Element | Mockup says | Code does | Verdict |
|---|---|---|---|
| Frame | topbar · tags · prhero · plan · history · dock | `Scaffold` + `TopAppBar`, `AppCard` sections | **differs** |
| `.prhero` | 1px `--molten-br`, `--molten-bg`, radius 16dp, mdot + `Рекорд` + date + `9×12` | `PersonalRecordCard` exists — verify geometry, the 9dp dot, and the `.x` colour | verify |
| Type tag | `.tag` — `--sec` bg, `--meta`, 15 rung | — | verify |
| Plan card | `--sec`, ruled `.planline`s, `.ord` + `.val` (mono 500) | `AppCard` + plan rows | verify |
| Section heads | `AppSectionHeader` label + trailing | uses none of the step-4 primitives | **differs** |
| History row | **88dp** `.row`, 2-line clamp, `.meta` sub-line, chev **or** PR tag | — | verify |
| List rules | 1px `--hair-s` above first **and** below every row | — | **conflicts with `AppSection`** |
| Dock | ghost `Изменить` (128dp fixed) + primary `Записать сейчас`, gradient scrim | — | verify |
| Ghost button | `--field` bg (`surfaceTier3`) + `--body` text | `AppButton` variant uses `surfaceTier1` + `textPrimary` | **differs** |
| Numerals | Archivo for the hero, **mono** for plan values | — | verify |
| Image / tags / archive | **not drawn** | present | **spec gap** |
---

# Part 4 — CHART
`pass2d.html` §`s-chart` + its `<script>` · code: `feature/exercise-chart/`

The script **is** the specification for this screen's behaviour. Read §4.6 as normative.

## 4.1 Frame

```
.topbar        back · spacer · ⋮            (no title)
.exhead        h2 "разведение ног"  +  .swap chevron-down   → opens sh-pick
.tabs          Вес · Сессия · Подход        with a sliding .ind
.ranges        1М · 3М · 1Г · Всё           .tag chips, margin-top 16px
.readout       metric name + caption  |  value + unit
.chartwrap     the SVG canvas
div            3 × .statrow                 margin-top 26px
```

## 4.2 `.exhead` — the exercise switcher

```css
.exhead{display:flex;align-items:center;gap:11px;width:100%;padding:0 var(--gutter) 4px;
        background:none;border:0;cursor:pointer;text-align:left}
.exhead h2{margin:0;font-size:24px;font-weight:600;color:var(--max);letter-spacing:-.02em;
           line-height:1.2;flex:1;min-width:0}
.exhead .swap{width:34px;height:34px;flex:none;border-radius:11px;background:var(--sec);
              display:grid;place-items:center}
.exhead:hover .swap{background:var(--raise)}
.exhead .swap svg{width:16px;height:16px;stroke:var(--body);stroke-width:2.2;fill:none}
```

- The **whole row is one button**. Title 24px → **26 rung**, weight **600**, `letter-spacing:-.02em`.
- `.swap` — a **34×34 filled `--sec` tile**, radius 11px → **12dp** (see §0.5 — no 12 rung), holding
  a **chevron-down** `M6 9l6 6 6-6`, stroke **2.2**, `--body`.
- Opens the exercise picker sheet `sh-pick`.
- The mockup's own `.hint` on this screen states the intent: *"Переключатель упражнения ушёл из
  шапки: заголовок крупный, стрелка ровно одна — назад."* — the switcher moved out of the topbar; the
  title is large; there is exactly one arrow, and it means back.

## 4.3 `.tabs` — metric tabs with a sliding indicator

```css
.tabs{position:relative;margin:16px var(--gutter) 0;background:var(--sec);border-radius:14px;padding:5px}
.tabtrack{position:relative;display:flex;gap:4px}
.tabs .ind{position:absolute;top:0;bottom:0;border-radius:10px;background:var(--slab);
           box-shadow:var(--slabtop);transition:left 320ms var(--e-out),width 320ms var(--e-out)}
.tabs button{position:relative;flex:1;height:44px;padding:0 12px;border:0;background:none;
             color:var(--meta);font-family:var(--ff-ui);font-size:14px;font-weight:500;
             white-space:nowrap;border-radius:10px;transition:color var(--d-base) var(--e-out)}
.tabs button.on{color:var(--max)}
```

- Track: **`--sec`** (`surfaceTier1`), radius 14px → **16dp**, padding 5px → 4dp.
- Buttons: height 44px → **48dp**, 14px → **15 rung**, weight **500**, `--meta` → **`--max` when on**.
- **`.ind`** — the sliding thumb. `--slab` (**`surfaceTier2`**) + **`--slabtop`**, radius 10px → 8dp,
  full track height (`top:0;bottom:0`).
  **It animates `left` and `width` over 320ms `--e-out`** — a sixth duration, not in `--d-*`.
  `moveInd(btn)` sets `ind.style.left = btn.offsetLeft` and `ind.style.width = btn.offsetWidth`,
  so the thumb **resizes** to each tab, not just slides.
- Three tabs: **`Вес`** · **`Сессия`** · **`Подход`**. `Сессия` is §11.2's new metric (in scope).

## 4.4 `.ranges` — preset chips

```css
.ranges{display:flex;gap:8px;padding:0 var(--gutter);margin:14px 0}
```
Four `.tag` buttons (§3.2 geometry): **`1М`** · **`3М`** · **`1Г`** · **`Всё`**, the last `.on`.
`.tag.on` → `background:--raise` (`surfaceTier4`), `color:--max`, `border-color:--hair-s`.
On this screen the chips **are** interactive (`pickTag` toggles `.on` within the parent).
Inline `style="margin-top:16px"` overrides the class's `14px` → **16dp**.

## 4.5 `.readout`

```css
.readout{padding:18px var(--gutter) 0;display:flex;justify-content:space-between;
         align-items:flex-end;gap:14px;min-height:78px}
```

- Left: `.label` (metric name, uppercase mono 11, `--dim`) — **prefixed with `.mdot` when the active
  point is the record**; then `.meta` at `margin-top:6px`.
- Right: `.data-hero` at **`style="font-size:38px"`** → **34 rung**, then `.unit` at
  `style="font-size:14px"` → **15 rung**.
- `align-items: flex-end` — the two columns sit on a common baseline at the bottom.
- `min-height: 78px` → **80dp**-ish; nearest rung is 72 or 80, neither exists. Reported.

**Strings, from `METRICS` and `readout()`:**
- metric names: **`Максимальный вес`**, **`Объём за сессию`**, **`Объём за подход`**
- unit for all three: **` кг`**
- caption: `{date} 2026 · 4 подхода` plus **` · рекорд`** when the active point is the record
- values are `Math.round(n).toLocaleString('ru-RU')` → **space-grouped** (`4 620`)

## 4.6 `.chartwrap` — the canvas. Normative.

```css
.chartwrap{position:relative;margin-top:14px;padding:0 var(--gutter);touch-action:none;cursor:crosshair}
.gridline{stroke:var(--grid);stroke-width:1}
.series{fill:none;stroke:var(--max);stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}
.pt{fill:var(--base);stroke:var(--max);stroke-width:2}
.pt.pr{fill:var(--molten-solid);stroke:var(--base);stroke-width:2.5}
.pt.act{fill:var(--max)}
.pt.pr.act{fill:var(--molten-solid)}
.scrub{stroke:var(--dim);stroke-width:1;stroke-dasharray:3 4}
```

Geometry from `draw()`: `H = 212`, `PADT = 16`, `PADB = 24`, `W = clientWidth - 40`.

- **Gridlines: exactly four**, horizontal, at `y = PADT + k*(H-PADT-PADB)/3` for `k = 0..3`,
  spanning the full width `x1=0 → x2=W`. Colour **`--grid`** (`rgba(255,255,255,.07)` /
  `rgba(13,17,20,.09)`), 1px. There are **no vertical gridlines and no axis lines**.
- **Series: exactly one.** A polyline through every point, `--max`, **stroke-width 2.2** → 2dp,
  round caps and joins, no fill. `xs(i) = (i/(n-1))*(W-10)+5`, i.e. **index-spaced, not
  date-spaced**. `ys(v)` normalises to `[min,max]` of the visible metric.
- **Ordinary point**: `r = 4`, **fill `--base`**, stroke `--max` 2px → a **donut**.
- **Record point** (`.pt.pr`): **fill `--molten-solid`**, **stroke `--base`**, stroke-width **2.5** —
  the fill/stroke roles invert, so it reads as a solid molten disc ringed by the page colour.
- **Active/scrubbed point**: `r = 5.5` instead of 4, and `.pt.act` fills `--max` (a solid dot).
  A record that is also active stays molten-filled.
- **Scrub line**: a vertical line at the active x, from `y = PADT-8` to `y = H-PADB+6`, `--dim`,
  1px, **`stroke-dasharray: 3 4`**.
- Draw order: gridlines → scrub line → series → points. **The scrub sits under the series.**

**Interaction:** `pointerdown` captures the pointer and scrubs; `pointermove` scrubs while pressed.
`scrub()` maps x to the **nearest index** (`Math.round`), and on a change fires
`navigator.vibrate(4)` — a 4ms haptic tick per point crossed.

**Metric switching is animated:** `setMetric()` tweens **every point's value** from the old series to
the new over **D = 420ms** with `e = 1-(1-k)³` (ease-out cubic), redrawing each frame. The line
morphs; it does not cross-fade.

## 4.7 `.statrow` — three footer rows

```css
.statrow{display:flex;justify-content:space-between;padding:14px var(--gutter);
         border-bottom:1px solid var(--hair-s)}
```
The block has `margin-top:26px` → 24dp. The **first** row carries an inline
`style="border-top:1px solid var(--hair-s)"`, so the group is ruled top and bottom.
Padding 14px → **12dp**.

Left: `.meta`. Right: `.val` (mono **15 rung**, weight 500, `--body`, tabular) with a trailing
`.unit`.
Strings: **`Минимум`** · **`Максимум`** · **`Последний`**.

## 4.8 Empty state

`pass2d` §`s-empty` draws the chart's empty state:

```html
<div class="empty"><div class="glyph"><svg><path d="M3 17l5-6 4 4 5-8"/></svg></div>
  <h4>Пока нечего показать</h4>
  <p>График появится после двух записанных сессий с этим упражнением.</p></div>
```

```css
.empty{display:flex;flex-direction:column;align-items:center;text-align:center;padding:48px 34px;gap:10px}
.empty .glyph{width:52px;height:52px;border-radius:16px;border:1px dashed var(--hair-s);
              display:grid;place-items:center;margin-bottom:6px}
.empty .glyph svg{width:22px;height:22px;stroke:var(--dim);stroke-width:1.6;fill:none}
.empty h4{margin:0;font-size:18px;font-weight:600;color:var(--max)}
.empty p{margin:0;color:var(--meta);font-size:14.5px;line-height:1.55;max-width:274px}
```

- Glyph: **52×52, radius 16px, 1px dashed `--hair-s`**, containing a 22×22 `--dim` line icon
  (a rising zig-zag — the chart glyph). `margin-bottom:6px` → 8dp.
- Heading 18px → **19 rung**, weight **600**.
- Body 14.5px → **15 rung**, `--meta`, `max-width:274px` → 272dp.
- **No buttons** for the chart variant (§19 agrees).
- Padding `48px 34px` → 48dp / 32dp.

**The copy states the threshold: the chart appears after TWO recorded sessions.** So `< 2` points is
an empty state, not a degenerate chart.

## 4.9 Surfaces reachable

| Surface | Source | Notes |
|---|---|---|
| `sh-pick` exercise picker | mockup | `.mitem` list, the current one `.mitem.on` with a check `M4 12.5l5 5L20 7` in `--max` |
| topbar `⋮` overflow | mockup | no target drawn |
| tooltip / readout | **code** `ChartTooltipPopup` (375 lines, `SubcomposeLayout` + `GenericShape`) | **the mockup has no tooltip** — it has the fixed `.readout` + scrub instead |
| empty-state CTA | code `ChartEmptyState`, 3 `EmptyReason` variants | mockup draws one variant, no CTA |

**Structural conflict:** the mockup replaces a floating tooltip with a **persistent readout block
above the chart plus a scrub line**. The code has the opposite — no readout, a tap-driven popup with
a custom shadow (the only shadow in the four screens, against `AppElevation.shadow = 0.dp`).

## 4.10 DELTA — mockup vs `feature/exercise-chart/`

| Element | Mockup says | Code does | Verdict |
|---|---|---|---|
| Exercise switcher | `.exhead` — 26-rung title + 34dp `--sec` swap tile, below the topbar | picker opened from elsewhere | **differs** |
| Metric tabs | 3 tabs in a `--sec` track with a **sliding `--slab` indicator** | `MetricToggle` (71 lines) | **differs** |
| Third metric | **`Сессия`** (per-session volume) | 2 metrics only | **missing** (§11.2, in scope) |
| Range chips | 4 `.tag`s, `.on` = `--raise` + `--hair-s` border | `PresetChipsRow` | verify |
| Readout block | persistent, metric + caption / value + unit, mdot on record | **absent** | **missing** |
| Gridlines | **4 horizontal**, `--grid` | **2 lines** (left + bottom axis), `borderSubtle` | **differs** |
| Series | 1 line, `--max`, 2.2px, round caps | 1 line, `accent`, `Border.medium` | matches |
| Ordinary point | r4 donut: fill `--base`, stroke `--max` 2px | r=`Space.xs` donut, fill `accent`, hole `surfaceTier0` | **differs (inverted)** |
| **Record point** | **fill `--molten-solid`, stroke `--base`, 2.5px, r4** | **no record marking at all** | **missing** |
| Active point | r5.5, solid `--max` | — | **missing** |
| Scrub line | vertical `--dim`, dash `3 4`, extends past the plot | — | **missing** |
| Scrub interaction | pointer drag, nearest index, 4ms haptic per step | tap-to-select a point | **differs** |
| Metric switch | 420ms value tween, line morphs | — | **missing** |
| Tooltip | **none** | 375-line popup with a shadow | **differs** |
| Stat rows | 3 rows, ruled, `.meta` / `.val`+unit | `ChartFooterStats` | verify |
| Empty state | `< 2` sessions, glyph + heading + body, **no CTA** | fires at **0** points; 3 variants with CTAs | **differs** |

**PF2 note (already reported):** the chart never draws two series, so no new palette role is needed
for series distinction. The record point needs `molten.text` (9.19:1 dark / 5.04:1 light on
`surfaceTier0`) rather than `molten.solid` (**2.61:1 light — fails**), plus a non-colour channel,
because molten-vs-accent is only **1.93:1** in dark. Note this **conflicts with the mockup**, which
specifies `--molten-solid` as the fill. Reported, not resolved.
---

# Part 5 — SETTINGS
`pass2d.html` §`s-set` · code: `feature/settings/`

## 5.1 Frame

```
.topbar   back · h1 "Настройки"            (h1, NOT h1.sm — 20px)
.sgroup   .label "Оформление"        → Тема (+ Единицы, OMITTED)
.sgroup   .label "Резервные копии"   → 6 rows
.sgroup   .label "Данные"            → 1 row
.sgroup   .label "О приложении"      → 4 rows
```

The first group carries `style="margin-top:8px"`, overriding `.sgroup`'s 30px.

## 5.2 The group — **there is no container**

```css
.sgroup{margin-top:30px}
.sgroup>.label{display:block;padding:0 var(--gutter);margin-bottom:10px}
```

A group is **30px of air (→ 32dp) plus an uppercase mono label**. No box, no border, no radius,
no background. This is exactly `AppSection`'s rule ("separation is carried by the gutter above and
the header label; it is not carried by a line"), and it is exactly what the current code violates.

Label: `.label` — mono **11 → 11 rung**, weight 500, `.14em`, **uppercase**, `--dim`,
`margin-bottom:10px` → 8dp.

## 5.3 The row

```css
.srow{display:flex;align-items:center;gap:14px;padding:0 var(--gutter);min-height:64px;
      border-top:1px solid var(--hair-s);cursor:pointer}
.srow:last-child{border-bottom:1px solid var(--hair-s)}
.srow:hover{background:var(--sec)}
.srow.plain{cursor:default}
.srow.plain:hover{background:none}
.srow-b{flex:1;min-width:0;padding:13px 0}
.srow-t{font-size:16px;color:var(--max)}
.srow.rust .srow-t{color:var(--rust)}
```

- **`min-height: 64px` → 64dp.** Note this is *not* the 88dp `--row-h` used by `.row` on Exercise
  detail. Two row heights coexist in the design.
- Gap 14px → **12dp**. Inner padding `13px 0` → **12dp**.
- **Rules: `border-top` on every row, `border-bottom` only on the last.** So a group of N rows has
  N+1 rules — ruled above each row and closed at the bottom. `--hair-s` (solid), not `--hair`.
  **This conflicts with `AppSection`, which draws rules only *between* rows and never above the
  first or below the last.** Report; do not resolve.
- `.srow-t` — 16px → **15 rung**, `--max`. Optional sub-line beneath is `.meta` at
  `margin-top:3px` → 4dp.

### Four row variants

| Variant | Class | Trailing | Cursor |
|---|---|---|---|
| navigable | `.srow` | `.chev` (18×18, `--dim`, 1.8) | pointer |
| navigable + value | `.srow` | `.val` then `.chev` | pointer |
| plain | `.srow.plain` | a control (`.mseg`, `.sw`) or nothing | default, **no hover** |
| destructive | `.srow.rust` | nothing | pointer |

`.val` — mono **15 rung**, weight 500, `--body`, tabular.
An external-link row uses a different chevron glyph: **`M7 17L17 7M9 7h8v8`** (arrow-out-of-box),
not the `M9 6l6 6-6 6` right-chevron.

## 5.4 `.mseg` — the theme segmented control

```css
.mseg{display:flex;gap:3px;background:var(--sec);padding:3px;border-radius:11px;flex:none}
.mseg button{width:38px;height:32px;border:0;border-radius:8px;background:none;color:var(--meta);
             display:grid;place-items:center}
.mseg button.on{background:var(--slab);color:var(--max);box-shadow:var(--slabtop)}
.mseg svg{width:16px;height:16px;stroke:currentColor;stroke-width:1.9;fill:none}
```

- Track `--sec` (`surfaceTier1`), radius 11px → **12dp** (no rung — see §0.5), padding 3px → 4dp,
  gap 3px → 4dp.
- Three buttons, **38×32** each, radius 8px → 8dp.
- Selected: **`--slab` (`surfaceTier2`) + `--slabtop`** + `--max` icon. Same lift signature as the
  chart's tab indicator and the active card — one vocabulary, three uses.
- Icons, 16×16, `stroke-width:1.9`:
  1. **system** — `<rect x=3 y=4 w=18 h=13 rx=2/><path d="M8 21h8"/>` (a monitor), `title="Системная"`
  2. **light** — `<circle cx=12 cy=12 r=4/>` + 8 rays, `title="Светлая"`
  3. **dark** — `<path d="M20 13.5A8 8 0 1 1 10.5 4a6.5 6.5 0 0 0 9.5 9.5z"/>` (a moon), `title="Тёмная"`
- The row is `.srow.plain`; the current theme name is the **sub-line**: `Системная` / `Светлая` /
  `Тёмная`.

## 5.5 `.sw` — the switch

Identical to the session sheet's switch (§1.9): **46×28**, radius 14px, track `--hair-s` →
**`--max`** when on; knob 22×22 circle at `top:3px left:3px`, `--meta` → **`--base`** when on,
`translateX(18px)`, `--e-spring`, `--d-base`.

**The mockup's switch is a `--max`-track switch, not a Material 3 switch.** When on, the track is
the brightest colour in the palette and the knob is the page colour — the same inversion as the
primary button and the done-marker.

## 5.6 The four groups, verbatim

### `Оформление`
| Row | Type | Sub-line | Trailing |
|---|---|---|---|
| **`Тема`** | `.srow.plain` | `Тёмная` (current) | `.mseg` |
| ~~`Единицы`~~ | ~~`.srow`~~ | ~~`Килограммы`~~ | ~~`.chev`~~ |

**`Единицы` is drawn but is OUT OF SCOPE (§11.3). Omit the row entirely — do not stub it, do not
disable it, do not add a "coming soon".** The group then contains one row.

### `Резервные копии`
| Row | Type | Sub-line | Trailing |
|---|---|---|---|
| `user@example.com` | `.srow.plain` | `User` | — |
| **`Автокопия`** | `.srow` | `Ежедневно · следующая через 23 ч` | `.val` **`Вкл`** + `.chev` |
| **`Снимок для ИИ-ассистента`** | `.srow.plain` | `Читаемая копия в видимой папке Google Drive` | `.sw` (on) |
| **`Создать копию сейчас`** | `.srow` | — | `.chev` |
| **`Восстановить из копии`** | `.srow` | `3 копии · последняя минуту назад` | `.chev` |
| **`Выйти из аккаунта`** | **`.srow.rust`** | — | — |

The account row is a **placeholder** — a real email and a real name. Flagged: needs a signed-out
variant, which the mockup does not draw.

### `Данные`
| Row | Type | Sub-line | Trailing |
|---|---|---|---|
| **`Архив`** | `.srow` | `4 упражнения · 1 тренировка` | `.chev` |

### `О приложении`
| Row | Type | Sub-line | Trailing |
|---|---|---|---|
| `Workeeper` | `.srow.plain` | `Версия 1.48.0 (49)` | — |
| **`Исходный код`** | `.srow` | — | external-link chevron |
| **`Лицензия GPLv3`** | `.srow` | — | external-link chevron |
| **`Политика конфиденциальности`** | `.srow` | — | external-link chevron |

## 5.7 Surfaces reachable

| Surface | Source |
|---|---|
| `Автокопия` → frequency picker | **code** `FrequencyPickerBottomSheet` (186 lines); mockup draws a chevron but no target |
| `Восстановить из копии` → restore flow | code |
| `Выйти из аккаунта` → confirmation | **code**; the mockup draws no confirmation for a destructive row |
| Google sign-in | code |
| `Архив` → archive screen | both |
| three external links | both |
| restore progress overlay | **code only** |

## 5.8 DELTA — mockup vs `feature/settings/`

| Element | Mockup says | Code does | Verdict |
|---|---|---|---|
| **Group order** | Оформление → Резервные копии → Данные → О приложении | **About → Appearance → Backup → Data** | **differs** |
| **Group container** | **none** — 32dp air + label only | `SettingsSection` draws a **2dp `borderSubtle` border, radius 16dp, padding 12dp** | **differs** |
| Group label | uppercase mono 11, `.14em`, `--dim` | `labelSmall` M3 alias, **not uppercased**, `textTertiary` | **differs** |
| Row height | **64dp** min | — | verify |
| Row rules | 1px `--hair-s` above each, below the last | — | **conflicts with `AppSection`** |
| Row title | 15 rung, `--max` | — | verify |
| Theme control | **`.mseg`** — 3 icon buttons, `--slab`+`--slabtop` thumb | `RadioButton` list | **differs** |
| Switch | `--max` track, `--base` knob, 46×28 | stock M3 `Switch` | **differs** |
| Destructive row | `.srow.rust` — **text colour only**, no icon, no container | — | verify |
| `Единицы` | drawn | — | **correctly absent (§11.3)** |
| External-link glyph | `M7 17L17 7M9 7h8v8` | — | verify |
| Loading | — | `CircularProgressIndicator` | **not drawn** |

**Note:** `SettingsSection` is not merely a different look — it contradicts the kit's own
`AppSection`, whose KDoc states the rule the mockup follows. The screen predates step 4 and never
migrated.
---

# Part 6 — Conflicts, blockers, and gaps

Everything here is **reported, not resolved**. Each is a decision that belongs to Ilya.

## 6.1 Spec-vs-mockup conflicts

| # | Conflict | Spec says | Mockup says | Notes |
|---|---|---|---|---|
| C1 | **`dim` is swapped between themes** | §2.1 dark `#98A0A9`, §2.2 light `#6B7078` | dark `#6B7078`, light `#98A0A9` — **both files agree** | §2.5's own "3.91:1" figure is derived from the *mockup's* dark value, so §2.5 contradicts §2.1. Measured: mockup-dark 3.91:1, mockup-light **2.47:1**, spec-dark 7.36:1, spec-light 4.65:1. |
| C2 | **`raise` vs `slab` for the active card** | §2.1: `raise` = "the single raised surface — active exercise" | `.card.active{background:var(--slab)}`; `--raise` is the **rail pill track** | The spec's role description names the wrong token. |
| C3 | **light `molten`** | §2.2 `#C2410C` | `#C2410C` | Code ships **`#BE3E0C`** with a documented measurement. Both spec and mockup are stale against code. |
| C4 | **light `meta`** | §2.4 `#596169` (deliberate deviation) | `#69727C` | Already settled in the spec's favour; noted for completeness. |
| C5 | **Section rules** | §3.1 + `AppSection`: separation is air + label, "there is no rule above the first row and none below the last" | `.list` and `.srow` are ruled **above each row and below the last** | Affects Exercise-detail history and every Settings group. |
| C6 | **Chart record point** | PF2 measurement: `molten.solid` is **2.61:1** on light `base` — fails 3:1 | `.pt.pr{fill:var(--molten-solid)}` | The mockup's own choice fails the locked threshold. |
| C7 | **Rail thresholds** | §8: one threshold, 9dp, "unverified" | **two**: 9px (sets) and 11px (groups) | |
| C8 | **Session sheet count** | §14 lists 3 sheets | **4** — `sh-desc` is omitted from the spec | Confirmed. |
| C9 | **Skip confirmation** | §6.1: "Skip is reversible in place. No snackbar — nothing to undo." | menu item, label toggles to `Вернуть в сессию`, no dialog | Code has a confirmation **dialog**, contradicting both. |

## 6.2 Blockers — cannot be expressed today

**RENUMBERED `B*` → `E*`, and the renumbering is the finding.** This table shipped as `B1`–`B9`
while `v3-redesign-spec.md` §25 — the **live, append-only** blocker registry — shipped its own
`B1`–`B35`. Two registries, one namespace, and the numbers do not line up: this table's `B1` is
§25's **B2**, its `B2` is §25's **B3**, its `B3` is §25's **B5**, and only `B4` happens to collide
on the same subject by coincidence. The collision was not hypothetical — §0.4's own note above
already reads *"the second is blocker B1"* meaning **§25's** B1 (the 26sp record value) while
sitting three hundred lines above a table whose `B1` is the font weight. A citation that resolves
to two different rows depending on which file the reader opens is worse than an uncited claim,
because it reads as precise.

**The convention, so this cannot recur:** `B<n>` belongs to **§25 of `v3-redesign-spec.md` and
nowhere else** — it is the one registry that is append-only and therefore the one whose numbers
must never be reused. Every other document prefixes its own series with a letter of its own:
`E<n>` here (extraction blockers), `C<n>` for §6.1's spec-vs-mockup conflicts, `G<n>`/`D<n>` in the
per-screen delta documents. A new series picks an unused letter; it does not pick `B`.

**This table is CLOSED.** It records what the extraction found when it was written, and it is not
where any of these are tracked now — the third column says where each one went. New blockers are
filed in §25, not appended here.

| # | Blocker | Scope | Where it lives now |
|---|---|---|---|
| E1 | **Weight 600 is not bundled.** Only 400/500 ship. | `.ctitle`, `.chead .title`, `.sheet h3`, `.topbar h1`, `.shead h2`, `.exhead h2`, `.empty h4`, `.btn`, `.prtag` — **every heading and every primary button on all five screens** | §25 **B2** — resolved, fonts PR (premise corrected: headings rendered at 400, not 500) |
| E2 | **Archivo width axis.** Drawn at `wdth` 115 / 116 / 122; the bundled static cut is **125**. | every numeral: `.data-l`, `.data-s`, `.data-hero` | §25 **B3** — resolved at `wdth 116`; the three-width treatment stays unimplemented and is recorded as the reinstatement path |
| E3 | **Two numeral tiers.** `.data-s` is **32px** in the session mockup and **26px** in pass2d — same class, two sizes, and the spec records neither. | session timer vs exercise-detail record value | §25 **B5** — resolved by naming (`AppTypography.timer`); the premise was wrong, the timer is 32px in both files |
| E4 | **letter-spacing is absent from `AppTypography`.** Ten declared values, positive on mono labels and negative on headings. | `.label` `.14em`, `.prtag` `.1em`, `.tempbadge` `.12em`, `.setbar` `.06em`, `.toast button` `.08em`, `.ctitle` `-.01em`, `.shead h2` `-.015em`, `.exhead h2` `-.02em`, `.data-hero` `-.02em`, `.topbar h1` `-.015em` | §25 **B4** — the one number that collides with §25's on the same subject, by coincidence. Resolved: `text.title` only, at −0.39sp |
| E5 | **No `dim` tier.** The palette has four text tiers; the mockups use five, and `meta`/`dim` appear on the same screen with different jobs. See C1 — and note light `dim` measures 2.47:1, so it cannot ship as drawn either. | `.label`, `.unit`, `.set-i`, `.ord`, `.sub`, `.plan-line`, `.chev`, `.tchip`, `.val .x`, `.scrub`, `.tempbadge`, `.mini` | **No §25 row.** Ruled in §26 ("`dim` fourth step" — merged into `meta`, on perceptual collapse) and re-audited by §25 **B28**, which found the *slot* (`AppColors.textDim`) survives at meta's value |
| E6 | **No `--slabtop` equivalent.** An inset 1px top highlight in dark, a two-layer drop shadow in light. `AppElevation.shadow` is `0.dp`. | `.card.active`, `.card.open`, `.tabs .ind`, `.mseg button.on` — the entire "lifted" vocabulary | **No §25 row.** Ruled in §26 ("Elevation") and built as `Modifier.liftedSurface`. This is the `B-2025-elevation` §25's own preamble says a rewrite lost once |
| E7 | **No 12dp radius rung.** `AppDimension.Radius` is 4/8/16/32/64/128; the mockup's most common radius is **12px** (`.field`, `.set`, `.mitem`, `.icon-btn`), with 11px and 13px nearby. | every field, every sheet menu item | **Open, and answered per component rather than by a rung** — each site rounds onto the ladder with its reason stated at the site (`AppIconButton`'s KDoc rounds 12 → `Radius.small` and says why). No rung was added |
| E8 | **No `--donefill`, `--flash`, `--grid` tokens.** Three translucent washes with no palette slot. | done field, set flash, chart gridlines | **Closed in code, not by a row.** `AppColors.donefill` and `AppColors.grid` both ship, declared in `ContrastContract` (SURFACE and DECORATIVE respectively); the flash wash lives in `SetClosureVisuals`. §25 **B7** is a different question about the same wash — *which element* it paints |
| E9 | **Durations outside `--d-*`.** 320ms (tab indicator), 380ms (pstrip), 420ms (rail fill, metric tween), 560ms (pulse), 620ms (flash), 900ms (PR sweep), 1000ms (sweep cleanup), 5000ms (toast). §5 defines three. | motion tokens | **Partly closed, and deliberately not by widening the scale.** `AppMotion` still declares three durations; the off-scale values ship as component constants with their reason at the site. The toast's 5000 is ruled in §25 **B25** (branch B, host-owned `TOAST_VISIBLE_MS`) |

## 6.3 Drawn but out of scope, or in code but not drawn

| Item | Where | Status |
|---|---|---|
| `Единицы` units row | Settings mockup | **OUT** (§11.3) — omit the row, do not stub |
| `Сессия` third chart metric | Chart mockup | **IN** (§11.2) |
| Tonnage `4 820 кг` | Past-session mockup | **IN** (§11.1) — implemented |
| `.tools` demo toolbar | both mockups | **not part of the app** |
| `.hint` explanatory notes | pass2d | **not part of the app** |
| Exercise image, tag editor, archiving | `feature/exercise/` | **in code, not drawn** — spec gap |
| `Изменить план`, `Сбросить сеты` | `feature/live-workout/` | **in code, not drawn** — spec gap |
| Chart tooltip popup | `feature/exercise-chart/` | **in code**; the mockup uses a readout + scrub instead |
| Signed-out backup state | — | **neither** — the mockup draws only a signed-in account |
| Loading / error states | code | **not drawn** on any of the five |

## 6.4 Kit candidates — ranked by how many screens they serve

| Rank | Component | Screens | Today |
|---|---|---|---|
| 1 | **`.topbar` + `.icon-btn`** (44dp, −12px hang, 21dp glyphs, no title on Session) | all 5 | stock `TopAppBar` everywhere |
| 2 | **`.label`** (uppercase mono 11, `.14em`, the `dim` tier) | all 5 | private inside `AppSectionHeader` |
| 3 | **lifted surface** = `--slab` + `--slabtop` | session, past, chart, settings | none |
| 4 | **`.card` + `.chead` + `.cbody` disclosure** (grid-rows animation) | session, past | two separate implementations |
| 5 | **`.field`** (value + unit on `--field`) | session, past | `AppNumberInput` — **wrong tier** |
| 6 | **`.mini`** 34dp icon button | session, past | none |
| 7 | **`.sw`** switch (`--max` track) | session sheet, settings | stock M3 `Switch` |
| 8 | **`.set` row** | session, past | deliberately separate (PF1) |
| 9 | **`.tchip` / `.prtag`** | session, past, exercise | `AppSetTypeChip`, `PersonalRecordTag` |
| 10 | **`.ord` / `.ordchip`** | session, past, exercise | none |
| 11 | **`.row`** 88dp list row | exercise, lists | `AppSectionRow` |
| 12 | **`.btn` ghost/danger** | session, exercise, empty | `AppButton` — **ghost and danger both differ** |
| 13 | **`.tag`** | exercise, chart | `AppTagChip` |
| 14 | **`.sheet` + `.grab` + `.mitem` + `.mrow` + `.msep`** | session, chart, settings | `AppSheetLayout` |
| 15 | **`.empty`** | chart, lists | `AppEmptyState` |

## 6.5 The cross-cutting defects, collected

Four defects are not per-screen — they are one mistake repeated:

1. **`AppNumberInput` paints `surfaceTier2`; the mockup's `.field` is `surfaceTier3`.** Every numeric
   input on every screen is one tier too light in dark.
2. **The "lifted" signature is missing everywhere.** `--slab` + `--slabtop` marks the active card,
   the open card, the tab indicator and the segmented thumb. None of the four is implemented; three
   of them are marked by something else instead (an accent border, a chevron, nothing).
3. **The `dim` tier is collapsed into `meta` everywhere**, erasing a distinction the mockups make on
   every screen. Documented once in `AppSectionHeader`, applied silently everywhere else.
4. **Headings render at weight 500 because 600 is not bundled.** Every screen, every heading.

Any one of these is invisible in a golden — all four produce a stable, self-consistent picture that
simply is not the design. That is the §10.2 failure mode, and it is why this document exists.

---

# Part 7 — THE EDITORS
`pass2d.html` §`s-editor` (the five ruled forms) · `session-v3f.html` L137–145 (`.setbar`, `.addex`)
· code: `feature/exercise/ui/ExerciseEditScreen.kt`,
`feature/single-training/ui/TrainingEditScreen.kt`,
`feature/plan-editor/ui/PlanEditorScreen.kt`, `core/ui/plan-editor/PlanEditorBody.kt`

**Why this part is numbered 7 and sits after Part 6.** Parts 0–5 were written in one pass and Part 6
records what that pass could not resolve; renumbering to insert the editors between them would
invalidate every `§n.m` citation already in circulation. The number is the cheap thing to give up.

**Three screens, one vocabulary, and the vocabulary is already drawn.** The editors add exactly
three forms nobody had drawn — the typing field, the thumb in the pushed bar, and the set-type
letters. Everything else on these screens is a citation: the card is `#s-past`'s, the sheet is
`#sh-del`'s, the bar is `#s-topbar`'s pushed shape, the card's foot and the add-exercise button are
`session-v3f`'s. Where this part says "as drawn at X", **X is the normative source and this document
is not a second copy of it.**

## 7.1 Frames

```
EXERCISE EDITOR                       TRAINING EDITOR                PLAN EDITOR
.topbar  ‹  h1.sm name  .thumb        .topbar  ‹  h1.sm name        .topbar  ‹  h1.sm name
.form                                 .form                          .form
  .fgrp  Название   .tf                 .fgrp  Название   .tf          (type toggle — plan editor only)
  .fgrp  Теги       .selrow + .tf       .fgrp  Описание   .tf.multi
  .fgrp  Описание   .tf.multi           .fgrp  Теги       .selrow + .tf
  (default-plan summary or the card)  .cards  n × .card.open        .cards  1 × .card.open
                                      .addex                        (no .addex — one exercise)
.dock  .btn.ghost Отмена · .btn Сохранить   — all three
```

The exercise editor's thumb is the **only** trailing element any of the three bars carries. The
training and plan editors draw the pushed bar's plain shape (`.icon-btn.lead` + `h1.sm`, §`s-topbar`).

## 7.2 `.tf` — the typing field. **NOT `.field`.**

```css
.tf{width:100%;box-sizing:border-box;display:block;min-height:52px;padding:14px 12px;
    border:1px solid var(--idle);border-radius:12px;background:none;
    color:var(--max);font-family:var(--ff-ui);font-size:16px}
.tf.multi{min-height:96px}
.tf.ghosty{color:var(--dim)}
.tf.err{border-color:var(--rust);border-width:1.5px}
.ferr{font-family:var(--ff-ui);font-size:12.5px;color:var(--rust);margin:6px 0 0 2px}
.flabel{font-family:var(--ff-ui);font-size:13px;color:var(--dim);margin:0 0 6px 2px}
```

| Property | Drawn | Ships | Why |
|---|---|---|---|
| fill | `background:none` | **transparent** — the page tier shows through | Outlined, not filled. `.field` is the session's tap-to-enter value box: no caret, no label, no error, so it is not the referent for a thing you type into |
| outline | 1px `--idle` | **1dp `borderDefault`** | See the divergence note below — this is the one place in this part where the code deliberately does not paint the drawn token |
| radius | 12px | **8dp** (`Radius.small`) | E7's missing rung, rounded down at the site, as `AppIconButton` already does |
| min-height | 52px | **48dp** (`heightMd`) | `.field` resolves the same 52 the same way |
| type | 16px `--ff-ui`, `--max` | **body rung**, `textPrimary` | |
| placeholder | `.tf.ghosty` → `--dim` | `textDim` | |
| multiline | `min-height:96px` | **the same composable, taller** | One height changes. Not a second component, and not `singleLine=false` on a differently-shaped field |
| label | **above** the field, `.flabel` 13px `--dim` | the form's own `FormSection` label | The form already puts it there; the M3 floating label is not drawn anywhere and is not used |
| error outline | `--rust`, **1.5px** | `status.error`, **1.5dp** | The width step is the second half of the signal: the same contour is changing colour, and weight is what makes that legible without adding an element |
| error reason | `.ferr` under the field | `bodySmall` / `status.error`, `Space.xs` above | |

**THE ONE DELIBERATE TOKEN DIVERGENCE, and it must not be "fixed".** The drawing paints the outline
`--idle`; the build paints `borderDefault`. `--idle` maps to `AppColors.textDisabled`, which
`ContrastContract` declares **EXEMPT** — WCAG carves disabled controls out of the non-text
requirement — so painting an **enabled** field's outline with it is wrong on the semantics and would
drag every `textDisabled` pair into the gate to make it right. `borderDefault` is
`*_CONTROL_OUTLINE`, the slot the app created when it made the same move `--hair-s` forced (B19's
non-mapping). Both clear the 3:1 an enabled outline owes, measured with the gate's own arithmetic:

| Pair | Dark | Light |
|---|---|---|
| `--idle` on `--base` | **6.40** | **3.49** |
| `--idle` on `--field` | 5.64 | 3.15 |
| `borderDefault` on `--base` (what ships) | **4.09** | **3.60** |
| `--hair-s` on `--base` — **the rejected candidate** | 1.51 | 1.35 |
| `--hair-s` on `--field` — the rejected candidate | 1.33 | 1.22 |

## 7.3 The two errors, and why the button stops being disabled

`isSaveEnabled` (exercise) is `name.isNotBlank()`; `canSave` (training) is
`name.isNotBlank() && exercises.isNotEmpty()`. **The first conjunct is the exact condition that
produces `nameError`**, so blank-name is unreachable from the UI on both, and two green
`ClickHandlerTest` cases certify a branch production cannot enter. Save is **enabled always**; both
name errors become reachable and draw `.tf.err` + `.ferr`.

The training editor's **second** conjunct is a second dead branch and it is a different shape: an
empty exercise list emits `Event.ShowSaveError` — a **snackbar**, not a field error, because there
is no field for it. Enabling the button makes that reachable too. **The snackbar stays**: the
drawing draws no error surface for a section, and inventing one here would be deriving a decision
from an analogy. Reported, not folded in.

Precedence when both name errors could be set: `nameError` (blank) is checked before the save runs,
`nameDuplicateError` comes back from the save, so they cannot be true at once by construction — the
UI's `when` orders blank first anyway.

## 7.4 Modals — six instances, five components, **all sheets**

`pass2d.html` draws **no dialog primitive at all**; it draws sheets twice (`#sh-del`, `#sh-pick`).

| # | Screen | State | Component today | Becomes |
|---|---|---|---|---|
| 1 | exercise editor | `DialogState.DiscardConfirm` | `AppDialog` | the discard sheet |
| 2 | exercise editor | `DialogState.ImageSourcePicker` | `ImageSourceDialog` | `AppBottomSheet` + `AppSheetItem` ×2 |
| 3 | exercise editor | `DialogState.PermissionDenied` | `PermissionDeniedDialog` | `AppBottomSheet` + `AppSheetLayout` |
| 4 | training editor | `DialogState.DiscardConfirm` | `AppDialog` | the discard sheet |
| 5 | plan editor | `confirmDiscardOpen: Boolean` | a bespoke three-action `Dialog` | the discard sheet |
| 6 | plan editor | `DialogState.TypeChangeConfirm` | `AppConfirmDialog` | `AppBottomSheet` + `AppSheetLayout` |

**The discard sheet, drawn (`#s-editor` form 3):**

```html
<div class="sheet shx">
  <div class="grab"></div><h3>Выйти без сохранения?</h3>
  <div class="desc">Несохранённые правки будут потеряны.</div>
  <button class="mitem dang">Выйти без сохранения</button>
  <button class="mitem">Продолжить правку</button>
</div>
```

`.shx` is a **position override only** — surface (`--field` → `surfaceTier3`), 26px top radius
(→ 32dp), padding, `.grab`, `h3` and `.desc` are `.sheet`'s own, so the in-place drawing and the
real sheet cannot say different things. `.mitem.dang` is `--rust` → `status.error`, measured
**5.39 dark / 5.04 light** on `--field`, over the 4.5 text owes; the pair already ships
(`AppSheetItem(destructive = true)` on `AppBottomSheet`'s `surfaceTier3`) and is already declared.

**Two actions, not three.** «Сохранить» is removed: the sheet appears only when there is something
to lose, and saving already lives on the form. «Отмена» → **«Продолжить правку»** — the old label
read as dismissing the window rather than as declining to discard. This is also what disposes of
the plan editor's bespoke dialog and of its stated reason ("`AppConfirmDialog` only renders two").

**Plan editor: two modal channels collapse to one.** `confirmDiscardOpen: Boolean` sits beside
`dialogState: DialogState`, so both can be open at once — the exact state the `mvi-dialog-state`
skill exists to make unrepresentable. `DialogState` gains a `DiscardConfirm` variant and the
Boolean goes, along with `Action.Click.OnConfirmSave` (the third action's only consumer).

## 7.5 The exercise card — `#s-past`'s card, with a foot

Head, body and row are drawn already; **nothing here is a new form.**

```
.card.open              --slab + --slabtop, radius 18px → 16dp
  .chead                .ord + .title       — no actions drawn (see below)
  .cbody > .inner
    .sets               n × .set
    .setbar             + подход | − подход
```

**`.set`** is `#s-past`'s logged row verbatim: `.set-i` ordinal, `.field` weight, `.field` reps,
`.tchip`. Weight is omitted for a weightless exercise, exactly as `PlanEditorBody` already does.

**Values render in the normal colour.** `PlanEditorBody` passes none of
`isDone / isRecord / isLogged / isError`, so every authored plan value falls to `textTertiary` and
**a value the user typed is drawn as "not yet entered"**. It takes the **`isLogged`** treatment —
`textPrimary` on the plain `surfaceTier3` field — which is `#s-past`'s own inline
`style="color:var(--max)"` on every ordinary row. `isLogged` is **reused, not duplicated**: a fourth
boolean resolving to the same colour would be a rename, and a rename is the mutation a gate cannot
catch (§27, and `textDim`/`textTertiary` is the standing example).

**`.tchip` — 34×32, mono, unchanged geometry, and the letters are NEW.**

| Type | Mark | Ru | En |
|---|---|---|---|
| work | `·` as drawn in the session | `·` | `·` |
| warmup | **first letter** | **Р** | **W** |
| failure | **first letter** | **О** | **F** |
| drop | **first letter** | **Д** | **D** |

No mockup draws a mark for the three non-work types, so this is a decision. The chip is **not
widened** and takes no other treatment — the letter occupies exactly the dot's place. Russian
«Рабочий» also begins with Р and there is no collision, because the work set is never lettered.
The build already emits `"W" / "·" / "F" / "D"` as **hardcoded English literals** in
`AppSetTypeChip`; they become localised resources, which is what stops a Russian build showing `W`
for разминка.

**`.chead` carries no actions, and that is a statement.** Ordinal and title only — `#s-past`'s first
card verbatim. No disclosure chevron (in the plan editor there is one exercise and it is always
open) and no `✕`. **"The per-row `✕` goes" is about the SET row**: its work moves to «− подход».
Removing an **exercise** from a training is untouched by this stage and stays where the build has
it; giving it a new home here would be deriving a ruling from an analogy.

**`.setbar` — `session-v3f` L137–141, and this document does not restate its values.** Two mono
uppercase actions, `flex:1` each, split by a `--hair` rule, `border-top:1px solid var(--hair)`
above. `pass2d.html` now carries a byte-for-byte copy because it already drew this card minus its
foot; **changing one changes two.**

## 7.6 `.addex` — `session-v3f` L143–145

Dashed `--hair-s`, 60px tall, `radius:16px`, `--ff-ui` 15px with a leading 17px plus glyph and the
label «Добавить упражнение», full width minus two gutters. **Drawn already; not redrawn here.** It
replaces the training editor's `AppButton.Tertiary` + `Icons.Default.Add` in the section header row.
The `(N)` count stays on the section label, where it already is.

## 7.7 The pushed bar and `.thumb` — **NEW**

```css
.thumb{width:44px;height:44px;flex:none;border-radius:12px;border:1px solid var(--hair-s);
       background:var(--field);display:grid;place-items:center;overflow:hidden;cursor:pointer}
.thumb svg{width:21px;height:21px;stroke:var(--dim);stroke-width:1.7;fill:none;
           stroke-linecap:round;stroke-linejoin:round}
.thumb.has{background:linear-gradient(135deg,var(--raise),var(--sec))}
.thumb.none{border-style:dashed}
```

44px → **48dp**, the `.icon-btn` rung, by the `44px / 46px / 48px → 48dp` row of §0.5's ladder. **The thumb
takes the bar's control size rather than one of its own**: it is a control in that bar, beside a
17px `h1.sm`, and a bar carrying two controls at two sizes has no rung at all. Colliding with
`.icon-btn`'s 48 is the point, not the objection. Radius 12 → **8dp**. Border 1px `--hair-s` →
**1dp `borderDefault`**, the same reroute §7.2 records.

The 48dp box is also the minimum interactive target, so the drawn box **is** the touch target and
nothing has to be added around it.

- **Image present** → the gradient stands in for the photo; **no glyph inside**. Tap opens the
  full-screen viewer, and replace lives there. The form's whole `ImageEditRow` — thumb, «Изменить»,
  «Удалить» — is deleted; the row's two buttons move to the viewer.
- **Image absent** → dashed border, and the thumb draws the **exercise type mark**. Tap opens the
  picker sheet.

**The type mark is not invented for this.** `ImageThumb` already takes `type` and already draws it
when there is no photo; the ruling keeps that relationship and moves the container. **Rejected: a
camera inside the empty thumb** — it promises one of the two actions the sheet offers, and it erases
the relationship.

**Two new stroke glyphs, and they join `AppIcons`:**

| Mark | Path (24-unit viewBox, stroke 1.7) | Replaces |
|---|---|---|
| weighted — a dumbbell | `M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10` | `Icons.Filled.FitnessCenter` (B33(b), ×4) |
| weightless — a figure | `M9.9 5a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0-4.2 0Z` + `M12 8v6M12 14l-3 6M12 14l3 6M6 10.5h12` | `Icons.Filled.AccessibilityNew` (B33(b), ×3) |

The circle is rewritten as two arcs, the only change of notation `AppIcons` allows.

**No third mark, and this is a refusal rather than a deferral.** Time-based exercises need a schema
migration past v6; that is new functionality and this stage is a redesign. Nothing is prepared for
it — not in the type enum, not in the icon set, not in the drawing. **Preparing a slot is how the
decision gets taken by whoever fills it.**

## 7.8 Reorder — long-press drag

The kit's `ReorderableColumn` (`reorderableColumnItem` + `reorderableColumnDragHandle`), long-press,
as past-session already does — a second consumer of a shipped component, not a new mechanic. What
goes is `TrainingExerciseEditRow`'s `ReorderControls`: **two `IconButton`s drawing
`Icons.Default.DragHandle` twice**, one meaning up and one meaning down. The same glyph twice is not
a control. The `moveUp`/`moveDown` **semantics survive** as
`CustomAccessibilityAction`s — `reorderableColumnItem` already registers both — so the capability is
not lost with the arrows, it stops being drawn as two identical marks.

## 7.9 Loading — **there is no loading surface, and that is the ruling**

Neither mockup draws one. **A route does not compose until it has loaded.**

`isLoading` is written by all three stores and **read by no editor UI**, and
`PlanEditorStoreImpl.kt` carried a KDoc claiming a gate that does not exist: *"The Composable
doesn't render the toggle until `state.isLoading == false`, so the user never sees the
placeholder."* It does render it. For a weightless exercise opened through «Изменить план» the
toggle shows the seeded `WEIGHTED` and **visibly flips** when the load lands, and
`CommonHandler.loadPlan` then overwrites `draft`, `type`, `initialType` and `initialDraft`
**unconditionally** — so anything touched in that window is silently discarded. Gating composition
removes the flip, the false KDoc and the unconditional overwrite together.

**The obligation it comes with.** All three load paths take `launch` / `launchDefault` with
`onError` defaulting to `{}` (B17's and B21's class). Before this rule a thrown load left
`isLoading` latched and cost nothing visible; **after it, the same throw is a permanently empty
screen.** Each of the three closes its error path in the same commit — end loading, then surface the
existing error event. That is not a spinner drawn to cover the rule; it is the rule's precondition.

What composes before the load: the graph's own `processor.Handle`, `BackHandler` and the launcher
plumbing. Only the **screen** is gated, and the nav host already backs every destination
(`AppNavigationHost` paints `colorScheme.background`), so an unloaded route is an empty frame in the
app's own colour rather than a transparent hole.

## 7.10 DELTA — mockup vs code

| # | Item | Code today | Contract |
|---|---|---|---|
| D1 | Top bar | `AppTopAppBar` ×2 + a raw M3 `TopAppBar` ×1, **and the exercise screen swaps bars mid-mode** — the bar changes under the user when one screen flips to Edit | all three on `AppTopBar` (§7.1, §1.2) |
| D2 | Text field | `OutlinedTextField` with `unfocusedContainerColor = surfaceTier1` — outlined **and** filled | transparent fill, outline only (§7.2) |
| D3 | Error outline width | M3's own 1dp/2dp, not settable on the plain `OutlinedTextField` | 1.5dp (§7.2). **Built**, and the field IS rebuilt on `BasicTextField` — M3 exposes thickness only as focused/unfocused via `OutlinedTextFieldDefaults.Container`, so an *unfocused* error would have drawn at 1dp |
| D4 | Save button | disabled by the condition that produces the error | always enabled (§7.3) |
| D5 | Modals | six dialog instances, five components | six sheets (§7.4) |
| D6 | Plan editor modal state | two channels, both openable at once | one sealed `DialogState` (§7.4) |
| D7 | Discard actions | three (Save / Discard / Continue), in a bespoke `Dialog` | two, in the drawn sheet (§7.4) |
| D8 | Set add/remove | per-row `✕` + a full-width `AppButton.Tertiary` outside the body | `.setbar` in the card's foot (§7.5) |
| D9 | Plan values | `textTertiary` — an authored value drawn as "not yet entered" | `isLogged` → `textPrimary` (§7.5) |
| D10 | Set-type labels | `"W" / "·" / "F" / "D"` **hardcoded English** in `AppSetTypeChip` | localised; Ru `Р / · / О / Д` (§7.5) |
| D11 | Set-type picker | a `DropdownMenu` anchored to the chip | unchanged — the drawing rules the chip, not the picker. **Reported, not folded in.** Note it is the one modal on these screens that stayed a dropdown: §26's sheet ruling names the six *confirmation and choice* modals, and an anchored menu on a 34dp chip is a different object from a sheet. Whether it should also be a sheet is a decision nobody has taken |
| D12 | Add exercise | `AppButton.Tertiary` + `Icons.Default.Add` in the section head | `.addex` (§7.6) |
| D13 | Image | a 72dp thumb + two buttons in a form row | 48dp thumb in the bar, on `.icon-btn`'s rung; the row is deleted (§7.7) |
| D14 | Type marks | `Icons.Filled.FitnessCenter` / `AccessibilityNew` | two new stroke glyphs in `AppIcons` (§7.7) |
| D15 | Reorder | two identical `DragHandle` arrows | long-press drag (§7.8) |
| D16 | Loading | written, never read; a false KDoc and an unconditional overwrite | the route does not compose until loaded (§7.9) |
| D17 | Glyph swaps (B33(a)) | `Close` ×4, `Add` ×1, `ArrowBack` ×1 as filled Material imports | `AppIcons.Close` / `.Plus` / `.ChevronLeft` |

## 7.10a What building it found that reading it did not

Four things, each caught by an instrument rather than by review, recorded because the instrument is
the transferable part.

1. **`hasChanges` was permanently true after the plan editor saved** — §25 **B39**. `adhocPlan` had
   two baselines (`originalSnapshot.adhocPlan` and a field of its own) and the plan editor's return
   reset one. Found by writing the three-states-one-term fixture §27 requires for a multi-term
   predicate: modelling the return's exact writes went red against the unmodified tree. Fixed by
   deleting the second baseline, not by synchronising it.
2. **`.addex` rendered «+ + Добавить».** The old string carried a leading `+` for the Material
   `Add` icon that used to sit beside it, and `.addex` draws its own plus. Caught by *looking at*
   the first recorded golden, which is a different act from recording it.
3. **The thumb sat 2dp from the screen edge.** `AppTopBar`'s 2dp row padding is right for an
   `.icon-btn`, whose glyph is inset 13.5dp inside a 48dp box; the thumb's box edge IS its visual
   edge. Same instrument, same act.
4. **The discard strings existed in FOUR places** — `feature/exercise`, `feature/single-training`,
   `feature/plan-editor` and `core/ui/plan-editor` — and the fourth surfaced only when Android
   Lint called it unused after the other three were deleted. They live in the kit now, one table
   for one component.

And one **green** that is recorded as a no-op rather than a hole, per §27: taking
`ReorderableColumnState`'s drag direction off the accumulated offset instead of the latest delta —
the exact defect that line's own comment warns about — changes nothing the suite can see, because
the loop cannot leave an uncommitted crossing for the wrong expression to find. The derivation is
§27 "Gates", "A guard can be correct, load-bearing, and still un-mutatable"; the line keeps the
guard and the conclusion.

## 7.11 Adjacent, and deliberately NOT folded in

Found while extracting; each is real and none is this stage's.

1. **`TagPickerInline` is duplicated**, privately, in `feature/exercise` and
   `feature/single-training`. Diffed: the two files differ **only** in test tags
   (`ExerciseTag*` / `TrainingTag*`), two string-resource ids, one `@Suppress` and the previews.
   Two files, one component, no kit home.
2. **`AppTagPicker` and `AppDatePickerDialog` ship with zero production consumers.** Grepped: the
   only call to either is inside its own `@Preview`. `AppTagPicker` is **not** a third copy of (1)
   — it takes `Set<String>` and owns its query internally, where the feature copies take
   `ImmutableList<TagUiModel>` with the query hoisted — so it is a *different* answer to the same
   question, shipped and never wired. Whether (1) collapses onto it or onto something new is the
   decision, and it is one decision covering both rows.
3. **Back interception differs between the two editors.** The exercise editor's `interceptBack` is
   `(mode is Mode.Edit && (hasChanges || !mode.isCreate)) || dialogState !is Hidden` — so an
   *unmodified* edit of an existing exercise still intercepts, to flip back to Read. The training
   editor's is `(mode is Mode.Edit && hasChanges) || dialogState !is Hidden`: no `!isCreate` clause,
   so the same gesture on the same kind of screen pops instead of flipping. Same gesture, two
   answers, and neither file mentions the other.
