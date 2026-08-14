# Exercise feature — UI test scenarios

> Post-v1.42.0 architecture: PlanEditor owns type, Snapshot consolidates dirty tracking,
> DialogState replaces Show*Dialog events, create-mode opens PlanEditor in Draft mode.

## Test infrastructure prerequisites

These must exist before any scenario in this file can be implemented:

- `core/ui/test-utils` extended with `@HiltAndroidApp class TestApp` and a generic
  `TestActivity` that hosts arbitrary content via `setContent`.
- `core/ui/test-utils` exposes `TestInfraModule` via `@TestInstallIn` replacing:
  `ImageStorage` (in-memory fake), `Clock` (fixed), `SystemFeedback` (no-op),
  Camera ARC launcher (programmable fake), Gallery ARC launcher (programmable fake),
  Permission gate (programmable fake), Settings intent dispatcher (capturing fake).
- New module `core/data/database-test` exporting an in-memory `AppDatabase` builder
  and a `@TestInstallIn` module replacing the production `DatabaseModule` binding.
- All scenarios use `@Regression` annotation unless explicitly marked `@Smoke`.
- All scenarios use a fresh in-memory `AppDatabase` per `@Test` (created in `@Before`,
  closed in `@After`).
- Coroutine dispatchers stay real (no test-dispatcher injection).

## Scenario format

```
ID: <feature>-<group>-<NN>
Mode: create | edit | read | archive
Level: @Smoke | @Regression
User goal (en): <one technical sentence>
User goal (ru): <одно человеческое предложение, что юзер делает>
Preconditions:
  - <DB seed>
  - <screen state>
Stable testTags used:
  - "TestTag.Name"
Mocked dependencies:
  - <each fake explicitly listed; "none beyond TestInfraModule defaults" if so>
Steps:
  1. <action>
Expected outcomes:
  - <UI / DB / state assertion>
Invariants:
  - <what must NOT happen>
Coverage references:
  - <Action subtypes / DialogState variants / SaveOutcome / handler paths covered>
Bug history:
  - <PR# or issue or "none">
```

## Coverage matrix

| ID    | Group                | Mode    | Level       | What                                                                       |
|-------|----------------------|---------|-------------|----------------------------------------------------------------------------|
| F-01  | Form basics          | create  | @Smoke      | Открыл "+", вижу пустую форму                                              |
| F-02  | Form basics          | create  | @Regression | Имя → Save → упражнение в БД                                               |
| F-03  | Form basics          | create  | @Regression | Имя + описание → Save                                                      |
| F-04  | Form basics          | create  | @Regression | Save in flight: показывается loading, повторный тап no-op                  |
| F-05  | Form basics          | create  | @Regression | Имя с пробелами по краям → trim на Save                                    |
| F-06  | Form basics          | edit    | @Regression | Read → Edit → originalSnapshot захватывает текущее состояние               |
| F-07  | Form basics          | edit    | @Regression | Edit имени → Save → возврат в Read с новым именем                          |
| F-08  | Form basics          | edit    | @Regression | Edit описания → Save → персистится                                         |
| F-09  | Form basics          | edit    | @Regression | Edit без изменений → Save → no-op (но не падает)                           |
| V-01  | Validation           | create  | @Regression | Save с пустым именем → ошибка под полем, ничего в БД                       |
| V-02  | Validation           | edit    | @Regression | Стереть имя → Save → ошибка                                                |
| V-03  | Validation           | create  | @Regression | Имя совпадает с существующим → NameConflict диалог                         |
| V-04  | Validation           | edit    | @Regression | Изменил имя на существующее → NameConflict                                 |
| V-05  | Validation           | edit    | @Regression | Save с тем же именем (не изменено) → no conflict (same UUID)               |
| V-06  | Validation           | create  | @Regression | Image IO failure → ImageSaveFailed flow, state recovers                    |
| T-01  | Tags                 | create  | @Regression | Тыкаю существующий тег → выбран                                            |
| T-02  | Tags                 | create  | @Smoke      | Тыкаю выбранный тег ещё раз → снят                                         |
| T-03  | Tags                 | create  | @Regression | Создал новый тег inline → выбран → Save → tag в БД                         |
| T-04  | Tags                 | create  | @Smoke      | Пустое имя нового тега → "Создать" disabled                                |
| T-05  | Tags                 | create  | @Regression | Имя нового тега совпадает с существующим → выбираем существующий           |
| T-06  | Tags                 | edit    | @Regression | Добавил тег к существующему → Save → exercise_tag updated                  |
| T-07  | Tags                 | edit    | @Regression | Снял тег с существующего → Save → exercise_tag очищен                      |
| T-08  | Tags                 | edit    | @Regression | Edit без касания тегов → Save → теги сохраняются (regression)              |
| I-01  | Images               | create  | @Regression | Галерея → миниатюра → Save → image_path в БД                               |
| I-02  | Images               | create  | @Regression | Камера → миниатюра → Save                                                  |
| I-03  | Images               | create  | @Regression | Камера + permission denied → PermissionDenied диалог → Settings intent      |
| I-04  | Images               | create  | @Smoke      | ImageSourcePicker dismiss → диалог закрылся, картинки нет                  |
| I-05  | Images               | create  | @Regression | Pick → Remove до Save → pendingImage сброшен, ничего не committed          |
| I-06  | Images               | edit    | @Regression | Заменил картинку на новую → Save → старый файл удалён, новый image_path    |
| I-07  | Images               | edit    | @Regression | Снял картинку → Save → image_path=null, старый файл удалён                 |
| I-08  | Images               | edit    | @Regression | Снял → передумал до Save → существующая картинка восстановлена             |
| I-09  | Images               | edit    | @Regression | Сохранение картинки IO-fail → ImageSaveFailed, image_path не меняется      |
| P-01  | Plan & Type          | create  | @Regression | Add plan → Draft PlanEditor с пустым state                                 |
| P-02  | Plan & Type          | create  | @Regression | Draft: 3 сета → Done → Save → план в БД                                    |
| P-03  | Plan & Type          | create  | @Regression | Draft: type change WEIGHTED→WEIGHTLESS с весами → confirm → wipe          |
| P-04  | Plan & Type          | create  | @Smoke      | Draft: type change без сетов → silent toggle (no dialog)                   |
| P-05  | Plan & Type          | create  | @Smoke      | Draft: открыл, ничего не делал, Done → возврат с пустым результатом        |
| P-06  | Plan & Type          | create  | @Regression | Draft: system back → no result, parent state unchanged                     |
| P-07  | Plan & Type          | edit    | @Regression | Existing PlanEditor: edit set → Save → DB updated → partial reload         |
| P-08  | Plan & Type          | edit    | @Regression | Existing return: pending name в parent сохранилось (regression)            |
| P-09  | Plan & Type          | edit    | @Regression | Existing: type change с весами → confirm → wipe → partial reload type+plan |
| P-10  | Plan & Type          | edit    | @Regression | Type chip в parent отражает новый type после Existing return               |
| P-11  | Plan & Type          | edit    | @Regression | После Existing Save → originalSnapshot обновлён → no dirty (regression)    |
| P-12  | Plan & Type          | edit    | @Regression | Existing back без Save → DB не тронута, parent state без изменений         |
| P-13  | Plan & Type          | edit    | @Regression | Existing для упражнения без плана → empty state → add sets → Save          |
| D-01  | Dialogs & abandon    | create  | @Regression | Back с unsaved → DiscardConfirm                                            |
| D-02  | Dialogs & abandon    | edit    | @Regression | Back в edit с unsaved → DiscardConfirm                                     |
| D-03  | Dialogs & abandon    | create  | @Regression | DiscardConfirm → confirm → popBack, ничего в БД                            |
| D-04  | Dialogs & abandon    | edit    | @Regression | DiscardConfirm в edit → confirm → возврат в Read с original значениями     |
| D-05  | Dialogs & abandon    | create  | @Smoke      | DiscardConfirm → dismiss → диалог закрыт, state без изменений              |
| D-06  | Dialogs & abandon    | create  | @Smoke      | System back во время диалога → закрывает только диалог                     |
| D-07  | Dialogs & abandon    | edit    | @Regression | Read → Edit → сразу Back → no dialog (state == originalSnapshot)           |
| L-01  | Lifecycle            | edit    | @Regression | Archive → archived_at set, упражнение пропадает из AllExercises            |
| L-02  | Lifecycle            | edit    | @Regression | Archive с активной сессией → ActiveSessionConflict диалог                  |
| L-03  | Lifecycle            | edit    | @Regression | Archive когда упражнение в активной training → ArchiveBlocked              |
| L-04  | Lifecycle            | edit    | @Regression | Undo archive (snackbar action) → archived_at снят                          |
| L-05  | Lifecycle            | archive | @Regression | Permanent delete → confirm → exercise + cascade связи удалены              |
| L-06  | Lifecycle            | archive | @Regression | Permanent delete cascade: exercise_tag, training_exercise очищены          |
| M-01  | Mode transitions     | edit    | @Regression | Read → Edit → originalSnapshot захватывает type+adhocPlan+rest             |
| M-02  | Mode transitions     | edit    | @Regression | Edit → Save → Mode.Read → originalSnapshot обновлён                        |
| M-03  | Mode transitions     | edit    | @Smoke      | Edit → back без изменений → Mode.Read без диалога                          |
| M-04  | Mode transitions     | edit    | @Regression | Edit → modify → cancel discard confirm → Read с восстановленным state      |
| M-05  | Mode transitions     | edit    | @Smoke      | Edit → Image card в режиме Pending → Cancel → возврат к committed image    |

