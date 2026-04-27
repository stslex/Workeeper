# Feature spec — Trainings (Stage 5.3)

This is the Stage 5.3 feature spec — the third v1 feature
implementation after Settings + Archive (Stage 5.1) and Exercises
(Stage 5.2). It builds on:

- [product.md](../product.md)
- [ux-architecture.md](../ux-architecture.md) — Trainings tab,
  Training detail, Edit training sections
- [data-needs.md](../data-needs.md)
- [db-redesign.md](../db-redesign.md) and
  [db-redesign-plan-model.md](../db-redesign-plan-model.md) — plan-first model in v5 schema
- [design-system.md](../design-system.md)
- [architecture.md](../architecture.md) — Navigation flow,
  Localization, Back gesture handling
- [feature-specs/exercises.md](exercises.md) — patterns to mirror

## Scope

Three screens for Trainings:

- **Trainings tab** (library) — paged list, FAB create, tag filter,
  multi-select archive/delete, active session marker.
- **Training detail** — name, description, tags, ordered exercise
  list with inline plans, past sessions list, Start/Resume session
  CTA.
- **Edit training** — form: name, description, tags, ordered
  exercise list with **inline plan editor per exercise**,
  drag-to-reorder.

Plus this PR retrofits existing Exercises feature with **plan
editor** for `exercise.last_adhoc_sets` and adds **multi-select
mode** to feature/all-exercises (parity with Trainings).

A new shared component `AppPlanEditor` is introduced in
`core/ui/kit/components/` — the modal bottom sheet for editing a
plan, used by both Trainings and Exercises features.

## Module structure

Two trainings modules + retrofit to two exercises modules.

```
feature/all-trainings/             — the library tab (rewrite)
  src/main/kotlin/.../
    di/AllTrainingsModule.kt + HandlerStore + Processor
    domain/AllTrainingsInteractor[Impl].kt
    ui/
      AllTrainingsGraph.kt
      AllTrainingsScreen.kt
      components/
        TrainingRow.kt              — list row with active marker, multi-select checkbox
        TrainingsEmptyState.kt
        TagFilterRow.kt
        SelectionTopBar.kt          — top bar for multi-select mode
        BulkActionBar.kt            — bottom action bar with Archive/Delete
      mvi/
        store/AllTrainingsStore[Impl].kt
        handler/
          ClickHandler.kt           — incl. multi-select toggle
          PagingHandler.kt
          NavigationHandler.kt
          CommonHandler.kt
          SelectionHandler.kt       — multi-select state mgmt
          AllTrainingsComponent.kt
  src/main/res/values/strings.xml + values-ru/strings.xml

feature/single-training/           — detail + edit (rewrite)
  src/main/kotlin/.../
    di/SingleTrainingModule.kt + HandlerStore + Processor + EntryPoint
    domain/SingleTrainingInteractor[Impl].kt
    ui/
      SingleTrainingGraph.kt
      TrainingDetailScreen.kt       — read view
      TrainingEditScreen.kt         — form view (with inline plan editor opens sheet)
      components/
        TrainingHero.kt              — name + tags + description card
        TrainingExerciseRow.kt       — row with inline plan summary, plan editor affordance
        TrainingHistoryRow.kt        — past session row
        ExercisePickerSheet.kt       — modal sheet for adding exercises
      mvi/
        store/SingleTrainingStore[Impl].kt
        handler/
          ClickHandler.kt
          InputHandler.kt
          NavigationHandler.kt
          CommonHandler.kt
          SingleTrainingComponent.kt
  src/main/res/values/strings.xml + values-ru/strings.xml

feature/all-exercises/             — RETROFIT for multi-select
  ui/components/
    + (reuse SelectionTopBar from Trainings? Or keep local? See "Shared selection components" below)
  ui/AllExercisesScreen.kt          — wire selection mode
  mvi/store/AllExercisesStore.kt    — extend State, Action, Event for multi-select
  mvi/handler/SelectionHandler.kt   — new handler

feature/exercise/                  — RETROFIT for plan editor on last_adhoc_sets
  ui/ExerciseEditScreen.kt          — new "Plan" section opens AppPlanEditor sheet
  ui/components/                    — reference AppPlanEditor from kit
  mvi/store/ExerciseStore.kt        — extend State (planSets, planEditorOpen), Action (OnPlanEdit, OnPlanSave)
  mvi/handler/ClickHandler.kt       — handle plan edit flow

core/ui/kit/components/
  + AppPlanEditor.kt                — NEW shared component, modal bottom sheet
  + components/AppSetTypeChip.kt    — already exists, reused
```

### Shared selection components

Two options for the multi-select UI shared between Trainings and
Exercises:

**A. Inline in each feature** — each feature has its own
`SelectionTopBar.kt` and `BulkActionBar.kt`. Pro: zero coupling. Con:
duplication, drift over time.

**B. Promote to core/ui/kit** — `AppSelectionTopBar` and
`AppBulkActionBar` as shared components with slot APIs. Pro: single
source of truth. Con: kit grows.

**Decision: A — keep inline.** The two implementations differ in
the bulk actions exposed (Trainings: Archive + Delete; Exercises:
Archive + Delete), but the selection state semantics differ
slightly enough (Trainings tracks active session in row, Exercises
tracks `canPermanentlyDelete` flag) that a shared kit component
would need many slots. Keep inline; if a third multi-select list
emerges in v2, promote then.

The visual styling (TopBar layout, BulkActionBar position above
bottom nav) is documented as a pattern in this spec so both copies
stay aligned.

## Row layout invariant

Every list row across Trainings tab, Training detail's exercise
list, Exercises tab, Past sessions list **shares the same shape**:

```
[28dp icon] [body — exactly 3 lines] [22dp trailing chevron OR 22dp checkbox]
            line 1: name                    (single line, ellipsis)
            line 2: tags + meta             (single line, ellipsis, "+N" if tags overflow)
            line 3: plan or status info     (single line, ellipsis, italic if empty)
```

- Vertical padding: `AppDimension.spaceMd` (16dp) top + bottom.
- Inter-line spacing: `AppDimension.spaceXs` (4dp).
- Text styles: name = `bodyMedium` weight 500; lines 2 + 3 = `bodySmall`.
- Line height: `1.4` × font size, fixed.
- Total row height: ~80dp regardless of content density.

**Line 3 content varies by context:**

| Screen | Line 3 content |
|--------|----------------|
| Trainings tab | "in progress · started Xm ago" / "last: X days ago" / "never trained" (italic) |
| Training detail exercise list | plan summary "100×5 · 100×5 · 102.5×5" / "no plan yet" (italic) |
| Edit training exercise list | same as Training detail, with "edit plan"/"add plan" trailing affordance |
| Exercises tab | "last: X days ago" / "never logged" (italic) |
| Past sessions list | "5 exercises · 24 sets" |

**Truncation rules:**

