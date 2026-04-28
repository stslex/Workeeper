# Feature spec — Exercises

**Status:** Merged in Stage 5.2 (PR #59). For current architecture, see [architecture.md](../architecture.md). This spec is preserved as a historical record of the planning state.

This is the Stage 5.2 feature spec — second v1 feature implementation
after Settings + Archive. It builds on:

- [product.md](../product.md)
- [ux-architecture.md](../ux-architecture.md) — Exercises tab,
  Exercise detail, Edit exercise sections
- [data-needs.md](../data-needs.md)
- [db-redesign.md](../db-redesign.md)
- [design-system.md](../design-system.md)
- [architecture.md](../architecture.md) — Navigation flow, Localization

## Scope

Three screens:

- **Exercises tab** (library) — paged list, FAB create, tag filter,
  archive flow.
- **Exercise detail** — name, type, tags, description, recent
  history, Track now CTA, edit/archive.
- **Edit exercise** — form: name, type toggle, tags, description.
  No image (deferred to v1.5).

The existing `feature/exercise` and `feature/all-exercises` modules
were created in earlier development. This spec **rewrites them**
under v3 schema and v1 design system. Phantom shims in core/exercise
(`trainingUuid: Uuid?`, etc.) are removed during this rewrite.

## Module structure

Two modules. They mirror the existing split.

```
feature/all-exercises/             — the library tab
  src/main/kotlin/.../
    di/AllExercisesModule.kt + HandlerStore + Processor
    domain/AllExercisesInteractor[Impl].kt
    ui/
      AllExercisesGraph.kt         — NavGraphBuilder extension
      AllExercisesScreen.kt        — top-level Composable
      components/
        ExerciseRow.kt             — single library row
        ExercisesEmptyState.kt
        TagFilterRow.kt            — horizontal scrollable filter chips
      mvi/
        store/AllExercisesStore[Impl].kt
        handler/
          ClickHandler.kt
          PagingHandler.kt
          NavigationHandler.kt
          AllExercisesComponent.kt
  src/main/res/
    values/strings.xml
    values-ru/strings.xml

feature/exercise/                  — detail + edit
  src/main/kotlin/.../
    di/ExerciseModule.kt + HandlerStore + Processor + EntryPoint
    domain/ExerciseInteractor[Impl].kt
    ui/
      ExerciseGraph.kt
      ExerciseDetailScreen.kt      — read view
      ExerciseEditScreen.kt        — form view
      components/
        ExerciseHero.kt            — placeholder for v1.5 image
        ExerciseHistoryRow.kt
        TypeToggle.kt              — 2-option big toggle for weighted/weightless
        TagPickerInline.kt         — wraps AppTagPicker for the form
      mvi/
        store/ExerciseStore[Impl].kt
        handler/
          ClickHandler.kt
          InputHandler.kt          — form field changes
          NavigationHandler.kt
          CommonHandler.kt         — Init load
          ExerciseComponent.kt
  src/main/res/
    values/strings.xml
    values-ru/strings.xml
```

The detail and edit views share state and ViewModel (single
`ExerciseStore`) but render different Composables based on a state
field (`mode: Mode.Read | Mode.Edit`). When the user taps Edit on
detail, `Action.Click.OnEditClick` flips state to `Mode.Edit`. When
they Save, it flips back to `Mode.Read` after persistence.

This avoids splitting one entity's screen into two ViewModels and
keeps the detail-edit transition local.

## Screens

### Exercises tab

Top-level screen for the bottom-bar tab. Layout top to bottom:

1. `AppTopAppBar` — title `feature_all_exercises_title` ("Exercises"
   / "Упражнения"). No trailing actions in v1 (filter is inline
   below).
2. `TagFilterRow` — horizontal scrollable row of `AppTagChip.Selectable`
   chips. Tapping toggles inclusion in the active filter set. Empty
   state when no tags exist yet — row is hidden.
3. Paged exercise list — each row is `ExerciseRow`.
4. `AppFAB` (icon Add) — bottom-right, navigates to Edit exercise in
   create mode.

#### `ExerciseRow` layout

```
[type icon | name (bodyMedium) | trailing chevron]
            [tag chips inline]
            [N sessions (bodySmall, textTertiary)]
```

- Type icon: `Icons.Filled.FitnessCenter` for weighted (accent
  color), `Icons.Filled.AccessibilityNew` for weightless (warning
  color, set type warmup amber). 28dp leading box with surface tier
  4 background and AppShapes.small corners.
- Name: `bodyMedium` weight 500, primary text.
- Tag chips: up to 3 inline. Overflow shown as "+N" chip if more.
- Session count: pluralized via `feature_all_exercises_session_count`.
- Tap row → Exercise detail.
- Swipe-from-end → AppSwipeAction with archive icon. Tap reveals
  archive action which calls archive on the row's exercise. If the
  archive is blocked (exercise used in active templates), shows
  snackbar with the message
  `feature_all_exercises_archive_blocked` and the list of training
  names truncated to first 2 with "+N more".

#### Empty state

`AppEmptyState`:

- Icon: `Icons.Filled.FitnessCenter` (large)
- Headline: `feature_all_exercises_empty_headline` ("No exercises
  yet" / "Пока нет упражнений")
- Supporting: `feature_all_exercises_empty_supporting` ("Tap + to
  create your first exercise" / "Нажмите +, чтобы создать первое
  упражнение")

#### Filter behavior

- Selecting one or more tag chips filters the list to exercises
  containing **all** selected tags (intersection / AND semantics).
  This closes the v1 open question from product.md "Tag filter
  semantics" — picking AND because it's the more useful default for
  a small tag pool.
- Filter state lives in store, not URL. Switching tabs and coming
  back resets to no filter (this is acceptable v1 behavior; can be
  reconsidered later).

### Exercise detail

Reached from Exercises tab row tap, or from Training detail's
exercise list.

Layout:

1. `AppTopAppBar` — back arrow + overflow menu (Edit, Archive).
2. `ExerciseHero` — placeholder card 36dp hero icon for v1; reserved
   for image in v1.5.
3. Type chip (`feature_exercise_detail_type_weighted` /
   `feature_exercise_detail_type_weightless`).
4. Exercise name (`headlineSmall`).
5. Tag chips (`AppTagChip.Static` row).
6. Description card (`AppCard`) — body text inside, only shown if
   description is non-empty.
7. Section eyebrow `feature_exercise_detail_recent` ("Recent" /
   "Последнее").
8. Up to 5 history rows (`ExerciseHistoryRow`). Tap → Past session
   detail (deferred — link is wired but Past session screen comes
   in Stage 5.4).
9. Bottom action bar (sticky):
   - `AppButton.Primary` "Track now" (full width minus icon button)
   - `AppButton.Secondary` icon-only Edit button (navigates to Edit)

#### `ExerciseHistoryRow` layout

```
[set summary "100 × 5 · 100 × 5 · 102.5 × 4" (bodyMedium, tabularNumbers)]
[meta "Today · Push Day" (bodySmall, textTertiary)]
```

Set summary shows up to 5 sets joined by `·`. If more, append "…".
Format weight without trailing zeros (100 not 100.0; 102.5 keeps
the decimal). Reps as integer.

#### Track now action

`Action.Click.OnTrackNow` — creates an ad-hoc training with this
exercise as the single member, starts a session, navigates to Live
workout. The ad-hoc training is named per
`feature_exercise_detail_adhoc_training_format` ("%1$s session" /
"Сессия: %1$s") with the exercise name.

This action depends on Live workout existing (Stage 5.4). For
Stage 5.2 the action is wired but until Live workout exists, the
button shows a snackbar `feature_exercise_detail_track_now_pending`
("Live workout coming in next stage" / "Live-тренировка появится в
следующем обновлении"). Track now becomes fully functional when
Stage 5.4 lands.

#### Archive action

Overflow menu → "Archive". If the exercise is used in active
templates: show `AppDialog` (not `AppConfirmDialog`) explaining
which templates use it, with a single "OK" action. Body text:
`feature_exercise_detail_archive_blocked_body` with format args for
training names.

If not used: archive immediately, show snackbar
`feature_exercise_detail_archive_success` with "Undo" action.

### Edit exercise

Reached from FAB on Exercises tab (create mode) or from
Edit overflow on Exercise detail (edit mode).

Layout (full screen scroll):

1. `AppTopAppBar` — close icon × on left, title (create or edit
   variant), no right action.
2. Form fields stacked, with eyebrow labels in `labelSmall` uppercase:
   - **Name** (`AppTextField`, single line, required)
   - **Type** (`TypeToggle` — two `AppButton.Secondary` variants
     side by side, one selected styled with accent border + tinted
     background)
   - **Tags** (`TagPickerInline` — wraps AppTagPicker)
   - **Description** (`AppTextField` multi-line, optional, min 3 rows)
3. Sticky bottom bar:
   - `AppButton.Tertiary` Cancel
   - `AppButton.Primary` Save (disabled when name is blank)

#### TypeToggle component

Two `AppButton.Secondary`s in a row with `weight = 1f` each. Selected
option uses accent border + accent tinted background + accent text
(via `AppButton` variant override or local styling). Unselected stays
default secondary.

Choice between `WEIGHTED` and `WEIGHTLESS` (using
`ExerciseTypeEntity` / `ExerciseTypeDataModel` in domain).

#### TagPickerInline component

Wraps `AppTagPicker` from kit. Behavior:

- Shows currently-selected tags as `AppTagChip.Removable` chips at top.
- Search field below chips, filters available tags by prefix.
- When search text doesn't match any existing tag, shows
  `+ Create '<input>'` affordance with `feature_exercise_edit_tag_create_format`.
- Tapping an existing tag adds it; tapping create makes a new one
  via `TagRepository.create(name)` then adds.
- Maximum 10 tags per exercise (validation; if user tries to add
  11th, snackbar `feature_exercise_edit_tag_limit`).

#### Save validation

- Name required (non-blank, trimmed).
- Type required (toggle is initialized to `WEIGHTED` so always set).
- Tags optional, max 10.
- Description optional, max 2000 chars.

If Save is tapped with blank name → field shows error state with
`feature_exercise_edit_error_name_required`. No snackbar.

#### Cancel behavior

- If form is unchanged from initial state (or empty in create mode)
  — close immediately.
- If form has changes — show `AppDialog` confirmation with title
  `feature_exercise_edit_discard_title` ("Discard changes?" /
  "Отменить изменения?"), confirm "Discard" / "Отменить", dismiss
  "Keep editing" / "Продолжить".

## MVI surface

### AllExercisesStore

```kotlin
interface AllExercisesStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingItems: PagingData<ExerciseDataModel>,
        val availableTags: ImmutableList<TagDataModel>,
        val activeTagFilter: ImmutableSet<String>,        // tag UUIDs
        val isEmpty: Boolean,
    ) : Store.State

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data class OnExerciseClick(val uuid: String) : Click
            data object OnFabClick : Click
            data class OnTagFilterToggle(val tagUuid: String) : Click
            data class OnArchiveSwipe(val uuid: String) : Click
            data class OnArchiveBlockedDismiss(val uuid: String) : Click
            data class OnUndoArchive(val uuid: String) : Click
        }
        sealed interface Navigation : Action {
            data class OpenDetail(val uuid: String) : Navigation
            data object OpenCreate : Navigation
        }
        sealed interface Common : Action {
            data object Init : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowArchiveSuccess(val name: String) : Event
        data class ShowArchiveBlocked(val trainings: List<String>) : Event
    }
}
```

### ExerciseStore (detail + edit)

```kotlin
interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val mode: Mode,
        val name: String,
        val nameError: Boolean,
        val type: ExerciseTypeDataModel,
        val description: String,
        val tags: ImmutableList<TagDataModel>,
        val availableTags: ImmutableList<TagDataModel>,
        val tagSearchQuery: String,
        val recentHistory: ImmutableList<HistoryEntry>,
        val originalSnapshot: Snapshot?,         // for change detection in edit mode
        val isLoading: Boolean,
    ) : Store.State {
        sealed interface Mode {
            data object Read : Mode               // detail screen
            data class Edit(val isCreate: Boolean) : Mode
        }
        data class Snapshot(...)                  // captures fields for diff
    }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data object OnEditClick : Click
            data object OnTrackNowClick : Click
            data object OnArchiveClick : Click
            data class OnUndoArchive(val uuid: String) : Click
            data object OnSaveClick : Click
            data object OnCancelClick : Click
            data object OnConfirmDiscard : Click
            data object OnDismissDiscard : Click
            data class OnTypeSelect(val type: ExerciseTypeDataModel) : Click
            data class OnTagAdd(val tagUuid: String) : Click
            data class OnTagRemove(val tagUuid: String) : Click
            data class OnTagCreate(val name: String) : Click
            data class OnHistoryRowClick(val sessionUuid: String) : Click
        }
        sealed interface Input : Action {
            data class OnNameChange(val value: String) : Input
            data class OnDescriptionChange(val value: String) : Input
            data class OnTagSearchChange(val value: String) : Input
        }
        sealed interface Navigation : Action {
            data object Back : Navigation
            data class OpenSession(val sessionUuid: String) : Navigation
            data object OpenLiveWorkout : Navigation     // for ad-hoc Track now
        }
        sealed interface Common : Action {
            data class Init(val uuid: String?) : Common  // null = create mode
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowArchiveSuccess(val name: String) : Event
        data class ShowArchiveBlocked(val trainings: List<String>) : Event
        data object ShowTagLimitReached : Event
        data object ShowTrackNowPending : Event
        data object ShowDiscardConfirmDialog : Event
    }
}
```

### Handlers

Settings/Archive pattern is the reference. Each store has:

- `ClickHandler` — pure UI clicks, emits Events for side effects,
  emits `Action.Navigation.*` via `consume` for navigation.
- `InputHandler` — text field changes (only for ExerciseStore).
- `PagingHandler` — paging concerns (only for AllExercisesStore).
- `NavigationHandler` — internal class with `@Inject Navigator`,
  `Handler<Action.Navigation>`, calls `navigator.navTo(...)` /
  `navigator.popBack()`.
- `CommonHandler` — handles `Init` action (load data).

Reference: `feature/all-trainings/.../mvi/handler/NavigationHandler.kt`.

The graph composables consume only UI events (Haptic, snackbar
events, ShowDiscardConfirmDialog). They do NOT read `LocalNavigator`.

## Domain layer

### `AllExercisesInteractor`

```kotlin
interface AllExercisesInteractor {
    fun observeExercises(filterTagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>
    fun observeAvailableTags(): Flow<List<TagDataModel>>
    suspend fun archiveExercise(uuid: String): ArchiveResult
    suspend fun restoreExercise(uuid: String)
}

sealed interface ArchiveResult {
    data object Success : ArchiveResult
    data class Blocked(val activeTrainings: List<String>) : ArchiveResult
}
```

`observeExercises` reads from the new Room query that supports tag
intersection — see Data layer additions below.

### `ExerciseInteractor`

```kotlin
interface ExerciseInteractor {
    suspend fun getExercise(uuid: String): ExerciseDataModel?
    suspend fun getRecentHistory(exerciseUuid: String, limit: Int = 5): List<HistoryEntry>
    fun observeAvailableTags(): Flow<List<TagDataModel>>
    suspend fun searchTags(query: String): List<TagDataModel>
    suspend fun saveExercise(snapshot: ExerciseChangeDataModel)
    suspend fun createTag(name: String): TagDataModel
    suspend fun archive(uuid: String): ArchiveResult
}
```

`getRecentHistory` queries last N finished sessions containing this
exercise, with their performed_exercise sets.

## Data layer additions

### Repository

`ExerciseRepository`: add filter-aware paging:

```kotlin
fun pagedActiveByTags(tagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>
```

If `tagUuids.isEmpty()`, falls back to existing `pagedActive()`.
Otherwise calls a new DAO query.

### DAO query for tag intersection

The existing DAO has `pagedActiveByTags` taking a list, but it
returns exercises matching ANY tag (OR). We need ALL (AND). New
query:

```kotlin
@Query("""
    SELECT e.* FROM exercise_table e
    WHERE e.archived = 0
      AND (
        SELECT COUNT(DISTINCT et.tag_uuid)
        FROM exercise_tag_table et
        WHERE et.exercise_uuid = e.uuid AND et.tag_uuid IN (:tagUuids)
      ) = :tagCount
    ORDER BY e.name COLLATE NOCASE ASC
""")
fun pagedActiveByAllTags(tagUuids: List<Uuid>, tagCount: Int): PagingSource<Int, ExerciseEntity>
```

`tagCount` is `tagUuids.size`. The subquery counts how many of the
filter tags this exercise has — if equal to filter size, it has all
of them.

### History query

`SetDao` (or new `SessionDao` query):

```kotlin
@Query("""
    SELECT s.uuid AS session_uuid,
           s.finished_at,
           t.name AS training_name,
           t.is_adhoc
    FROM session_table s
    JOIN training_table t ON t.uuid = s.training_uuid
    JOIN performed_exercise_table pe ON pe.session_uuid = s.uuid
    WHERE pe.exercise_uuid = :exerciseUuid AND s.state = 'FINISHED'
    GROUP BY s.uuid
    ORDER BY s.finished_at DESC
    LIMIT :limit
""")
suspend fun getRecentSessionsForExercise(
    exerciseUuid: Uuid,
    limit: Int,
): List<SessionHistoryRow>
```

Then for each session, `SetDao` already has methods to load sets per
performed_exercise. The interactor composes the two.

### `HistoryEntry` data model

```kotlin
data class HistoryEntry(
    val sessionUuid: String,
    val finishedAt: Long,
    val trainingName: String,
    val isAdhoc: Boolean,
    val sets: List<SetSummary>,
)

data class SetSummary(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDataModel,
)
```

## Localization

EN strings file `feature/all-exercises/src/main/res/values/strings.xml`:

```xml
<resources>
    <!-- All exercises tab -->
    <string name="feature_all_exercises_title">Exercises</string>
    <string name="feature_all_exercises_empty_headline">No exercises yet</string>
    <string name="feature_all_exercises_empty_supporting">Tap + to create your first exercise</string>
    <string name="feature_all_exercises_fab_create">Create exercise</string>
    <string name="feature_all_exercises_archive_success">Exercise archived</string>
    <string name="feature_all_exercises_archive_undo">Undo</string>
    <string name="feature_all_exercises_archive_blocked_format">Cannot archive — used in: %1$s</string>
    <string name="feature_all_exercises_session_count_more">+%1$d more</string>

    <plurals name="feature_all_exercises_session_count">
        <item quantity="one">%d session</item>
        <item quantity="other">%d sessions</item>
    </plurals>
</resources>
```

RU strings file `feature/all-exercises/src/main/res/values-ru/strings.xml`:

```xml
<resources>
    <string name="feature_all_exercises_title">Упражнения</string>
    <string name="feature_all_exercises_empty_headline">Пока нет упражнений</string>
    <string name="feature_all_exercises_empty_supporting">Нажмите +, чтобы создать первое упражнение</string>
    <string name="feature_all_exercises_fab_create">Создать упражнение</string>
    <string name="feature_all_exercises_archive_success">Упражнение в архиве</string>
    <string name="feature_all_exercises_archive_undo">Отменить</string>
    <string name="feature_all_exercises_archive_blocked_format">Нельзя архивировать — используется в: %1$s</string>
    <string name="feature_all_exercises_session_count_more">+%1$d ещё</string>

    <plurals name="feature_all_exercises_session_count">
        <item quantity="one">%d сессия</item>
        <item quantity="few">%d сессии</item>
        <item quantity="many">%d сессий</item>
        <item quantity="other">%d сессии</item>
    </plurals>
</resources>
```

EN strings file `feature/exercise/src/main/res/values/strings.xml`:

```xml
<resources>
    <!-- Exercise detail -->
    <string name="feature_exercise_detail_recent">Recent</string>
    <string name="feature_exercise_detail_track_now">Track now</string>
    <string name="feature_exercise_detail_track_now_pending">Live workout coming in next stage</string>
    <string name="feature_exercise_detail_edit">Edit</string>
    <string name="feature_exercise_detail_archive">Archive</string>
    <string name="feature_exercise_detail_type_weighted">Weighted</string>
    <string name="feature_exercise_detail_type_weightless">Weightless</string>
    <string name="feature_exercise_detail_no_history">No sessions yet</string>
    <string name="feature_exercise_detail_archive_success_format">'%1$s' archived</string>
    <string name="feature_exercise_detail_archive_blocked_title">Cannot archive</string>
    <string name="feature_exercise_detail_archive_blocked_body_format">'%1$s' is used in active trainings: %2$s. Remove from those first.</string>
    <string name="feature_exercise_detail_history_meta_format">%1$s · %2$s</string>
    <string name="feature_exercise_detail_history_meta_adhoc_format">%1$s · ad-hoc</string>
    <string name="feature_exercise_detail_adhoc_training_format">%1$s session</string>

    <!-- Exercise edit -->
    <string name="feature_exercise_edit_title_create">New exercise</string>
    <string name="feature_exercise_edit_title_edit">Edit exercise</string>
    <string name="feature_exercise_edit_label_name">Name</string>
    <string name="feature_exercise_edit_label_type">Type</string>
    <string name="feature_exercise_edit_label_tags">Tags</string>
    <string name="feature_exercise_edit_label_description">Description</string>
    <string name="feature_exercise_edit_placeholder_description">Optional notes — technique, gym-specific details</string>
    <string name="feature_exercise_edit_tag_search_placeholder">Add tag…</string>
    <string name="feature_exercise_edit_tag_create_format">+ Create &#8220;%1$s&#8221;</string>
    <string name="feature_exercise_edit_tag_limit">Maximum 10 tags per exercise</string>
    <string name="feature_exercise_edit_error_name_required">Name is required</string>
    <string name="feature_exercise_edit_discard_title">Discard changes?</string>
    <string name="feature_exercise_edit_discard_body">Your changes will be lost.</string>
    <string name="feature_exercise_edit_discard_confirm">Discard</string>
    <string name="feature_exercise_edit_discard_dismiss">Keep editing</string>
</resources>
```

RU strings file `feature/exercise/src/main/res/values-ru/strings.xml`:

```xml
<resources>
    <!-- Exercise detail -->
    <string name="feature_exercise_detail_recent">Последнее</string>
    <string name="feature_exercise_detail_track_now">Тренировать</string>
    <string name="feature_exercise_detail_track_now_pending">Live-тренировка появится в следующем обновлении</string>
    <string name="feature_exercise_detail_edit">Изменить</string>
    <string name="feature_exercise_detail_archive">В архив</string>
    <string name="feature_exercise_detail_type_weighted">С весом</string>
    <string name="feature_exercise_detail_type_weightless">Без веса</string>
    <string name="feature_exercise_detail_no_history">Сессий ещё не было</string>
    <string name="feature_exercise_detail_archive_success_format">«%1$s» в архиве</string>
    <string name="feature_exercise_detail_archive_blocked_title">Нельзя архивировать</string>
    <string name="feature_exercise_detail_archive_blocked_body_format">«%1$s» используется в активных тренировках: %2$s. Удали из них сначала.</string>
    <string name="feature_exercise_detail_history_meta_format">%1$s · %2$s</string>
    <string name="feature_exercise_detail_history_meta_adhoc_format">%1$s · отдельно</string>
    <string name="feature_exercise_detail_adhoc_training_format">Сессия: %1$s</string>

    <!-- Exercise edit -->
    <string name="feature_exercise_edit_title_create">Новое упражнение</string>
    <string name="feature_exercise_edit_title_edit">Изменить упражнение</string>
    <string name="feature_exercise_edit_label_name">Название</string>
    <string name="feature_exercise_edit_label_type">Тип</string>
    <string name="feature_exercise_edit_label_tags">Теги</string>
    <string name="feature_exercise_edit_label_description">Описание</string>
    <string name="feature_exercise_edit_placeholder_description">Заметки — техника, особенности зала</string>
    <string name="feature_exercise_edit_tag_search_placeholder">Добавить тег…</string>
    <string name="feature_exercise_edit_tag_create_format">+ Создать «%1$s»</string>
    <string name="feature_exercise_edit_tag_limit">Максимум 10 тегов на упражнение</string>
    <string name="feature_exercise_edit_error_name_required">Введите название</string>
    <string name="feature_exercise_edit_discard_title">Отменить изменения?</string>
    <string name="feature_exercise_edit_discard_body">Все изменения будут потеряны.</string>
    <string name="feature_exercise_edit_discard_confirm">Отменить</string>
    <string name="feature_exercise_edit_discard_dismiss">Продолжить</string>
</resources>
```

## Navigation

Add to `core/ui/navigation/Screen.kt`:

```kotlin
@Serializable
sealed interface Screen {
    @Serializable
    data class Exercise(val uuid: String?) : Screen   // null = create mode
    // ...
}
```

The Exercises tab itself is a bottom-bar screen — already exists as
`Screen.BottomBar.AllExercises`. The Exercise(uuid) detail/edit
screen is separate.

When `uuid == null` → ExerciseScreen renders in `Mode.Edit(isCreate = true)`.
When `uuid != null` → ExerciseScreen loads exercise and renders in
`Mode.Read`. User can flip to `Mode.Edit(isCreate = false)` via the
overflow menu.

## Edge cases and decisions

- **Mode flip preserves form state across read↔edit transitions
  within the same screen instance.** When user enters edit, form
  is populated from current state. When they cancel, state reverts
  to `originalSnapshot` and mode goes back to Read.
- **Concurrent edit by another flow** is not a concern in v1 (single-user,
  single-process app).
- **Archive an exercise with active session in progress** — blocked
  (the active session is referenced via performed_exercise →
  RESTRICT FK). User must finish or delete the active session first.
  This is an extreme edge case in v1; surface a snackbar with the
  relevant message.
- **History query returns sessions where the exercise was skipped.**
  We exclude these — only show history rows where at least one set
  was logged. The history query joins through performed_exercise but
  filters by `EXISTS (SELECT 1 FROM set_table WHERE
  performed_exercise_uuid = pe.uuid)`.

## Testing

Unit tests:

- `AllExercisesClickHandlerTest`, `AllExercisesPagingHandlerTest`,
  `AllExercisesNavigationHandlerTest`, `AllExercisesCommonHandlerTest`.
- `AllExercisesInteractorImplTest`.
- `ExerciseClickHandlerTest`, `ExerciseInputHandlerTest`,
  `ExerciseNavigationHandlerTest`, `ExerciseCommonHandlerTest`.
- `ExerciseInteractorImplTest`.
- DAO tests for `pagedActiveByAllTags` and
  `getRecentSessionsForExercise`.

UI: `@Smoke` stubs with `TODO(feature-rewrite-tests)` (per testing.md).

## Stage outcomes

- [x] feature/all-exercises rewritten with v1 design and v3 schema.
- [x] feature/exercise rewritten with v1 design and v3 schema.
- [ ] Phantom shims (trainingUuid, sets, labels, exerciseUuids) on
      ExerciseDataModel + TrainingDataModel — relevant ones removed.
      ExerciseDataModel dropped the Stage 5.2 shims, but
      TrainingDataModel still carries `labels` and `exerciseUuids`.
- [ ] Repository extended with `pagedActiveByTags(Set<String>)` for
      AND semantics. The repository method ships, but the current
      implementation uses OR semantics; AND semantics live in the
      deprecated `ExerciseDao.pagedActiveByAllTags`.
- [x] DAO query `pagedActiveByAllTags` added.
- [x] DAO query `getRecentSessionsForExercise` added.
- [x] `feature_exercise_*` and `feature_all_exercises_*` strings in
      both EN and RU strings.xml.
- [x] All Composables use `stringResource(R.string.xxx)` — no
      hardcoded literals.
- [x] All UI uses `core/ui/kit` components and AppUi tokens.
- [ ] Canonical navigation pattern (NavigationHandler with @Inject
      Navigator; graph composables consume only UI events).
      NavigationHandlers and event-driven graph wiring shipped, but the
      handlers are manually constructed rather than `@Inject`ed.
- [ ] Haptics emitted for every Click action per the architecture.md
      Haptics convention. Several click paths still bypass haptics,
      including undo/cancel flows.
- [x] Unit tests for handlers, interactors, new DAO queries.
- [x] Smoke UI test stubs.
- [ ] App launches; Exercises tab loads; create/edit/archive flow
      works; tag filter narrows list correctly; Russian locale shows
      Russian strings. verification needed.