---

## Group 1 — Form basics

```
ID: F-01
Mode: create
Level: @Smoke
User goal (en): Render an empty create form with default values.
User goal (ru): Открыл "+", вижу пустую форму: имя, чип "WEIGHTED", описание, теги, "Add plan", картинка, Save.
Preconditions:
  - User taps "+" on AllExercises → ExerciseGraph navigates with Screen.Exercise(uuid = null).
Stable testTags used:
  - "ExerciseEditScreen", "ExerciseNameField", "ExerciseTypeChip"
  - "ExerciseDescriptionField", "ExerciseTagPicker"
  - "ExercisePlanCard", "ExercisePlanCard.AddButton", "ExerciseImageCard"
  - "ExerciseSaveButton"
Mocked dependencies: none beyond TestInfraModule defaults
Steps:
  1. Mount Screen.Exercise(uuid = null) via TestActivity.
Expected outcomes:
  - "ExerciseEditScreen" displayed.
  - "ExerciseNameField" empty.
  - "ExerciseTypeChip" shows WEIGHTED, no toggle controls present.
  - "ExerciseDescriptionField" empty.
  - "ExerciseTagPicker" present, no chips selected.
  - "ExercisePlanCard.AddButton" labelled "Add plan", no summary.
  - "ExerciseImageCard" empty placeholder.
  - "ExerciseSaveButton" enabled.
Invariants:
  - state.dialogState == Hidden.
  - No TypeToggle Composable mounted.
Coverage references:
  - State.create(uuid=null), Mode.Edit(isCreate=true)
Bug history: none
```

```
ID: F-02
Mode: create
Level: @Regression
User goal (en): Persist a minimal exercise with only a name.
User goal (ru): Жму "+", ввожу "Bench Press", Save → упражнение в списке.
Preconditions:
  - DB seeded with zero exercises.
  - Create-mode screen mounted.
Stable testTags used:
  - "ExerciseNameField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed at T_0
  - SystemFeedback → no-op
Steps:
  1. Type "Bench Press" into name field.
  2. Tap Save.
Expected outcomes:
  - exercise_table has one row: name="Bench Press", type=WEIGHTED, description="",
    last_adhoc_sets=null, image_path=null, archived_at=null.
  - Screen popped back.
Invariants:
  - Zero rows in exercise_tag_table for the new exercise.
  - ImageStorage fake received zero commit() calls.
Coverage references:
  - Action.Click.OnNameChange, Action.Click.OnSaveClick
  - SaveOutcome.Success, handleSaveSuccess Create branch
Bug history: none (pilot test for infrastructure validation)
```

```
ID: F-03
Mode: create
Level: @Regression
User goal (en): Persist exercise with name and description.
User goal (ru): Ввожу имя "Squat" и описание "Back to parallel, pause 1 sec", Save.
Preconditions:
  - DB seeded with zero exercises.
Stable testTags used:
  - "ExerciseNameField", "ExerciseDescriptionField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed at T_0
  - SystemFeedback → no-op
Steps:
  1. Type "Squat" into name.
  2. Type "Back to parallel, pause 1 sec" into description.
  3. Tap Save.
Expected outcomes:
  - exercise_table row with name="Squat", description="Back to parallel, pause 1 sec".
Invariants:
  - Description stored verbatim (no whitespace tampering beyond what production trims).
Coverage references:
  - Action.Click.OnDescriptionChange
Bug history: none
```

```
ID: F-04
Mode: create
Level: @Regression
User goal (en): While Save is in flight, the screen shows loading state and rapid taps don't double-save.
User goal (ru): Тыкаю Save, пока идёт сохранение — Save disabled, повторный тап ничего не делает.
Preconditions:
  - DB seeded with zero exercises.
  - Repository configured to delay save by 500ms (programmable in TestInfraModule).
Stable testTags used:
  - "ExerciseSaveButton", "ExerciseSaveButton.Loading"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
  - Repository save delay → 500ms (one-shot)
Steps:
  1. Type "X" in name.
  2. Tap Save.
  3. Within 100ms, tap Save again. Tap again 200ms later.
  4. Wait for save to complete.
Expected outcomes:
  - exercise_table has exactly one row (not three).
  - "ExerciseSaveButton.Loading" rendered during the 500ms window.
Invariants:
  - Only one interactor.save invocation observed.
Coverage references:
  - state.isSaveInFlight derivation, Save button enabled/disabled gating
Bug history: none
```

```
ID: F-05
Mode: create
Level: @Regression
User goal (en): Leading and trailing whitespace in name is trimmed before persistence.
User goal (ru): Ввёл "  Squat  ", Save → в БД "Squat".
Preconditions:
  - DB seeded with zero exercises.
Stable testTags used:
  - "ExerciseNameField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Type "  Squat  ".
  2. Save.
Expected outcomes:
  - exercise_table row has name="Squat".
Invariants:
  - Internal whitespace preserved if any.
Coverage references:
  - savedSnapshot.name uses current.name.trim()
Bug history: none
```

```
ID: F-06
Mode: edit
Level: @Regression
User goal (en): Entering Edit mode captures current state into originalSnapshot.
User goal (ru): На Read-mode жму Edit — Snapshot захватил текущие имя/тип/описание/теги/план.
Preconditions:
  - DB seeded with one exercise: { uuid="ex_1", name="Squat", type=WEIGHTED,
    description="paused", last_adhoc_sets=[{60kg, 10reps}, {70kg, 8reps}], tags=["legs"] }.
Stable testTags used:
  - "ExerciseDetailScreen", "ExerciseDetail.EditButton"
  - "ExerciseEditScreen", "ExerciseNameField"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Mount Screen.Exercise(uuid="ex_1").
  2. Wait for load. Verify Read-mode rendered.
  3. Tap EditButton.
Expected outcomes:
  - state.mode == Mode.Edit(isCreate=false).
  - state.originalSnapshot has name="Squat", type=WEIGHTED, description="paused",
    tagUuids=["legs"], adhocPlan with the 2 sets.
  - state.hasChanges == false (Snapshot.matches returns true on entry).
Invariants:
  - No DB writes triggered by Edit transition.
Coverage references:
  - processEditClick, originalSnapshot construction with adhocPlan + type
Bug history: none (regression coverage for Snapshot consolidation)
```

```
ID: F-07
Mode: edit
Level: @Regression
User goal (en): Save name change in edit-mode persists and returns to Read.
User goal (ru): В Edit поменял имя, Save → вернулся в Read с новым именем.
Preconditions:
  - DB seeded with { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField", "ExerciseSaveButton"
  - "ExerciseDetailScreen"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Mount Screen.Exercise(uuid="ex_1"). Tap Edit.
  2. Clear name field. Type "Front Squat".
  3. Tap Save.
Expected outcomes:
  - exercise_table.name == "Front Squat" for ex_1.
  - state.mode == Mode.Read.
  - state.originalSnapshot.name == "Front Squat".
  - state.hasChanges == false.
Invariants:
  - Other fields unchanged in DB.
Coverage references:
  - handleSaveSuccess Edit branch updates originalSnapshot
Bug history: PR #145 Codex flag — originalAdhocPlan baseline drift, closed by Snapshot consolidation
```

