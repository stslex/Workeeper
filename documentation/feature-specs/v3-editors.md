# v3 — The editors arc

Governing documents this one does **not** restate: `v3-redesign-spec.md` (foundation, palette,
type, gates), `screen-extraction.md` Part 3 (exercise detail), Part 7 (the editors). Where this
document and Part 7 disagree, **this document wins and says so in the row** — Part 7 was written
before the mockup pass and the pass overturned four of its rulings.

Drawing of record: `pass2e-editors-v9.html` (six screens, six switches, both themes). **Not in this
repository** — same carve-out as `v3-topbar-candidates.html`. Every claim below that says "as drawn"
means that file, and the file carries its own reasoning in `.navnote` blocks.

---

## 0. What this arc is

The plan of an exercise is edited **where it is drawn**. Today it is edited on a separate route
(`Screen.PlanEditor`), reached from a summary line with a button, on two different screens. That
route, its return-value bridge and its partial reload all die for the two editors.

Everything else in this arc follows from that one move plus the mockup pass's rulings on rhythm,
tags and the read screens.

---

## 1. Locked decisions

Numbered `ED*` so they can be cited — **not** bare `E*`. `screen-extraction.md` already owns an
`E1`–`E9` series and cites it bare ("E7's missing rung", §7.2), so a bare `E7` would name two
different things in one corpus. Nothing enforces an `E` namespace the way `v3-redesign-spec.md`
§25 enforces `B`, so the amending document is the one that moves. Ilya ruled ED1–ED13 in the
mockup pass and ED14 with the §5 rulings.

| # | Decision | Overturns |
|---|---|---|
| **ED1** | The plan is edited **inline**, in the form, on both editors. No route, no summary line, no "edit plan" button. | — |
| **ED2** | The **exercise read screen shows the plan as the set-row card** — ordinal, weight and reps in separate `.field` boxes with units, set-type chip. Not `.planline`, not a dot-separated summary, no `×` compression. | extraction §3.4 (`.plancard` + `.planline`) |
| **ED3** | **Form rhythm**: a labelled field only where text is typed (`.flabel` + `.tf`); everything else is a **section** with `.section-head`. Order — name, then the screen's main slot (sets / exercises), then tags, then description. | extraction §7.1 frame order |
| **ED4** | **No placeholders that repeat the label.** An empty name field is empty. | build (`placeholder = label`) |
| **ED5** | **Type toggle is monochrome**, in `.tabs` grammar: track `surfaceTier1`, selected on `surfaceTier2` + `slabtop`, text `textPrimary` / `textSecondary`. The accent trio leaves this call site. | build (`accent`, `accentTintedBackground`, `accentTintedForeground`) |
| **ED6** | **No thumbnail in the pushed top bar**, on either read or edit. | extraction §7.7, **and #213 which shipped it on the editor** (never on read — see D-OPEN-3) |
| **ED7** | **Tags are a sheet.** The form shows selected chips with `✕` plus a dashed `+ тег` chip; the chip opens `AppBottomSheet` carrying search, the dictionary as selectable chips, and `+ Создать «X»` when there is no exact match. | build (`TagPickerInline`: two `internal` copies, field + two `FlowRow`s inline) |
| **ED8** | **Long explanations move under an `i` button.** A section head carries a short label; the reason lives in a sheet. The referent is the session's exercise description (`.mini.info` → `#sh-desc`). | this arc's own first draft (a two-label head reading as one long title) |
| **ED9** | **Training read screen: exercises are cards** (`#s-past` collapsed card — ordinal, name, `.plan-line`, chevron), **history stays a ruled list**. Different object, different form. | nothing — the screen was never drawn |
| **ED10** | **`Изменить` moves from the `⋮` menu to the dock** on the training read screen, matching `#s-ex`. | build (menu item) |
| **ED11** | **Undo everywhere; confirmation only where the act is irreversible.** A set, and an exercise removed from a training: **snackbar with undo, no confirmation** (D-OPEN-11). **Permanent deletion of the entity** (`⋮`): confirmation sheet, then the same snackbar. **Mechanism, for every undoable delete: deferred.** Nothing is deleted while the snackbar lives. The order is strict and it *is* the rule — timer expires → snackbar dismissed → only then the delete commits. Never delete first and undo by re-inserting (D-OPEN-2). **A deferred delete that loses its process is not committed** — the row survives (D-OPEN-10). | build (no undo anywhere); and this row's own earlier "confirmation, then a snackbar" on every exercise delete |
| **ED12** | **Type of the exercise is declared on the plan section head** (`С ВЕСОМ` / `БЕЗ ВЕСА`) on the read screen, not as a tag chip. Tags render as one `.meta` line. | extraction §3.2 (type as first `.tag`) |
| **ED13** | **Creation starts from an empty plan** — no seeded sets. `− подход` is `:disabled` while the draft is empty. | this arc's own earlier drawing |
| **ED14** | **Training-editor cards are collapsed by default.** Collapsed is the drawn form — ordinal, type glyph, name, `.plan-line` summary (`#s-past`, as ED9) — plus the head's drag handle and `✕`. Entering the editor you see the whole list; you expand the one you mean. | **this document's own §3.4** ("All cards open") — reversed by D-OPEN-7 |