- Name → ellipsis at end if doesn't fit.
- Tags row → render up to 3 chips inline; if more would overflow,
  replace last chip with `+N` chip in `textTertiary` muted color.
  Always keep the `· N exercises` / `· N sessions` count visible
  even if it means truncating tags more aggressively.
- Plan/status row → ellipsis at end. For long plans, truncate the
  text after as many full sets as fit.

This invariant is enforced visually by Composable structure (fixed
height container, single-line `Text` with `maxLines = 1`,
`overflow = TextOverflow.Ellipsis`).

## Screens

### Trainings tab

Top-level screen for the bottom-bar tab. Layout top to bottom:

1. `AppTopAppBar` — title `feature_all_trainings_title` ("Trainings" / "Тренировки"), no trailing actions.
2. `TagFilterRow` — same OR semantics as Exercises (was changed to OR in Stage 5.2 fix). Hidden if no tags.
3. Paged trainings list — each row is `TrainingRow`.
4. `AppFAB` (icon Add) — bottom-right, navigates to Edit training in create mode.

#### `TrainingRow` layout (per row layout invariant)

```
Leading icon: ⊞ (compound exercise / "training" abstract icon, accent color)
Line 1: training name + (if active session) "Active" pill
Line 2: tags chips (up to 3) + "· N exercises"
Line 3: status — "in progress · started Xm ago" / "last: X days ago" / "never trained"
Trailing: chevron ›, OR checkbox (in selection mode)
```

Active state: row has 2dp left border in accent color, `bgSecondary`
background tint instead of default `bgPrimary`.

#### Multi-select mode

Triggered by long-press on any row.

- TopAppBar replaced by `SelectionTopBar`:
  - Leading: × close (exits selection mode)
  - Center: "%d selected" count
  - Trailing: empty
- Each row gets a checkbox slot in place of trailing chevron.
- Tap on row in selection mode → toggle inclusion in selected set.
- BackHandler is enabled in selection mode → back exits selection,
  not the screen.
- `BulkActionBar` appears above bottom navigation:
  - "Archive" — archive all selected (always available)
  - "Delete" — permanently delete all selected, only enabled if
    every selected training has zero sessions and isn't an active
    template. Disabled visually otherwise.
- After bulk action: selection mode exits, snackbar with count
  ("3 trainings archived" / "3 тренировки в архиве"). Undo not
  supported for bulk archive in v1 (single archive has undo).

#### Empty state

`AppEmptyState`:
- Icon: `Icons.Filled.FitnessCenter` (or training-specific icon)
- Headline: `feature_all_trainings_empty_headline` ("No trainings yet")
- Supporting: `feature_all_trainings_empty_supporting` ("Tap + to create your first training")

#### Filter behavior

- OR semantics (matches Exercises tab decision).
- State lives in store, resets on tab switch.

### Training detail

Reached from Trainings tab row tap (when not in selection mode).

Layout:

1. `AppTopAppBar` — back arrow + overflow menu (Edit, Archive, Delete permanently if no history).
2. `TrainingHero`:
   - Training name (`headlineSmall`)
   - Tags chips row
   - Description card (`AppCard`) — only if non-empty
3. Section eyebrow `feature_training_detail_exercises` ("Exercises") + count badge.
4. Ordered exercise list — each row is `TrainingExerciseRow`.
5. Section eyebrow `feature_training_detail_past_sessions` ("Past sessions") — only if at least one session exists.
6. Up to 5 past sessions, paged. Each row is `TrainingHistoryRow`.
7. Bottom action bar (sticky):
   - `AppButton.Primary` — "Start session" / "Resume session" depending on whether an active session exists for this training.

#### `TrainingExerciseRow` layout

Same row layout invariant. Line 3 = plan summary for this
`(training_uuid, exercise_uuid)` pair.

```
Leading: type icon (weighted/weightless, accent or warning color)
Line 1: exercise name (with leading position number "1." / "2.")
Line 2: tags chips of the exercise + (no count, no need)
Line 3: plan summary "100×5 · 100×5 · 102.5×5" / "no plan yet" (italic)
Trailing: chevron ›
```

Position number is rendered in line 1 as part of name prefix:
`"1. Bench Press"`. This is simpler than a separate column and
matches the row layout invariant.

Tap row → Exercise detail (Stage 5.2 implementation).

#### `TrainingHistoryRow` layout

Same row invariant, all 3 lines present.

```
Leading: ⊟ session icon (clock or check) in textTertiary color
Line 1: relative date "Today" / "Yesterday" / "3 days ago"
Line 2: training name (the snapshot, not template — see data layer)
Line 3: "5 exercises · 24 sets · 47 min"
Trailing: chevron ›
```