```
ID: F-08
Mode: edit
Level: @Regression
User goal (en): Save description change persists.
User goal (ru): Поменял описание, Save → в БД новое описание.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", description="old" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseDescriptionField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Edit. Clear description. Type "new description".
  2. Save.
Expected outcomes:
  - exercise_table.description == "new description".
Invariants:
  - exercise_table.name unchanged.
Coverage references:
  - Action.Click.OnDescriptionChange in edit-mode
Bug history: none
```

```
ID: F-09
Mode: edit
Level: @Regression
User goal (en): Save with no actual changes is a no-op but doesn't fail.
User goal (ru): Edit → Save без правок → просто вернулся в Read, в БД ничего не изменилось.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", description="paused", updated_at=T_seed }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed at T_now (T_now > T_seed)
Steps:
  1. Edit. Don't touch any field.
  2. Save.
Expected outcomes:
  - state.mode == Mode.Read.
Invariants:
  - exercise_table row content equal to seed (assert via DAO read, field by field).
  - Whether updated_at changes or not — match production behavior; document explicit choice
    in production code and assert here.
Coverage references:
  - handleSaveSuccess on no-diff payload
Bug history: none
```

---

## Group 2 — Validation & errors

```
ID: V-01
Mode: create
Level: @Regression
User goal (en): Empty name on Save surfaces validation error, no DB writes.
User goal (ru): Save с пустым именем → ошибка под полем, ничего в БД, экран на месте.
Preconditions:
  - DB: zero exercises.
Stable testTags used:
  - "ExerciseSaveButton", "ExerciseNameField.Error"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Tap Save without typing.
Expected outcomes:
  - "ExerciseNameField.Error" rendered with R.string.feature_exercise_error_name_empty.
  - exercise_table has zero rows.
Invariants:
  - state.dialogState == Hidden.
  - interactor.save not invoked.
Coverage references:
  - processSaveClick name validation guard
Bug history: none
```

```
ID: V-02
Mode: edit
Level: @Regression
User goal (en): Clearing name in edit-mode and tapping Save surfaces validation error.
User goal (ru): В Edit стёр имя, Save → ошибка под полем, в БД ничего не поменялось.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField", "ExerciseSaveButton"
  - "ExerciseNameField.Error"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Edit. Clear name field.
  2. Save.
Expected outcomes:
  - "ExerciseNameField.Error" rendered.
  - exercise_table row name still "Squat".
Invariants:
  - state.mode still Mode.Edit.
Coverage references:
  - same guard as V-01 in edit-mode path
Bug history: none
```

```
ID: V-03
Mode: create
Level: @Regression
User goal (en): Saving with a duplicate name surfaces NameConflict dialog.
User goal (ru): Создаю ещё одно с именем "Squat", уже есть → диалог "имя занято".
Preconditions:
  - DB: { uuid="ex_existing", name="Squat" }.
Stable testTags used:
  - "ExerciseNameField", "ExerciseSaveButton"
  - "ExerciseDialog.NameConflict"
  - "ExerciseDialog.NameConflict.DismissButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Type "Squat".
  2. Save.
Expected outcomes:
  - state.dialogState is DialogState.NameConflict with pre-resolved labels.
  - exercise_table still has only "ex_existing".
Invariants:
  - Tap DismissButton → dialogState == Hidden, name field still "Squat", no popBack.
  - System back closes dialog first.
Coverage references:
  - SaveOutcome.DuplicateName → DialogState.NameConflict
  - interceptBack with dialog open
Bug history: none
```

```
ID: V-04
Mode: edit
Level: @Regression
User goal (en): Renaming an existing exercise to a name that's already taken surfaces NameConflict.
User goal (ru): Edit упражнения "Squat", меняю имя на "Bench Press" (уже есть такое) → конфликт.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }, { uuid="ex_2", name="Bench Press" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField", "ExerciseSaveButton"
  - "ExerciseDialog.NameConflict"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Mount Screen.Exercise(uuid="ex_1"). Edit.
  2. Clear name. Type "Bench Press".
  3. Save.
Expected outcomes:
  - DialogState.NameConflict.
  - DB unchanged: ex_1 still "Squat", ex_2 still "Bench Press".
Invariants:
  - state.mode still Mode.Edit.
Coverage references:
  - Duplicate-name check uses uuid-aware comparison (same uuid → no conflict)
Bug history: none
```

```
ID: V-05
Mode: edit
Level: @Regression
User goal (en): Saving an unmodified existing exercise does NOT trigger NameConflict for its own name.
User goal (ru): Открыл Edit и сразу Save (имя совпадает с самим собой) → не конфликт, нормально сохраняется.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Clock → fixed
Steps:
  1. Edit. Save without changes.
Expected outcomes:
  - No dialog. Mode == Read.
Invariants:
  - Conflict check correctly excludes the current exercise's own UUID.
Coverage references:
  - same as V-04 negative branch
Bug history: regression — early implementations of conflict check would false-positive on self
```

```
ID: V-06
Mode: create
Level: @Regression
User goal (en): Image storage IO failure rolls back to no image, exercise still saves.
User goal (ru): Картинку выбрал, но файл не сохранился (IO ошибка) → image_path null, упражнение всё равно сохранилось без картинки.
Preconditions:
  - DB: zero exercises.
  - ImageStorage fake configured to fail on commit() with IOException.
Stable testTags used:
  - "ExerciseNameField", "ExerciseImageCard.AddButton"
  - "ExerciseDialog.ImageSourcePicker.GalleryButton"
  - "ExerciseSaveButton", "ExerciseSnackbar"
Mocked dependencies:
  - ImageStorage → fake throwing IOException on commit
  - Gallery ARC launcher → returns "fake://image_1"
  - Clock → fixed
Steps:
  1. Type "Squat".
  2. Pick image from gallery.
  3. Save.
Expected outcomes:
  - Snackbar with error message rendered (or other production-defined error UI).
  - exercise_table either has zero rows OR has one row with image_path=null
    (decide explicit behavior in production: for now, expect image-failure aborts the save
    entirely so user can retry or skip; assert exercise_table is empty until production
    chooses otherwise).
Invariants:
  - state remains in create-mode if save aborted.
Coverage references:
  - SaveOutcome.ImageSaveFailed
Bug history: none
```

---

## Group 3 — Tags

```
ID: T-01
Mode: create
Level: @Regression
User goal (en): Toggle existing tag on, save, exercise persists with link.
User goal (ru): Тыкнул "Legs" → выбран → Save → в exercise_tag_table связь.
Preconditions:
  - DB: zero exercises, one tag { uuid="tag_legs", name="Legs" }.
Stable testTags used:
  - "ExerciseNameField", "ExerciseTagItem.tag_legs", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Type "Squat".
  2. Tap "ExerciseTagItem.tag_legs".
  3. Save.
Expected outcomes:
  - exercise_tag_table has one row linking new exercise to tag_legs.
Invariants:
  - tag_table unchanged.
Coverage references:
  - Action.Click.OnTagToggle, exerciseTagDao.insert
Bug history: none
```

```
ID: T-02
Mode: create
Level: @Smoke
User goal (en): Toggle a previously-selected tag off; chip reverts to unselected state.
User goal (ru): Тыкаю выбранный тег ещё раз → снимается.
Preconditions:
  - One tag in DB. Mocked Store with state where this tag is selected.
Stable testTags used:
  - "ExerciseTagItem.tag_legs", "ExerciseTagItem.tag_legs.Selected"
Mocked dependencies:
  - Store mocked via ActionCapture
Steps:
  1. Mount with tag pre-selected in mocked state.
  2. Tap "ExerciseTagItem.tag_legs".
Expected outcomes:
  - ActionCapture has Action.Click.OnTagToggle("tag_legs").
Invariants: none (Smoke level)
Coverage references:
  - Composable dispatches OnTagToggle for selected and unselected chips identically
Bug history: none
```

```
ID: T-03
Mode: create
Level: @Regression
User goal (en): Create new tag inline, select it, save — tag and exercise both persist.
User goal (ru): Поле нового тега → пишу "Push" → "Создать" → выбран → Save → в БД и тег, и упражнение, и связь.
Preconditions:
  - DB: zero exercises, zero tags.
Stable testTags used:
  - "ExerciseNameField"
  - "ExerciseTagPicker.NewTagInput", "ExerciseTagPicker.CreateTagButton"
  - "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Type "Squat" in name.
  2. Type "Push" in new-tag input.
  3. Tap CreateTagButton.
  4. Save.
Expected outcomes:
  - tag_table has new row { name="Push", uuid=<generated> }.
  - exercise_table has new exercise.
  - exercise_tag_table links them.
Invariants:
  - Tag UUID is structurally a UUID (parseable).
Coverage references:
  - Action.Click.OnTagCreate, tagDao.insert + exerciseTagDao.insert sequencing
Bug history: none
```