---

## 2. What dies

Verified by grep against `dev @ 5b3c1cb2`. Each symbol is named so CC deletes rather than orphans.

**`feature/exercise`**
- `Action.Click.OnEditPlanClick`, `ClickHandler.processEditPlanClick()`
- `Action.Navigation.OpenPlanEditorExisting`, its `NavigationHandler` branch
- `Action.Common.PlanEditorExistingReturned`, `CommonHandler.processPlanEditorExistingReturned()`, `PartialReload`
- `ExerciseGraph`'s `savedStateHandle.getStateFlow(Screen.PlanEditor.planEditorSavedAttr)` bridge
- `TypeChipReadOnly` + `feature_exercise_edit_type_chip_hint`
- `DefaultPlanSection`'s summary branch — **two identically-named private composables exist**. The
  one that dies is `ExerciseEditScreen.kt:201-255`, which branches on `isCreate` and renders the
  summary label in its else-branch. `ExerciseDetailScreen.kt:224` has **no branch** and renders
  `PlanCard` unconditionally — that one is PR-2's to rebuild, not PR-3's to delete.
- `adhocPlanSummaryLabel` — **measured, not assumed**: its only read is `ExerciseEditScreen.kt:236`,
  inside the branch above. The read screen never references it, so it dies with the branch.
- `ExerciseTopBarThumb` and its **one** call site, `ExerciseEditScreen.kt:92` (ED6). The read screen
  never had a thumb: its trailing slot is the `⋮` `AppIconButton` and its image affordance is
  `ExerciseHero` in the scrolling body, so ED6's read half is already true at HEAD.
