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

This is an **extraction**, not a derivation. Where a region has a drawn referent, the drawing decides
and this document only records where the code currently differs. Where a region has **no** referent,
that is a finding for table 2 — it is not filled in here.

Two ledger facts that shape everything below: `#s-list` draws **no normal-mode top app bar** (the
`.topbar` at :411 is the catalogue's own page heading — the pattern is confirmed against `#s-live`
:260, `#s-chart` :279, `#s-ex` :314, `#s-past` :342, `#s-set` :367, all of which draw back-button +
title + overflow, versus the bare `<h1>` page names at :396, :411, :462), and `#s-list` draws **no
filter affordance of any kind** above any list.

---

## 1. The mapping

`§26:N` = row at that line of `v3-redesign-spec.md`. `—` = no ledger row; the drawing governs alone
via §0.1. Verdicts: **MATCH** · **DELTA** (referent exists, code differs) · **UNBUILT** (drawn, not
built) · **CONFLICT** (see §3).

| # | Region | Referent | §26 | Contract fixes | Code today | Verdict |
|---|---|---|---|---|---|---|
| **Host chrome** |
| M1 | Bottom-nav inset under content | `#s-list` navnote :450 | §26:409 | Host holds it, **globally**, with the system inset. The list carries **0** of it | `AppNavigationHost.kt:47-51` pads by `BottomNavBar.height` then `.systemBarsPadding()` | **MATCH** |
| M2 | Bottom nav bar itself | `#s-nav` `.nb.track.slide` :469 | §26:402-403 | 60px track (`--sec` + hairline), lifted `--slab`+`--slabtop` pill, inactive icons **`--meta` not `--dim`**, 3 icon-only items | `WorkeeperBottomAppBar` 72dp, `FilledIconButton`, no track, no sliding pill | **out of scope** — the rebuild and the two-bar deletion are a separate code PR (§26:402, §24:323) |
| **Top app bar** |
| M3 | Normal-mode bar, container | `.topbar` :48 (treatment only) | — | `min-height:60`, `padding:6px 20px`, title 20px/600 `-.015em` → 19 rung SemiBold; tracking **not** taken (§26:395 + B4 put −0.39sp on `text.title` only) | `AppTopAppBar`, `titleMedium` | **DELTA** (rung) — but see G11: the *contents* of this bar are undrawn |
| M4 | Normal-mode title string | none | — | undrawn | `feature_all_trainings_title` = "Trainings" | **gap G11** |
| **List frame** |
| M5 | List container padding | `.row{padding:0 var(--gutter)}` :69, gutter 20px | — | Row owns its 20px horizontal padding; the list is full-bleed | `contentPadding` 16dp horizontal on the `LazyColumn`, row is an inset card | **DELTA** — see M7 |
| M6 | Bottom clearance | `.pad` caption :449 + navnote :450 | §26:409 | `16 + 56 + 16` = **88** | `heightLg + screenEdge` = **72** | **DELTA — 16dp short.** See §4 |
| **The row** |
| M7 | Row container | `.row` :69 | §26:405 | **Full-bleed, unrounded, ruled**: `min-height:88px` (border-box → 87 content + 1px rule), `padding:0 20px`, `gap:14px`, `border-bottom:1px solid var(--hair-s)` | Inset rounded card: `shapes.medium` 10dp, filled, `padding(12.dp)`, `spacedBy(8.dp)`, **no min-height**, **no rule** | **DELTA (structural).** The kit already ships the drawn shape — `AppSectionRow` has `heightIn(min = rowHeight)` + `maxLines = 2` + `mono.meta` — and `TrainingRow` does not use it |
| M8 | Leading media | `.row` children :415-453 — none; `.ord` :75 exists and is unused in `#s-list` | **§26:407** | **"None.** `imagePath` and the type icon stay on the detail screen; the row has no media slot" | `TrainingRowLeading` — 30dp tile, `surfaceTier4`, `Text("⊞")` | **DELTA — explicitly forbidden.** The referent is a stated *absence*, which is a decision, not a blank |
| M9 | Name | `.row-name` :72 | §26:405 | 16px/500 `--max`, `-webkit-line-clamp:2` + `max-height:2.5em` (= 2 × 20px), ellipsis → 15 rung Medium | `maxLines = 1` | **DELTA** |
| M10 | Meta line | `.meta` :39 + `.frame .meta` :227 | §26:405-406 | **One** line, mono 12.5 `--meta`, `white-space:nowrap` + `text-overflow:ellipsis` → tail truncates | Two further lines: a wrapping `FlowRow` of chips + count, then a status line | **DELTA** |
| M11 | Meta ordering | drawn strings :415, :422 | **§26:406** | **Information first, tags last.** Exercise type is the first token (`со весом · 14 сессий · …`). What truncates is always the tail, and the tail is tags | Tags first (chips), count last | **DELTA** |
| M12 | In-row tag chips | — | **§26:406** | **Rejected.** "`.tag` as drawn (14px, 8/13) does not sit in an 88dp row, and a smaller chip would be a new treatment. The full tag set lives in `TagFilterRow` above the list — the row confirms tags, it does not enumerate them" | `AppTagChip.Static` ×3 + `+N` overflow chip | **DELTA — rejected treatment shipped** |
| M13 | "Active" pill in the name row | — | §26:406 (same rejection) | not drawn; "running" is carried by the row surface + the meta string `идёт сейчас · 12:04` | `AppTagChip.Static("Active")` | **DELTA** |
| M14 | `isActive` row treatment | `.row.live` :229, markup :420, navnote :421 | **— (drawing only)** | `background:var(--slab); box-shadow:var(--slabtop)` — byte-identical to `.row.sel` :228 and to `.card.open` :93. No accent, no border, no `--molten` | `surfaceTier3` (`--field`) + a 2dp `accent` border | **DELTA.** Note the ledger has **no** row for the live-row decision — the *mechanism* is governed (§2.6, §26:385) but the decision to lift this row is drawing-only |
| M15 | Trailing chevron **and its slot** | `.chev` :73, `.slot` :74, empty slot at :432 | **§26:408** | The slot is **fixed-width 20px** and always present; its contents change — chevron, check, or nothing. See **§3**: a conflict, not a delta, and the slot half is an amendment this mapping caused | always rendered, no slot | **CONFLICT** (contents) + **DELTA** (slot) |
| **Selection mode** |
| M16 | Selected row | `.row.sel` :228 | §26:408 | `--slab` + `--slabtop` | `accentTintedBackground` (`--raise`) | **DELTA** |
| M17 | The mark | `.chk` :230, markup :431/:433 | §26:408 | Check glyph `M4 12.5l5 5L20 7`, **20px, stroke 2.2, `--max`**, in the trailing slot. Provenance is `.mitem.on`; note the picker draws it at 18px/1.8 (`.chev` recoloured, :498) — **`#s-list` re-draws it larger and heavier, and that is the drawn value** | no mark at all | **UNBUILT** |
| M18 | Topbar replaced whole | `.topbar` :430 | §26:408 | close (`.icon-btn.lead`, `M18 6L6 18M6 6l12 12`) + `h1.sm` **"Выбрано 2"** + trailing trash (`.icon-btn.trail`, `M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13`). Buttons 44×44 radius 12, glyph 21px/1.7, `--meta`; negative margins give an 8px effective edge inset | close + count title, **no trailing action** | **partly UNBUILT** (M19) |
| M19 | Selection topbar trailing action | `.icon-btn.trail` :430 | §26:408 ("count plus **actions**", plural) | a trash action lives in the bar | absent | **UNBUILT** |
| M20 | Selection title rung | `.topbar h1.sm` :50 | — | **17px**, distinct from the normal 20px | `titleMedium` for both modes | **DELTA** |
| M21 | Bulk action bar | `.bulk` :231-232, markup :434 | **— (drawn, unreconciled)** | `--sec` fill, top hairline, `12px 20px`, two 46px buttons radius 16 (`В архив` ghost + `Готово`) | absent (deleted in v2.4: "`BulkActionBar.kt` is deleted. Bulk actions exposed via FAB transform only.") | **UNBUILT + unreconciled** — see D2 |
| **FAB** |
| M22 | FAB, resting | `.fab` :233 | §26:409-410 | 56×56, **radius 18**, `--max` fill / `--base` glyph, `box-shadow:var(--slabtop)`, glyph 24px stroke 2.1 | 56dp, `shapes.medium` **10dp**, no lift | **DELTA** (radius, lift) |
| M23 | FAB, selection morph | `.fab.del` :243-247 | **§26:410** | **Icon only.** Squircle **18 → circle 28**, fill `--rust`, glyph `--base`, transition `border-radius 260ms --e-spring` + background/color `260ms --e-out`; glyph swap is a **hard cut** (`display`), not a crossfade. **No count** | colour swap only (`status.error` = `--rust` ✓), `Add`→`Delete`, no shape morph | **DELTA** — colours match, the morph does not exist |
| **Paging tails** |
| M24 | Loading footer | `.pfoot` + `.spin` :236-238, markup :444 | §26:412 | Centred spinner (15px, 1.6px `--hair-s` ring, `--meta` top arc, 760ms linear) + **`Загружаю`**, mono 11px uppercase `.12em`, `--dim`; **no top border** | nothing — `loadState.append` is never read | **UNBUILT** |
| M25 | Exhausted | *deliberately not drawn* :446 | §26:412 | **No footer at all** — "end of list states only what is already visible" | nothing | **MATCH** (by absence) |
| M26 | Error footer | `.perr` :239-241, markup :445 | §26:412 | reason `Не удалось загрузить дальше` in `.meta` (and it **truncates**, inheriting `.frame .meta`) + `.btn.ghost` **`Повторить`** 40px radius 12 | nothing; no retry path exists in the module | **UNBUILT** |
| **Empty state** |
| M27 | Empty container | `#s-empty > .empty:nth-of-type(1)` :397-399 | — (only §13:300 "pattern in kit; **placement per screen**") | `padding:48px 34px`, `gap:10px`, centred column | `AppEmptyState`, 48dp/24dp, centred in the list Box | **DELTA** (horizontal padding); placement is explicitly delegated, so centring is **not** a violation |
| M28 | Empty glyph | `.empty .glyph` :180-181 | §26:402 (couples it to the nav icon) | 52×52 tile, radius 16, **1px dashed `--hair-s`**; mark `M4 12h3l2.5-7 5 14L17 12h3`, 22px stroke 1.6 `--dim`. **This path is byte-identical to the nav bar's trainings icon** — §26:402 "the drawn empty-state glyphs **verbatim**" | 48dp tile, **solid** 0.5dp border, `Icons.Filled.FitnessCenter` | **DELTA — wrong mark**, and it breaks the nav-icon coupling |
| M29 | Empty headline | `.empty h4` :182, markup :398 | — | **"Здесь появятся тренировки"**, 18px/600 `--max` → 19 rung | "No trainings yet" / "Пока нет тренировок" | **DELTA (string)** |
| M30 | Empty sentence | `.empty p` :183, markup :398 | — | **"Собери шаблон заранее или начни пустую и добавляй упражнения по ходу."**, 14.5px `--meta`, `max-width:274px` → 15 rung / 272dp | "Tap + to create your first training" | **DELTA (string)** |
| M31 | Empty CTAs | `.empty .btns` :184-185, markup :399 | — | **Two**, stacked, primary first, 50px tall, radius 16: **`Создать тренировку`** (`.btn`) and **`Начать пустую тренировку`** (`.btn.ghost`) | **zero actions passed** | **UNBUILT.** The brief calls these contract, not suggestion; note there is no §26 row and the extraction's §4.8 covers only the chart variant |
| **Haptics** |
| M32 | Entering selection | navnote :458 | §26:413 | **`LongPress`** | `LongPress` on row long-press | **MATCH** |
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
| **G1** | **`TagFilterRow`** | every `.tag` occurrence in the whole file; every `.frame` in `#s-list`; `#s-empty`; `#s-nav` | **Not drawn.** `.tag` exists in exactly three places: the chart's range picker (`#s-chart` :295, single-select via `pickTag`), a **static display** pair on exercise detail (`#s-ex` :318, `cursor:default`), and the mockup's own demo toggle (`#s-list` :452, `id="selBtn"`, whose `onclick` only morphs the FAB). Every `.frame` in `#s-list` begins directly with a `.row`. The component is **named in prose and never drawn**: :416 "над списком стоит **TagFilterRow**, и полный набор живёт там", and §26:406 leans on it to justify rejecting in-row chips. **Proposal below.** |
| ~~G2~~ | Chip selected-vs-unselected is one colour | — | **Recategorised → [B20](v3-redesign-spec.md#25-blocker-registry--append-only).** Not an undrawn region: a live defect, and not screen-local — every `AppTagChip.Selectable` in the app has it. Evidence moved to the registry with it |
| **G3** | Host settings gear | `#s-list` :411 topbar; §26:411's audit of the undrawn topbar `+` | Drawn nowhere. `App.kt:172-192` overlays an `IconButton` at `TopEnd` on every bottom-bar destination, i.e. over this screen's app bar. §26:411 audits what the topbar may carry and never mentions it |
| **G4** | Confirm dialog treatment | all three sections; every modal primitive in the file (`.sheet`/`.grab`/`.mitem` :495-498 are session/chart/settings) | **No dialog is drawn anywhere in the shell.** §26:413 nonetheless *presupposes* one ("`Confirm` after a **confirmed** destructive action"). The screen ships `AppConfirmDialog` with four strings; its treatment has no referent |
| **G5** | Snackbar / result surface | all three sections | Not drawn. `.toast` is a session element only. The screen emits `ShowBulkDeleteSuccess` into the host `SnackbarHost` |
| ~~G6~~ | Non-paging error surface | — | **Recategorised → B21.** Not an undrawn region: a live defect, and the **second instance of B17's class** (a `launch` with no `onError`) rather than a screen quirk. The genuinely undrawn half — the shell draws no general error surface, `.perr` is a paging tail only — is recorded in the B21 entry |
| ~~G7~~ | First-load state | — | **Recategorised → B22.** Not an undrawn region: a blank first load, reachable on every cold start. Absent from the drawing too, and the entry says so |
| **G8** | Filtered-to-empty state | `#s-empty` (three `.empty` blocks: trainings, chart, exercises) | **Not drawn.** The screen has a multi-select filter and cannot distinguish "no data" from "filter matched nothing" — both render the no-data copy |
| **G9** | Selection suppresses the empty state | — | `!state.isSelecting` in the render condition. No drawing, no ledger row |
| **G10** | Normal-mode top app bar **contents** | `#s-list` :411 vs the five screen sections | The section's `.topbar` is the catalogue page heading. The screen's own title, its size and any actions are **undrawn**; only the `.topbar` *treatment* has a referent (M3) |
| **G11** | Screen title string | — | undrawn (see G10) |
| **G12** | Fixed sort order | — | `ORDER BY (last_session_at IS NULL), last_session_at DESC, created_at DESC`, no affordance either side. Absent from both, but **no ledger row records that the fixed order is a decision** |
| **G13** | `BackHandler` exits selection | — | Its KDoc cites `Spec §"Multi-select mode"`, which **does not exist** in this document. Dangling citation |

**Closed by absence, on record** (listed so the next reader does not re-open them): search — :439 "Поиска нет ни в одном из четырёх экранов, поэтому в shell он не входит"; pull-to-refresh; scrollbar; per-row archive (swipe-to-archive deleted in v2.4).

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

- "loses the chevron" is not "loses the trailing slot" — the slot is occupied in one state and empty in
  the other;
- **the slot is fixed-width, and that is an amendment this mapping caused rather than a reading of it.**
  As first drawn, the trailing slot's width followed its contents (18px chevron, 20px check, or
  nothing), and the row's text column moved with it: measured at the 452px shell, `.row-body` came back
  **338 / 336 / 370px**. I first filed that as drawn behaviour. It was not — the selection frame was
  drawn without the width question being decided, and collapsing the slot reflows **every** row on
  entering selection mode and **one** row on every toggle, against a two-line clamped name. The
  drawing and §26:408 are amended together in this PR: the slot stays at a fixed **20px** (the wider
  of the two glyphs, `.chk`; `.chev` centres in it) and empties rather than disappearing.
  Re-measured after: **336px across all 16 rows**, one width, no reflow. The promise argument is
  untouched — what the amendment removes is the reflow.

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