```
ID: T-04
Mode: create
Level: @Smoke
User goal (en): CreateTagButton disabled when input is empty.
User goal (ru): Поле нового тега пустое → кнопка "Создать" disabled.
Preconditions:
  - Mocked Store with empty new-tag input state.
Stable testTags used:
  - "ExerciseTagPicker.NewTagInput", "ExerciseTagPicker.CreateTagButton"
Mocked dependencies:
  - Store mocked
Steps:
  1. Mount.
  2. Verify CreateTagButton.assertIsNotEnabled().
  3. Type "X" in input. Verify enabled. Clear. Verify disabled again.
Expected outcomes:
  - Button enabled state reflects input non-emptiness.
Invariants: —
Coverage references:
  - Composable derived enable state for create-tag button
Bug history: none
```

```
ID: T-05
Mode: create
Level: @Regression
User goal (en): Creating a tag with a name matching an existing tag toggles the existing tag instead of creating duplicate.
User goal (ru): Существует "Push", пишу "Push" в поле нового → жму "Создать" → существующий тег выбирается, нового не создаётся.
Preconditions:
  - DB: one tag { uuid="tag_push", name="Push" }, zero exercises.
Stable testTags used:
  - "ExerciseTagPicker.NewTagInput", "ExerciseTagPicker.CreateTagButton"
  - "ExerciseTagItem.tag_push"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Type "Push" in new-tag input.
  2. Tap CreateTagButton.
Expected outcomes:
  - tag_table still has one row.
  - "ExerciseTagItem.tag_push" is now in selected state.
Invariants:
  - exercise_tag_table empty until Save.
Coverage references:
  - OnTagCreate path with name lookup before insert
Bug history: prevents accidental duplicate-name tag creation
```

```
ID: T-06
Mode: edit
Level: @Regression
User goal (en): Add a tag to existing exercise, save persists the link.
User goal (ru): У существующего упражнения добавил тег → Save → в exercise_tag_table новая строка.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }, tag { uuid="tag_legs" }, no link between them.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseTagItem.tag_legs", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Tap tag_legs.
  2. Save.
Expected outcomes:
  - exercise_tag_table has one row linking ex_1 to tag_legs.
Invariants:
  - exercise_table.name still "Squat".
Coverage references:
  - tag diff on save (insert added, no delete)
Bug history: none
```

```
ID: T-07
Mode: edit
Level: @Regression
User goal (en): Remove tag from existing exercise, save removes the link.
User goal (ru): Снял тег у существующего → Save → связь в exercise_tag_table удалена.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }, tag tag_legs, link in exercise_tag_table.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseTagItem.tag_legs.Selected", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Tap tag_legs (toggles off).
  2. Save.
Expected outcomes:
  - exercise_tag_table has zero rows for ex_1.
Invariants:
  - tag_table.tag_legs row still exists (only the link removed).
Coverage references:
  - tag diff on save (delete removed, no insert)
Bug history: none
```

```
ID: T-08
Mode: edit
Level: @Regression
User goal (en): Edit-mode save without touching tags preserves existing tag links.
User goal (ru): Зашёл в Edit, поменял имя, не трогал теги → Save → теги остались.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }, tag_legs, tag_quads, both linked to ex_1.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Change name to "Front Squat".
  2. Save.
Expected outcomes:
  - exercise_tag_table still has both links for ex_1.
Invariants: —
Coverage references:
  - tag diff identifies no-op when tags not modified
Bug history: regression — tag preservation across edit-save was a v1.3.x bug
```

---

## Group 4 — Images

```
ID: I-01
Mode: create
Level: @Regression
User goal (en): Pick from gallery, save, image_path persists.
User goal (ru): Тыкнул на картинку → "Галерея" → выбрал → миниатюра → Save → в БД image_path.
Preconditions:
  - DB: zero exercises.
Stable testTags used:
  - "ExerciseNameField", "ExerciseImageCard.AddButton"
  - "ExerciseDialog.ImageSourcePicker", "ExerciseDialog.ImageSourcePicker.GalleryButton"
  - "ExerciseImageCard.Thumbnail", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake; pre-loaded "fake://image_1" → 8x8 PNG bytes
  - Gallery ARC launcher → returns "fake://image_1"
  - Camera ARC launcher → not used (must not be invoked)
  - Clock → fixed
Steps:
  1. Type "Squat".
  2. Tap AddButton. Tap GalleryButton.
  3. Save.
Expected outcomes:
  - ImageStorage commit() called once.
  - exercise_table.image_path == fake's persisted path.
Invariants:
  - Camera launcher not invoked.
  - DialogState.ImageSourcePicker hidden after gallery selection.
  - state.pendingImage == PendingImage.Unchanged after save.
Coverage references:
  - DialogState.ImageSourcePicker, PendingImage flow
Bug history: none
```

```
ID: I-02
Mode: create
Level: @Regression
User goal (en): Take photo via camera, save persists.
User goal (ru): Тыкнул на картинку → "Камера" → сделал кадр → миниатюра → Save → в БД image_path.
Preconditions:
  - DB: zero exercises.
  - Camera permission → granted.
Stable testTags used:
  - "ExerciseImageCard.AddButton"
  - "ExerciseDialog.ImageSourcePicker.CameraButton"
  - "ExerciseImageCard.Thumbnail", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Camera ARC launcher → fake writes 4x4 PNG to provided pendingCameraTempUri, returns success
  - Permission gate → granted for CAMERA
  - Clock → fixed
Steps:
  1. Type "Squat".
  2. Tap AddButton. Tap CameraButton.
  3. Save.
Expected outcomes:
  - exercise_table.image_path != null.
  - ImageStorage commit() called once.
Invariants:
  - pendingCameraTempUri (Composable-local) cleared after commit.
Coverage references:
  - Camera ARC bridge state (PR #146 keeps pendingCameraTempUri local)
Bug history: none
```

```
ID: I-03
Mode: create
Level: @Regression
User goal (en): Camera permission denied surfaces PermissionDenied dialog with Settings shortcut.
User goal (ru): Тыкнул "Камера", разрешения нет → диалог "разрешение запрещено" → "Settings" открывает интент.
Preconditions:
  - Permission gate returns Denied.
Stable testTags used:
  - "ExerciseDialog.ImageSourcePicker.CameraButton"
  - "ExerciseDialog.PermissionDenied"
  - "ExerciseDialog.PermissionDenied.OpenSettingsButton"
  - "ExerciseDialog.PermissionDenied.CancelButton"
Mocked dependencies:
  - Permission gate → Denied
  - Camera launcher → must not be invoked
  - Settings intent dispatcher → capturing fake
Steps:
  1. Tap AddButton. Tap CameraButton.
Expected outcomes:
  - DialogState transitions: ImageSourcePicker → PermissionDenied.
  - Tap OpenSettingsButton → captured intent observed (action == ACTION_APPLICATION_DETAILS_SETTINGS).
  - Tap CancelButton → DialogState == Hidden.
Invariants:
  - Camera launcher never invoked.
  - System back from PermissionDenied closes dialog without re-prompting.
Coverage references:
  - DialogState.PermissionDenied
Bug history: none
```

```
ID: I-04
Mode: create
Level: @Smoke
User goal (en): Dismissing ImageSourcePicker closes the dialog without side effects.
User goal (ru): Открыл диалог "источник картинки", тыкнул мимо/Back → закрылся без последствий.
Preconditions:
  - Mocked Store with DialogState.ImageSourcePicker.
Stable testTags used:
  - "ExerciseDialog.ImageSourcePicker"
Mocked dependencies:
  - Store mocked
Steps:
  1. System back.
Expected outcomes:
  - ActionCapture has dismiss action; or asserts via mocked Store dialog hidden.
Invariants: —
Coverage references:
  - interceptBack
Bug history: none
```

