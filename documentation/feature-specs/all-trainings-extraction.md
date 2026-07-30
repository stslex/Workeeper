# All-trainings — extraction mapping (v3 stage 5, group 1)

**Status: for approval. No code follows until this is approved.**

Read-only pass over `feature/all-trainings` against the shell contract, taken at `dev` = `1a486a13`
(the merged shell-contract stack, #193 → #194 → #195).

**This PR amends the contract in two places and cites the amended text, not the text it was written
against.** Both amendments were found by writing this mapping: the row's trailing slot is now
fixed-width (§3), and both stray nav bars now carry the normative track (D6). A gate check for the
second class came with it. No screen code.

## What governs, and in what order

- **Appearance** — `documentation/mockups/pass2d.html`, sections `#s-list` (409–458), `#s-nav`
  (460–495), `#s-empty` (394–406). §0.1: the mockups are the contract for "layout, structure,
  component treatment, iconography, affordances, states, strings".
- **Tokens, type scale, thresholds, behaviour, scope** — this spec. §26 is the append-only ledger of
  resolved decisions; **18 of its 31 rows govern this screen**.
- **Values the mockup does not decide** — §0.2: font sizes round onto the six-step scale
  (34/26/19/15/12.5/11), raw px round onto the `AppDimension` ladder, CSS mechanics transcribe as
  treatments.

### How this document cites

**By anchor, never by line.** `#s-list`'s selection frame, `.row.sel`, §26 "Selection mode" — not
`pass2d.html:431` or `§26:410`. The rule exists because line citations have now decayed three times in
two rounds of this arc, each time from a one-line insertion somewhere above:

- §10.6 was added to the spec and moved every §26 row by four;
- the `.slot` rule was added to the mockup and moved 73 citations in this document by one;
- B20–B22 were appended to §25 and moved every §26 row again, by three — so `§26:410`, which named
  "FAB in selection mode" when this mapping was written, now names "Leading media in list rows".

Re-deriving after the fact caught all three, and it does not scale: the third happened inside a single
session, to a document whose citations had just been verified. A selector or a row title survives
insertion because it does not encode position. One citation in the first draft was wrong *before* any
shift — a navnote cited three lines off, inherited from reconnaissance — and an anchor would not have
allowed the error to be expressed at all.

Code is cited by **symbol** — `TrainingRow.kt`'s chevron `Icon`, `isEmptyAndIdle()` — with a line
number kept only as a convenience that may go stale, never as the identifier.

This is an **extraction**, not a derivation. Where a region has a drawn referent, the drawing decides
and this document only records where the code currently differs. Where a region has **no** referent,
that is a finding for table 2 — it is not filled in here.

Two ledger facts that shape everything below: `#s-list` draws **no normal-mode top app bar** (the
`.topbar` at `#s-list`'s page heading is the catalogue's own page heading — the pattern is confirmed against `#s-live`, `#s-chart`, `#s-ex`, `#s-past`, `#s-set`, all of which draw back-button +
title + overflow, versus the bare `<h1>` page names at `#s-empty`'s page heading, `#s-list`'s page heading, `#s-nav`'s page heading), and `#s-list` draws **no
filter affordance of any kind** above any list.

---

## 1. The mapping

`§26:N` = row at that line of `v3-redesign-spec.md`. `—` = no ledger row; the drawing governs alone
via §0.1. Verdicts: **MATCH** · **DELTA** (referent exists, code differs) · **UNBUILT** (drawn, not
built) · **CONFLICT** (see §3).

| # | Region | Referent | §26 | Contract fixes | Code today | Verdict |
|---|---|---|---|---|---|---|
| **Host chrome** |
| M1 | Bottom-nav inset under content | `#s-list` navnote `#s-list`'s clearance navnote | §26 "Add action" | Host holds it, **globally**, with the system inset. The list carries **0** of it | `AppNavigationHost.kt:47-51` pads by `BottomNavBar.height` then `.systemBarsPadding()` | **MATCH** |
| M2 | Bottom nav bar itself | `#s-nav` `.nb.track.slide` | §26 "Bottom navigation" / "Nav pill motion" | 60px track (`--sec` + hairline), lifted `--slab`+`--slabtop` pill, inactive icons **`--meta` not `--dim`**, 3 icon-only items | `WorkeeperBottomAppBar` 72dp, `FilledIconButton`, no track, no sliding pill | **out of scope** — the rebuild and the two-bar deletion are a separate code PR (§26 "Bottom navigation", §24 (deletion perimeter)) |
| **Top app bar** |
| M3 | Normal-mode bar, container | `.topbar` (treatment only) | — | `min-height:60`, `padding:6px 20px`, title 20px/600 `-.015em` → 19 rung SemiBold; tracking **not** taken (§26 "Heading tracking value" + B4 put −0.39sp on `text.title` only) | `AppTopAppBar`, `titleMedium` | **DELTA** (rung) — but see G11: the *contents* of this bar are undrawn |
| M4 | Normal-mode title string | none | — | undrawn | `feature_all_trainings_title` = "Trainings" | **gap G11** |
| **List frame** |
| M5 | List container padding | `.row{padding:0 var(--gutter)}`, gutter 20px | — | Row owns its 20px horizontal padding; the list is full-bleed | `contentPadding` 16dp horizontal on the `LazyColumn`, row is an inset card | **DELTA** — see M7 |
| M6 | Bottom clearance | `.pad` caption `#s-list`'s bottom-clearance frame + navnote `#s-list`'s clearance navnote | §26 "Add action" | `16 + 56 + 16` = **88** | `heightLg + screenEdge` = **72** | **DELTA — 16dp short.** See §4 |
| **The row** |
| M7 | Row container | `.row` | §26 "List row" | **Full-bleed, unrounded, ruled**: `min-height:88px` (border-box → 87 content + 1px rule), `padding:0 20px`, `gap:14px`, `border-bottom:1px solid var(--hair-s)` | Inset rounded card: `shapes.medium` 10dp, filled, `padding(12.dp)`, `spacedBy(8.dp)`, **no min-height**, **no rule** | **DELTA (structural).** The kit already ships the drawn shape — `AppSectionRow` has `heightIn(min = rowHeight)` + `maxLines = 2` + `mono.meta` — and `TrainingRow` does not use it |
| M8 | Leading media | `.row` children across every `#s-list` frame — none; `.ord` exists and is unused in `#s-list` | **§26 "Leading media in list rows"** | **"None.** `imagePath` and the type icon stay on the detail screen; the row has no media slot" | `TrainingRowLeading` — 30dp tile, `surfaceTier4`, `Text("⊞")` | **DELTA — explicitly forbidden.** The referent is a stated *absence*, which is a decision, not a blank |
| M9 | Name | `.row-name` | §26 "List row" | 16px/500 `--max`, `-webkit-line-clamp:2` + `max-height:2.5em` (= 2 × 20px), ellipsis → 15 rung Medium | `maxLines = 1` | **DELTA** |
| M10 | Meta line | `.meta` + `.frame .meta` | §26 "List row" / "Meta-line order" | **One** line, mono 12.5 `--meta`, `white-space:nowrap` + `text-overflow:ellipsis` → tail truncates | Two further lines: a wrapping `FlowRow` of chips + count, then a status line | **DELTA** |
| M11 | Meta ordering | drawn strings `#s-list`'s skeleton frame, `#s-list`'s type-token row | **§26 "Meta-line order"** | **Information first, tags last.** Exercise type is the first token (`со весом · 14 сессий · …`). What truncates is always the tail, and the tail is tags | Tags first (chips), count last | **DELTA** |
| M12 | In-row tag chips | — | **§26 "Meta-line order"** | **Rejected.** "`.tag` as drawn (14px, 8/13) does not sit in an 88dp row, and a smaller chip would be a new treatment. The full tag set lives in `TagFilterRow` above the list — the row confirms tags, it does not enumerate them" | `AppTagChip.Static` ×3 + `+N` overflow chip | **DELTA — rejected treatment shipped** |
| M13 | "Active" pill in the name row | — | §26 "Meta-line order" (same rejection) | not drawn; "running" is carried by the row surface + the meta string `идёт сейчас · 12:04` | `AppTagChip.Static("Active")` | **DELTA** |
| M14 | `isActive` row treatment | `.row.live`, markup `#s-list`'s `.row.live`, navnote `#s-list`'s live-row navnote | **— (drawing only)** | `background:var(--slab); box-shadow:var(--slabtop)` — byte-identical to `.row.sel` and to `.card.open`. No accent, no border, no `--molten` | `surfaceTier3` (`--field`) + a 2dp `accent` border | **DELTA** — see **§3.2**, a conflict resolved by D5's finding rather than by precedence. Note the ledger has **no** row for the live-row decision — the *mechanism* is governed (§2.6, §26 "Elevation") but the decision to lift this row is drawing-only |
| M15 | Trailing chevron **and its slot** | `.chev`, `.slot`, empty slot at `#s-list`'s selection frame, the unselected `.row` | **§26 "Selection mode"** | The slot is **fixed-width 20px** and always present; its contents change — chevron, check, or nothing. See **§3**: a conflict, not a delta, and the slot half is an amendment this mapping caused | always rendered, no slot | **CONFLICT** (contents) + **DELTA** (slot) |
| **Selection mode** |
| M16 | Selected row | `.row.sel` | §26 "Selection mode" | `--slab` + `--slabtop` | `accentTintedBackground` (`--raise`) | **DELTA** |
| M17 | The mark | `.chk`, markup `#s-list`'s selection frame, first `.row.sel`/`#s-list`'s selection frame, second `.row.sel` | §26 "Selection mode" | Check glyph `M4 12.5l5 5L20 7`, **20px, stroke 2.2, `--max`**, in the trailing slot. Provenance is `.mitem.on`; note the picker draws it at 18px/1.8 (`.chev` recoloured, `.mitem.on`) — **`#s-list` re-draws it larger and heavier, and that is the drawn value** | no mark at all | **UNBUILT** |
| M18 | Topbar replaced whole | `.topbar` | §26 "Selection mode" | close (`.icon-btn.lead`, `M18 6L6 18M6 6l12 12`) + `h1.sm` **"Выбрано 2"** + trailing trash (`.icon-btn.trail`, `M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13`). Buttons 44×44 radius 12, glyph 21px/1.7, `--meta`; negative margins give an 8px effective edge inset | close + count title, **no trailing action** | **partly UNBUILT** (M19) |
| M19 | Selection topbar trailing action | `.icon-btn.trail` | §26 "Selection mode" ("count plus **actions**", plural) | a trash action lives in the bar | absent | **UNBUILT** |
| M20 | Selection title rung | `.topbar h1.sm` | — | **17px**, distinct from the normal 20px | `titleMedium` for both modes | **DELTA** |
| M21 | Bulk action bar | `.bulk`, markup `#s-list`'s `.bulk` | **— (drawn, unreconciled)** | `--sec` fill, top hairline, `12px 20px`, two 46px buttons radius 16 (`В архив` ghost + `Готово`) | absent (deleted in v2.4: "`BulkActionBar.kt` is deleted. Bulk actions exposed via FAB transform only.") | **UNBUILT + unreconciled** — see D2 |
| **FAB** |
| M22 | FAB, resting | `.fab` | §26 "Add action" / "FAB in selection mode" | 56×56, **radius 18**, `--max` fill / `--base` glyph, `box-shadow:var(--slabtop)`, glyph 24px stroke 2.1 | 56dp, `shapes.medium` **10dp**, no lift | **DELTA** (radius, lift) |
| M23 | FAB, selection morph | `.fab.del`, `.garch` | **§26 "FAB in selection mode"** | **Shape and glyph only.** Squircle **18 → circle 28**, `260ms --e-spring`; the fill stays `--max` and the content `--base` throughout. The glyph is the archive mark `M4 8h16M6 8v11h12V8M10 12h4` — stroke, `fill:none`, 2.1 from `.fab svg`. Glyph swap is a hard cut (`display`), not a crossfade. **No count** | 56dp, `shapes.medium` 10dp, no lift; `Icons.Filled.Delete`; `status.error` fill | **DELTA** — radius, the morph, the lift, the fill, and the glyph, which must become the archive mark and not `Delete` |
| **Paging tails** |
| M39 | Tag filter band | `.tagrow`, `.tag` / `.tag.on` | **§26 "Tag filter band"** | Above the list, inside the frame: `display:flex`, gap 8, `12px var(--gutter)`, `overflow-x:auto` with the scrollbar hidden, **`--hair-s` bottom rule**. Chips are `.tag` as drawn — 14px, `8px 13px`, radius 10, `--sec`/`--meta`; `.on` → `--raise`/`--max` **plus a `--hair-s` border**. **Multi-select**, two chips drawn `.on` | `LazyRow`, `spacedBy(4.dp)`, `contentPadding` 16/8, **no rule**; `AppTagChip.Selectable` at 11sp caption in a 6dp shell, `7.dp/2.dp` padding, no border, and **both states resolve to `--raise`** (B20) | **DELTA** on every axis: container rule, chip size, radius, padding, type rung, and the missing selected/unselected distinction |
| M24 | Loading footer | `.pfoot` + `.spin`, markup `#s-list`'s loading-tail frame | §26 "Paging tails" | Centred spinner (15px, 1.6px `--hair-s` ring, `--meta` top arc, 760ms linear) + **`Загружаю`**, mono 11px uppercase `.12em`, `--dim`; **no top border** | nothing — `loadState.append` is never read | **UNBUILT** |
| M25 | Exhausted | *deliberately not drawn* `#s-list`'s paging navnote | §26 "Paging tails" | **No footer at all** — "end of list states only what is already visible" | nothing | **MATCH** (by absence) |
| M26 | Error footer | `.perr`, markup `#s-list`'s error-tail frame | §26 "Paging tails" | reason `Не удалось загрузить дальше` in `.meta` (and it **truncates**, inheriting `.frame .meta`) + `.btn.ghost` **`Повторить`** 40px radius 12 | nothing; no retry path exists in the module | **UNBUILT** |
| **Empty state** |
| M27 | Empty container | `#s-empty > .empty:nth-of-type(1)` | — (only §13 "Empty states" "pattern in kit; **placement per screen**") | `padding:48px 34px`, `gap:10px`, centred column | `AppEmptyState`, 48dp/24dp, centred in the list Box | **DELTA** (horizontal padding); placement is explicitly delegated, so centring is **not** a violation |
| M28 | Empty glyph | `.empty .glyph` | §26 "Bottom navigation" (couples it to the nav icon) | 52×52 tile, radius 16, **1px dashed `--hair-s`**; mark `M4 12h3l2.5-7 5 14L17 12h3`, 22px stroke 1.6 `--dim`. **This path is byte-identical to the nav bar's trainings icon** — §26 "Bottom navigation" "the drawn empty-state glyphs **verbatim**" | 48dp tile, **solid** 0.5dp border, `Icons.Filled.FitnessCenter` | **DELTA — wrong mark**, and it breaks the nav-icon coupling |
| M29 | Empty headline | `.empty h4`, markup `#s-empty .empty:nth-of-type(1)` | — | **"Здесь появятся тренировки"**, 18px/600 `--max` → 19 rung | "No trainings yet" / "Пока нет тренировок" | **DELTA (string)** |
| M30 | Empty sentence | `.empty p`, markup `#s-empty .empty:nth-of-type(1)` | — | **"Собери шаблон заранее или начни пустую и добавляй упражнения по ходу."**, 14.5px `--meta`, `max-width:274px` → 15 rung / 272dp | "Tap + to create your first training" | **DELTA (string)** |
| M31 | Empty CTAs | `.empty .btns`, markup `#s-empty .empty:nth-of-type(1)` `.btns` | — | **Two**, stacked, primary first, 50px tall, radius 16: **`Создать тренировку`** (`.btn`) and **`Начать пустую тренировку`** (`.btn.ghost`) | **zero actions passed** | **UNBUILT.** The brief calls these contract, not suggestion; note there is no §26 row and the extraction's §4.8 covers only the chart variant |
| **Haptics** |
| M32 | Entering selection | navnote `#s-list`'s haptics navnote | §26 "Haptics" | **`LongPress`** | `LongPress` on row long-press | **MATCH** |
| M33 | Toggling an item | " | §26 "Haptics" | **`ContextClick`** | `ContextClick` | **MATCH** |
| M34 | After a **confirmed** destructive action | " | §26 "Haptics" | **`Confirm`**, after the dialog — "а не по нажатию кнопки" | `LongPress` on bulk confirm; `Confirm` is **never used** in the module | **DELTA** |
| M35 | FAB morph | " | §26 "Haptics" | **fires nothing** — "it follows the long press that already fired, and two in a row read as a fault" | `LongPress` on FAB press in selection mode | **DELTA** |
| M36 | Nav tab change | " | §26 "Haptics" | `SegmentTick` (already shipped) | `SegmentTick` in `BottomAppBar.kt:76` | **MATCH** (host) |
| **Cross-cutting** |
| M37 | Row rule colour | `--hair-s` | — | `--hair-s` `#2B333B` / `#D2D7DD` | — | **no app token exists.** `hair-s` has no slot (it delivers 1.12–1.52:1 where the slots that would take it owe 3:1); it is one of the two named exceptions in `shell_gate.py`. **Decision needed — D3** |
| M38 | Type rungs | §4 + §0.2 | §26 "Scope of 600" | 16→15 Medium, 12.5→`mono.meta`, 18→19 SemiBold, 20→19, 14.5→15. `.btn`'s 600 stays a component treatment, not a rung move | — | rule, applied per row above |

---

## 2. Gaps — regions with no referent

These are **findings to decide**, not obstacles to route around. Nothing below is invented past.

| # | Region | What was searched | Result |
|---|---|---|---|
| ~~G1~~ | **`TagFilterRow`** | — | **CLOSED — drawn.** `.tagrow` is in `#s-list` above the list, with its own §26 row. It stopped being a gap the moment it was drawn; the deltas against the code are M39 |
| ~~G2~~ | Chip selected-vs-unselected is one colour | — | **Recategorised → [B20](v3-redesign-spec.md#25-blocker-registry--append-only).** Not an undrawn region: a live defect, and not screen-local — every `AppTagChip.Selectable` in the app has it. Evidence moved to the registry with it |
| **G3** | Host settings gear | `#s-list` topbar; §26 "Drawn rejections"'s audit of the undrawn topbar `+` | Drawn nowhere. `App.kt:172-192` overlays an `IconButton` at `TopEnd` on every bottom-bar destination, i.e. over this screen's app bar. §26 "Drawn rejections" audits what the topbar may carry and never mentions it |
| **G4** | Confirm dialog treatment | all three sections; every modal primitive in the file (`.sheet`/`.grab`/`.mitem` are session/chart/settings) | **No dialog is drawn anywhere in the shell.** §26 "Haptics" nonetheless *presupposes* one ("`Confirm` after a **confirmed** destructive action"). The screen ships `AppConfirmDialog` with four strings; its treatment has no referent |
| **G5** | Snackbar / result surface | all three sections | Not drawn. `.toast` is a session element only. The screen emits `ShowBulkDeleteSuccess` into the host `SnackbarHost` |
| ~~G6~~ | Non-paging error surface | — | **Recategorised → B21.** Not an undrawn region: a live defect, and the **second instance of B17's class** (a `launch` with no `onError`) rather than a screen quirk. The genuinely undrawn half — the shell draws no general error surface, `.perr` is a paging tail only — is recorded in the B21 entry |
| ~~G7~~ | First-load state | — | **Recategorised → B22.** Not an undrawn region: a blank first load, reachable on every cold start. Absent from the drawing too, and the entry says so |
| **G8** | Filtered-to-empty state | `#s-empty` (three `.empty` blocks: trainings, chart, exercises) | **Not drawn.** The screen has a multi-select filter and cannot distinguish "no data" from "filter matched nothing" — both render the no-data copy |
| **G9** | Selection suppresses the empty state | — | `!state.isSelecting` in the render condition. No drawing, no ledger row |
| **G10** | Normal-mode top app bar **contents** | `#s-list` vs the five screen sections | The section's `.topbar` is the catalogue page heading. The screen's own title, its size and any actions are **undrawn**; only the `.topbar` *treatment* has a referent (M3) |
| **G11** | Screen title string | — | undrawn (see G10) |
| **G12** | Fixed sort order | — | `ORDER BY (last_session_at IS NULL), last_session_at DESC, created_at DESC`, no affordance either side. Absent from both, but **no ledger row records that the fixed order is a decision** |
| **G13** | `BackHandler` exits selection | — | Its KDoc cites `Spec §"Multi-select mode"`, which **does not exist** in this document. Dangling citation |

**Closed by absence, on record** (listed so the next reader does not re-open them): search — `#s-list`'s no-search navnote "Поиска нет ни в одном из четырёх экранов, поэтому в shell он не входит"; pull-to-refresh; scrollbar; per-row archive (swipe-to-archive deleted in v2.4).

### G1 — proposal, not a drawing

I am not drawing `TagFilterRow` and not inventing a treatment inline. The decision is Ilya's; these are
the constraints it has to satisfy, all of them already on record:

1. §26 "Meta-line order" **depends** on this row existing — it is the stated reason in-row chips were rejected. So the
   row cannot simply be deleted without reopening that decision.
2. The only drawn chip treatments are `.tag` (14px, `8px 13px`, radius 10, `--sec` → `.tag.on`
   `--raise` + `--max` + `--hair-s` border) and its static variant. §26 "Meta-line order" says `.tag` **as drawn**
   does not fit an 88dp row — it says nothing against it *above* the list, which is where it already is.
3. `.tag` as drawn is **single-select** in the mockup (`pickTag` is a radio group); the code is
   **multi-select**. The mockup's semantics belong to the chart's range picker and do not transfer.
4. The shipped chip is 11sp caption in a 6dp-radius shell — neither the drawn size nor the drawn radius.

The cheapest honest resolutions, in the order I would put them: **(a)** draw it in `#s-list` and extract
it like everything else — it is one row of the shell that the shell forgot; **(b)** adopt `.tag`'s drawn
treatment as-is for the filter row and record the multi-select divergence in §26; **(c)** record in §26
that the filter row is deliberately undrawn and code-owned, which is honest but leaves §26 "Meta-line order" resting on
an ungoverned component. **(a)** is the only one that leaves the contract able to answer the question it
is asked next time.

---

## 3. Two conflicts between the contract and a deliberate prior decision

Neither is a gap: the contract has a drawn answer in both cases. Both are places where the code made a
considered decision, left a comment saying why, and the drawing says otherwise — and both trace to the
same round, `v2.4-design-foundation.md`, which predates the v3 contract. They get the same treatment,
because a resolution recorded once and skipped once is one somebody re-opens.

### 3.1 The chevron

The contract has a drawn answer here, so this is not a gap. It is a **conflict between the contract and
a deliberate prior decision that left a comment behind**, and it gets its own row so that whoever wrote
the comment is not overruled silently.

**The code's argument** — `TrainingRow.kt:99`:

> `// Chevron always visible — selection state is conveyed by the filled card.`

implementing `v2.4-design-foundation.md:319`, which is explicit and was a decision, not an oversight:

> `- Right-side chevron always visible (not hidden in selection mode).`

Read on its own it is coherent: if the selected row is already filled, the chevron carries no ambiguity,
and removing it makes rows change width mid-list.

**The contract's argument** — §26 "Selection mode":

> Unselected rows **lose the chevron** — in this mode a row does not lead anywhere and has nothing to
> promise.

and the drawing agrees at `pass2d.html:431`, where the unselected row in the selection frame has an
**empty** trailing slot — not a placeholder, not a dimmed glyph. The argument is about a promise, not
about redundancy: in selection mode a tap toggles, it does not navigate, and a chevron says it will.

**Resolution: the contract wins, on §0.1** — the mockups are the contract for "affordances" and
"states", and this is both. Two consequences worth stating rather than discovering:

- "loses the chevron" is not "loses the trailing slot" — the slot is occupied in one state and empty in
  the other;
- **the slot is fixed-width, and that is an amendment this mapping caused rather than a reading of it.**
  As first drawn, the trailing slot's width followed its contents (18px chevron, 20px check, or
  nothing), and the row's text column moved with it: measured at the 452px shell, `.row-body` came back
  **338 / 336 / 370px**. I first filed that as drawn behaviour. It was not — the selection frame was
  drawn without the width question being decided, and collapsing the slot reflows **every** row on
  entering selection mode and **one** row on every toggle, against a two-line clamped name. The
  drawing and §26 "Selection mode" are amended together in this PR: the slot stays at a fixed **20px** (the wider
  of the two glyphs, `.chk`; `.chev` centres in it) and empties rather than disappearing.
  Re-measured after: **336px across all 16 rows**, one width, no reflow. The promise argument is
  untouched — what the amendment removes is the reflow.

**The v2.4 decision is superseded, not wrong.** It answered "is the chevron ambiguous next to a filled
card" (no). §26 "Selection mode" answers a different question — "does a row in selection mode promise navigation"
(also no, and the chevron says otherwise). The comment at `TrainingRow.kt:99` should be replaced by a
citation to §26 "Selection mode" rather than deleted, so the next reader finds the reason and not a silence.

---

### 3.2 The active row's accent ring

**The code's argument** — `TrainingRow.kt`, above the surface decision:

> `// Selected → filled accent-tinted card (spec C3). Active session takes a secondary tier accent so`
> `// users can distinguish "selected for bulk action" from "actively running". Border keeps the active`
> `// accent ring even when selected so we don't lose the running-session affordance.`

`spec C3` resolves — `v2.4-design-foundation.md`, "Selected state visualization: whole `ExerciseRow` /
`TrainingRow` filled with `accentTintedBackground`". The ring is not decoration and not an accident: it
was added to solve a specific collision, that `isSelected` wins the surface over `item.isActive`, so a
row that is both would otherwise stop looking live. The comment says so in as many words.

**The contract's argument** — the drawn live row is `.row.live`: `background:var(--slab)` +
`box-shadow:var(--slabtop)`, byte-identical to `.row.sel` and to `.card.open`. No border, no accent, no
second colour. §26 has no row for the live-row decision at all, so the drawing governs alone under §0.1.

**Resolution — and it is stronger than "the drawing wins", which is the part to record.** The ring was
added to solve the live-and-selected collision. D5's check established that the collision is already
solved, by a signal that was there all along: the drawn live row carries «идёт сейчас · 12:04» in its
meta, and the meta is content that survives selection mode on both sides — the drawn selection frame's
rows keep their metas, and `TrainingRow` renders name, tags and status with no selection branch.

So **the ring is not overruled; it is made unnecessary.** The problem it was built for does not exist,
because the meta line answers it. That distinction matters: "the drawing wins" invites the ring back the
first time somebody notices that a selected live row looks like any other selected row, whereas "the
meta already says it" answers that question when it is asked. As with the chevron, the comment should be
**replaced by a citation to this finding, not deleted** — the reasoning in it is sound, it is the
premise that turned out to be false.

**Shared conditional with D5, and it is the one thing that re-opens both:** the surviving signal is the
meta line. Anything that later suppresses meta in selection mode — a compact selection row, a truncation
rule that drops the tail, a different meta for selected rows — re-opens the chevron's slot question and
this one together, and neither has a second signal left.

## 4. The +16dp clearance delta — all-trainings' half

Recorded, and explicitly **not applied**, at §24 "Carried to the code PR":

> **Carried to the code PR (the only code change this gate found).** `AllTrainingsScreen:171` and
> `AllExercisesScreen:74`: `bottom = AppDimension.heightLg + AppDimension.screenEdge` is **56 + 16 =
> 72** and omits the **leading 16** — the gap between the last row and the top of the FAB. Both become
> `screenEdge + heightLg + screenEdge` = **88** (§26, "Add action"). Two call sites, +16dp each, no
> other screen affected. Recorded here rather than applied: this PR ships no code.

All-trainings' half is **one call site**: `AllTrainingsScreen.kt:171-176`, `bottom = heightLg +
screenEdge` → `screenEdge + heightLg + screenEdge`. Verified still 72dp at `dev` = `1a486a13`; the §24
line numbers have not drifted. The sibling screen's identical edit is **not** in this screen's scope.

The arithmetic is the list's alone. Per navnote `#s-list`'s clearance navnote, the list carries **no** nav-bar inset —
"его держит хост, глобально и вместе с системной вставкой, которую мокап нарисовать не может" — and the
mockup deliberately writes **no total anywhere**. One transcription trap: `.pad` is drawn **132px** tall
while its own caption says **88**; 132 is drawing room (`60 bar + 16 + 56 FAB`), the caption is the
contract.

---

## 5. Verification, as this mapping will be gated

**Baseline goldens before any change.** `feature/all-trainings` has **zero** Paparazzi coverage today —
no plugin, no `golden-gate.gradle.kts` apply, no `src/test/snapshots/`. So "baseline" means the first
commit **records** the goldens against the screen as it is now, and it must add plugin + golden-gate +
recorded PNGs **in one commit**: `golden-gate.gradle.kts:51` fails a module with zero images, and it
finalises every `verifyPaparazzi*`/`recordPaparazzi*` task, so a plugin-only commit turns the repo-wide
verify red. `recordPaparazziDebug` first is the safe order.

**Whole surface, not the body only.** The golden set must cover: the list at rest, the empty state,
selection mode (topbar + selected + unselected row together), the FAB in both states, both paging tails,
the confirm dialog, and the tag filter row — in **both themes**. Transient states go in as **pairs**
(at rest and mid-transient); a lone transient golden asserts nothing about *when* it fires (§10.2).
The FAB morph and the pill are the two transients here.

**Liveness is counted from the XML, not from the word green.** `assertGoldenLiveness` counts
`<testcase>` minus `<skipped>` in suites whose name contains `.golden.`, and fails when
`executed < images` — "A Paparazzi task that skips its tests still exits 0, so this is the check that
turns that into a failure." Its cache hole is already closed (`doNotCacheIf` + `upToDateWhen { false }`
in Paparazzi mode).

**Commands.** `./gradlew clean` then the gates as separate invocations with `--rerun-tasks
--no-build-cache`; detekt on its own invocation, **zero suppressions**; the summary line must read
`N actionable tasks: N executed` — any `from cache` or `up-to-date` voids the result.

**Both directions.** Every gate added here must be shown red on a targeted mutation and reverted, not
merely green: the 88dp row height, the two-line clamp, the single-line meta, the chevron-in-selection
rule, the 88dp clearance, the FAB morph radius, and each haptic constant.

**And this screen's PR will now carry `Mockup Appearance Gate`** (no paths filter). It should pass in
strict mode. It **does** now change `pass2d.html`, so that is worth being precise about rather than
waving through: check 1 compares the `:root` / `body.light` blocks and neither is touched; check 3 scans
added lines for hex literals and the added lines carry none; checks 7, 8 and the new 10 read the amended
file and pass. Verified locally at **11 passed, 0 failed** before this was committed. If it ever goes red
here, that is a real finding about the change, not noise.

---

## 6. What must be decided before code

| # | Decision | Why it blocks |
|---|---|---|
| ~~D1~~ | **RULED (a) and DONE.** `TagFilterRow` is drawn in `#s-list` and extracted as M39; §26 "Tag filter band" records it, including that the drawn treatment's origin is single-select and does **not** transfer | drawn |
| ~~D2~~ | **RULED and DONE.** Fill corrected and check 8 inverted (#198); glyph drawn as the archive mark `.garch`, on the FAB and on the selection topbar, in this PR. `Icons.Filled.Delete` → the archive mark on both screens rides with the code PR; `feature/archive`'s own filled icon is **owed the correction the other way** — recorded, not done | drawn; code pending |
| ~~D3~~ | **RULED — its own palette PR, not this one.** The parity exception's expiry is recorded where the exception lives; the `AppColors.kt` KDoc clause is the text that changes with the decision | → palette PR |
| ~~D4~~ | **RULED — confirmed as contract, and §26 now carries the row.** The glyph, the headline, the sentence and **both** CTAs are contract. The new row also records the coupling the Bottom navigation row already leans on: the empty-state mark and the nav bar's trainings icon are one path, so changing either changes both. Placement stays delegated per §13 | ledger done |
| ~~D5~~ | **CLOSED on the check, without a new treatment.** The collision is narrower than "indistinguishable": only the **surface** collides, and only for the live-and-selected row. The drawn live row's second signal is its meta — «идёт сейчас · 12:04» — and the meta is content that survives selection mode on both sides: the drawn selection frame's rows keep their metas, and `TrainingRow` renders name, tags and status with no selection branch. No live+selected row is drawn anywhere, so the collision was never drawn either way. Nothing to draw in PR 2. **Two things worth keeping**: the surviving signal is the meta line, not the surface, so anything that later suppresses meta in selection mode re-opens this; and the code reached independently for a *different* second signal — it keeps the active accent ring under selection, with a comment saying exactly why — which is itself a DELTA (M14), because the drawn live row has no border | closed |
| ~~D6~~ | **Fixed in this PR — it was not cosmetic** | `#s-list``#s-list`'s bottom-clearance frame and the `.clash` demo both drew `.nb plain pill`, the untracked variant the device pass rejected, while `#s-nav``#s-nav`'s `.nb.track.slide` and §26 "Bottom navigation" make the track normative: **the contract drew two different nav bars**, which is the principle that removed variant B, one level up. Both are now `.nb track pill`, and **check 10** makes the class visible — every other check reads the drawing against a baseline or against `AppColors.kt`; this one reads it against **itself**. Proven both directions |
| ~~D7~~ | **RULED — both need drawing.** Filtered-to-empty and selection-mode-empty are reachable and neither is drawn | → PR 2 (§8) |
| **D8** | **`.chev` is drawn with two different paths** — stays **reported**, not enforced | `M9 5l7 7-7 7` in the shell rows, `M9 6l6 6-6 6` in `#s-past`. One class, two geometries — D6's class. Check 10 is deliberately narrow and does not cover it; widening it is a decision about `#s-past`'s drawing | open, reported |

---

## 7. D2 — RULED: the morph keeps the ordinary FAB treatment

**Ruled, and the chain was accepted as verified.** The ledger is amended in this PR; the drawing and the
code follow (§8). The chain is kept below because it is the reasoning the amendment rests on, and a
ledger row whose reasoning lives only in a merged PR body is the decay this arc keeps finding.

| # | Link | Verdict |
|---|---|---|
| 1 | **The action is archive.** `archiveTrainings` → `BulkArchiveResult`; strings "will move to archive. Restore from Settings → Archive", "Reversible · history preserved" | **confirmed.** `deleteTrainings` exists and is dead on this screen; `pendingBulkDelete` is a misnomer |
| 2 | **`rust` marks destruction only, so a `--rust` fill on an archive action breaks the palette's own rule** — in the direction that misleads, promising irreversibility for a reversible act | **confirmed, with one correction: the rule is §1 "Principle", not §26.** "No coloured accent. `molten` marks records only; `rust` marks destruction only." `AppColors.kt` maps `rust` → `status.error, setType.failure*` — error and failure, not archive. Nothing in the spec carves archive out as destructive |
| 3 | **§26's own wording is the origin: "Morphs to destructive"** — the glyph followed the word | **confirmed.** The row opens "Morphs to destructive, **icon only**". Both the fill and the glyph follow that one adjective |
| 4 | **The count's first rejection ground exists only because the fill was rust** — on `--max` fill, `--base` content clears with room | **confirmed, measured.** With the gate's own arithmetic (`WcagContrast`, truncated), reproduced first against the numbers §26 and B18 already publish (`--base` on `--rust`: 6.11 dark / 5.57 light — exact match), `--base` on `--max` measures **17.77 dark / 17.69 light**. Both clear 7:1 AAA, against the 4.5:1 a count would need. The ground is gone |
| 5 | **The second ground stands and is colour-independent** — the selection topbar already states the count | **confirmed.** Verbatim in the row. The rejection now rests on one argument where it rested on two, and the row does not say so |
| 6 | **B18 loses its candidate** | **confirmed, and slightly stronger than stated.** B18 is already latent with no current consumer — its own text: "nothing draws text on a rust fill today — the only candidate was the count this arc just rejected". Verified that claim rather than trusting it: `AppButton.Destructive` fills with `setType.failureBackground` = `*_RUST_WASH`, not solid rust, so nothing paints content on a rust fill. And `.fab.del` is the **only solid `--rust` fill in the drawing** (the other three uses are rust as text, as a dashed border, and as the `.clash` frame border). Drop it and the shell has no rust fill at all, so B18's wake condition — "any label placed on a rust fill" — has no candidate surface left anywhere in the shell, not merely no count |

**The ruling.** The morph keeps the ordinary FAB treatment — `--max` fill, `--base` content — and
changes **shape and glyph only**. The shape morph was always what announced the mode change; the colour
was carrying a claim the action does not support. The glyph becomes archive, not trash.

**Applied in this PR (ledger):** §26 "FAB in selection mode" is rewritten — the opening word is gone,
because "morphs to destructive" is the origin of the error and must not survive the fix it caused; the
fill and content are stated as the FAB's ordinary treatment; the glyph is archive; and the count
rejection **keeps both grounds**, with the surviving one restated as stronger than when it was written —
a count before an irreversible act is arguable redundancy, before a reversible one it is plainly
unnecessary. A row that recorded only the loss would read as a weakened decision. B18 stays **latent**
and its wake condition is restated on the wider finding: it wakes on any solid `--rust` **fill** being
drawn at all, not on a label placed on one, and there are **zero** such surfaces in the contract once
the drawing follows. Its measurements are why it stays open rather than closing — they are what a future
rust fill would need.

**Applied in this PR (drawing, fill half):** `.fab.del` is now `border-radius:28px` alone — the fill and
content fall back to `.fab`'s `--max`/`--base`. The fill needed no decision, only the glyph did, so
carrying it into PR 2 would have left the ledger and the drawing disagreeing for however long PR 2 took.
**Check 8 moved with it**, and inverted: it asserted the fill *changed* and became `--rust`, which
encoded the retracted decision and would have made the correction unshippable. It now asserts the fill
does **not** change and resolves to `--max` — the stronger form, because it catches a regression to rust
where the old assertion could not. Proven both directions.

**Deferred to PR 2 (drawing, glyph half):** `.gtrash` becomes an archive mark, and the selection topbar's
trail button takes the same mark. **Note there is no archive glyph in the contract to reuse** — the settings "Архив" row carries only a chevron — so PR
2 draws one. The code already ships `Icons.Filled.Inventory2` for archive in `feature/archive`, which is
either the mark to match or the mark to reject deliberately; §0.1 gives the drawing the decision.

**Deferred to the code PR:** `Icons.Filled.Delete` → the archive mark on `all-trainings` **and**
`all-exercises`. One correction applied twice; the screens do not diverge and no ledger row is owed.

**What it does not touch:** the shape morph itself (squircle 18 → circle 28, spring legal — it encodes
nothing), the icon-only decision, and the topbar-replaced-whole rule.

## 8. Sequencing — PR 2 is done; the next step is the code

This is the mapping's own result, and it is what the screen was sent first to discover. **Three of the
regions all-trainings must draw have no referent, and two more are stale in the drawing.** Building the
screen now means inventing exactly the regions the contract cannot answer — which is derivation, the
thing this arc replaced.

So the order is: **a second, small mockup PR, then the code.**

### PR 2 — DONE. The shell now answers the questions this screen asked

Drawn and extracted the same way as the shell-contract stack: drawing, ledger row, mapping row, gate.

| | What | Ruling |
|---|---|---|
| 1 | **D2's glyph half** — `.gtrash` becomes an archive mark, and the selection topbar's trail button takes the same mark. *(The fill half landed in #198: `.fab.del` is shape only and check 8 was inverted to match.)* | D2 |
| 2 | **`TagFilterRow`, drawn in `#s-list`** above the list, and a §26 row for it | D1 (a) |
| 3 | **Filtered-to-empty**, in `#s-empty` | D7 |
| 4 | **Selection-mode empty**, in `#s-empty` | D7 |
| 5 | *(nothing)* — D5 closed on its check; no live-row treatment is owed | D5 |

**There is no longer a window to keep short.** The fill needed no decision, so it was corrected in #198
rather than carried here: the ledger and the drawing agree today, and check 8 enforces it. What is left
pending is one undecided mark, and M23 warns about that rather than about a row nobody should build from.

**Three things PR 2 has to decide that this mapping deliberately does not:**

- **The archive mark itself.** There is none in the contract to extract — the settings "Архив" row
  carries only a chevron. `feature/archive` ships `Icons.Filled.Inventory2` today, and **matching it is
  not the neutral option it looks like.** Measured across the whole drawing: every mark in the shell is
  `fill:none` with a stroke — `.empty .glyph svg` 1.6, `.icon-btn svg` and `.nb button svg` 1.7, `.chev`
  and `.mini svg` 1.8, `.mseg svg` 1.9, `.fab svg` 2.1, `.chk` and `.exhead .swap svg` 2.2, `.mark svg`
  2.7 — and **not one filled mark exists anywhere in it**. Importing a filled Material glyph is the same
  category of import as the stock M3 shapes the nav-bar rebuild exists to remove. So: drawing a stroke
  archive mark and correcting `feature/archive` to match is one correction applied where it belongs;
  matching the existing filled icon is a **deliberate exception and has to be recorded as one**. §0.1
  gives the drawing the decision either way.
- **`TagFilterRow`'s treatment.** `.tag` as drawn is 14px / `8px 13px` / radius 10 / `--sec` → `.tag.on`
  `--raise` + `--max` + a `--hair-s` border, and it is **single-select** in the mockup (`pickTag` is a
  radio group) where the code is multi-select. The shipped chip is 11sp caption in a 6dp shell — neither
  the drawn size nor the drawn radius. Whatever is drawn, the multi-select divergence needs recording.
- **What `.empty` is.** The question is not "are the new ones variants" — `#s-empty` draws three empties
  today and all three belong to *different screens*, so there are no variants yet to be a variant of. The
  real question is whether `.empty` **becomes a component with variants, or stays a pattern instantiated
  per screen**, and that answer governs far more than these two states: every screen's empty, and whether
  `AppEmptyState` grows a variant axis or keeps taking whole strings and actions from its call site.

**What PR 2 is not:** code. No screen work, no kit work, no `Icons.Filled.Delete` change — that rides
with the code PR, which is the step after.

### Then the code PR

All-trainings against a contract that answers, with the deltas this mapping already enumerates: the
88dp ruled row, the two-line clamp, the single meta line, the fixed trailing slot, the selection
treatment and its check glyph, the FAB's shape morph and archive glyph, all three paging tails, the
empty state's strings and both CTAs, the four haptics, and the +16dp clearance. Plus
`Icons.Filled.Delete` → the archive mark on **both** screens.

### Deferred — logged, not chased

One line each. None of these stops a line of screen code being written; if one of them turns out to,
that is worth interrupting for and this list is where to look first.

- **`.empty` as a component or a pattern** — the question §8 reframed. Governs every screen's empty
  state and whether `AppEmptyState` grows a variant axis. Not needed to build this screen.
- **The active row's accent ring** (§3.2) — resolved on paper; the code change rides with the screen.
- **D3, `--hair-s` has no app token** — its own palette PR. The row rule ships against `borderSubtle`
  until then, which is `--hair`, a different value, and that is a known and recorded approximation.
- **D8, `.chev` drawn with two paths** — `M9 5l7 7-7 7` in the shell rows, `M9 6l6 6-6 6` in `#s-past`.
  Check 10 stays narrow; widening it is a decision about `#s-past`'s drawing.
- **`feature/archive`'s filled `Icons.Filled.Inventory2`** — owed the correction the other way, so the
  app has one archive mark and it is the drawn stroke one. Not this arc's screen.
- **The two new drawn regions are structurally ungated** — nothing asserts `.tagrow` exists above the
  list, or that the FAB's morphed glyph is the archive mark. Checks 7/8 measure the morph's geometry and
  the swap, not which mark is in the slot.
- **`AppTagChip`'s own treatment** — the chip is 11sp caption in a 6dp shell where `.tag` is drawn at
  14px / `8px 13px` / radius 10, and both of its states resolve to one colour (B20). It is shared with
  `all-exercises`, so it belongs to B20 and a kit pass, not to this screen's rebuild. The **band** around
  it — padding, spacing and its closing rule — is feature-owned and is built as drawn.
- **The selection top bar's title rung** — `.topbar h1.sm` is 17px against the normal 20px, and
  `AppTopAppBar` has one title style for both. A compact-title variant is a kit API change affecting
  every screen.
- **Two navigation haptics this screen fires that §26 does not name** — `ContextClick` on a row tap that
  navigates, and on exiting selection mode. §26 fixes where its four constants fire and does not forbid
  other uses; recorded so the next reader knows it was seen rather than missed.
- **`Screen.LiveWorkout`'s KDoc is stale** — it says at least one uuid must be non-null, and
  `blank-init adhoc entry leaves both uuids null for downstream session creation` is a shipped, tested
  path that the empty state's second CTA now uses. One comment, in a file this arc does not otherwise
  touch.
- **`AppConfirmDialog`'s window** — the scrim, the placement and the window itself stay out of
  Paparazzi's model. Its *content* is now golden; the window is manual verification (§10.4).
- **B21 and B22** are open in the registry and are code defects this screen's rebuild will pass through;
  they are not contract questions.

## 9. Scope, restated

**In:** `feature/all-trainings` and its own consumers of the kit. The +16dp clearance (§4).

**Out:** the nav-bar rebuild and the deletion of the two existing bars (separate code PR — this screen
ships with what is there today); `home` paging; the other seven screens; any kit-wide migration beyond
this screen's own consumers; `AllExercisesScreen`'s half of the clearance delta.