- `ExerciseHero`'s **call site in the read body** and the `state.effectiveImageDisplay !is
  ImageDisplay.None` gate around it — `ExerciseDetailScreen.kt:134-142`, the **only** production
  call site; the image moves beside the description (D-OPEN-9). Perimeter counted by kind: 1
  production call site, 2 previews and 3 `testTag` literals, all three kinds inside
  `ExerciseHero.kt` itself, and **no test asserts the tags**. Whether the component file survives
  as the placeholder-icon source — `exercise-image.md` reuses it for the thumb — is PR-2's
  mechanical call, not a decision.

**`feature/single-training`**
- `Action.Click.OnEditPlanClick(exerciseUuid)`, `ClickHandler.processEditPlanClick`
- `Action.Navigation.OpenPlanEditor`, its `NavigationHandler` branch
- `SingleTrainingGraph`'s `planEditorSavedAttr` bridge and the `Action.Common.Reload` it fires —
  `SingleTrainingGraph.kt:45` is the **only** production dispatch site of `Reload` in the feature,
  so deleting the bridge leaves `CommonHandler`'s `Reload` branch unreachable and the action goes
  with it. PR-4's "check for other callers" is answered: none.
- `TrainingExerciseEditRow`'s plan summary row and its `AppButton.Tertiary`

**Kit / shared**
- one of the two `internal` `TagPickerInline` copies (both are `internal`, not `private`); the
  survivor moves to the kit (ED7)

**`feature/plan-editor` — DOES NOT DIE, and this is the arc's largest scoping fact.**
`Screen.PlanEditor.Existing` has a **third consumer: the live session**
(`feature/live-workout/.../NavigationHandler.kt:26`, `LiveWorkoutGraph.kt:29`). The session's own
inline editing is not in this arc. So the module, the route and the `planEditorSavedAttr` contract
**survive with exactly one caller**, and deleting them is a later arc's work.
Recorded as blocker **B-E1** so nobody discovers it mid-PR.

---

## 3. Per-screen contract

Appearance is the drawing; this section names structure and the states the drawing does not carry.

### 3.1 Exercise — read (`ExerciseDetailScreen`)

```
topbar    ‹ · h1.sm name · ⋮
meta      tags, one line, mono                       (type is NOT here — ED12)
prhero    record                                     (absent when personalRecord == null)
head      ПЛАН ПО УМОЛЧАНИЮ            С ВЕСОМ
card      set rows, read-only                        (ED2)
head      ОПИСАНИЕ
          description, image beside it               (D-OPEN-9, pairing from D-OPEN-3)
head      ИСТОРИЯ                    4 СЕССИИ
list      history rows, PR row carries .prtag not .chev
dock      Изменить (128dp) · Записать сейчас
```

The set row is **the same component** `PlanEditorBody` draws. Extract it to
`core/ui/plan-editor` as a read-only host rather than copying it — a copy is the drift this
arc exists to remove.

**The drawn read frame omits both blocks, and the omission is a gap in the drawing, not a ruling.**
The drawing shows no image and no description on read at all, while the build ships an image there
today and D-OPEN-3 ruled the image must be available on read. A PR-2 built strictly to the drawing
would therefore **delete the image from read**, against that ruling. The build says as much in its
own hand: the hero's call site carries the comment *"hero only when a custom image is present (in
code, not drawn in the mockup — kept as shipped)"*.

**D-OPEN-9 ruled: read gains the description block, with the image beside it** — the same pairing
as the editor (§3.2). `ExerciseHero`'s role is **replaced, not kept**; §2 records its one call site
and the full perimeter. Placement is after the plan and before `ИСТОРИЯ`: it mirrors ED3's order
(description after the screen's main slot) and leaves history as the trailing log, which moves the
image down from the top of the body where HEAD draws it.

### 3.2 Exercise — create / edit (`ExerciseEditScreen`)

```
topbar    ‹ · h1.sm (name, or «Новое упражнение» dim)     no thumb (ED6)
fgrp      Название            .tf
head      ПЛАН ПО УМОЛЧАНИЮ        (i) → sheet          (ED8)
          type toggle, monochrome                        (ED5)
          plan card + .setbar                            (ED1, ED13)
head      ТЕГИ                     2 из 10               counter only where a limit exists
          selected chips ✕ · + тег → sheet               (ED7)
head      ОПИСАНИЕ
          .tf.multi
          image entry point, beside the description      (D-OPEN-3)