```
ID: I-05
Mode: create
Level: @Regression
User goal (en): Pick image, then tap Remove before save — pendingImage cleared, no commit.
User goal (ru): Выбрал картинку, передумал, нажал "удалить" до Save → pendingImage сброшен, ничего не commit'нулось.
Preconditions:
  - DB: zero exercises.
Stable testTags used:
  - "ExerciseImageCard.AddButton"
  - "ExerciseDialog.ImageSourcePicker.GalleryButton"
  - "ExerciseImageCard.Thumbnail", "ExerciseImageCard.RemoveButton"
  - "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → in-memory fake
  - Gallery ARC launcher → returns "fake://image_1"
Steps:
  1. Pick from gallery.
  2. Tap RemoveButton.
  3. Type name. Save.
Expected outcomes:
  - exercise_table.image_path == null.
  - ImageStorage commit() called zero times.
Invariants:
  - pendingImage == PendingImage.Unchanged at save time.
Coverage references:
  - PendingImage.Pending → PendingImage.Unchanged transition
Bug history: none
```

```
ID: I-06
Mode: edit
Level: @Regression
User goal (en): Replace existing image with a new one; old file deleted, new image_path saved.
User goal (ru): У упражнения есть картинка, выбрал новую, Save → в БД новый path, старый файл удалён.
Preconditions:
  - DB: { uuid="ex_1", image_path="/storage/old.png" }.
  - ImageStorage fake has "/storage/old.png" registered.
Stable testTags used:
  - "ExerciseDetail.EditButton"
  - "ExerciseImageCard", "ExerciseImageCard.AddButton" or "ChangeButton"
  - "ExerciseDialog.ImageSourcePicker.GalleryButton"
  - "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Gallery ARC launcher → returns "fake://image_2"
  - Clock → fixed
Steps:
  1. Edit. Tap image change. Pick from gallery.
  2. Save.
Expected outcomes:
  - exercise_table.image_path == new persisted path.
  - ImageStorage fake reports old file deleted, new file registered.
Invariants:
  - Old image file removed exactly once (assert via fake bookkeeping).
Coverage references:
  - ImageCommitOutcome.Stored with previousPath
Bug history: none
```

```
ID: I-07
Mode: edit
Level: @Regression
User goal (en): Remove image from existing exercise; image_path null and file deleted.
User goal (ru): Снял картинку → Save → image_path=null, файл удалён.
Preconditions:
  - DB: { uuid="ex_1", image_path="/storage/old.png" }.
Stable testTags used:
  - "ExerciseDetail.EditButton"
  - "ExerciseImageCard.RemoveButton", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Tap RemoveButton.
  2. Save.
Expected outcomes:
  - exercise_table.image_path == null.
  - ImageStorage fake reports file deleted.
Invariants: —
Coverage references:
  - ImageCommitOutcome.Removed
Bug history: none
```

```
ID: I-08
Mode: edit
Level: @Regression
User goal (en): Toggle remove image, then change mind before save — committed image restored.
User goal (ru): Снял картинку, передумал, "восстановить" до Save → в БД старая картинка осталась.
Preconditions:
  - DB: { uuid="ex_1", image_path="/storage/old.png" }.
Stable testTags used:
  - "ExerciseDetail.EditButton"
  - "ExerciseImageCard.RemoveButton", "ExerciseImageCard.UndoRemoveButton"
  - "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Tap RemoveButton. Tap UndoRemoveButton.
  2. Save.
Expected outcomes:
  - exercise_table.image_path == "/storage/old.png" (unchanged).
  - ImageStorage fake reports zero file operations.
Invariants:
  - state.pendingImage == PendingImage.Unchanged at save.
Coverage references:
  - PendingImage.Removed → Unchanged path
Bug history: none
```

```
ID: I-09
Mode: edit
Level: @Regression
User goal (en): Image save IO failure during replace leaves DB image_path unchanged.
User goal (ru): Меняю картинку, на сохранении IO error → image_path в БД остаётся старым, snackbar.
Preconditions:
  - DB: { uuid="ex_1", image_path="/storage/old.png" }.
  - ImageStorage commit() throws on new image.
Stable testTags used:
  - "ExerciseDetail.EditButton"
  - "ExerciseImageCard.AddButton", "ExerciseDialog.ImageSourcePicker.GalleryButton"
  - "ExerciseSaveButton", "ExerciseSnackbar"
Mocked dependencies:
  - ImageStorage → fake throwing IOException on commit
  - Gallery ARC launcher → returns "fake://image_2"
  - Clock → fixed
Steps:
  1. Edit. Pick new image from gallery.
  2. Save.
Expected outcomes:
  - exercise_table.image_path still "/storage/old.png".
  - ImageSaveFailed snackbar/event surfaced.
  - state remains in Edit mode (save aborted).
Invariants:
  - Old file NOT deleted.
Coverage references:
  - SaveOutcome.ImageSaveFailed in edit-mode
Bug history: none
```

---

## Group 5 — Plan & Type

```
ID: P-01
Mode: create
Level: @Regression
User goal (en): Tap Add plan in create-mode opens PlanEditor in Draft mode with empty state.
User goal (ru): "Add plan" в create → открылся PlanEditor с пустым планом, тип WEIGHTED.
Preconditions:
  - DB: zero exercises.
  - Create-mode screen mounted, name typed.
Stable testTags used:
  - "ExercisePlanCard.AddButton"
  - "PlanEditorScreen", "PlanEditor.TypeToggle"
  - "PlanEditor.EmptyState", "PlanEditor.AddSetButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Tap AddButton.
Expected outcomes:
  - Navigated to Screen.PlanEditor.Draft(initialType=WEIGHTED, initialPlanJson=null).
  - PlanEditorScreen rendered. TypeToggle shows WEIGHTED.
  - EmptyState rendered (or empty list).
  - AddSetButton enabled.
Invariants:
  - No DB writes.
  - PlanEditor mode == Mode.Draft (verify via state if accessible, otherwise via behavior).
Coverage references:
  - Action.Navigation.OpenPlanEditorDraft, Screen.PlanEditor.Draft destination
Bug history: none
```

```
ID: P-02
Mode: create
Level: @Regression
User goal (en): Author a 3-set plan in Draft mode, return, save — plan persists with exercise.
User goal (ru): В Draft забил 3 сета → Done → Save → план в БД вместе с упражнением.
Preconditions:
  - DB: zero exercises.
  - Create-mode, name "Squat" typed.
Stable testTags used:
  - "ExercisePlanCard.AddButton"
  - "PlanEditor.AddSetButton", "PlanEditor.SetRow.0", "PlanEditor.SetRow.1", "PlanEditor.SetRow.2"
  - "PlanEditor.SetRow.X.WeightField", "PlanEditor.SetRow.X.RepsField"
  - "PlanEditor.DoneButton"
  - "ExercisePlanCard.Summary", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Tap AddButton → Draft PlanEditor.
  2. Tap AddSetButton three times.
  3. SetRow.0: 60, 10. SetRow.1: 70, 8. SetRow.2: 80, 6.
  4. Tap DoneButton.
  5. Verify Summary shows "3 sets".
  6. Save.
Expected outcomes:
  - exercise_table row last_adhoc_sets contains 3 sets in order: (60,10), (70,8), (80,6).
  - state.adhocPlan in parent has 3 sets after return.
Invariants:
  - PlanEditor did NOT call interactor.savePlan in Draft mode (assert via spy or absence of intermediate writes).
  - planEditorDraftResultAttr observed exactly once.
Coverage references:
  - Mode.Draft Save → popBack with planEditorDraftResultAttr
  - Action.Common.PlanEditorReturned.FromDraft
  - PlanDraftResult JSON round-trip
Bug history:
  - 1.41.0 silent fail on uuid==null (architecturally impossible post-1.42.0 due to typed Draft route)
```

```
ID: P-03
Mode: create
Level: @Regression
User goal (en): Type change WEIGHTED→WEIGHTLESS in Draft with weighted sets fires confirm dialog and wipes weights on confirm.
User goal (ru): В Draft с двумя весовыми сетами переключаю на WEIGHTLESS → диалог "стереть веса?" → confirm → веса исчезли.
Preconditions:
  - Draft PlanEditor open, two sets {30,15}, {35,12} authored.
Stable testTags used:
  - "PlanEditor.TypeToggle"
  - "PlanEditorDialog.TypeChangeConfirm"
  - "PlanEditorDialog.TypeChangeConfirm.ConfirmButton"
  - "PlanEditorDialog.TypeChangeConfirm.DismissButton"
  - "PlanEditor.SetRow.0.WeightField"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Tap TypeToggle to WEIGHTLESS.
  2. Verify TypeChangeConfirm dialog rendered with pre-resolved labels.
  3. Tap ConfirmButton.
Expected outcomes:
  - Dialog hidden.
  - Set rows re-render without WeightField.
  - PlanEditor state.type == WEIGHTLESS, draft sets have no weight values.
Invariants:
  - Pre-resolved labels in dialog (no ResourceWrapper call inside updateState lambda — Rule 1).
  - Dismiss path: dismiss → dialog hidden, type still WEIGHTED, sets retain weights.
  - System back during dialog open closes only the dialog.
Coverage references:
  - DialogState.TypeChangeConfirm in PlanEditor
  - PlanDraftReducer ApplyTypeChange path
Bug history: user-found stale-plan-on-type-change (architecturally closed by ownership migration)
```

