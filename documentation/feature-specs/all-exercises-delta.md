# All-exercises — delta mapping (v3 stage 5, group 2)

**Status: the mapping this screen's rebuild is built against. One region stops it — §6.**

Read-only pass over `feature/all-exercises`, taken on `feature/v3-all-exercises`, stacked on
`feature/v3-all-trainings` (six commits off `dev` = `c342e042`).

## What this is, and what it deliberately is not

[`all-trainings-extraction.md`](all-trainings-extraction.md) is a **full** extraction: 39 mapped
regions, 13 gaps, two conflicts, eight rulings. The two screens draw **one skeleton** — `#s-list`'s
own hint says so in as many words ("Скелет строки один — 88px, линейка снизу, имя и мета-строка,
шеврон. Начинки разные") — so re-deriving that skeleton here would produce a second document that has
to be kept in step with the first, and the arc has already lost three citations to exactly that class
of duplication.

So this is a **delta**. Everything the sibling mapping ruled is cited, not re-argued:

- the 88dp full-bleed ruled row, the two-line clamp, the single non-wrapping meta line, and
  "information first, tags last" — *all-trainings §1* M-rows and §26 "List row" / "Meta-line order";
- the fixed-width trailing slot and its three contents — *all-trainings §3* and the §26 "Selection
  mode" amendment;
- the chevron's meaning and why the always-visible comment was replaced rather than deleted —
  *all-trainings §3.1*;
- the FAB's shape-and-glyph-only morph, the archive mark, the rejected count — *all-trainings §7*
  (D2) and §26 "FAB in selection mode";
- the paging tails, three states / two drawings — §26 "Paging tails";
- the `16 + 56 + 16` = 88 clearance — *all-trainings §4* and §26 "Add action";
- the four haptic constants — `#s-list`'s haptics navnote;
- the tag filter band above the list, and that its drawn treatment's single-select grammar does
  **not** transfer — D1 (a) and §26 "Tag filter band".

**Citations are by anchor, never by line** — the rule and the three decays behind it are in
*all-trainings §"How this document cites"*. Code is cited by symbol.

**Nothing here re-opens a ruled decision.** Where this screen's code differs from a ruling, the ruling
wins and the difference is a DELTA to apply, not a question to re-ask.

---

## 1. The row — `ExerciseRow` against `TrainingRow`

### 1.1 The models, field by field

`TrainingListItemUi` vs `ExerciseUiModel`. This is the whole reason the two rows are **not** one
component yet: the payloads do not line up on a single field.

| `TrainingListItemUi` | `ExerciseUiModel` | Delta |
|---|---|---|
| `uuid: String` | `uuid: String` | same |
| `name: String` | `name: String` | same |
| `tags: ImmutableList<String>` | `tags: ImmutableList<String>` | same type, same role — the meta tail |
| `exerciseCount: Int` | `sessionCount: Int` | one count each, but they count different things and are pluralised by different resources |
| — | `linkedTrainingsCount: Int` | **exercise-only.** A second count with no training-side twin |
| — | `lastTrainedAt: Long?` | **exercise-only**, and already folded into `footerLabel` by the mapper — the row never reads it |
| `isActive: Boolean` | — | **training-only.** An exercise is never "running"; there is no live exercise row anywhere in the contract |
| `statusLabel: String` | `footerLabel: String` | same *shape*, opposite *authorship* — see 1.2 |
| — | `type: ExerciseTypeUiModel` | **exercise-only, and it is the delta the drawing cares about** — see 1.3 |
| — | `imagePath: String?` | **exercise-only**, and the drawing says it does not belong in the row — see 1.4 |

Two fields the row will not read after the rebuild (`lastTrainedAt`, `imagePath`) and one it must now
read (`type`). No model change: `lastTrainedAt` is the mapper's input for `footerLabel`, and
`imagePath` is the detail screen's.

### 1.2 Where the meta line is composed — the one structural difference

`TrainingRow` composes its own meta, in the row, from three fields:

```kotlin
(listOf(statusLabel.trim(), count).filter { it.isNotEmpty() } + tags).joinToString(" · ")
```

`ExerciseUiModel` arrives with `footerLabel` **already joined** by `AllExercisesUiMapper.composeFooterLabel`
— sessions, linked trainings and last-trained, interpunct-separated — and `tags` arrives separately,
unjoined and unused by the current row's meta.

That is not a defect and it is not being unified. It is the reason a shared row component would have
to take either a pre-joined string (losing the training side's pluralisation at the render site) or
seven nullable fields (a union type wearing a component's name). **The row is built on its own** and
the extraction question stays open — deliberately, per the brief.

What the rebuild does change: the row now appends `tags` to `footerLabel` and prepends the type, so
the composed order matches the drawn one. `composeFooterLabel` is untouched.

### 1.3 The type is the meta line's **first token**, and this is the screen's own drawn region

`#s-list`'s "идущая тренировка · тип упражнения" frame draws it:

> Отведение гантелей через стороны
> **со весом** · 14 сессий · последняя 9 июля · плечи

and its navnote states the rule rather than leaving it to be inferred:

> Ведущего слота у строки нет: миниатюра и иконка типа остаются на детали. Тип поэтому идёт словом и
> **первым токеном** мета-строки — по тому же правилу «сведения, потом теги», — и обрезается
> последним, а не первым.

Three things follow, and only the first is obvious:

1. The type renders as a **word**, not a glyph or a tile.
2. It goes **first**, which is a positional consequence of §26 "Meta-line order" rather than a new
   rule: the line does not wrap, so what truncates is the tail, and the type is the token that must
   never truncate.
3. It is the *reason* there is no leading slot — the drawing removes the tile and the thumbnail and
   then says where the information they carried went. Dropping the leading media without moving the
   type into the meta would lose the type entirely.

`ExerciseTypeUiModel` has two variants and **no display string** anywhere — `ExerciseTypeIcon` is the
only current consumer and it renders a Material glyph, not a word. Two strings are owed
(`…_type_weighted` / `…_type_weightless`), and per the domain-layer rule in `CLAUDE.md` they resolve
in the UI layer, not the mapper's domain input.

The drawn word is «со весом»; the weightless case is not drawn and takes «без веса». `values/strings.xml`
is `en`, so the shipped pair there is `with weight` / `bodyweight`.

**One deliberate one-character departure from the contract.** «со весом» is a typo — the preposition
takes its bare form before «вес» — and a typo is not a string decision, so `values-ru` ships «с
весом». Recorded here and in a comment on the string rather than corrected silently, because §0.1
gives the drawing the strings and this is the one place the transcription does not obey it.

### 1.4 No leading media — the same stated absence, with a second referent

*all-trainings §1* records §26 "Leading media in list rows" as a stated absence and drops nothing,
because `TrainingRow` had no leading media to drop. **This screen is where that row actually bites.**
`ExerciseRow` draws `ExerciseLeading`: a Coil `AsyncImage` at `LEADING_THUMB_SIZE = 28.dp` when
`imagePath != null`, and `ExerciseTypeIcon` — a 28dp `surfaceTier4` tile holding
`Icons.Filled.FitnessCenter` or `Icons.Filled.AccessibilityNew` — otherwise.

Both go. The navnote names both by name ("миниатюра и иконка типа остаются на детали"), so this is
transcription, not interpretation.

**The ruling, restated as what it actually means.** It was given as "the miniature and the type icon
stay on the detail screen", and this document repeated that wording faithfully — but it cannot be
true of `ExerciseTypeIcon` and never was. The component is `internal` to `feature/all-exercises` and
takes that module's `ExerciseTypeUiModel`, so `feature/exercise` cannot reference it across the
module boundary, and does not need to: it already draws the type glyph itself (`ExerciseHero`,
`ImageEditRow`). What the ruling means is two separate facts, and only the first is about the
drawing:

1. **The row has no leading slot.** That is the contract, and it is what the navnote says.
2. **The type icon therefore has no consumer in this module.** That is a consequence, and it makes
   the file dead code, not an asset held for elsewhere.

So `ExerciseTypeIcon.kt` is **deleted**. Left alone it would have been an unreferenced file shipping
the exact 28dp `surfaceTier4` tile with filled Material glyphs this rebuild removes, with nothing to
signal it: detekt's unused-declaration rules only reach `private`.

Consequence worth stating: **Coil leaves the row**, so the row becomes a pure function of its model
and is photographable without a network/disk fake. That is why the golden set can cover it at all.

### 1.5 The composable, line by line

What actually differs, rather than what looks like it might:

**Verdicts on behaviour say whether they are verified.** A MATCH row asserts two implementations
agree, and nothing contradicts it unless a test fails when they stop agreeing. This mapping shipped
a MATCH on the long-press site and a summary claiming both screens fire identically, while this
screen fired two haptics — because it had no long-press test at all. §27 now carries the rule: a
behavioural MATCH cites a test covering both sides, or is marked **UNVERIFIED**. Appearance rows are
exempt only where a golden covers both sides.

| Region | `ExerciseRow` today | After | Same as `TrainingRow`? |
|---|---|---|---|
| Container | `clip(AppUi.shapes.medium)` + `animateColorAsState` between `accentTintedBackground` / `surfaceTier1`, inset by the list's `screenEdge` contentPadding, `Space.sm` between items | full-bleed, `RectangleShape`, `liftedSurface(lifted = isSelected, restingColor = Transparent)`, no inter-item spacing | **yes** — identical modifier chain except `lifted`, which is selection alone here (see the note below this table) |
| Height | `padding(cardPadding)`, intrinsic | `heightIn(min = AppDimension.rowHeight)` + `padding(horizontal = screenEdge)` | yes |
| Divider | none (cards are separated by spacing) | `HorizontalDivider(borderHairline, borderSubtle)` when `showDivider` | yes |
| Leading | `ExerciseLeading` — thumb or type tile | **removed** (1.4) | yes (neither has one) |
| Name | `bodyMedium`, **no `maxLines`** | `titleMedium`, `maxLines = 2`, `TextOverflow.Ellipsis` | yes |
| Tags | `ExerciseRowTags` — a `FlowRow` of chips, `MAX_INLINE_TAGS = 3` + an overflow chip | **removed as chips**, appended to the meta line as text | yes (§26 "Meta-line order" rejects in-row chips) |
| Meta | `footerLabel` in `bodySmall`, on its own line below the tags | one `mono.meta` line: `type · footerLabel · tags`, `maxLines = 1`, ellipsis | **no** — same treatment, different composition (1.2, 1.3) |
| Trailing | `Icons.AutoMirrored.Filled.KeyboardArrowRight`, always visible, `Modifier.size(iconSm)` | fixed-width `SLOT` box: `AppIcons.ChevronRight` / `AppIcons.RowCheck` / **empty** | yes |
| Selection | `isSelected` only | `isSelected` **and** `isSelecting` | **no** — the parameter does not exist yet |
| Position | — | `showDivider` | **no** — the parameter does not exist yet |

**`lifted` is `isSelected` alone, not `isSelected || item.isActive`.** `TrainingRow`'s disjunction
exists because a training can be live; nothing on this screen can. The comment explaining the
collision (*all-trainings §3.2*) does not transfer and must not be copied — there is no collision
here to explain.

**The always-visible-chevron comment goes the way *all-trainings §3.1* ruled**: replaced by a citation
to the finding, not deleted. Its premise — "the filled-card selection visual replaces the checkbox
affordance entirely (spec C3)" — is false twice over on this screen after the rebuild: the card is
gone, and the drawn selection mark is the check in the slot.

---

## 2. The screen — what differs beyond the row

| Region | `AllExercisesScreen` today | Referent | Verdict |
|---|---|---|---|
| List padding | `PaddingValues(start/end = screenEdge, top = Space.sm, bottom = heightLg + screenEdge)` + `spacedBy(Space.sm)` | `#s-list` `.row` owns its gutter; the clearance navnote | **DELTA** — full-bleed, `bottom = LIST_BOTTOM_CLEARANCE` only |
| Clearance | `heightLg + screenEdge` = **72** | `16 + 56 + 16` = **88** | **DELTA** — the sibling of the edit *all-trainings §4* applied; this is the second and last call site |
| Paging tail | **none.** `loadState.append` is never read | `#s-list`'s pagination frame: loader footer / nothing / reason + Повторить | **UNBUILT** — a failed page is a list that quietly stops |
| Selection top bar | close + count; **no actions** | `#s-list`'s selection frame draws the archive mark in the trail slot | **UNBUILT** — same omission the sibling had |
| FAB glyph | `Icons.Filled.Delete` / `Icons.Filled.Add` | `.garch` / `.gplus` | **DELTA** — D2's second application, already ruled |
| FAB fill | `status.error` when selecting | `--max` throughout | **DELTA** — D2. The fill is the half that was already wrong in the ledger and is now corrected in it |
| FAB shape | no morph | squircle 18 → circle 28 | **UNBUILT** |
| Empty state | glyph `Icons.Filled.FitnessCenter`, headline + sentence, **no action** | `#s-empty`'s third `.empty` | **DELTA** — see 2.2 |
| Tag band | `LazyRow`, `Space.xs` spacing, `Space.sm` vertical, **no closing rule** | `.tagrow` + the hairline the sibling built | **DELTA** — `Space.sm` / `Space.md` / closing `HorizontalDivider` |
| Bulk-archive confirm | `pendingBulkDelete` → `AppConfirmDialog` | §26; `AppConfirmDialogContent` is already split | **MATCH — appearance, covered by `confirmDialogContent` on both screens** |
| Permanent-delete confirm | `pendingPermanentDelete` → `AppConfirmDialog` | — | **DEAD.** See 2.3 |
| Blocked-archive dialog | `AppBlockedArchiveDialog` | nothing drawn; it is a behavioural surface | **ungated** — see 5 |

### 2.3 The permanent-delete dialog is unreachable

`pendingPermanentDelete` is read in three places and **written to non-null in none.** Every write in
the repository sets it to `null`: `State.init`, `processConfirmPermanentDelete`,
`processCancelPermanentDelete`. It is a store field, so only this feature could set it, and this
feature does not — there is no `OnPermanentDelete` action and no other producer. The two tests that
exercise it construct the non-null state by hand.

So the screen carries a whole destructive path that cannot be entered: a dialog, four strings, two
actions, a `Confirm`-tier haptic and `AllExercisesInteractor.permanentlyDelete` behind it.

**The two tests covering it pass by building their own precondition.** They set
`pendingPermanentDelete` to a hand-made `PendingDelete` and assert the handler behaves — which it
does, faithfully, on a state the application cannot produce. A test that constructs its own
precondition cannot tell you whether the precondition is reachable, so it certifies an unreachable
surface while counting as coverage. The cheap discriminator is to ask what *writes* the state the
test reads; here nothing does, and one `grep` said so.

**Not fixed here** — restoring the trigger is a behavioural decision about when a permanent delete is
offered at all (the strings claim "This exercise has no history and isn't used", which is a
precondition nothing currently evaluates), and the contract draws no affordance for it. Filed as
**B23**, both findings, and left standing.

It changes one thing about the gating plan: this dialog is **not** goldened. A golden of an
unreachable surface asserts that a picture nobody can see has not changed — which is the "gate that
gates nothing" shape §27 exists to catch, in a form that would look like coverage on the count.

### 2.1 The empty-state glyph is the second doubly-load-bearing mark

`#s-empty`'s third `.empty` draws `M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01` at the 1.6
empty-glyph stroke — and `#s-list`'s bottom-clearance frame draws the **same path** as the nav bar's
third tab. That is the exact coupling `AppIcons.Trainings` already carries for trainings (its KDoc:
"the empty state and the nav bar are one mark and changing either changes both"), now instantiated a
second time. `AppIcons.Exercises` is added with the same warning, and §26 "Bottom navigation" already
takes the nav icons from "the drawn empty-state glyphs verbatim", so nothing new is decided.

The three `h.01` segments are zero-length strokes with round caps — dots. They ship as drawn.

### 2.2 One CTA, not two

The trainings empty draws two buttons; the exercises empty draws **one** — «Добавить упражнение» —
and no second. `AppEmptyState` already renders 0/1/2 actions, so the difference is entirely in the
call. The strings change too: the shipped `Tap + to create your first exercise` is a sentence about
an affordance the user must find; the drawn one is «Добавь первое — дальше его можно будет класть в
любую тренировку», which says what the thing is *for*. §26 "Empty state" makes the glyph, the
headline, the sentence and the CTAs contract.

---

## 3. Haptics

`#s-list`'s haptics navnote fixes four constants. Two of this screen's sites already match; four
diverge, and **all four diverge from the sibling too** — the corrections below are what makes the two
screens fire the same way, not a fresh reading of the navnote.

| Site | Today | Contract | Action |
|---|---|---|---|
| Enter selection (`processExerciseLongPress`) | `LongPress` | `LongPress` | **MATCH — verified** (`entering selection by long press fires LongPress`, both screens) |
| Toggle (`processSelectionToggle`) | `ContextClick` | `ContextClick` | **MATCH — verified** (`selection toggle fires ContextClick`, both screens) |
| **Long press *inside* selection** | fired `LongPress` **then** `ContextClick` — two for one gesture | `ContextClick` alone | **DELTA, and it was recorded as MATCH.** The first draft of this table had no row for it at all and the summary below claimed six identical sites. The sibling's fix and its "two in a row reads as a fault" comment predate this branch and were never carried over. Now `long press inside selection fires ContextClick, not a second LongPress`, on both screens, with `exactly = 1` — a `captured.any { … }` check passes on the broken code |
| Confirmed archive (`processBulkDeleteConfirm`) | **`LongPress`** | **`Confirm`** — "подтверждённое удаление, после диалога" | **DELTA** |
| FAB in selection mode (`processBulkDelete`) | `LongPress` | nothing — "морф отдачи не даёт: он следствие долгого нажатия, которое уже отработало" | **DELTA**, remove. The sibling's `processFabClick` already carries this comment verbatim |
| Permanent delete confirm (`processConfirmPermanentDelete`) | `LongPress` | `Confirm` | **DELTA** |
| Tag chip (`processTagFilterToggle`) | `SegmentTick` | the navnote assigns `SegmentTick` to the nav bar's tab change | **DELTA**, remove. Not a judgement call this mapping makes: the sibling *already* removed it, with the reasoning in a comment on `processTagFilterToggle` — "this screen borrowed it for a filter chip, which is a different gesture on a different surface". Leaving it would make two screens with one filter band behave differently |
| Row tap that navigates, exit selection | `ContextClick` | unnamed | **recorded** — the same pair *all-trainings §"Deferred"* already logged |

After the corrections both screens fire the same four constants at the same **seven** sites, which
is the actual test of "the vocabulary is not extended" — one screen holding the line while its
sibling borrows a fifth meaning is the same defect with a smaller blast radius.

That sentence originally said six, and was written while the two screens differed at the seventh.
It is the reason §27 now requires a behavioural MATCH to name its test: the count was arrived at by
reading both handlers side by side, which is exactly the method that produced the error.

---

## 4. Recurrences — deferred items that now have a second consumer

The brief's rule: *a deferred item that recurs stops being deferred*. Five items from
*all-trainings §"Deferred"* and §25 acquire their second consumer here. Four are answerable in place;
**one is not, and it stops this mapping** (§6).

| Item | Second consumer | Disposition |
|---|---|---|
| **D7 — filtered-to-empty** (and its twin, selection-mode-empty) | this screen's tag filter is wired straight into paging (`PagingHandler.pagingUiState` flat-maps `activeTagFilter` into `observeExercises`), so a filter that matches nothing is one tap away | **STOPS. See §6** |
| **B21** — bulk archive has no `onError` | `ClickHandler.processBulkDeleteConfirm` here has the identical shape: `launch(onSuccess = { … })`, state reset and snackbar both *inside* `onSuccess` | **registry widened**, not fixed here. B21 was filed as a code defect with no drawn error surface; a second instance confirms the class (B21 already calls itself "the second instance of one defect class" against B17 — this makes three) and changes nothing about what it needs, which is a drawn general error surface |
| **B22** — first load renders a blank screen | `isEmptyAndIdle()` is copied verbatim into `AllExercisesScreen`, and the empty state is gated on it identically | **registry widened.** Also undrawn, and unlike D7 it is *not* filter-dependent — it fires on every cold open. Recorded as a second consumer, not resolved: its answer is the same drawing D7 needs |
| **B20** — `AppTagChip`'s two states resolve to one colour | this screen's `TagFilterRow` | **already recorded as two-consumer** when it was filed ("Not screen-local: every `AppTagChip.Selectable` in the app has it"). It stays a kit pass. Fixing it on this branch would touch a shared component while the sibling screen is under a concurrent device pass — the one thing the brief asked to avoid |
| **`.empty` — component or pattern?** | the exercises empty (one CTA) against the trainings empty (two) | **answered by instantiation, not by decision.** `AppEmptyState` already takes whole strings and 0/1/2 actions from its call site; two screens now use it at different arities without it growing a variant axis. The open question was whether it *should* grow one — and the answer this screen provides is that it does not need to. Recorded, closed |

---

## 5. Gating — the whole surface, and the holes in it

`feature/all-exercises` has **zero** Paparazzi coverage: no plugin, no `golden-gate` apply, no
`src/test/snapshots/`. Same starting point as the sibling, so the same order applies and for the same
reason — `golden-gate.gradle.kts` fails a module with zero images and finalises every
`verifyPaparazzi*`/`recordPaparazzi*` task, so plugin + apply + recorded PNGs go in **one commit**,
`recordPaparazziDebug` first.

**Baselines are recorded against the screen as it is now**, before a single change — the rebuild is a
whole-surface change and a rebuild with no before-picture is a diff nobody can read.

**What the golden set must cover**, in both themes: the row at rest, with a clamped name, selected,
unselected-while-selecting, and both types (the type token is the screen's own drawn region and
nothing else asserts it); the tag band; both paging tails; the empty state; the bulk-archive confirm
dialog's content; the blocked-archive dialog's content; and whole-screen frames for list, selection
and empty. Transients as pairs (§10.2): rest/selected for the row, list/selection for the screen.
**Not** the permanent-delete dialog — 2.3.

**Two surfaces need splitting before they can be photographed at all.**
`AppConfirmDialogContent` already exists — the sibling split it. `AppBlockedArchiveDialog` does
**not**: its content is inside `Dialog {}`, which composes into its own window, and Paparazzi models
one window. It is the only bulk-archive failure surface on this screen and it has a drawn treatment
and no visual gate — precisely the combination §27 records as worth avoiding. It gets the same split.

**What a golden cannot see, and therefore needs a named constant and a direct assertion** — the class
§27 now carries, learned twice on the sibling:

- `LIST_BOTTOM_CLEARANCE` — `contentPadding.bottom` moves no pixel in an unscrolled frame, and
  Paparazzi renders one unscrolled frame. `AllExercisesClearanceTest` is the gate.
- the two-line clamp — invisible unless a fixture actually reaches two lines. The sibling proved
  this the hard way: with only a truncates-on-one-line fixture, mutating `maxLines` 2 → 1 left every
  golden byte-identical. A `rowClamped` fixture is mandatory, not decorative.

**Both directions, on every gate.** Red on a targeted mutation, then reverted — a green result from
a detector never proven to fire is worth nothing.

### 5.1 The mutation run — 23 for 23, and one hole

Every mutation was applied to committed code, run, and reverted. A mutation that fails to **compile**
proves nothing and was re-run in a compiling form (the first attempt at the row height did exactly
that).

| # | Mutation | Caught by |
|---|---|---|
| 1 | row height `rowHeight` → `heightLg` (88 → 56) | 16 goldens |
| 2 | name clamp `maxLines` 2 → 1 | `rowClamped` + both screens |
| 3 | meta line allowed to wrap (1 → 2) | 16 goldens |
| 4 | type token moved from head to tail | 16 goldens |
| 5 | unselected-in-selection keeps its chevron | `rowUnselectedInSelection` + `screenSelection` |
| 6 | clearance back to the shipped 72 | `AllExercisesClearanceTest`, both cases |
| 7 | FAB morph radius → the resting squircle | `screenSelection` |
| 8 | FAB glyph → plus (no swap) | `screenSelection` |
| 9 | empty-state glyph → the trainings mark | `emptyState` |
| 10 | empty state loses its CTA | `emptyState` |
| 11 | bulk-archive confirm `Confirm` → `LongPress` | `ClickHandlerTest` |
| 12 | permanent-delete confirm `Confirm` → `LongPress` | `ClickHandlerTest` |
| 13 | tag chip fires `SegmentTick` again | `ClickHandlerTest` |
| 14 | the FAB morph buzzes again | `ClickHandlerTest` |
| 15 | selection top bar loses its archive action | `screenSelection` |
| 16 | row divider removed | 16 goldens |
| 17 | tag band loses its closing rule | `tagFilterBand` + all three screens |
| 18 | list re-inset by `screenEdge` (back to cards) | `screenList` + `screenSelection` |
| 19 | **error tail silenced** | **nothing — see below** |
| 20 | selected row stops lifting | `rowSelected` + `screenSelection` |
| 21 | check glyph → the lighter picker check | `rowSelected` + `screenSelection` |
| 22 | blocked-archive dialog drops its next-step line | `blockedArchiveDialogContent` |
| 23 | golden suite `@Disabled` wholesale | `assertGoldenLiveness`: "0 executed but 30 committed" |

**#19 was a real hole, on both screens.** Deleting `is LoadState.Error ->` from the screen's tail
block left all 30 goldens byte-identical. The footers are photographed; *when* they appear was not,
and no whole-screen golden can reach an append-error state. Closed the same way the clearance was —
`pagingTailKind(LoadState): PagingTailKind` as a pure function, `pagingTail` as dispatch, asserted in
`PagingTailKindTest` including the **absence** case. Re-proven in four directions afterwards (error
silenced, loading silenced, exhausted growing a footer, and the sibling's error case), all red.

Two findings came out of the same run and are recorded in §27: the incomplete mitigation the previous
entry had already prescribed for this class, and `screenEmpty` photographing a blank screen rather
than the empty state.

### 5.1a Residual holes, named rather than closed

Two things the mutation run showed are **not** gated, recorded here because a gate list that omits
its own gaps reads as coverage:

- **Which action the empty state's CTA dispatches.** Routing it back through `OnFabClick` — the
  divergence an adversarial review found, where this screen's CTA buzzed and the sibling's did not —
  leaves every test green. The handler side is gated on both screens (`OnEmptyCreate` opens create
  and fires nothing); the screen-side wiring is not. A Compose UI test would see it, but
  `ui_tests.yml` is `workflow_dispatch`-only and does not gate PRs.
- **`rowLongName` catches nothing alone.** Every mutation it reddens also reddens `rowWeighted`,
  which renders the same composable in the same state. It is kept because "no mutation catches it
  alone" is not provable in general and the four images are cheap — but it is not carrying a
  distinct property, and its KDoc no longer claims it proves the name's ellipsis (it does not; the
  name fits on one line and only the meta truncates).

### 5.2 The clean run

`./gradlew clean`, then `detekt --rerun-tasks --no-build-cache` on its own invocation
(**36 actionable tasks: 36 executed**, zero issues, zero suppressions), `lintDebug` likewise
(**1079 / 1079 executed**, 0 errors), then assemble + unit tests + all three visual gates
(**1487 actionable tasks: 1487 executed** — no `from cache`, no `up-to-date`):

```
Visual gate live: 30 golden test case(s) executed for 30 golden image(s).   feature/all-exercises
Visual gate live: 30 golden test case(s) executed for 30 golden image(s).   feature/all-trainings
Visual gate live: 62 golden test case(s) executed for 62 golden image(s).   core/ui/kit
```

`lintDebug` found **two errors belonging to the sibling branch**, both `lintDebug`-only (the
pre-commit hook runs detekt and skips lint, so neither could surface locally there): a duplicate
Russian string pair introduced with the empty state's CTAs, and three strings that screen's own
rebuild orphaned. They were fixed on this branch first and then **moved down to
`feature/v3-all-trainings`**, which is where the defect lives; that branch was verified green
standalone afterwards and this one rebased onto it. The AGENTS.md rule about why neither was ever
visible locally landed with them.

**Commands.** `./gradlew clean`, then the gates as separate invocations with `--rerun-tasks
--no-build-cache`; detekt on its own invocation, zero suppressions; the summary must read
`N actionable tasks: N executed`. Any `from cache` or `up-to-date` voids the result.

**Mockup Appearance Gate.** This branch changes no mockup and no `AppColors.kt`, so the gate should
pass unchanged. If it goes red it is a real finding about this change.

---

## 6. The region the contract cannot answer — **STOP**

**Filtered-to-empty is undrawn, was ruled as needing drawing, was scheduled, was cut, and has now
recurred with a second consumer.** That is the full chain, and every link is on paper already:

1. *all-trainings §6*, **D7**: "**RULED — both need drawing.** Filtered-to-empty and
   selection-mode-empty are reachable and neither is drawn" → PR 2.
2. *all-trainings §8*, the PR 2 table, rows 3 and 4: "**Filtered-to-empty**, in `#s-empty`" and
   "**Selection-mode empty**, in `#s-empty`".
3. PR 2 as executed shipped rows 1 and 2 only — the archive mark and `TagFilterRow`. `#s-empty` still
   draws exactly three `.empty` blocks: trainings, chart, exercises. Verified in the current
   drawing, not assumed.
4. `AllTrainingsScreen` therefore shipped with `items.isEmptyAndIdle() && !state.isSelecting` →
   the *first-run* empty. Filter to nothing and it says «Здесь появятся тренировки» with a
   "Create training" button, under a filter band the user just used. The copy is wrong, and the CTA
   is wrong: the next action is *clear the filter*, not *create*.
5. This screen has the same structure and the filter is wired into paging, so it is the same wrong
   answer, one tap away, on a second screen.

**It is not being deferred a third time, and it is not being invented.** Inventing a treatment for an
undrawn region is derivation — the exact failure this arc's contract-first order exists to prevent,
and §0.1 gives the drawing the decision. So the screen is built with the sibling's behaviour
unchanged — no regression, no divergence between two screens that must stay in step — and the region
is handed back with the chain above.

**What is needed:** two `.empty` blocks in `#s-empty` (filtered-to-empty; selection-mode-empty), a
§26 row, and a mapping row. Small — PR 2 sized it at two items and shipped two.

**The twin, B22, rides with it.** First-load-blank is the same class: undrawn, reachable, and now
confirmed on both screens. It differs only in that no filter is required to reach it. Whatever
`#s-empty` gains for the filtered case should answer the loading case in the same pass, or B22 stays
open against a screen that draws nothing at all on every cold start.

---

## 7. Scope

**In:** `feature/all-exercises` — the row, the list, the tag band, the top bars, the FAB, the paging
tails, the empty state's strings and CTA, the haptic corrections, the clearance, and the module's
golden suite from zero. `AppIcons.Exercises`. The `AppBlockedArchiveDialog` content split.

**Touched on the sibling module, deliberately and minimally:** two `lintDebug` errors that blocked
this branch's own verification (5.2), the suite-KDoc claim `screenEmpty` could not support (§27), and
the paging-tail gate hole, which was additive, moved no pixel and needed no re-record. Nothing that
changes `TrainingRow`'s shape, and nothing that forces a golden to be re-recorded — a rebase over the
device pass stays cheap.

**Out:** `TrainingRow` and anything that changes its shape — the sibling screen is under a concurrent
device pass and this branch must rebase cheaply. A shared row component. `AppTagChip`'s own treatment
(B20). `feature/archive`. B11's weightless cluster —
if a weightless render is wrong it is goldened and reported, never fixed here. B21's missing
`onError`. And the undrawn empty states, which are §6's.