dock      Отмена · Сохранить                             Save always enabled (§7.3)
```

`2 из 10` renders **only** on the exercise: `MAX_TAGS_PER_EXERCISE = 10` lives in
`feature/exercise`'s `ClickHandler`; `feature/single-training` has no limit. Showing it there
would be a lie.

The image entry point is **beside the description** — not in the top bar (ED6 stands, the thumb
is deleted), not among the plan and not among the tags. The placement is the statement: the image
is optional and descriptive, so it sits with the other optional descriptive thing.

### 3.3 Training — read (`TrainingDetailScreen`)

```
topbar  ‹ · h1.sm name · ⋮
meta    tags, one line
head    УПРАЖНЕНИЯ                3
cards   collapsed cards: .ord + title + .plan-line + .chev        (ED9)
head    ИСТОРИЯ                   2 СЕССИИ
list    ruled rows
dock    Изменить (128dp) · Начать сессию                          (ED10)
```

### 3.4 Training — edit (`TrainingEditScreen`)

Each exercise is a card, **collapsed by default** (ED14). Collapsed head = ordinal, type glyph,
name, `.plan-line` summary, drag handle, `✕` — the drawn `#s-past` form, plus the two controls the
editor adds. Expanded body = the plan card's rows + `.setbar`. **No type toggle inside** — type
belongs to the exercise, not to a training-scoped editor, which is exactly what `PlanEditorBody`'s
`onTypeChange = null` already encodes. `.addex` below the list.

Entering the editor you see the **whole list**; you expand the one you mean. All-open makes a long
training unscannable, which is why D-OPEN-7 reversed this section's earlier "All cards open".

**An exercise inserted from the picker opens; the rest stay collapsed (D-OPEN-8).** Collapse-by-
default governs *scanning* an existing list. An insert is an addressed gesture whose next step is
the plan — and the inserted card has no plan yet — so it opens where it lands. Inserting several at
once opens **the first only**.

---

## 4. Deletion and undo (ED11)

Infrastructure exists: `AppSnackbarModel(message, actionLabel, action)` and
`SnackbarManager.showSnackbar`. Nothing further is needed for the *set* case.

| Action | Confirmation | Undo | Note |
|---|---|---|---|
| `− подход` | **none** | snackbar, `Отменить` | session already does this (`session-v3f` L431) |
| `✕` — remove from this training | **none** (D-OPEN-11) | snackbar, `Отменить` | removes it from **that training only** (D-OPEN-2); the entity is untouched |
| `⋮` — delete permanently | **sheet** (`#sh-del`, form 3) | snackbar, `Отменить` | the only irreversible act on the screen, and the only one that confirms |

**"Dialog" is read as a sheet — ruled (D-OPEN-1).** §7.4 leaves no dialog primitive in this
language and none is added. The one confirmation above is an `AppBottomSheet`.

**No confirmation on `✕` — ruled (D-OPEN-11).** The editor commits nothing until Save, so the
removal is already protected three ways: undo in the snackbar, `Отмена` on the dock, and the fact
that the draft is unsaved until Save. A fourth sheet would fire once per exercise while you edit a
list of them. The confirmation stays reserved for the irreversible case — permanent delete from the
`⋮` menu — which narrows D-OPEN-2's earlier "confirmation sheet + undo snackbar" on this row.

**Which "delete an exercise" — ruled (D-OPEN-2).** The `✕` in the card head removes the exercise
from **that training only**; it does not touch the entity. Permanent deletion of the entity stays
on the `⋮` menu. The two read the same in ED11's rule and cost differently, which is why they are
separate rows above.

**The mechanism, for every undoable delete: deferred — ruled (D-OPEN-2).** Nothing is deleted
while the snackbar lives. The order is strict and it *is* the rule:

```
timer expires → snackbar dismissed → only then the delete commits
```

Never delete first and undo by re-inserting. The rejected alternative was payload retention and
re-insert, which for an exercise with history is not one row — it is the exercise, its tag links,
its plan rows and its logged sets, all of which a deferred delete simply never removes. The
accepted cost: "deleted" is a UI state the DB does not share while the snackbar lives.

**A deferred delete that loses its process is not committed — ruled (D-OPEN-10).** The row
survives. Committing at next launch would be a deletion the user never saw complete and never
confirmed; the opposite error — the item is still there — is **visible and repeatable**, and the
user simply deletes it again. It also spares the alternative's cost: a persisted queue of pending
operations, replayed at startup, for a five-second window.

---

## 5. Decisions

**No decision remains open. Every PR, PR-1 through PR-8, is startable.**

