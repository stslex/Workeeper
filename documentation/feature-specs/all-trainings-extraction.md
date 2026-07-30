# All-trainings — extraction mapping (v3 stage 5, group 1)

**Status: for approval. No code follows until this is approved.**

Read-only pass over `feature/all-trainings` against the shell contract, taken at `dev` = `1a486a13`
(the merged shell-contract stack, #193 → #194 → #195).

## What governs, and in what order

- **Appearance** — `documentation/mockups/pass2d.html`, sections `#s-list` (409–458), `#s-nav`
  (460–495), `#s-empty` (394–406). §0.1: the mockups are the contract for "layout, structure,
  component treatment, iconography, affordances, states, strings".
- **Tokens, type scale, thresholds, behaviour, scope** — this spec. §26 is the append-only ledger of
  resolved decisions; **18 of its 31 rows govern this screen**.
- **Values the mockup does not decide** — §0.2: font sizes round onto the six-step scale
  (34/26/19/15/12.5/11), raw px round onto the `AppDimension` ladder, CSS mechanics transcribe as
  treatments.

This is an **extraction**, not a derivation. Where a region has a drawn referent, the drawing decides
and this document only records where the code currently differs. Where a region has **no** referent,
that is a finding for table 2 — it is not filled in here.

Two ledger facts that shape everything below: `#s-list` draws **no normal-mode top app bar** (the
`.topbar` at :410 is the catalogue's own page heading — the pattern is confirmed against `#s-live`
:259, `#s-chart` :278, `#s-ex` :313, `#s-past` :341, `#s-set` :366, all of which draw back-button +
title + overflow, versus the bare `<h1>` page names at :395, :410, :461), and `#s-list` draws **no
filter affordance of any kind** above any list.

---

## 1. The mapping

`§26:N` = row at that line of `v3-redesign-spec.md`. `—` = no ledger row; the drawing governs alone
via §0.1. Verdicts: **MATCH** · **DELTA** (referent exists, code differs) · **UNBUILT** (drawn, not
built) · **CONFLICT** (see §3).

| # | Region | Referent | §26 | Contract fixes | Code today | Verdict |
|---|---|---|---|---|---|---|
| **Host chrome** |
| M1 | Bottom-nav inset under content | `#s-list` navnote :449 | §26:409 | Host holds it, **globally**, with the system inset. The list carries **0** of it | `AppNavigationHost.kt:47-51` pads by `BottomNavBar.height` then `.systemBarsPadding()` | **MATCH** |
| M2 | Bottom nav bar itself | `#s-nav` `.nb.track.slide` :468 | §26:402-403 | 60px track (`--sec` + hairline), lifted `--slab`+`--slabtop` pill, inactive icons **`--meta` not `--dim`**, 3 icon-only items | `WorkeeperBottomAppBar` 72dp, `FilledIconButton`, no track, no sliding pill | **out of scope** — the rebuild and the two-bar deletion are a separate code PR (§26:402, §24:323) |
| **Top app bar** |
| M3 | Normal-mode bar, container | `.topbar` :48 (treatment only) | — | `min-height:60`, `padding:6px 20px`, title 20px/600 `-.015em` → 19 rung SemiBold; tracking **not** taken (§26:395 + B4 put −0.39sp on `text.title` only) | `AppTopAppBar`, `titleMedium` | **DELTA** (rung) — but see G11: the *contents* of this bar are undrawn |
| M4 | Normal-mode title string | none | — | undrawn | `feature_all_trainings_title` = "Trainings" | **gap G11** |
| **List frame** |
| M5 | List container padding | `.row{padding:0 var(--gutter)}` :69, gutter 20px | — | Row owns its 20px horizontal padding; the list is full-bleed | `contentPadding` 16dp horizontal on the `LazyColumn`, row is an inset card | **DELTA** — see M7 |
| M6 | Bottom clearance | `.pad` caption :448 + navnote :449 | §26:409 | `16 + 56 + 16` = **88** | `heightLg + screenEdge` = **72** | **DELTA — 16dp short.** See §4 |
| **The row** |
| M7 | Row container | `.row` :69 | §26:405 | **Full-bleed, unrounded, ruled**: `min-height:88px` (border-box → 87 content + 1px rule), `padding:0 20px`, `gap:14px`, `border-bottom:1px solid var(--hair-s)` | Inset rounded card: `shapes.medium` 10dp, filled, `padding(12.dp)`, `spacedBy(8.dp)`, **no min-height**, **no rule** | **DELTA (structural).** The kit already ships the drawn shape — `AppSectionRow` has `heightIn(min = rowHeight)` + `maxLines = 2` + `mono.meta` — and `TrainingRow` does not use it |
| M8 | Leading media | `.row` children :414-452 — none; `.ord` :74 exists and is unused in `#s-list` | **§26:407** | **"None.** `imagePath` and the type icon stay on the detail screen; the row has no media slot" | `TrainingRowLeading` — 30dp tile, `surfaceTier4`, `Text("⊞")` | **DELTA — explicitly forbidden.** The referent is a stated *absence*, which is a decision, not a blank |
| M9 | Name | `.row-name` :72 | §26:405 | 16px/500 `--max`, `-webkit-line-clamp:2` + `max-height:2.5em` (= 2 × 20px), ellipsis → 15 rung Medium | `maxLines = 1` | **DELTA** |
| M10 | Meta line | `.meta` :39 + `.frame .meta` :226 | §26:405-406 | **One** line, mono 12.5 `--meta`, `white-space:nowrap` + `text-overflow:ellipsis` → tail truncates | Two further lines: a wrapping `FlowRow` of chips + count, then a status line | **DELTA** |
| M11 | Meta ordering | drawn strings :414, :421 | **§26:406** | **Information first, tags last.** Exercise type is the first token (`со весом · 14 сессий · …`). What truncates is always the tail, and the tail is tags | Tags first (chips), count last | **DELTA** |
| M12 | In-row tag chips | — | **§26:406** | **Rejected.** "`.tag` as drawn (14px, 8/13) does not sit in an 88dp row, and a smaller chip would be a new treatment. The full tag set lives in `TagFilterRow` above the list — the row confirms tags, it does not enumerate them" | `AppTagChip.Static` ×3 + `+N` overflow chip | **DELTA — rejected treatment shipped** |
| M13 | "Active" pill in the name row | — | §26:406 (same rejection) | not drawn; "running" is carried by the row surface + the meta string `идёт сейчас · 12:04` | `AppTagChip.Static("Active")` | **DELTA** |
| M14 | `isActive` row treatment | `.row.live` :228, markup :419, navnote :420 | **— (drawing only)** | `background:var(--slab); box-shadow:var(--slabtop)` — byte-identical to `.row.sel` :227 and to `.card.open` :92. No accent, no border, no `--molten` | `surfaceTier3` (`--field`) + a 2dp `accent` border | **DELTA.** Note the ledger has **no** row for the live-row decision — the *mechanism* is governed (§2.6, §26:385) but the decision to lift this row is drawing-only |
| M15 | Trailing chevron | `.chev` :73 / absent at :431 | **§26:408** | see **§3 — this is a conflict, not a delta** | always rendered | **CONFLICT** |
| **Selection mode** |
| M16 | Selected row | `.row.sel` :227 | §26:408 | `--slab` + `--slabtop` | `accentTintedBackground` (`--raise`) | **DELTA** |
| M17 | The mark | `.chk` :229, markup :430/:432 | §26:408 | Check glyph `M4 12.5l5 5L20 7`, **20px, stroke 2.2, `--max`**, in the trailing slot. Provenance is `.mitem.on`; note the picker draws it at 18px/1.8 (`.chev` recoloured, :497) — **`#s-list` re-draws it larger and heavier, and that is the drawn value** | no mark at all | **UNBUILT** |
| M18 | Topbar replaced whole | `.topbar` :429 | §26:408 | close (`.icon-btn.lead`, `M18 6L6 18M6 6l12 12`) + `h1.sm` **"Выбрано 2"** + trailing trash (`.icon-btn.trail`, `M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13`). Buttons 44×44 radius 12, glyph 21px/1.7, `--meta`; negative margins give an 8px effective edge inset | close + count title, **no trailing action** | **partly UNBUILT** (M19) |
| M19 | Selection topbar trailing action | `.icon-btn.trail` :429 | §26:408 ("count plus **actions**", plural) | a trash action lives in the bar | absent | **UNBUILT** |
| M20 | Selection title rung | `.topbar h1.sm` :50 | — | **17px**, distinct from the normal 20px | `titleMedium` for both modes | **DELTA** |
| M21 | Bulk action bar | `.bulk` :230-231, markup :433 | **— (drawn, unreconciled)** | `--sec` fill, top hairline, `12px 20px`, two 46px buttons radius 16 (`В архив` ghost + `Готово`) | absent (deleted in v2.4: "`BulkActionBar.kt` is deleted. Bulk actions exposed via FAB transform only.") | **UNBUILT + unreconciled** — see D2 |
| **FAB** |
| M22 | FAB, resting | `.fab` :232 | §26:409-410 | 56×56, **radius 18**, `--max` fill / `--base` glyph, `box-shadow:var(--slabtop)`, glyph 24px stroke 2.1 | 56dp, `shapes.medium` **10dp**, no lift | **DELTA** (radius, lift) |
| M23 | FAB, selection morph | `.fab.del` :242-246 | **§26:410** | **Icon only.** Squircle **18 → circle 28**, fill `--rust`, glyph `--base`, transition `border-radius 260ms --e-spring` + background/color `260ms --e-out`; glyph swap is a **hard cut** (`display`), not a crossfade. **No count** | colour swap only (`status.error` = `--rust` ✓), `Add`→`Delete`, no shape morph | **DELTA** — colours match, the morph does not exist |
| **Paging tails** |
| M24 | Loading footer | `.pfoot` + `.spin` :235-237, markup :443 | §26:412 | Centred spinner (15px, 1.6px `--hair-s` ring, `--meta` top arc, 760ms linear) + **`Загружаю`**, mono 11px uppercase `.12em`, `--dim`; **no top border** | nothing — `loadState.append` is never read | **UNBUILT** |
| M25 | Exhausted | *deliberately not drawn* :445 | §26:412 | **No footer at all** — "end of list states only what is already visible" | nothing | **MATCH** (by absence) |
| M26 | Error footer | `.perr` :238-240, markup :444 | §26:412 | reason `Не удалось загрузить дальше` in `.meta` (and it **truncates**, inheriting `.frame .meta`) + `.btn.ghost` **`Повторить`** 40px radius 12 | nothing; no retry path exists in the module | **UNBUILT** |
| **Empty state** |
| M27 | Empty container | `#s-empty > .empty:nth-of-type(1)` :396-398 | — (only §13:300 "pattern in kit; **placement per screen**") | `padding:48px 34px`, `gap:10px`, centred column | `AppEmptyState`, 48dp/24dp, centred in the list Box | **DELTA** (horizontal padding); placement is explicitly delegated, so centring is **not** a violation |
| M28 | Empty glyph | `.empty .glyph` :179-180 | §26:402 (couples it to the nav icon) | 52×52 tile, radius 16, **1px dashed `--hair-s`**; mark `M4 12h3l2.5-7 5 14L17 12h3`, 22px stroke 1.6 `--dim`. **This path is byte-identical to the nav bar's trainings icon** — §26:402 "the drawn empty-state glyphs **verbatim**" | 48dp tile, **solid** 0.5dp border, `Icons.Filled.FitnessCenter` | **DELTA — wrong mark**, and it breaks the nav-icon coupling |
| M29 | Empty headline | `.empty h4` :181, markup :397 | — | **"Здесь появятся тренировки"**, 18px/600 `--max` → 19 rung | "No trainings yet" / "Пока нет тренировок" | **DELTA (string)** |
| M30 | Empty sentence | `.empty p` :182, markup :397 | — | **"Собери шаблон заранее или начни пустую и добавляй упражнения по ходу."**, 14.5px `--meta`, `max-width:274px` → 15 rung / 272dp | "Tap + to create your first training" | **DELTA (string)** |
| M31 | Empty CTAs | `.empty .btns` :183-184, markup :398 | — | **Two**, stacked, primary first, 50px tall, radius 16: **`Создать тренировку`** (`.btn`) and **`Начать пустую тренировку`** (`.btn.ghost`) | **zero actions passed** | **UNBUILT.** The brief calls these contract, not suggestion; note there is no §26 row and the extraction's §4.8 covers only the chart variant |
| **Haptics** |
| M32 | Entering selection | navnote :457 | §26:413 | **`LongPress`** | `LongPress` on row long-press | **MATCH** |
| M33 | Toggling an item | " | §26:413 | **`ContextClick`** | `ContextClick` | **MATCH** |
| M34 | After a **confirmed** destructive action | " | §26:413 | **`Confirm`**, after the dialog — "а не по нажатию кнопки" | `LongPress` on bulk confirm; `Confirm` is **never used** in the module | **DELTA** |
| M35 | FAB morph | " | §26:413 | **fires nothing** — "it follows the long press that already fired, and two in a row read as a fault" | `LongPress` on FAB press in selection mode | **DELTA** |
| M36 | Nav tab change | " | §26:413 | `SegmentTick` (already shipped) | `SegmentTick` in `BottomAppBar.kt:76` | **MATCH** (host) |
| **Cross-cutting** |
| M37 | Row rule colour | `--hair-s` :69 | — | `--hair-s` `#2B333B` / `#D2D7DD` | — | **no app token exists.** `hair-s` has no slot (it delivers 1.12–1.52:1 where the slots that would take it owe 3:1); it is one of the two named exceptions in `shell_gate.py`. **Decision needed — D3** |
| M38 | Type rungs | §4 + §0.2 | §26:397 | 16→15 Medium, 12.5→`mono.meta`, 18→19 SemiBold, 20→19, 14.5→15. `.btn`'s 600 stays a component treatment, not a rung move | — | rule, applied per row above |

---

## 2. Gaps — regions with no referent

These are **findings to decide**, not obstacles to route around. Nothing below is invented past.

| # | Region | What was searched | Result |
|---|---|---|---|
| **G1** | **`TagFilterRow`** | every `.tag` occurrence in the whole file; every `.frame` in `#s-list`; `#s-empty`; `#s-nav` | **Not drawn.** `.tag` exists in exactly three places: the chart's range picker (`#s-chart` :294, single-select via `pickTag`), a **static display** pair on exercise detail (`#s-ex` :317, `cursor:default`), and the mockup's own demo toggle (`#s-list` :451, `id="selBtn"`, whose `onclick` only morphs the FAB). Every `.frame` in `#s-list` begins directly with a `.row`. The component is **named in prose and never drawn**: :415 "над списком стоит **TagFilterRow**, и полный набор живёт там", and §26:406 leans on it to justify rejecting in-row chips. **Proposal below.** |
| **G2** | Chip selected-vs-unselected is one colour | `AppTagChip.kt:76` against `AppColors.kt:448/454, 506/512` | `surfaceTier4` and `accentTintedBackground` **both resolve to `--raise`** in both themes, so the only surviving selection signal is the label (`--body` → `--max`). The contract's `.tag` → `.tag.on` moves `--sec` → `--raise` **plus** `--max` label **plus** a `--hair-s` border. Latent defect independent of G1 |
| **G3** | Host settings gear | `#s-list` :410 topbar; §26:411's audit of the undrawn topbar `+` | Drawn nowhere. `App.kt:172-192` overlays an `IconButton` at `TopEnd` on every bottom-bar destination, i.e. over this screen's app bar. §26:411 audits what the topbar may carry and never mentions it |
| **G4** | Confirm dialog treatment | all three sections; every modal primitive in the file (`.sheet`/`.grab`/`.mitem` :494-497 are session/chart/settings) | **No dialog is drawn anywhere in the shell.** §26:413 nonetheless *presupposes* one ("`Confirm` after a **confirmed** destructive action"). The screen ships `AppConfirmDialog` with four strings; its treatment has no referent |
| **G5** | Snackbar / result surface | all three sections | Not drawn. `.toast` is a session element only. The screen emits `ShowBulkDeleteSuccess` into the host `SnackbarHost` |
| **G6** | Non-paging error surface | all three sections | Not drawn (`.perr` is a *paging* tail). And nothing is built: a failed `archiveTrainings` surfaces nothing and leaves `pendingBulkDelete` set. Same shape as B17 |
| **G7** | First-load state | all three sections | Not drawn. And `isEmptyAndIdle()` requires `refresh is NotLoading`, so during the first load the list is empty **and** the empty state is suppressed → a blank screen |
| **G8** | Filtered-to-empty state | `#s-empty` (three `.empty` blocks: trainings, chart, exercises) | **Not drawn.** The screen has a multi-select filter and cannot distinguish "no data" from "filter matched nothing" — both render the no-data copy |
| **G9** | Selection suppresses the empty state | — | `!state.isSelecting` in the render condition. No drawing, no ledger row |
| **G10** | Normal-mode top app bar **contents** | `#s-list` :410 vs the five screen sections | The section's `.topbar` is the catalogue page heading. The screen's own title, its size and any actions are **undrawn**; only the `.topbar` *treatment* has a referent (M3) |
| **G11** | Screen title string | — | undrawn (see G10) |
| **G12** | Fixed sort order | — | `ORDER BY (last_session_at IS NULL), last_session_at DESC, created_at DESC`, no affordance either side. Absent from both, but **no ledger row records that the fixed order is a decision** |
| **G13** | `BackHandler` exits selection | — | Its KDoc cites `Spec §"Multi-select mode"`, which **does not exist** in this document. Dangling citation |

**Closed by absence, on record** (listed so the next reader does not re-open them): search — :438 "Поиска нет ни в одном из четырёх экранов, поэтому в shell он не входит"; pull-to-refresh; scrollbar; per-row archive (swipe-to-archive deleted in v2.4).

### G1 — proposal, not a drawing

I am not drawing `TagFilterRow` and not inventing a treatment inline. The decision is Ilya's; these are
the constraints it has to satisfy, all of them already on record:

1. §26:406 **depends** on this row existing — it is the stated reason in-row chips were rejected. So the
   row cannot simply be deleted without reopening that decision.
2. The only drawn chip treatments are `.tag` (14px, `8px 13px`, radius 10, `--sec` → `.tag.on`
   `--raise` + `--max` + `--hair-s` border) and its static variant. §26:406 says `.tag` **as drawn**
   does not fit an 88dp row — it says nothing against it *above* the list, which is where it already is.
3. `.tag` as drawn is **single-select** in the mockup (`pickTag` is a radio group); the code is
   **multi-select**. The mockup's semantics belong to the chart's range picker and do not transfer.
4. The shipped chip is 11sp caption in a 6dp-radius shell — neither the drawn size nor the drawn radius.

The cheapest honest resolutions, in the order I would put them: **(a)** draw it in `#s-list` and extract
it like everything else — it is one row of the shell that the shell forgot; **(b)** adopt `.tag`'s drawn
treatment as-is for the filter row and record the multi-select divergence in §26; **(c)** record in §26
that the filter row is deliberately undrawn and code-owned, which is honest but leaves §26:406 resting on
an ungoverned component. **(a)** is the only one that leaves the contract able to answer the question it
is asked next time.

---

## 3. The chevron — a conflict, not a gap

The contract has a drawn answer here, so this is not a gap. It is a **conflict between the contract and
a deliberate prior decision that left a comment behind**, and it gets its own row so that whoever wrote
the comment is not overruled silently.

**The code's argument** — `TrainingRow.kt:99`:

> `// Chevron always visible — selection state is conveyed by the filled card.`

implementing `v2.4-design-foundation.md:319`, which is explicit and was a decision, not an oversight:

> `- Right-side chevron always visible (not hidden in selection mode).`

Read on its own it is coherent: if the selected row is already filled, the chevron carries no ambiguity,
and removing it makes rows change width mid-list.

**The contract's argument** — §26:408:

> Unselected rows **lose the chevron** — in this mode a row does not lead anywhere and has nothing to
> promise.

and the drawing agrees at `pass2d.html:431`, where the unselected row in the selection frame has an
**empty** trailing slot — not a placeholder, not a dimmed glyph. The argument is about a promise, not
about redundancy: in selection mode a tap toggles, it does not navigate, and a chevron says it will.

**Resolution: the contract wins, on §0.1** — the mockups are the contract for "affordances" and
"states", and this is both. Two consequences worth stating rather than discovering:

- the drawn selection frame gives the **selected** row a `.chk` and the **unselected** row nothing, so
  "loses the chevron" is not "loses the trailing slot" — the slot is occupied in one state and empty in
  the other;
- the trailing slot's width therefore differs between states (18px chevron, 20px check, or nothing),
  and the drawn row's text column changes width with it — **338 / 336 / 370px** at the 452px shell.
  The name and meta reflow between selection states. That is drawn behaviour, not an artefact.

**The v2.4 decision is superseded, not wrong.** It answered "is the chevron ambiguous next to a filled
card" (no). §26:408 answers a different question — "does a row in selection mode promise navigation"
(also no, and the chevron says otherwise). The comment at `TrainingRow.kt:99` should be replaced by a
citation to §26:408 rather than deleted, so the next reader finds the reason and not a silence.

---

## 4. The +16dp clearance delta — all-trainings' half

Recorded, and explicitly **not applied**, at §24:338:

> **Carried to the code PR (the only code change this gate found).** `AllTrainingsScreen:171` and
> `AllExercisesScreen:74`: `bottom = AppDimension.heightLg + AppDimension.screenEdge` is **56 + 16 =
> 72** and omits the **leading 16** — the gap between the last row and the top of the FAB. Both become
> `screenEdge + heightLg + screenEdge` = **88** (§26, "Add action"). Two call sites, +16dp each, no
> other screen affected. Recorded here rather than applied: this PR ships no code.

All-trainings' half is **one call site**: `AllTrainingsScreen.kt:171-176`, `bottom = heightLg +
screenEdge` → `screenEdge + heightLg + screenEdge`. Verified still 72dp at `dev` = `1a486a13`; the §24
line numbers have not drifted. The sibling screen's identical edit is **not** in this screen's scope.

The arithmetic is the list's alone. Per navnote :449, the list carries **no** nav-bar inset —
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
strict mode untouched — this work changes no `:root` token and no `AppColors.kt` constant. If it ever
goes red here, that is a real finding about the change, not noise.

---

## 6. What must be decided before code

| # | Decision | Why it blocks |
|---|---|---|
| **D1** | **G1 — `TagFilterRow`** | It is on screen, it has no referent, and §26:406 depends on it existing. Proposal in §2 |
| **D2** | **Three archive affordances are drawn for one mode** | `#s-list` draws the topbar trash (:429), the `.bulk` bar (:433) **and** the morphing FAB (:452) — and no single frame draws them together. §26:410 reconciles only the FAB; v2.4 deleted `BulkActionBar`. Which of the three ships? |
| **D3** | **`--hair-s` has no app token** and it is what the 88dp row is ruled with | Every drawn hairline on this screen (row rule, footers, dashed borders) resolves to a token that deliberately does not exist. `borderSubtle` is the nearest, and it is `--hair`, a different value |
| **D4** | **Empty-state CTAs** | Two drawn actions, zero built, no §26 row, and the extraction's §4.8 covers only the chart variant (citing a `§19` that does not exist). The brief treats them as contract — confirming that here makes it a ledger row rather than a reading |
| **D5** | **Live-row treatment has no ledger row** | `.row.live` is drawing-only. It is byte-identical to `.row.sel`, so a selected *and* active row is indistinguishable from either. Worth a §26 row before it is built |
| **D6** | **`#s-list`'s own nav bar is the pre-collapse variant** | :448 still draws `.nb plain pill` (no track) where `#s-nav` :468 and §26:402 make `.nb track` normative. Cosmetic for this screen — the bar is out of scope — but it is drift inside the contract |
| **D7** | **Selection-mode empty state / filtered-empty** (G8, G9) | Both reachable, neither drawn |

---

## 7. Scope, restated

**In:** `feature/all-trainings` and its own consumers of the kit. The +16dp clearance (§4).

**Out:** the nav-bar rebuild and the deletion of the two existing bars (separate code PR — this screen
ships with what is there today); `home` paging; the other seven screens; any kit-wide migration beyond
this screen's own consumers; `AllExercisesScreen`'s half of the clearance delta.