```
ID: P-04
Mode: create
Level: @Smoke
User goal (en): Type toggle in Draft with empty plan applies silently (no dialog).
User goal (ru): План пустой → переключаю тип → без диалога, тип меняется молча.
Preconditions:
  - Draft PlanEditor open, no sets.
Stable testTags used:
  - "PlanEditor.TypeToggle"
Mocked dependencies:
  - Mocked Store
Steps:
  1. Tap TypeToggle.
Expected outcomes:
  - state.type changes immediately, no dialogState transition.
Invariants:
  - DialogState remains Hidden.
Coverage references:
  - Type-change confirm gate (only fires when weighted sets exist)
Bug history: none
```

```
ID: P-05
Mode: create
Level: @Smoke
User goal (en): Open Draft, do nothing, tap Done — returns with empty result, parent unchanged.
User goal (ru): Открыл Draft, ничего не сделал, Done → вернулся, в parent ничего не изменилось.
Preconditions:
  - Create-mode, name "X" typed, no plan.
Stable testTags used:
  - "ExercisePlanCard.AddButton", "PlanEditor.DoneButton"
  - "ExercisePlanCard"
Mocked dependencies:
  - Store mocked / minimal infra
Steps:
  1. Tap AddButton. Tap DoneButton.
Expected outcomes:
  - state.adhocPlan in parent still null/empty.
  - state.type still WEIGHTED.
Invariants:
  - Returning empty result is a no-op merge; parent does not become "dirty" from the empty draft return.
Coverage references:
  - PlanDraftResult merge with empty plan
Bug history: none
```

```
ID: P-06
Mode: create
Level: @Regression
User goal (en): System back from Draft PlanEditor returns no result; parent state unchanged.
User goal (ru): В Draft что-то понабивал, потом системный Back → вернулся, в parent ничего не появилось.
Preconditions:
  - Create-mode, name "X" typed.
Stable testTags used:
  - "ExercisePlanCard.AddButton", "PlanEditor.AddSetButton"
  - "ExercisePlanCard"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Tap AddButton.
  2. In PlanEditor, add 2 sets with values.
  3. System back.
Expected outcomes:
  - Returned to parent screen.
  - state.adhocPlan in parent still null/empty.
  - state.type unchanged.
Invariants:
  - planEditorDraftResultAttr NOT signaled (or signaled with null/sentinel — match production).
Coverage references:
  - System back from PlanEditor.Draft does not commit
Bug history: none
```

```
ID: P-07
Mode: edit
Level: @Regression
User goal (en): Existing PlanEditor save persists plan to DB and triggers partial reload in parent.
User goal (ru): У существующего упражнения в PlanEditor поменял сет → Save → в БД новый план, parent обновился.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", type=WEIGHTED, last_adhoc_sets=[{60,10}, {70,8}] }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExercisePlanCard.EditButton"
  - "PlanEditor.SetRow.0.RepsField", "PlanEditor.SaveButton"
  - "ExercisePlanCard.Summary"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Mount Screen.Exercise(uuid="ex_1"). Edit. Tap EditButton on plan card.
  2. Navigated to Screen.PlanEditor.Existing.
  3. SetRow.0: change reps from 10 to 12.
  4. Tap SaveButton.
Expected outcomes:
  - exercise_table.last_adhoc_sets for ex_1 == [{60,12}, {70,8}].
  - Returned to parent Edit-mode.
  - state.adhocPlan in parent matches the new plan.
  - state.originalSnapshot.adhocPlan also updated to match.
  - state.hasChanges == false (partial reload also baselined the plan).
Invariants:
  - Parent's name/description NOT re-fetched; remains as user had it (unchanged here, but
    P-08 covers the case where it was modified).
  - Full processInit was NOT called.
Coverage references:
  - Action.Common.PlanEditorReturned.FromExisting
  - Partial reload: type + adhocPlan only
Bug history:
  - PR #145 Codex flag — partial reload contract was added to prevent name field stomping
```

```
ID: P-08
Mode: edit
Level: @Regression
User goal (en): Editing name in parent, then opening Existing PlanEditor and saving plan does NOT clobber the unsaved name.
User goal (ru): В Edit поменял имя на "X", не сохранил, открыл PlanEditor, поправил план, Save в plan editor → вернулся, имя "X" на месте.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", last_adhoc_sets=[{60,10}] }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField"
  - "ExercisePlanCard.EditButton", "PlanEditor.AddSetButton", "PlanEditor.SaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Clear name. Type "Front Squat". Do NOT save.
  2. Tap plan EditButton. In PlanEditor, add another set, save.
  3. Returned to parent.
Expected outcomes:
  - "ExerciseNameField" still shows "Front Squat" (NOT reverted to "Squat").
  - state.adhocPlan has 2 sets.
  - state.name == "Front Squat".
  - state.hasChanges == true (name still dirty).
Invariants:
  - exercise_table.name == "Squat" (not yet persisted from parent — only PlanEditor persisted plan).
Coverage references:
  - Partial reload preserves non-plan-non-type parent state
Bug history:
  - This is THE main regression-anchor for v1.42.0 partial-reload contract
```

```
ID: P-09
Mode: edit
Level: @Regression
User goal (en): Type change WEIGHTED→WEIGHTLESS in Existing PlanEditor with weighted sets, confirm, save — DB updated and parent partial-reloaded with new type+plan.
User goal (ru): В Existing PlanEditor type=WEIGHTLESS с весами → confirm → wipe → Save → в БД (type, plan) → parent type chip обновился, план без весов.
Preconditions:
  - DB: { uuid="ex_1", type=WEIGHTED, last_adhoc_sets=[{50,12},{55,10}] }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExercisePlanCard.EditButton"
  - "PlanEditor.TypeToggle", "PlanEditorDialog.TypeChangeConfirm.ConfirmButton"
  - "PlanEditor.SaveButton", "ExerciseTypeChip"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Plan EditButton. In PlanEditor toggle WEIGHTLESS, confirm.
  2. Save.
Expected outcomes:
  - exercise_table.type == WEIGHTLESS, last_adhoc_sets == [{0,12},{0,10}] (or null weights, match production).
  - parent state.type == WEIGHTLESS.
  - parent state.adhocPlan == 2 weightless sets.
  - parent ExerciseTypeChip displays WEIGHTLESS.
  - state.hasChanges (parent) == false (partial reload baselined both).
Invariants:
  - Subsequent parent Save does NOT overwrite type with WEIGHTED (verify this in P-10).
Coverage references:
  - Partial reload includes type
  - Snapshot.adhocPlan and Snapshot.type both updated
Bug history:
  - User-found stale-plan-on-type-change closed by ownership migration
  - Type-write conflict closed by partial reload
```

```
ID: P-10
Mode: edit
Level: @Regression
User goal (en): After P-09 partial reload, parent Save does NOT clobber the new type.
User goal (ru): После P-09 ничего больше не меняю, Save в parent → в БД остался WEIGHTLESS (не вернулся к WEIGHTED).
Preconditions: continuation of P-09.
Stable testTags used:
  - "ExerciseSaveButton"
Mocked dependencies: same as P-09
Steps:
  1. After P-09 completes, tap Save in parent.
Expected outcomes:
  - exercise_table.type == WEIGHTLESS (unchanged).
  - state.mode == Mode.Read.
Invariants: —
Coverage references:
  - Type-write conflict prevention (parent state.type synced with DB after partial reload)
Bug history:
  - Type-write conflict (covered by P-10 invariant)
```

