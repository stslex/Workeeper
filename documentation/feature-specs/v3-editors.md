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
§25 enforces `B`, so the amending document is the one that moves. Ilya ruled every one of these
in the mockup pass.

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
| **ED11** | **Deleting an exercise**: confirmation, then a snackbar with undo. **Deleting a set**: snackbar with undo only, no confirmation. | build (no undo anywhere) |
| **ED12** | **Type of the exercise is declared on the plan section head** (`С ВЕСОМ` / `БЕЗ ВЕСА`) on the read screen, not as a tag chip. Tags render as one `.meta` line. | extraction §3.2 (type as first `.tag`) |
| **ED13** | **Creation starts from an empty plan** — no seeded sets. `− подход` is `:disabled` while the draft is empty. | this arc's own earlier drawing |

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
head      ИСТОРИЯ                    4 СЕССИИ
list      history rows, PR row carries .prtag not .chev
dock      Изменить (128dp) · Записать сейчас
```

The set row is **the same component** `PlanEditorBody` draws. Extract it to
`core/ui/plan-editor` as a read-only host rather than copying it — a copy is the drift this
arc exists to remove.

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
dock      Отмена · Сохранить                             Save always enabled (§7.3)
```

`2 из 10` renders **only** on the exercise: `MAX_TAGS_PER_EXERCISE = 10` lives in
`feature/exercise`'s `ClickHandler`; `feature/single-training` has no limit. Showing it there
would be a lie.

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

Each exercise is a `.card.open`: head = ordinal, type glyph, name, drag handle, `✕`;
body = the plan card's rows + `.setbar`. **No type toggle inside** — type belongs to the exercise,
not to a training-scoped editor, which is exactly what `PlanEditorBody`'s
`onTypeChange = null` already encodes. `.addex` below the list. All cards open; collapsing in the
editor is **not ruled** (the referent exists — `#s-past` collapsed with `.plan-line`).

---

## 4. Deletion and undo (ED11)

Infrastructure exists: `AppSnackbarModel(message, actionLabel, action)` and
`SnackbarManager.showSnackbar`. Nothing further is needed for the *set* case.

| Action | Confirmation | Undo | Note |
|---|---|---|---|
| `− подход` | none | snackbar, `Отменить` | session already does this (`session-v3f` L431) |
| exercise, permanent delete | **sheet** (`#sh-del`, form 3) | snackbar, `Отменить` | see cost below |

**"Dialog" is read as a sheet.** §7.4 leaves no dialog primitive in this language; if a real
dialog was meant, that reverses §7.4 and must be taken explicitly. **D-OPEN-1.**

**Which "delete an exercise" — D-OPEN-2.** Removing an exercise from a training (the `✕` in the
card head) and deleting the entity permanently (`⋮` menu) read the same in the rule and cost
differently.

**The cost, stated before it is discovered.** Undo after a *permanent* delete is not a snackbar
over a completed delete. It is either (a) deferred delete — the rows stay while the snackbar
lives, or (b) payload retention and re-insert. For an exercise with history this is not one row:
it is the exercise, its tag links, its plan rows and its logged sets. Pick (a) or (b) **before**
the PR, and note that (a) makes "deleted" a UI state the DB does not share for five seconds.

---

## 5. Open decisions — Ilya's, before the PRs they block

| # | Decision | Blocks |
|---|---|---|
| **D-OPEN-1** | dialog vs sheet for delete confirmation | PR-8 |
| **D-OPEN-2** | which "delete an exercise", and (a) or (b) above | PR-8 |
| **D-OPEN-3** | **where the image entry point lives now that ED6 removed the thumb.** #213 shipped the thumb on the **editor only** — `ExerciseTopBarThumb` has exactly one call site, `ExerciseEditScreen.kt:92`; the read screen's trailing slot is the `⋮` `AppIconButton` and its image affordance is `ExerciseHero` in the scrolling body, so ED6's read half is already true at HEAD. The decision is about the editor alone and the deletion is one call site. The photo, the viewer and the source picker all still exist. A form row (what §26 removed) or something else — undecided. | PR-3 |
| **D-OPEN-4** | orphan tags. The symbol with **zero callers anywhere** is `TagRepository.delete` (`TagRepository.kt:16`); `TagDao.delete(uuid)` has exactly one production caller, `TagRepositoryImpl.delete` (`TagRepositoryImpl.kt:53`), which nothing calls. Nothing in the app ever deletes a tag, and `Создать` writes the dictionary immediately, before the exercise is saved. Options: auto-prune tags with no links / long-press in the tag sheet / a manager in settings. Recommendation on record: auto-prune. | PR-6 |
| **D-OPEN-5** | dashed `--hair-s` as a control outline (`+ тег`, `.addex`) measures **1.52 dark / 1.35 light** against 3.0. Either the label identifies the control and the dash is decoration, or both move to `borderDefault`. One answer for both. | PR-6 |
| **D-OPEN-6** | read card and edit card are now visually near-identical. Intended (you read it in the shape you will perform it), or does read drop the chip / sit on `surfaceTier1`? | PR-2 |
| **D-OPEN-7** | collapsing cards in the training editor | PR-4 |

---

## 6. PR ladder

Each PR is independently bisect-green and carries its own goldens. Dependencies are stated; where
none is stated the PR is free-standing.

| PR | Content | Depends on |
|---|---|---|
| **PR-1** | `TypeToggle` → monochrome `.tabs` grammar (ED5). `AppSegmentedControl` **is** the text variant (`items: ImmutableList<String>` → `Text`), with `AppSegmentedIconControl` as its sibling in the same file. #191 left the text form untouched — 6dp, no selection semantics — so *collapse onto it* means bringing it up to the `.tabs` grammar, not choosing between two controls. | — |
| **PR-2** | Read-only set-row card extracted to `core/ui/plan-editor`; `ExerciseDetailScreen`'s `DefaultPlanSection` / `PlanCard` / `PlanLine` / `PlanValue` rebuilt onto it. Type onto the section head, tags to one `.meta` line (ED2, ED12). | D-OPEN-6 |
| **PR-3** | Exercise editor: inline plan, section rhythm, placeholders out, `TypeChipReadOnly` out, thumb out, `i` sheet in (ED1, ED3, ED4, ED6, ED8, ED13). Deletes the exercise-side route symbols from §2, whose three measurements (the two `DefaultPlanSection`s, `adhocPlanSummaryLabel`'s single read, the thumb's single call site) are settled — do not re-derive them. | PR-1, PR-2, D-OPEN-3 |
| **PR-4** | Training editor: row → card with the plan body inside; route symbols deleted (ED1), `Action.Common.Reload` among them — §2 records it has no other dispatch site. | PR-3, D-OPEN-7 |
| **PR-5** | Training read screen: cards vs list, `Изменить` to the dock (ED9, ED10). | — |
| **PR-6** | Tags: one kit component, the sheet, the `+ тег` chip, the counter where a limit exists (ED7). | D-OPEN-4, D-OPEN-5 |
| **PR-7** | Deletion and undo (ED11). | D-OPEN-1, D-OPEN-2 |
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
(with plan / no plan × 2).

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
- **B-E3** — `AppTagPicker` and `AppDatePickerDialog` ship with zero production consumers
  (carried from extraction §7.11, unchanged by this arc).
- **B-E4** — no clamp is declared for `.prhero`'s meta line; a long training name grows it. The
  pushed-bar title is **not** part of this blocker: `AppTopBar` declares `maxLines = 1` +
  `TextOverflow.Ellipsis` (`AppTopBar.kt:89-90`) and has since the file was introduced.