D-OPEN-1..11 are all ruled: 1..7 in the first pass, and 8..11 — the decisions those rulings
themselves created — in the second. The rows stay, each with the question it asked and the ruling
that closed it, because later PRs cite the ids. The `Blocks` column records which PR each one was
holding.

| # | Status | Decision, and the ruling | Blocks |
|---|---|---|---|
| **D-OPEN-1** | **RULED** | dialog vs sheet for delete confirmation. → **Sheet.** §7.4 stands; **no dialog primitive is added** to this language. | PR-7 — unblocked |
| **D-OPEN-2** | **RULED**, both halves | which "delete an exercise", and deferred-delete (a) vs retain-and-re-insert (b). → **Scope:** removing an exercise from a training removes it **from that training only**; confirmation sheet + undo snackbar — **since narrowed by D-OPEN-11 to undo snackbar alone, no confirmation.** Cite both rows, never this one alone. → **Mechanism, for every undoable delete: deferred (a).** Nothing is deleted while the snackbar lives; the order is strict and it *is* the rule — timer expires → snackbar dismissed → only then the delete commits. **Never delete first and undo by re-inserting.** ED11 carries this as its mechanism sentence. | PR-7 — unblocked |
| **D-OPEN-3** | **RULED** | **where the image entry point lives now that ED6 removed the thumb.** #213 shipped the thumb on the **editor only** — `ExerciseTopBarThumb` has exactly one call site, `ExerciseEditScreen.kt:92`; the read screen's trailing slot is the `⋮` `AppIconButton` and its image affordance is `ExerciseHero` in the scrolling body, so ED6's read half is already true at HEAD. The photo, the viewer and the source picker all still exist. → **The image is available on both read and edit, and its entry point sits BESIDE THE DESCRIPTION** — not in the top bar, not among the plan, not among the tags. Its placement is what states that it is optional and descriptive. **The thumb deletion (ED6) stands.** | PR-3 — unblocked |
| **D-OPEN-4** | **RULED** | orphan tags. The symbol with **zero callers anywhere** is `TagRepository.delete` (`TagRepository.kt:16`); `TagDao.delete(uuid)` has exactly one production caller, `TagRepositoryImpl.delete` (`TagRepositoryImpl.kt:53`), which nothing calls. Nothing in the app ever deletes a tag, and `Создать` writes the dictionary immediately, before the exercise is saved. → **Auto-prune.** A tag with no remaining links is deleted from the dictionary; `TagRepository.delete` gains its first caller. A tag editor screen showing each tag's links is a **future item, not this arc** — recorded as **B-E5**. | PR-6 — unblocked |
| **D-OPEN-5** | **RULED** | dashed `--hair-s` as a control outline (`+ тег`, `.addex`) measures **1.52 dark / 1.35 light** against 3.0. → **Keep the dashed `--hair-s` outline.** The **label** identifies the control; the dash is decoration and owes no contrast threshold. Same answer for `+ тег` and `.addex`, as the row required. The measurement and this reasoning are recorded here so the pair is not re-litigated. | PR-6 — unblocked |
| **D-OPEN-6** | **RULED** | read card and edit card are now visually near-identical. Intended, or does read drop the chip / sit on `surfaceTier1`? → **Identical.** No chip removal, no tier change. You read the plan in the shape you will perform it. | PR-2 — unblocked |
| **D-OPEN-7** | **RULED**, and it **reverses §3.4** | collapsing cards in the training editor. → **Collapsed by default.** Entering the editor you see the whole list; you expand the one you mean — all-open makes a long training unscannable. The collapsed form is the drawn one: ordinal, type glyph, name, `.plan-line` summary, plus the head's drag handle and `✕`. §3.4's "All cards open" is struck; citable as **ED14**. | PR-4 — unblocked |
| **D-OPEN-8** | **RULED** | a newly added exercise has **no plan**. Does it open on insert, or stay collapsed like the rest (ED14)? → **It opens; the rest stay collapsed.** Collapse-by-default governs **scanning** an existing list; an **insert is an addressed gesture whose next step is the plan**. Inserting several at once opens **the first only**. | PR-4 — unblocked |
| **D-OPEN-9** | **RULED** | the read screen draws **no description block** today. If the image sits beside the description (D-OPEN-3), read either **gains a description section** or **keeps the image as `ExerciseHero`**. The gap this sits on: the drawing's read frame shows **no image and no description at all**, while `ExerciseHero` ships an image today and D-OPEN-3 ruled the image must be available on read — so a PR-2 built strictly to the drawing would **delete the image from read, against that ruling**. The omission is a gap in the drawing, not a ruling. → **Read gains a description block, with the image beside it** — the same pairing as the editor. **`ExerciseHero`'s role is replaced, not kept.** Otherwise "beside the description" would hold on one screen of two, and a description that can be typed and never read is a field with no reader. | PR-2 — unblocked |
| **D-OPEN-10** | **RULED** | what happens to a **pending deferred delete when the screen or the process dies inside the window** — commit, or drop? Leaving it undefined makes it a bug, not a default. → **Not committed. The row survives.** Committing at next launch would be a deletion the user never saw complete and never confirmed; the opposite error — the item is still there — is visible and repeatable. It also spares a persisted queue of pending operations. | PR-7 — unblocked |
| **D-OPEN-11** | **RULED**, and it **narrows D-OPEN-2** | with a confirmation sheet on `✕`, and the editor committing nothing until Save, the removal is protected **three times** (confirm, undo, Cancel). Is the confirmation kept? → **No confirmation sheet on `✕`. Snackbar with undo only.** The action is already protected three ways — undo, `Отмена`, and the unsaved draft — and the sheet would fire once per exercise in the list. **The confirmation sheet stays reserved for the irreversible case:** permanent delete from the `⋮` menu. §4's table carries the three true confirmations; ED11 is reworded to match. | PR-4 — unblocked |