```
ID: P-11
Mode: edit
Level: @Regression
User goal (en): After Existing PlanEditor save, hasChanges is false (Snapshot consolidation regression).
User goal (ru): В edit поменял план, Save в plan editor → Back в parent → нет диалога "discard?".
Preconditions:
  - DB: { uuid="ex_1", last_adhoc_sets=[{60,10}] }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExercisePlanCard.EditButton"
  - "PlanEditor.AddSetButton", "PlanEditor.SaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit → plan EditButton → add a set → save.
  2. Returned to parent. Tap system back.
Expected outcomes:
  - state.dialogState == Hidden (no DiscardConfirm).
  - Screen pops back.
Invariants:
  - state.hasChanges == false right after partial reload.
Coverage references:
  - Snapshot consolidation: originalSnapshot.adhocPlan and .type updated after FromExisting
Bug history:
  - PR #145 Codex regression — closed
```

```
ID: P-12
Mode: edit
Level: @Regression
User goal (en): System back from Existing PlanEditor without save — no DB write, parent state unchanged.
User goal (ru): В Existing PlanEditor что-то поменял, Back без Save → в БД старый план, parent план тоже старый.
Preconditions:
  - DB: { uuid="ex_1", last_adhoc_sets=[{60,10}] }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExercisePlanCard.EditButton"
  - "PlanEditor.SetRow.0.RepsField"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit → plan EditButton → modify reps from 10 to 99.
  2. System back.
Expected outcomes:
  - exercise_table.last_adhoc_sets == [{60,10}] (unchanged).
  - parent state.adhocPlan == [{60,10}] (unchanged).
Invariants:
  - No PlanEditor result produced (the read yields null).
  - No partial reload triggered.
Coverage references:
  - Cancel from Existing PlanEditor
Bug history: none
```

```
ID: P-13
Mode: edit
Level: @Regression
User goal (en): Existing PlanEditor for an exercise without an existing plan — empty state, can author from scratch.
У существующего упражнения плана нет → "Add plan" → Existing PlanEditor с пустым state → добавил сеты → Save.
Preconditions:
  - DB: { uuid="ex_1", last_adhoc_sets=null }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExercisePlanCard.AddButton"
  - "PlanEditor.EmptyState", "PlanEditor.AddSetButton", "PlanEditor.SaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. AddButton on plan card.
  2. PlanEditor in Existing mode for ex_1; empty state.
  3. Add 2 sets. Save.
Expected outcomes:
  - exercise_table.last_adhoc_sets has 2 sets.
  - parent state.adhocPlan has 2 sets after partial reload.
Invariants:
  - Existing mode used (not Draft) because uuid is non-null.
Coverage references:
  - Existing mode with null initial plan
Bug history: none
```

---

## Group 6 — Dialogs & abandon

```
ID: D-01
Mode: create
Level: @Regression
User goal (en): Back press in create-mode with unsaved changes shows DiscardConfirm dialog.
User goal (ru): В create что-то ввёл, Back → диалог "точно отменить?".
Preconditions:
  - DB: zero exercises.
Stable testTags used:
  - "ExerciseNameField", "ExerciseDialog.DiscardConfirm"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Type "X" in name.
  2. System back.
Expected outcomes:
  - state.dialogState is DialogState.DiscardConfirm.
Invariants:
  - Screen NOT popped.
Coverage references:
  - hasChanges true via originalSnapshot=null + non-default field
Bug history: none
```

```
ID: D-02
Mode: edit
Level: @Regression
User goal (en): Back press in edit-mode with unsaved changes shows DiscardConfirm dialog.
User goal (ru): В Edit что-то поменял, Back → диалог.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField"
  - "ExerciseDialog.DiscardConfirm"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Clear name. Type "Front Squat".
  2. System back.
Expected outcomes:
  - DialogState.DiscardConfirm with target = Mode.Edit(isCreate=false).
Invariants: —
Coverage references:
  - DiscardConfirm.target distinguishes create vs edit flow
Bug history: none
```

```
ID: D-03
Mode: create
Level: @Regression
User goal (en): Confirm discard in create-mode pops back without saving.
User goal (ru): В диалоге "точно отменить?" жму Confirm → закрылось, в БД ничего.
Preconditions: D-01 final state.
Stable testTags used:
  - "ExerciseDialog.DiscardConfirm.ConfirmButton"
Mocked dependencies: same as D-01
Steps:
  1. Tap ConfirmButton.
Expected outcomes:
  - Screen popped.
  - exercise_table empty.
Invariants:
  - ImageStorage fake commits zero.
  - exercise_tag_table untouched.
Coverage references:
  - DiscardConfirm.target == DiscardTarget.PopBack
Bug history: none
```

```
ID: D-04
Mode: edit
Level: @Regression
User goal (en): Confirm discard in edit-mode reverts to Read with original values.
User goal (ru): В Edit поменял, Discard confirm → вернулся в Read со старыми значениями.
Preconditions: D-02 final state.
Stable testTags used:
  - "ExerciseDialog.DiscardConfirm.ConfirmButton"
  - "ExerciseDetailScreen", "ExerciseDetail.NameLabel"
Mocked dependencies: same as D-02
Steps:
  1. Tap ConfirmButton.
Expected outcomes:
  - state.mode == Mode.Read.
  - state.name == "Squat" (restored from originalSnapshot).
  - "ExerciseDetail.NameLabel" displays "Squat".
Invariants:
  - exercise_table unchanged.
Coverage references:
  - DiscardConfirm.target == DiscardTarget.RevertToRead, restore from originalSnapshot
Bug history: none
```

```
ID: D-05
Mode: create
Level: @Smoke
User goal (en): Dismiss DiscardConfirm closes dialog without state changes.
User goal (ru): На диалоге Discard жму Dismiss → диалог закрылся, поля как были.
Preconditions:
  - Mocked Store with DialogState.DiscardConfirm and pre-typed fields.
Stable testTags used:
  - "ExerciseDialog.DiscardConfirm.DismissButton", "ExerciseNameField"
Mocked dependencies: Store mocked
Steps:
  1. Tap DismissButton.
Expected outcomes:
  - dialogState transitions to Hidden via captured action.
  - Name field still shows pre-typed value.
Invariants: —
Coverage references:
  - Discard dismiss path
Bug history: none
```

```
ID: D-06
Mode: create
Level: @Smoke
User goal (en): System back during open dialog closes only the dialog (interceptBack contract).
User goal (ru): Открыт любой диалог, жму системный Back → закрывается только диалог, экран остаётся.
Preconditions:
  - Mocked Store with DialogState.DiscardConfirm.
Stable testTags used:
  - "ExerciseDialog.DiscardConfirm"
Mocked dependencies: Store mocked
Steps:
  1. System back.
Expected outcomes:
  - dialogState == Hidden.
  - Screen still mounted.
Invariants:
  - Subsequent system back triggers normal back behavior (not the dialog again unless re-opened).
Coverage references:
  - interceptBack with dialogState !is Hidden
Bug history:
  - Pattern from PR #146 — verify it works for Exercise feature too
```

```
ID: D-07
Mode: edit
Level: @Regression
User goal (en): Read → tap Edit → immediately Back without changes → no dialog, return to Read.
User goal (ru): Read → Edit → сразу Back, ничего не трогал → без диалога, возврат в Read.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseDetailScreen"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Mount Screen.Exercise(uuid="ex_1"). Tap Edit.
  2. System back.
Expected outcomes:
  - state.mode == Mode.Read.
  - dialogState == Hidden.
Invariants:
  - hasChanges == false (originalSnapshot.matches(state) == true on entry).
Coverage references:
  - Edit-mode entry seeds originalSnapshot from current state, not from null
Bug history:
  - Pre-Snapshot-consolidation, type/plan could be considered dirty here. Regression coverage.
```

---

## Group 7 — Lifecycle

```
ID: L-01
Mode: edit
Level: @Regression
User goal (en): Archive existing exercise from edit-mode menu — archived_at set, falls out of AllExercises.
User goal (ru): В Edit-mode меню "Архивировать" → archived_at стоит, упражнение пропало из списка.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", archived_at=null }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseDetail.OverflowMenu", "ExerciseDetail.ArchiveAction"
  - "AllExercises.List", "AllExercises.ItemForUuid.ex_1"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed at T_archive
Steps:
  1. Mount Screen.Exercise(uuid="ex_1"). Edit. Open overflow menu.
  2. Tap ArchiveAction.
Expected outcomes:
  - exercise_table.archived_at == T_archive for ex_1.
  - Screen pops to AllExercises.
  - "AllExercises.ItemForUuid.ex_1" NOT present.
Invariants:
  - exercise_tag_table links preserved (archive is soft-delete).
Coverage references:
  - processArchiveClick, ArchiveResult.Success path
Bug history: none
```