Tap → Past session detail (Stage 5.4 — wired but stub'ed for now).

#### Action: Start / Resume session

`Action.Click.OnStartSessionClick` —
- If active session exists for this training → navigate to Live
  workout (Stage 5.4) with that session.
- If no active session → check for any other active session (across
  any training). If exists → snackbar
  `feature_training_detail_other_session_active` blocking. If not →
  create session, navigate to Live workout.
- Until Stage 5.4 lands, action emits
  `Event.ShowLiveWorkoutPending` snackbar
  ("Live workout coming in next stage").

#### Archive / permanent delete from detail

Overflow menu:
- "Edit" → switches to Edit mode (same screen, mode flip).
- "Archive" → archive flow with snackbar undo. If active session
  exists → archive blocked dialog "Finish active session first".
- "Delete permanently" → only shown if `canPermanentlyDelete = true`
  (no past sessions, no active session). On tap → AppConfirmDialog
  → permanent delete + snackbar + popBack to library.

### Edit training

Reached from FAB (create mode) or Edit overflow on Training detail
(edit mode).

Layout (full-screen scroll):

1. `AppTopAppBar` — × close, title (create or edit variant), no trailing.
2. **Name** (`AppTextField`, single line, required).
3. **Description** (`AppTextField`, multi-line, optional, min 3 rows).
4. **Tags** (`TagPickerInline` from Stage 5.2 — reused).
5. **Exercises (N)** section eyebrow + "+ Add" affordance trailing in eyebrow row.
6. Ordered list of `TrainingExerciseEditRow` — drag-to-reorder.
7. Sticky bottom bar:
   - `AppButton.Tertiary` Cancel
   - `AppButton.Primary` Save (disabled when name blank).

#### `TrainingExerciseEditRow` layout

Two visual rows packed in one container:

```
[Header row]
  [22dp position number] [22dp type icon] [exercise name] [22dp drag handle]
[Plan row]
  [indent 52dp] [plan summary or "no plan yet"]   [edit plan / add plan]
```

- Header row: position + type icon + name + drag handle. Tap on
  drag handle starts drag-to-reorder (long-press anywhere on header
  also works as fallback).
- Plan row: shows current plan inline (same formatting as Training
  detail's `TrainingExerciseRow`). Trailing affordance "edit plan"
  (if plan exists) or "add plan" (if null) is a tappable text
  button in accent color.
- Tap "edit plan" / "add plan" → opens `AppPlanEditor` modal sheet
  for this `(training_uuid, exercise_uuid)`.
- Tap row body (not handle, not affordance) → no-op in v1 (could
  navigate to Exercise detail in v2; for now ambiguous so disabled).

#### Add exercise flow

Tap "+ Add" in Exercises section eyebrow → opens
`ExercisePickerSheet`:

- Modal bottom sheet with:
  - Search field at top
  - List of all non-archived exercises matching query, with
    checkbox on each row
  - Footer buttons: Cancel + "Add (N)"
- Multi-select within picker — user can add multiple at once.
- Selected exercises append at end of training's exercise list with
  the next position numbers, and `plan_sets = null`.

#### Reorder

Drag handle on header row. Long-press anywhere on header also
starts drag (mobile UX nicety). Compose `Modifier.draggable` or
`reorderable` library if available.

#### Save validation

- Name required.
- At least one exercise required (cannot save empty training).
- If validation fails on Save tap → show error inline on first
  failing field. Snackbar fallback for "no exercises" case.

#### Cancel / Discard

Same pattern as Stage 5.2:
- Clean state → close.
- Dirty state → AppDialog "Discard changes?" (Confirm = exit, Dismiss = stay).
- BackHandler conditional on `interceptBack = state.hasUnsavedChanges()`.

### Plan editor sheet (`AppPlanEditor`)

Shared component in `core/ui/kit/components/AppPlanEditor.kt`. Used
by both Edit training and Edit exercise.

Layout:

```
[Drag handle]
[Title — "Bench Press · plan" / "Plan"]
[Subtitle — "Sets pre-fill in Live workout. Tracked sets update this plan."]
[List of set rows]
[+ Add set button]
[Cancel] [Save plan]
```

#### Set row layout

For weighted exercise:
```
[1] [weight: 100kg input] [reps: 5 input] [type chip] [×]
```

For weightless exercise (weight column hidden):
```
[1] [reps: 5 input] [type chip] [×]
```

- Weight input: `AppNumberInput` decimal, label "kg" beneath.
- Reps input: `AppNumberInput` integer, label "reps" beneath.
- Type chip: tappable, opens `AppMenu` with WARMUP/WORK/FAILURE/DROP.
  Default for new sets: WORK.
- × icon: removes set. Confirmation if removing would leave plan
  empty (in which case the entire plan is deleted = null).

#### Add set behavior

Tap "+ Add set" → appends a new set row at the end. Default values:
- If at least one existing set → copy values from previous set, type = WORK.
- If empty plan → empty values, type = WORK.

#### Save behavior

Tap "Save plan" → emits `Action.Click.OnPlanSave(planSets)` to the
parent store. Sheet dismisses. Parent persists to repository
(`trainingExerciseRepo.setPlan` or `exerciseRepo.setAdhocPlan`).

If plan is empty (zero sets) → save as `null` in DB.

#### Cancel behavior

If sheet is dirty (planSets differ from initial) → confirm dialog
"Discard plan changes?". If clean → dismiss immediately.

#### Type change WEIGHTED ↔ WEIGHTLESS protection

If user attempts to switch exercise type while a non-empty plan
exists with weight values, show `AppConfirmDialog` first:

> "Switching to weightless will clear weight values from this
> exercise's plans. Continue?"

If user confirms — clear weights from `last_adhoc_sets` (in case of
exercise edit) AND from all `training_exercise.plan_sets` rows
referencing this exercise. This is a multi-row update — handled in
repository transaction.

Add string keys `feature_exercise_edit_type_change_weightless_title`
and `feature_exercise_edit_type_change_weightless_body`, EN + RU.

This protection applies in **Edit exercise** screen (Stage 5.2
retrofit). Edit training does not change exercise type — exercises
in training inherit their type.

## MVI surface

### AllTrainingsStore

```kotlin
interface AllTrainingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingItems: PagingData<TrainingDataModel>,
        val availableTags: ImmutableList<TagDataModel>,
        val activeTagFilter: ImmutableSet<String>,
        val selectionMode: SelectionMode,
        val isEmpty: Boolean,
    ) : Store.State {
        sealed interface SelectionMode {
            data object Off : SelectionMode
            data class On(
                val selectedUuids: ImmutableSet<String>,
                val canDeleteAll: Boolean,           // true if all selected can be permanent-deleted
            ) : SelectionMode
        }

        val isSelecting: Boolean get() = selectionMode is SelectionMode.On
        val interceptBack: Boolean get() = isSelecting       // exit selection on back
    }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data class OnTrainingClick(val uuid: String) : Click
            data class OnTrainingLongPress(val uuid: String) : Click  // enter selection mode
            data object OnFabClick : Click
            data class OnTagFilterToggle(val tagUuid: String) : Click
            data class OnSelectionToggle(val uuid: String) : Click
            data object OnSelectionExit : Click
            data object OnBulkArchive : Click
            data object OnBulkDelete : Click
            data object OnBulkDeleteConfirm : Click
            data object OnBulkDeleteDismiss : Click
            data object OnBackClick : Click
        }
        sealed interface Navigation : Action {
            data class OpenDetail(val uuid: String) : Navigation
            data object OpenCreate : Navigation
            data object Back : Navigation
        }
        sealed interface Common : Action {
            data object Init : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowBulkArchiveSuccess(val count: Int) : Event
        data class ShowBulkDeleteSuccess(val count: Int) : Event
        data class ShowDeleteConfirm(val count: Int) : Event
    }
}
```

### SingleTrainingStore (detail + edit, Mode flip)

```kotlin
interface SingleTrainingStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val mode: Mode,
        val name: String,
        val nameError: Boolean,
        val description: String,
        val tags: ImmutableList<TagDataModel>,
        val availableTags: ImmutableList<TagDataModel>,
        val tagSearchQuery: String,
        val exercises: ImmutableList<TrainingExerciseItem>,
        val pastSessions: ImmutableList<HistorySessionItem>,
        val activeSession: ActiveSessionInfo?,
        val canPermanentlyDelete: Boolean,
        val originalSnapshot: Snapshot?,
        val planEditorTarget: PlanEditorTarget?,        // non-null = sheet is open
        val isLoading: Boolean,
    ) : Store.State {
        sealed interface Mode {
            data object Read : Mode
            data class Edit(val isCreate: Boolean) : Mode
        }
        data class Snapshot(...)
        data class TrainingExerciseItem(
            val exerciseUuid: String,
            val exerciseName: String,
            val exerciseType: ExerciseTypeDataModel,
            val exerciseTags: ImmutableList<TagDataModel>,
            val position: Int,
            val planSets: ImmutableList<PlanSetDataModel>?,
        )
        data class PlanEditorTarget(
            val exerciseUuid: String,
            val exerciseName: String,
            val exerciseType: ExerciseTypeDataModel,
            val initialPlan: ImmutableList<PlanSetDataModel>?,
        )

        fun hasUnsavedChanges(): Boolean = ...
        val interceptBack: Boolean get() = mode is Mode.Edit && hasUnsavedChanges()
    }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            // Detail-mode clicks
            data object OnEditClick : Click
            data object OnArchiveClick : Click
            data object OnPermanentDeleteClick : Click
            data object OnPermanentDeleteConfirm : Click
            data object OnPermanentDeleteDismiss : Click
            data object OnStartSessionClick : Click
            data class OnExerciseRowClick(val exerciseUuid: String) : Click
            data class OnPastSessionClick(val sessionUuid: String) : Click

            // Edit-mode clicks
            data object OnSaveClick : Click
            data object OnCancelClick : Click
            data object OnConfirmDiscard : Click
            data object OnDismissDiscard : Click
            data object OnAddExerciseClick : Click
            data class OnExerciseRemove(val exerciseUuid: String) : Click
            data class OnExerciseReorder(val from: Int, val to: Int) : Click
            data class OnEditPlanClick(val exerciseUuid: String) : Click
            data object OnPlanEditorDismiss : Click
            data class OnPlanEditorSave(val planSets: ImmutableList<PlanSetDataModel>?) : Click

            // Common
            data object OnBackClick : Click
            data class OnTagAdd(val tagUuid: String) : Click
            data class OnTagRemove(val tagUuid: String) : Click
            data class OnTagCreate(val name: String) : Click
            data class OnExercisesAdd(val exerciseUuids: List<String>) : Click
        }
        sealed interface Input : Action {
            data class OnNameChange(val value: String) : Input
            data class OnDescriptionChange(val value: String) : Input
            data class OnTagSearchChange(val value: String) : Input
        }
        sealed interface Navigation : Action {
            data object Back : Navigation
            data class OpenExerciseDetail(val uuid: String) : Navigation
            data class OpenSession(val sessionUuid: String) : Navigation
            data object OpenLiveWorkout : Navigation
        }
        sealed interface Common : Action {
            data class Init(val uuid: String?) : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowArchiveSuccess(val name: String) : Event
        data class ShowArchiveBlocked(val reason: String) : Event
        data object ShowDiscardConfirmDialog : Event
        data object ShowPermanentDeleteConfirmDialog : Event
        data object ShowExercisePickerSheet : Event
        data object ShowLiveWorkoutPending : Event       // until Stage 5.4
        data class ShowOtherSessionActive(val trainingName: String) : Event
    }
}
```

### Handlers

Standard pattern:
- `ClickHandler` — clicks, emits Events for side effects, emits `Action.Navigation.*` via consume.
- `InputHandler` — text field changes (SingleTraining only).
- `PagingHandler` — paging concerns (AllTrainings only).
- `SelectionHandler` — multi-select state mgmt (AllTrainings only).
- `NavigationHandler` — internal class with `@Inject Navigator`.
- `CommonHandler` — handles `Init` action.

ClickHandler.processOnTrainingLongPress emits HapticClick (medium)
+ enters selection mode with this training pre-selected.

ClickHandler.processOnTrainingClick:
- If `isSelecting` → toggle selection (route to SelectionHandler).
- Else → emit `Action.Navigation.OpenDetail(uuid)`.

When `Action.Navigation.*` is emitted from a background coroutine
(e.g. after repository.archive succeeds), the call site wraps with
`withContext(immediateDispatcher)` per the Navigation flow doc.

## Domain layer

### `AllTrainingsInteractor`

```kotlin
interface AllTrainingsInteractor {
    fun observeTrainings(filterTagUuids: Set<String>): Flow<PagingData<TrainingListItem>>
    fun observeAvailableTags(): Flow<List<TagDataModel>>
    fun observeActiveSessionTrainingUuid(): Flow<String?>
    suspend fun archiveTrainings(uuids: Set<String>): BulkArchiveResult
    suspend fun deleteTrainings(uuids: Set<String>): BulkDeleteResult
    suspend fun canPermanentlyDelete(uuids: Set<String>): Boolean
}

data class TrainingListItem(
    val data: TrainingDataModel,
    val exerciseCount: Int,
    val lastSessionAt: Long?,         // null if never trained
    val isActive: Boolean,             // active session exists for this training
    val activeSessionStartedAt: Long?,
)

sealed interface BulkArchiveResult {
    data class Success(val count: Int) : BulkArchiveResult
    data class PartialBlocked(val blockedNames: List<String>, val archivedCount: Int) : BulkArchiveResult
}

sealed interface BulkDeleteResult {
    data class Success(val count: Int) : BulkDeleteResult
}
```

`observeActiveSessionTrainingUuid` is a hot Flow that combines
session_table observations to detect the IN_PROGRESS session.

`TrainingListItem` is a projection over (training, exercise count,
last session, active session) computed in repository or interactor.

### `SingleTrainingInteractor`

```kotlin
interface SingleTrainingInteractor {
    suspend fun getTraining(uuid: String): TrainingDetail?
    suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<HistorySessionItem>
    fun observeAvailableTags(): Flow<List<TagDataModel>>
    suspend fun searchTags(query: String): List<TagDataModel>
    suspend fun saveTraining(snapshot: TrainingChangeDataModel)
    suspend fun createTag(name: String): TagDataModel
    suspend fun archive(uuid: String): ArchiveResult
    suspend fun permanentlyDelete(uuid: String)
    suspend fun getCurrentActiveSession(trainingUuid: String): SessionDataModel?
    suspend fun getAnyActiveSession(): ActiveSessionInfo?
    suspend fun getPlanForExercise(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>?
    suspend fun setPlanForExercise(trainingUuid: String, exerciseUuid: String, plan: List<PlanSetDataModel>?)
    suspend fun searchExercisesForPicker(query: String, excludeUuids: Set<String>): List<ExerciseDataModel>
}
```

### Plan persistence flow

When user saves plan in `AppPlanEditor`:
1. Sheet emits `Action.Click.OnPlanEditorSave(planSets)` to parent store.
2. Parent ClickHandler updates state (replaces the relevant
   `TrainingExerciseItem.planSets` in `state.exercises`).
3. Parent ClickHandler launches background coroutine, calls
   `interactor.setPlanForExercise(trainingUuid, exerciseUuid, planSets)`.
4. On success: `withContext(immediateDispatcher) { state.set(planEditorTarget = null) }`.
   No navigation needed.

If parent screen is in **edit mode** with unsaved Training-level
changes (e.g. name was changed), the plan is persisted **immediately**
even though the training-level Save button hasn't been tapped. This
is intentional: plans are sub-entities of the training_exercise
relationship, not of the training itself. They have their own
"save" via the editor sheet.

## Data layer additions

Most queries already exist after Stage 4.6. Specifically needed for
this feature:

### `TrainingDao` — extend

```kotlin
@Query("""
    SELECT t.uuid, t.name, t.description, t.is_adhoc, t.archived, t.created_at, t.archived_at,
           (SELECT COUNT(*) FROM training_exercise_table WHERE training_uuid = t.uuid) AS exercise_count,
           (SELECT MAX(s.finished_at) FROM session_table s WHERE s.training_uuid = t.uuid AND s.state = 'FINISHED') AS last_session_at,
           (SELECT s.uuid FROM session_table s WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_uuid,
           (SELECT s.started_at FROM session_table s WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_started_at
    FROM training_table t
    WHERE t.archived = 0 AND t.is_adhoc = 0
    ORDER BY t.name COLLATE NOCASE ASC
""")
fun pagedActiveWithStats(): PagingSource<Int, TrainingListItemRow>
```

For tag-filtered version (OR semantics, mirrors Exercises):

```kotlin
@Query("""
    SELECT t.uuid, t.name, ... (same projection)
    FROM training_table t
    WHERE t.archived = 0 AND t.is_adhoc = 0
      AND EXISTS (
        SELECT 1 FROM training_tag_table tt
        WHERE tt.training_uuid = t.uuid AND tt.tag_uuid IN (:tagUuids)
      )
    ORDER BY t.name COLLATE NOCASE ASC
""")
fun pagedActiveWithStatsByTags(tagUuids: List<Uuid>): PagingSource<Int, TrainingListItemRow>
```

`TrainingListItemRow` is a Room `@Embedded` / projection class
matching the SELECT columns.

### `SessionDao` — observe active session globally

```kotlin
@Query("SELECT uuid, training_uuid FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
fun observeAnyActiveSession(): Flow<ActiveSessionRow?>
```

Used by AllTrainings to mark the active row + by SingleTraining to
detect "other session active" guard.

### Plan persistence

Already exists from Stage 4.6: `TrainingExerciseDao.getPlanSets` /
`updatePlanSets`. SingleTrainingInteractor calls these directly.

For `Edit exercise → last_adhoc_sets` flow (retrofit): use existing
`ExerciseDao.updateLastAdhocSets`.

### Type change weight wipe (retrofit on Edit exercise)

When user confirms type change to WEIGHTLESS for an exercise with
weighted plans, must clear weights from:
- `exercise.last_adhoc_sets` — replace each set's weight with null.
- All `training_exercise.plan_sets` where `exercise_uuid = uuid` —
  same.

This is a multi-row update in a transaction:

```kotlin
@Transaction
suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: Uuid) {
    val adhoc = exerciseDao.getLastAdhocSets(exerciseUuid)
    if (adhoc != null) {
        val cleared = clearWeights(adhoc)
        exerciseDao.updateLastAdhocSets(exerciseUuid, cleared)
    }
    val trainingExercises = trainingExerciseDao.getAllForExercise(exerciseUuid)
    for (te in trainingExercises) {
        if (te.planSets != null) {
            val cleared = clearWeights(te.planSets)
            trainingExerciseDao.updatePlanSets(te.trainingUuid, te.exerciseUuid, cleared)
        }
    }
}
```

`clearWeights` is a pure helper in domain that maps each
`PlanSetDataModel` to a copy with `weight = null`.

## Localization

Strings for `feature/all-trainings/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="feature_all_trainings_title">Trainings</string>
    <string name="feature_all_trainings_empty_headline">No trainings yet</string>
    <string name="feature_all_trainings_empty_supporting">Tap + to create your first training</string>
    <string name="feature_all_trainings_fab_create">Create training</string>

    <string name="feature_all_trainings_status_in_progress_format">in progress · started %1$s ago</string>
    <string name="feature_all_trainings_status_last_format">last: %1$s</string>
    <string name="feature_all_trainings_status_never">never trained</string>
    <string name="feature_all_trainings_active_pill">Active</string>

    <plurals name="feature_all_trainings_exercise_count">
        <item quantity="one">%d exercise</item>
        <item quantity="other">%d exercises</item>
    </plurals>

    <!-- Multi-select -->
    <plurals name="feature_all_trainings_selected_count">
        <item quantity="one">%d selected</item>
        <item quantity="other">%d selected</item>
    </plurals>
    <string name="feature_all_trainings_bulk_archive">Archive</string>
    <string name="feature_all_trainings_bulk_delete">Delete</string>
    <string name="feature_all_trainings_bulk_archive_success_format">%1$d trainings archived</string>
    <string name="feature_all_trainings_bulk_delete_confirm_title">Delete selected permanently?</string>
    <plurals name="feature_all_trainings_bulk_delete_confirm_body">
        <item quantity="one">%d training will be permanently deleted. This cannot be undone.</item>
        <item quantity="other">%d trainings will be permanently deleted. This cannot be undone.</item>
    </plurals>
    <string name="feature_all_trainings_bulk_delete_success_format">%1$d trainings deleted</string>
</resources>
```

RU translation `values-ru/strings.xml`:

```xml
<resources>
    <string name="feature_all_trainings_title">Тренировки</string>
    <string name="feature_all_trainings_empty_headline">Пока нет тренировок</string>
    <string name="feature_all_trainings_empty_supporting">Нажмите +, чтобы создать первую тренировку</string>
    <string name="feature_all_trainings_fab_create">Создать тренировку</string>

    <string name="feature_all_trainings_status_in_progress_format">в процессе · началась %1$s назад</string>
    <string name="feature_all_trainings_status_last_format">последняя: %1$s</string>
    <string name="feature_all_trainings_status_never">ещё не было</string>
    <string name="feature_all_trainings_active_pill">Активна</string>

    <plurals name="feature_all_trainings_exercise_count">
        <item quantity="one">%d упражнение</item>
        <item quantity="few">%d упражнения</item>
        <item quantity="many">%d упражнений</item>
        <item quantity="other">%d упражнения</item>
    </plurals>

    <plurals name="feature_all_trainings_selected_count">
        <item quantity="one">выбрана %d</item>
        <item quantity="few">выбрано %d</item>
        <item quantity="many">выбрано %d</item>
        <item quantity="other">выбрано %d</item>
    </plurals>
    <string name="feature_all_trainings_bulk_archive">В архив</string>
    <string name="feature_all_trainings_bulk_delete">Удалить</string>
    <string name="feature_all_trainings_bulk_archive_success_format">%1$d в архиве</string>
    <string name="feature_all_trainings_bulk_delete_confirm_title">Удалить выбранные навсегда?</string>
    <plurals name="feature_all_trainings_bulk_delete_confirm_body">
        <item quantity="one">%d тренировка будет удалена навсегда. Это нельзя отменить.</item>
        <item quantity="few">%d тренировки будут удалены навсегда. Это нельзя отменить.</item>
        <item quantity="many">%d тренировок будут удалены навсегда. Это нельзя отменить.</item>
        <item quantity="other">%d тренировки будут удалены навсегда. Это нельзя отменить.</item>
    </plurals>
    <string name="feature_all_trainings_bulk_delete_success_format">Удалено: %1$d</string>
</resources>
```

Strings for `feature/single-training/src/main/res/values/strings.xml`:

```xml
<resources>
    <!-- Detail -->
    <string name="feature_training_detail_exercises">Exercises</string>
    <string name="feature_training_detail_past_sessions">Past sessions</string>
    <string name="feature_training_detail_start_session">Start session</string>
    <string name="feature_training_detail_resume_session">Resume session</string>
    <string name="feature_training_detail_no_history">No sessions yet</string>
    <string name="feature_training_detail_archive">Archive</string>
    <string name="feature_training_detail_archive_blocked">Cannot archive — finish active session first</string>
    <string name="feature_training_detail_archive_success_format">'%1$s' archived</string>
    <string name="feature_training_detail_permanent_delete">Delete permanently</string>
    <string name="feature_training_detail_permanent_delete_title">Delete '%1$s' permanently?</string>
    <string name="feature_training_detail_permanent_delete_body">This training has no history. This cannot be undone.</string>
    <string name="feature_training_detail_other_session_active_format">Active session for '%1$s' is in progress. Finish it first.</string>
    <string name="feature_training_detail_session_summary_format">%1$s · %2$s</string>

    <plurals name="feature_training_detail_session_set_count">
        <item quantity="one">%d set</item>
        <item quantity="other">%d sets</item>
    </plurals>

    <!-- Edit -->
    <string name="feature_training_edit_title_create">New training</string>
    <string name="feature_training_edit_title_edit">Edit training</string>
    <string name="feature_training_edit_label_name">Name</string>
    <string name="feature_training_edit_label_description">Description</string>
    <string name="feature_training_edit_placeholder_description">Optional notes — focus, week, etc.</string>
    <string name="feature_training_edit_label_tags">Tags</string>
    <string name="feature_training_edit_label_exercises_format">Exercises (%1$d)</string>
    <string name="feature_training_edit_add_exercise">+ Add</string>
    <string name="feature_training_edit_plan_edit">edit plan</string>
    <string name="feature_training_edit_plan_add">add plan</string>
    <string name="feature_training_edit_no_plan">no plan yet</string>
    <string name="feature_training_edit_error_name_required">Name is required</string>
    <string name="feature_training_edit_error_no_exercises">Add at least one exercise</string>
    <string name="feature_training_edit_discard_title">Discard changes?</string>
    <string name="feature_training_edit_discard_body">Your changes will be lost.</string>
    <string name="feature_training_edit_discard_confirm">Discard</string>
    <string name="feature_training_edit_discard_dismiss">Keep editing</string>

    <!-- Exercise picker -->
    <string name="feature_training_picker_title">Add exercises</string>
    <string name="feature_training_picker_search_placeholder">Search exercises…</string>
    <string name="feature_training_picker_add_format">Add (%1$d)</string>
    <string name="feature_training_picker_cancel">Cancel</string>
</resources>
```

RU mirror with same keys. Translation guidance:

- "Exercises" → "Упражнения", "Past sessions" → "История",
- "Start session" → "Начать сессию", "Resume session" → "Продолжить сессию"
- "Active" → "Активна"
- "edit plan" → "изменить план", "add plan" → "добавить план"
- "no plan yet" → "плана пока нет"
- "Discard changes?" → "Отменить изменения?"
- "Add exercises" → "Добавить упражнения"

## AppPlanEditor strings (core/ui/kit)

```xml
<string name="core_ui_kit_plan_editor_title_format">%1$s · plan</string>
<string name="core_ui_kit_plan_editor_subtitle">Sets pre-fill in Live workout. Tracked sets update this plan.</string>
<string name="core_ui_kit_plan_editor_unit_kg">kg</string>
<string name="core_ui_kit_plan_editor_unit_reps">reps</string>
<string name="core_ui_kit_plan_editor_unit_type">type</string>
<string name="core_ui_kit_plan_editor_add_set">+ Add set</string>
<string name="core_ui_kit_plan_editor_save">Save plan</string>
<string name="core_ui_kit_plan_editor_cancel">Cancel</string>
<string name="core_ui_kit_plan_editor_discard_title">Discard plan changes?</string>
<string name="core_ui_kit_plan_editor_set_type_warmup">Warmup</string>
<string name="core_ui_kit_plan_editor_set_type_work">Work</string>
<string name="core_ui_kit_plan_editor_set_type_failure">Failure</string>
<string name="core_ui_kit_plan_editor_set_type_drop">Drop</string>
```

RU translation:

```xml
<string name="core_ui_kit_plan_editor_title_format">%1$s · план</string>
<string name="core_ui_kit_plan_editor_subtitle">Сеты pre-fill\u0027ятся в Live-тренировке. Записанные сеты обновляют план.</string>
<string name="core_ui_kit_plan_editor_unit_kg">кг</string>
<string name="core_ui_kit_plan_editor_unit_reps">повт</string>
<string name="core_ui_kit_plan_editor_unit_type">тип</string>
<string name="core_ui_kit_plan_editor_add_set">+ Добавить сет</string>
<string name="core_ui_kit_plan_editor_save">Сохранить план</string>
<string name="core_ui_kit_plan_editor_cancel">Отмена</string>
<string name="core_ui_kit_plan_editor_discard_title">Отменить изменения плана?</string>
<string name="core_ui_kit_plan_editor_set_type_warmup">Разминка</string>
<string name="core_ui_kit_plan_editor_set_type_work">Рабочий</string>
<string name="core_ui_kit_plan_editor_set_type_failure">Отказ</string>
<string name="core_ui_kit_plan_editor_set_type_drop">Дроп</string>
```

For Exercise type-change retrofit (Stage 5.2 retrofit):

EN:
```xml
<string name="feature_exercise_edit_type_change_weightless_title">Switch to weightless?</string>
<string name="feature_exercise_edit_type_change_weightless_body">Weight values from this exercise's plans will be cleared. This cannot be undone.</string>
<string name="feature_exercise_edit_type_change_weightless_confirm">Switch</string>
```

RU:
```xml
<string name="feature_exercise_edit_type_change_weightless_title">Переключить на без веса?</string>
<string name="feature_exercise_edit_type_change_weightless_body">Значения веса из планов этого упражнения будут очищены. Это нельзя отменить.</string>
<string name="feature_exercise_edit_type_change_weightless_confirm">Переключить</string>
```

For Exercise edit screen — plan editor entry:

EN:
```xml
<string name="feature_exercise_edit_label_default_plan">Default plan (ad-hoc)</string>
<string name="feature_exercise_edit_default_plan_subtitle">Used when tracking this exercise outside any training</string>
<string name="feature_exercise_edit_plan_edit">Edit plan</string>
<string name="feature_exercise_edit_plan_add">+ Add plan</string>
```

RU:
```xml
<string name="feature_exercise_edit_label_default_plan">План по умолчанию (отдельно)</string>
<string name="feature_exercise_edit_default_plan_subtitle">Используется при отдельном трекинге упражнения</string>
<string name="feature_exercise_edit_plan_edit">Изменить план</string>
<string name="feature_exercise_edit_plan_add">+ Добавить план</string>
```

For all-exercises multi-select retrofit:

EN:
```xml
<plurals name="feature_all_exercises_selected_count">
    <item quantity="one">%d selected</item>
    <item quantity="other">%d selected</item>
</plurals>
<string name="feature_all_exercises_bulk_archive">Archive</string>
<string name="feature_all_exercises_bulk_delete">Delete</string>
<string name="feature_all_exercises_bulk_archive_success_format">%1$d exercises archived</string>
<string name="feature_all_exercises_bulk_delete_confirm_title">Delete selected permanently?</string>
<plurals name="feature_all_exercises_bulk_delete_confirm_body">
    <item quantity="one">%d exercise will be permanently deleted. This cannot be undone.</item>
    <item quantity="other">%d exercises will be permanently deleted. This cannot be undone.</item>
</plurals>
```

RU:
```xml
<plurals name="feature_all_exercises_selected_count">
    <item quantity="one">выбрано %d</item>
    <item quantity="few">выбрано %d</item>
    <item quantity="many">выбрано %d</item>
    <item quantity="other">выбрано %d</item>
</plurals>
<string name="feature_all_exercises_bulk_archive">В архив</string>
<string name="feature_all_exercises_bulk_delete">Удалить</string>
<string name="feature_all_exercises_bulk_archive_success_format">%1$d в архиве</string>
<string name="feature_all_exercises_bulk_delete_confirm_title">Удалить выбранные навсегда?</string>
<plurals name="feature_all_exercises_bulk_delete_confirm_body">
    <item quantity="one">%d упражнение будет удалено навсегда. Это нельзя отменить.</item>
    <item quantity="few">%d упражнения будут удалены навсегда. Это нельзя отменить.</item>
    <item quantity="many">%d упражнений будут удалены навсегда. Это нельзя отменить.</item>
    <item quantity="other">%d упражнения будут удалены навсегда. Это нельзя отменить.</item>
</plurals>
```

## Navigation

`Screen.Training(uuid: String?)` — uuid null = create mode, non-null
= existing training. Already exists in core/ui/navigation.

## Edge cases and decisions

- **Active session visibility.** `observeAnyActiveSession()` is a
  Flow that's hot — driver of UI updates. If user starts a session
  in another flow (Live workout), the AllTrainings tab updates
  reactively. Same for SingleTraining detail (active session info).
- **Bulk delete partial failure.** Bulk delete is wrapped in a
  single transaction. If any row fails (FK violation), entire
  transaction rolls back, error snackbar shown. We don't try to
  partial-succeed.
- **Bulk archive blocked.** If any selected training has an active
  session → archive that one is blocked. Other archive succeeds.
  Result is `BulkArchiveResult.PartialBlocked` with names list.
  Snackbar: "Archived %1$d, blocked: %2$s".
- **Empty plan vs null plan.** UI treats empty list and null
  identically: shows "no plan yet" italic. Internal storage prefers
  null.
- **Drag-to-reorder while editing plan.** Plan editor is modal — no
  reorder happens while sheet open. After dismiss, user is back in
  Edit training and can reorder normally.
- **Save Edit training without persisting individual plans.** Plan
  saves are individual (sheet emits Save plan → persists immediately).
  Training-level Save (name, description, tags, exercises list,
  positions) is independent. If user adds a plan but cancels overall
  Edit training (discard), the plan persistence already happened
  and is preserved. This is intentional — plans are sub-entities,
  not part of the training transaction.
- **Switching exercise type with plan data.** Confirmation dialog
  + transactional weight wipe across all plan storage. Rare
  operation; protective UX is worth the friction.

## Testing

Unit tests:

- `AllTrainingsClickHandlerTest`, including selection mode toggles
  and bulk action paths.
- `AllTrainingsSelectionHandlerTest` — entry, toggle, exit, bulk-archive,
  bulk-delete.
- `AllTrainingsPagingHandlerTest`, `AllTrainingsCommonHandlerTest`,
  `AllTrainingsNavigationHandlerTest`.
- `AllTrainingsInteractorImplTest`.
- `SingleTrainingClickHandlerTest`, `SingleTrainingInputHandlerTest`,
  `SingleTrainingCommonHandlerTest`, `SingleTrainingNavigationHandlerTest`.
- `SingleTrainingInteractorImplTest`.
- DAO tests for `pagedActiveWithStats`, `pagedActiveWithStatsByTags`,
  `observeAnyActiveSession`.
- `clearWeightsFromAllPlansForExerciseTest` (DAO + interactor).
- Component tests for `AppPlanEditor` — value entry, type chip,
  add/remove, save/cancel.
- Plan editor parametric tests for weighted vs weightless layout.

UI: `@Smoke` stubs with `TODO(feature-rewrite-tests)`.

Compose previews:
- Every public/internal Composable in feature/all-trainings,
  feature/single-training, feature/exercise (retrofit), and
  AppPlanEditor in core/ui/kit must have at least one `@Preview`,
  in both light and dark themes, with realistic stub data.
- `AppPlanEditor` previews include weighted, weightless, and
  populated/empty states.

## Stage 5.3 deliverables checklist

- [ ] `feature/all-trainings` rewritten per spec.
- [ ] `feature/single-training` rewritten per spec.
- [ ] Multi-select retrofit on `feature/all-exercises`.
- [ ] Plan editor + last_adhoc_sets retrofit on `feature/exercise`.
- [ ] Type change confirmation retrofit on `feature/exercise`.
- [ ] `core/ui/kit/components/AppPlanEditor.kt` new shared component.
- [ ] DAO additions: `pagedActiveWithStats`, `pagedActiveWithStatsByTags`,
      `observeAnyActiveSession`.
- [ ] Interactor + repository additions per spec.
- [ ] All EN + RU strings per spec.
- [ ] Canonical NavigationHandler pattern, conditional BackHandler
      via `interceptBack` derived flag.
- [ ] Haptics emitted for every Click action.
- [ ] Composable @Previews for every public/internal Composable
      including AppPlanEditor (weighted, weightless, populated, empty).
- [ ] Unit tests for handlers, interactors, new DAO queries, plan
      editor logic.
- [ ] Smoke UI test stubs.

---

## Claude Code prompt

Run after this spec is approved.

```
Implement Stage 5.3 — Trainings feature with multi-select retrofit on Exercises and plan editor on Exercises — per documentation/feature-specs/trainings.md.

CONTEXT
This is the third v1 feature implementation, after Settings + Archive (Stage 5.1) and Exercises (Stage 5.2). It also retrofits Exercises feature with multi-select and plan editor.

Read in order:
- documentation/product.md
- documentation/ux-architecture.md
- documentation/data-needs.md
- documentation/db-redesign.md
- documentation/db-redesign-plan-model.md
- documentation/design-system.md
- documentation/architecture.md (Navigation flow, Localization, Back gesture handling)
- documentation/feature-specs/settings-archive.md (reference pattern)
- documentation/feature-specs/exercises.md (reference pattern + retrofit target)
- documentation/feature-specs/trainings.md (this feature)
- .claude/skills/add-feature.md

THIS IS A SINGLE PASS. Implementation, verification, draft PR opened. No STOP gate.

PROCESS

1. Existing modules to rewrite:
   - feature/all-trainings (Trainings library tab)
   - feature/single-training (Training detail + edit)

2. Existing modules to retrofit (in same PR):
   - feature/all-exercises — add multi-select mode
   - feature/exercise — add plan editor for last_adhoc_sets, add type-change confirmation

3. New shared component in core/ui/kit:
   - AppPlanEditor — modal bottom sheet for editing a list of PlanSetDataModel.
   - Used by Edit training (per training_exercise) and Edit exercise (for last_adhoc_sets).
   - Strings live in core/ui/kit res files.

4. DAO additions per spec — pagedActiveWithStats, pagedActiveWithStatsByTags, observeAnyActiveSession.

5. Repository extensions:
   - TrainingRepository: bulk archive/delete, pagedActiveWithStats wrapper.
   - SessionRepository (or SessionDao): observeAnyActiveSession Flow.
   - clearWeightsFromAllPlansForExercise transactional helper.

6. Domain models:
   - TrainingListItem (data model + exerciseCount + lastSessionAt + isActive)
   - HistorySessionItem
   - ActiveSessionInfo
   - BulkArchiveResult, BulkDeleteResult sealed interfaces

7. Implement features per spec exactly:
   - State, Action, Event surfaces per spec
   - Handlers: Click, Input, Paging, Selection, Navigation, Common
   - All UI uses core/ui/kit components and AppUi tokens. No raw Color/dp/sp outside core/ui/kit/theme/.
   - Row layout invariant strictly applied: 3 lines (name + tags+meta + plan/status), 16dp vertical padding, single-line ellipsis on each.

8. Canonical navigation pattern (mandatory):
   - NavigationHandler: separate class with @Inject Navigator (Hilt), implements Handler<Action.Navigation>.
   - Graph composables consume ONLY UI events, never read LocalNavigator.
   - Reference: feature/all-trainings/.../mvi/handler/NavigationHandler.kt (existing — Stage 4.5 reference) and feature/all-exercises/...NavigationHandler.kt (Stage 5.2 reference).

9. Back gesture handling per architecture.md "Back gesture handling":
   - Computed `interceptBack: Boolean` on State.
   - BackHandler(enabled = state.interceptBack) — conditional, not always-on.
   - Top-bar back arrow + Cancel button always emit Click.OnBackClick.
   - In multi-select mode, interceptBack = isSelecting (back exits selection, not screen).
   - In Edit mode with dirty form, interceptBack = hasUnsavedChanges.

10. Haptics:
    - Every Click action that produces state change OR opens screen/dialog/sheet OR triggers navigation MUST emit Event.HapticClick.
    - Light haptic for normal clicks. Medium for destructive confirms (bulk delete, type change).
    - Long-press to enter selection mode emits medium haptic.
    - Cancel/dismiss in dialogs do NOT emit haptic.

11. Plan persistence flow (in SingleTraining and ExerciseStore retrofit):
    - User taps "edit plan" → emits OnEditPlanClick(exerciseUuid) → store sets state.planEditorTarget.
    - Sheet renders if planEditorTarget != null. AppPlanEditor consumes a slot and emits OnPlanEditorSave(planSets) when user saves.
    - Parent ClickHandler immediately persists via interactor (background coroutine, withContext(immediateDispatcher) for state update on success).
    - This is independent of Training-level Save/Cancel — plans persist immediately, not buffered to OnSaveClick.

12. Multi-select retrofit on feature/all-exercises:
    - Mirror the AllTrainings selection pattern.
    - Selection state in AllExercisesStore.State — same shape (SelectionMode sealed Off/On).
    - Handler additions for OnLongPress, OnSelectionToggle, OnSelectionExit, OnBulkArchive, OnBulkDelete.
    - Top-bar in selection mode replaced by SelectionTopBar inline component (mirror what's done in feature/all-trainings).
    - BulkActionBar above bottom navigation in selection mode.
    - Same EN+RU strings per spec.

13. Plan editor retrofit on feature/exercise:
    - Edit exercise screen gets new section "Default plan (ad-hoc)" with subtitle + "Edit plan" / "+ Add plan" affordance.
    - Tap → opens AppPlanEditor with last_adhoc_sets as input.
    - Save → exerciseRepo.setAdhocPlan(uuid, planSets).
    - Edit exercise type-change WEIGHTED→WEIGHTLESS protection: if plan has weights, show AppConfirmDialog. Confirm → call clearWeightsFromAllPlansForExercise transaction.

14. Tests (unit + smoke per spec).

15. Composable previews per the .claude/skills rule:
    - Every public/internal @Composable has at least one @Preview.
    - AppPlanEditor previews cover: weighted populated, weighted empty, weightless populated, weightless empty, type chip dropdown open.

VERIFICATION

- ./gradlew :feature:all-trainings:assembleDebug detekt lintDebug passes.
- ./gradlew :feature:single-training:assembleDebug detekt lintDebug passes.
- ./gradlew :feature:all-exercises:assembleDebug detekt lintDebug passes.
- ./gradlew :feature:exercise:assembleDebug detekt lintDebug passes.
- ./gradlew :core:ui:kit:assembleDebug detekt lintDebug passes.
- ./gradlew assembleDebug passes (whole project).
- ./gradlew testDebugUnitTest passes.
- App launches.
  - Trainings tab loads, FAB creates.
  - Long-press → selection mode → bulk archive works → snackbar.
  - Tap training → detail. Plans visible inline per exercise.
  - Edit → form, exercise list with inline plans + drag handles. Tap "edit plan" opens sheet, save persists.
  - Add exercises via picker sheet.
  - Save training updates list, Cancel with discard dialog.
  - System back gesture in Edit mode: predictive back works in clean state, intercepted in dirty.
  - In Russian locale, all strings translated.
- Exercises tab also has multi-select via long-press.
- Edit exercise has Default plan section with sheet entry.
- Switching exercise type from WEIGHTED to WEIGHTLESS with weighted plans shows confirm dialog.

CONSTRAINTS

- Phantom shims unrelated to Trainings rewrite stay.
- LICENSE / SPDX headers: SPDX-License-Identifier: GPL-3.0-only on every new file.
- Do not touch feature/charts (deferred to v2).
- Do not implement Live workout (Stage 5.4) — only stub the navigation entry from Start session button.

PR

Open a draft PR titled `feat(trainings): rewrite Trainings + retrofit Exercises with plan editor and multi-select (Stage 5.3)`. Body lists changed files grouped by module + brief summary of what was added per module. Mark ready for review after the verification gate passes.
```