The arithmetic is the list's alone. Per navnote :450, the list carries **no** nav-bar inset —
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
| **D1** | **G1 — `TagFilterRow`** | It is on screen, it has no referent, and §26:406 depends on it existing. Proposal in §2 |
| **D2** | **Three archive affordances are drawn for one mode — and all three say *delete*** | `#s-list` draws the topbar trash (:430), the `.bulk` bar (:434) **and** the morphing FAB (:453), and no single frame draws them together. §26:410 reconciles only the FAB; v2.4 deleted `BulkActionBar`. Which of the three ships is the decision — and the survivor **must say archive, in glyph and in string**, because the action is archive and archive is reversible. Verified rather than assumed from the name: `pendingBulkDelete` is a misnomer — the confirm path calls `interactor.archiveTrainings` returning `BulkArchiveResult`, the strings already read "Archive" / "will move to archive. Restore from Settings → Archive" / "Reversible · history preserved", and `deleteTrainings` is dead on this screen. **The two screens do not diverge** — `all-exercises` also archives (`bulkArchive` → `BulkArchiveResult`) and also draws `Icons.Filled.Delete`, so this needs no divergence row, just one correction applied twice. The drawing is the origin of the error, not the code: `.gtrash` and the topbar trash are both the deletion glyph (`M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13`) and §26:410 says "morphs to **destructive**" |
| **D3** | **`--hair-s` has no app token** and it is what the 88dp row is ruled with | Every drawn hairline on this screen (row rule, footers, dashed borders) resolves to a token that deliberately does not exist. `borderSubtle` is the nearest, and it is `--hair`, a different value. **The parity exception has now expired and `shell_gate.py` says so** — it was excused partly on `AppColors.kt`'s clause that "borderSubtle covers every decorative stroke in the app", and the v3 row rule is a decorative stroke it does not cover. A named exception is valid only while its reason holds. The palette decision itself belongs in its own small PR; the `AppColors.kt` KDoc clause is the text that should change with it |
| **D4** | **Empty-state CTAs** | Two drawn actions, zero built, no §26 row, and the extraction's §4.8 covers only the chart variant (citing a `§19` that does not exist). The brief treats them as contract — confirming that here makes it a ledger row rather than a reading |
| **D5** | **Live-row treatment has no ledger row** | `.row.live` is drawing-only. It is byte-identical to `.row.sel`, so a selected *and* active row is indistinguishable from either. Worth a §26 row before it is built |
| ~~D6~~ | **Fixed in this PR — it was not cosmetic** | `#s-list`:449 and the `.clash` demo both drew `.nb plain pill`, the untracked variant the device pass rejected, while `#s-nav`:469 and §26:402 make the track normative: **the contract drew two different nav bars**, which is the principle that removed variant B, one level up. Both are now `.nb track pill`, and **check 10** makes the class visible — every other check reads the drawing against a baseline or against `AppColors.kt`; this one reads it against **itself**. Proven both directions |
| **D7** | **Selection-mode empty state / filtered-empty** (G8, G9) | Both reachable, neither drawn |
| **D8** | **`.chev` is drawn with two different paths** | `M9 5l7 7-7 7` in the shell rows, `M9 6l6 6-6 6` in `#s-past` (:333, :335) — one class, two geometries, the same class of self-contradiction D6 was. Check 10 is deliberately narrow and does **not** cover it: the general form ("no class is drawn with two different geometries") would fail on this today, and widening it is a decision about `#s-past`'s drawing, not this screen's. Reported rather than enforced, so that widening the check is an explicit choice |

---

## 7. Scope, restated

**In:** `feature/all-trainings` and its own consumers of the kit. The +16dp clearance (§4).

**Out:** the nav-bar rebuild and the deletion of the two existing bars (separate code PR — this screen
ships with what is there today); `home` paging; the other seven screens; any kit-wide migration beyond
this screen's own consumers; `AllExercisesScreen`'s half of the clearance delta.