```
ID: L-02
Mode: edit
Level: @Regression
User goal (en): Archive blocked by active live-workout session shows ActiveSessionConflict dialog.
User goal (ru): У меня идёт live-workout с этим упражнением, жму Archive → диалог "сначала закрой сессию".
Preconditions:
  - DB: { uuid="ex_1" }, active session referencing ex_1.
Stable testTags used:
  - "ExerciseDetail.OverflowMenu", "ExerciseDetail.ArchiveAction"
  - "ExerciseDialog.ActiveSessionConflict"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Overflow → ArchiveAction.
Expected outcomes:
  - DialogState.ActiveSessionConflict with sessionUuid + activeSessionName + progressLabel pre-resolved.
  - exercise_table.archived_at still null.
Invariants:
  - Active session intact.
Coverage references:
  - DialogState.ActiveSessionConflict from PR #146
  - ArchiveResult.BlockedByActiveSession path
Bug history: none
```

```
ID: L-03
Mode: edit
Level: @Regression
User goal (en): Archive blocked by referenced training shows ArchiveBlocked dialog with formatted body.
User goal (ru): Упражнение в активных тренировках → Archive → диалог "блокирован, есть в этих тренировках: X, Y".
Preconditions:
  - DB: { uuid="ex_1" }, two training rows referencing ex_1.
Stable testTags used:
  - "ExerciseDetail.OverflowMenu", "ExerciseDetail.ArchiveAction"
  - "ExerciseDialog.ArchiveBlocked"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Overflow → ArchiveAction.
Expected outcomes:
  - DialogState.ArchiveBlocked rendered with body containing exercise name + comma-joined training names.
  - exercise_table.archived_at still null.
Invariants: —
Coverage references:
  - DialogState.ArchiveBlocked, body pre-formatted in handler (Rule 1)
Bug history: none
```

```
ID: L-04
Mode: edit
Level: @Regression
User goal (en): Undo archive via snackbar action restores archived_at to null.
User goal (ru): Архивировал → вижу snackbar "отменить" → жму отмену → архивация откатилась.
Preconditions:
  - DB: { uuid="ex_1", archived_at=null }.
Stable testTags used:
  - "ExerciseDetail.ArchiveAction", "ExerciseSnackbar", "ExerciseSnackbar.UndoAction"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Archive.
  2. On AllExercises with snackbar visible, tap UndoAction.
Expected outcomes:
  - exercise_table.archived_at == null for ex_1.
  - "AllExercises.ItemForUuid.ex_1" reappears.
Invariants: —
Coverage references:
  - undoArchive path
Bug history: none
```

```
ID: L-05
Mode: archive
Level: @Regression
User goal (en): Permanent delete from archive screen via confirm dialog wipes the row.
User goal (ru): В архиве → Permanent delete → confirm → упражнение удалено навсегда.
Preconditions:
  - DB: { uuid="ex_1", archived_at=T_old }.
Stable testTags used:
  - "ExerciseDetail.PermanentDeleteAction"
  - "ExerciseDialog.PermanentDeleteConfirm"
  - "ExerciseDialog.PermanentDeleteConfirm.ConfirmButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Mount archived exercise. Open archive flow. Tap PermanentDeleteAction.
  2. Tap ConfirmButton.
Expected outcomes:
  - exercise_table has zero rows for ex_1.
Invariants:
  - Pre-resolved title, body, impactSummary, confirmLabel in DialogState.PermanentDeleteConfirm
    (Rule 1).
Coverage references:
  - DialogState.PermanentDeleteConfirm
  - permanentDelete repository path
Bug history: none
```

```
ID: L-06
Mode: archive
Level: @Regression
User goal (en): Permanent delete cascades exercise_tag and training_exercise rows.
User goal (ru): Удалил упражнение навсегда → связанные строки в других таблицах тоже удалены.
Preconditions:
  - DB: { uuid="ex_1", archived_at=T_old }, exercise_tag link, training_exercise reference.
Stable testTags used: same as L-05
Mocked dependencies: same as L-05
Steps:
  1. Permanent delete with confirm.
Expected outcomes:
  - exercise_table, exercise_tag_table, training_exercise_table all have zero rows for ex_1.
Invariants:
  - tag_table and training_table rows themselves NOT deleted (only the join rows).
Coverage references:
  - Foreign-key cascade verified
Bug history: regression for cascade integrity
```

---

## Group 8 — Mode transitions

```
ID: M-01
Mode: edit
Level: @Regression
User goal (en): Read → Edit captures originalSnapshot including type and adhocPlan.
User goal (ru): Read → Edit → originalSnapshot захватил всё, включая тип и план.
Preconditions:
  - DB: { uuid="ex_1", name="Squat", type=WEIGHTED, last_adhoc_sets=[{60,10}], description="d" },
    one tag linked.
Stable testTags used:
  - "ExerciseDetail.EditButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Mount. Tap Edit.
Expected outcomes:
  - state.originalSnapshot has name="Squat", type=WEIGHTED, description="d", tagUuids=[<one>],
    adhocPlan with 1 set.
Invariants:
  - Snapshot.matches(state) == true on entry.
Coverage references:
  - processEditClick fills Snapshot with all 5 fields including type+adhocPlan
Bug history:
  - Pre-Snapshot-consolidation, type and adhocPlan were NOT in Snapshot. Regression coverage.
```

```
ID: M-02
Mode: edit
Level: @Regression
User goal (en): Edit → Save updates originalSnapshot to the saved values.
User goal (ru): В Edit поменял что-то, Save → originalSnapshot обновился.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField", "ExerciseSaveButton"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Change name to "Front Squat".
  2. Save.
Expected outcomes:
  - state.mode == Mode.Read.
  - state.originalSnapshot.name == "Front Squat".
  - state.hasChanges == false.
Invariants: —
Coverage references:
  - handleSaveSuccess Edit branch updates originalSnapshot with current.adhocPlan and current.type
Bug history:
  - PR #145 baseline-drift regression
```

```
ID: M-03
Mode: edit
Level: @Smoke
User goal (en): Edit → no-op → Back returns to Read without dialog.
User goal (ru): Read → Edit → ничего не трогал → Back → возврат в Read без диалога.
Preconditions:
  - DB: { uuid="ex_1" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseDetailScreen"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. System back.
Expected outcomes:
  - state.mode == Mode.Read.
  - dialogState == Hidden.
Invariants: —
Coverage references:
  - hasChanges == false on Edit-mode entry → DiscardConfirm not shown
Bug history: none
```

```
ID: M-04
Mode: edit
Level: @Regression
User goal (en): Edit → modify → cancel via discard confirm → Read with original values.
User goal (ru): В Edit поменял имя → Back → диалог → Confirm → Read со старым именем.
Preconditions:
  - DB: { uuid="ex_1", name="Squat" }.
Stable testTags used:
  - "ExerciseDetail.EditButton", "ExerciseNameField"
  - "ExerciseDialog.DiscardConfirm.ConfirmButton"
  - "ExerciseDetail.NameLabel"
Mocked dependencies:
  - ImageStorage → fake
  - Clock → fixed
Steps:
  1. Edit. Clear name. Type "X". System back. Confirm discard.
Expected outcomes:
  - state.name == "Squat".
  - state.mode == Mode.Read.
  - "ExerciseDetail.NameLabel" displays "Squat".
Invariants:
  - exercise_table unchanged.
Coverage references:
  - DiscardConfirm restore-from-snapshot path
Bug history: none
```

```
ID: M-05
Mode: edit
Level: @Smoke
User goal (en): Edit → mark image for removal → cancel discard → committed image restored.
User goal (ru): В Edit пометил картинку на удаление → Back → Discard cancel → возврат в Edit с не-pending картинкой.
Preconditions:
  - Mocked Store with state where image was committed and pendingImage == Removed.
Stable testTags used:
  - "ExerciseImageCard", "ExerciseImageCard.CommittedThumbnail"
  - "ExerciseDialog.DiscardConfirm.DismissButton"
Mocked dependencies: Store mocked
Steps:
  1. Mount with pendingImage == Removed.
  2. System back. DiscardConfirm.
  3. DismissButton (NOT Confirm).
Expected outcomes:
  - dialogState hidden.
  - Image card still shows pendingImage == Removed (no thumbnail rendered).
Invariants:
  - pendingImage NOT auto-reverted on dismiss.
Coverage references:
  - DiscardConfirm dismiss preserves pending state
Bug history: none
```