---

## 6. PR ladder

Each PR is independently bisect-green and carries its own goldens. Dependencies are stated; where
none is stated the PR is free-standing.

| PR | Content | Depends on |
|---|---|---|
| **PR-1** | `TypeToggle` → monochrome `.tabs` grammar (ED5). `AppSegmentedControl` **is** the text variant (`items: ImmutableList<String>` → `Text`), with `AppSegmentedIconControl` as its sibling in the same file. #191 left the text form untouched — 6dp, no selection semantics — so *collapse onto it* means bringing it up to the `.tabs` grammar, not choosing between two controls. | — |
| **PR-2** | Read-only set-row card extracted to `core/ui/plan-editor`; `ExerciseDetailScreen`'s `DefaultPlanSection` / `PlanCard` / `PlanLine` / `PlanValue` rebuilt onto it. Type onto the section head, tags to one `.meta` line (ED2, ED12). Read card and edit card are identical (D-OPEN-6). **Read gains the `ОПИСАНИЕ` block with the image beside it, replacing `ExerciseHero`'s role** (D-OPEN-9) — §3.1 records that the drawing omits both blocks and that building strictly to it would delete the image from read. | — |
| **PR-3** | Exercise editor: inline plan, section rhythm, placeholders out, `TypeChipReadOnly` out, thumb out, `i` sheet in, image entry point beside the description (ED1, ED3, ED4, ED6, ED8, ED13, D-OPEN-3). Deletes the exercise-side route symbols from §2, whose three measurements (the two `DefaultPlanSection`s, `adhocPlanSummaryLabel`'s single read, the thumb's single call site) are settled — do not re-derive them. | PR-1, PR-2 |
| **PR-4** | Training editor: row → card, collapsed by default with the plan body inside on expand (ED14); an exercise inserted from the picker opens, and a multi-insert opens the first only (D-OPEN-8). `✕` removes from this training with an undo snackbar and **no confirmation** (D-OPEN-11). Route symbols deleted (ED1), `Action.Common.Reload` among them — §2 records it has no other dispatch site. | PR-3 |
| **PR-5** | Training read screen: cards vs list, `Изменить` to the dock (ED9, ED10). | — |
| **PR-6** | Tags: one kit component, the sheet, the `+ тег` chip (dashed `--hair-s` kept, D-OPEN-5), the counter where a limit exists (ED7). Auto-prune on the last link (D-OPEN-4) — `TagRepository.delete`'s first caller, closing B-E2. | — |
| **PR-7** | Deletion and undo (ED11): the one sheet confirmation on permanent delete (D-OPEN-1, D-OPEN-11), removal scoped to the training (D-OPEN-2), deferred delete throughout, and **nothing committed when the process dies inside the window** (D-OPEN-10). | — |
| **PR-8** | States and clamps: no record, empty history, weightless read, empty plan on read; single-line ellipsis on `.prhero`'s meta line. The pushed-bar title is **not** in scope — it already clamps (B-E4). | PR-2, PR-5 |

`feature/plan-editor` survives all eight (B-E1).

---

## 7. Gates

Per commit, unchanged from the arc: compile · detekt with **zero suppressions** · unit tests ·
Paparazzi goldens in both themes · bisect-green · cyclic-proof.

Carried verification discipline, restated because every one of these has already produced a false
green in this project:

- `--rerun-tasks --no-build-cache`. `FROM-CACHE` is not "executed".
- `--stop` before measuring anything built in the same invocation.
- detekt and tests as **separate** invocations — concurrent runs raced the filesystem.
- every gate proven in **both** directions: it fires on the violation and is silent on the clean tree.
- a golden locks in what **is**, not what **should be**. Look at each new golden; recording one and
  reading one are different acts (§7.10a caught `+ + Добавить` exactly this way).

Per §13, **every screen PR attaches an element-by-element comparison against the drawing** before
review.

New goldens this arc: type toggle (2 states × 2 themes), read-only set card (weighted / weightless
/ empty × 2), editor plan card empty state, tag row (selected / empty × 2), training exercise card
(collapsed with plan / collapsed with no plan / expanded × 2, ED14), read description block
(with image / without × 2, D-OPEN-9).

---

## 8. Registry additions

- **B-E1** — `Screen.PlanEditor` survives with one caller (live session). The route, module and
  `planEditorSavedAttr` contract cannot be deleted until the session edits its plan inline. The
  later deletion arc inherits more than the three consumer features: the route is also referenced
  by `AppNavigationHost.kt:141` (`.reportScreenPlace<Screen.PlanEditor>()`, production) and by two
  test files outside those features — `app/app/.../PlanEditorExtensionIdentityTest.kt` and
  `core/ui/mvi/.../SavedStateHandleNavigationResultTest.kt` — on top of the feature-side
  `NavigationHandlerTest`s that go with their own features.
- **B-E2** — nothing in the app ever deletes a tag; the dictionary only grows. The symbol with zero
  callers anywhere is `TagRepository.delete` (`TagRepository.kt:16`); `TagDao.delete(uuid)` has one
  production caller, `TagRepositoryImpl.delete` (`TagRepositoryImpl.kt:53`), which nothing calls.
  **Closed by PR-6**: D-OPEN-4 ruled auto-prune, which gives `TagRepository.delete` its first
  caller.
- **B-E3** — `AppTagPicker` and `AppDatePickerDialog` ship with zero production consumers
  (carried from extraction §7.11, unchanged by this arc).
- **B-E4** — no clamp is declared for `.prhero`'s meta line; a long training name grows it. The
  pushed-bar title is **not** part of this blocker: `AppTopBar` declares `maxLines = 1` +
  `TextOverflow.Ellipsis` (`AppTopBar.kt:89-90`) and has since the file was introduced.
- **B-E5** — **future item, not this arc.** A tag editor screen showing each tag and its links.
  Opened by D-OPEN-4's ruling: auto-prune deletes a tag the moment its last link goes, which is
  the cheap answer; the screen is the one that lets you see and manage what the dictionary holds.
  Nothing in PR-6 depends on it.
