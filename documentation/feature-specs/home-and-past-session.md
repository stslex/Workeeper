# Feature spec — Home dashboard expansion + Past session detail (Stage 5.5)

**Status:** Merged in Stage 5.5 (PR #66). For current architecture, see [architecture.md](../architecture.md). This spec is preserved as a historical record of the planning state.

This is the Stage 5.5 feature spec — the **fifth and final v1 feature stage**, after Settings +
Archive (5.1), Exercises (5.2), Trainings (5.3), Live workout + minimal Home banner (5.4). After
this PR merges, every feature in `product.md → v1` is shipped. It builds on:

- [product.md](../product.md) — `v1` features #4 (Past session detail), #5 (Home dashboard basic),
  #12 (Edit past session)
- [ux-architecture.md](../ux-architecture.md) — Home, Training picker, Past session detail
- [data-needs.md](../data-needs.md) — `4. Past session detail`, `5. Home dashboard`
- [db-redesign.md](../db-redesign.md), [db-redesign-plan-model.md](../db-redesign-plan-model.md)
- [design-system.md](../design-system.md)
- [architecture.md](../architecture.md) — Navigation flow, Pre-formatted UI fields, DataModel
  hygiene, Back gesture handling, consumeOnMain
- [feature-specs/live-workout.md](live-workout.md) — patterns to mirror, finish flow change
- [feature-specs/trainings.md](trainings.md) — patterns to mirror

## Scope

Three deliverables, plus one navigation infrastructure tweak:

- **Home dashboard expansion** — Start CTA when no active session, Recent sessions list, plus the
  active-session banner from 5.4 unchanged.
- **Training picker bottom sheet** — modal sheet listing recent-used templates + "see all" →
  Trainings tab. Inline composable inside `feature/home` (not promoted to `core/ui`).
- **Past session detail screen** — new feature module `feature/past-session`. View finished
  session (header + per-exercise breakdown + sets), edit set values inline, delete the session.
- **Navigator extension** — single new method `replaceTo(screen)` so `feature/live-workout` can
  finish into Past session detail without leaving Live workout on the back stack.

This PR also rewires the Live workout finish flow (5.4 popped back to Training detail; 5.5 replaces
with Past session detail) and connects `feature/single-training`'s already-stubbed `OpenSession`
action.

## Out of scope (explicitly v2 or deferred)

- Achievement block on Home — v2.
- Stats dashboard / weekly volume widget — v2.
- Quick notes inbox — v2.
- Session summary as a separate screen — folded into Past session detail (same UI, finish flow lands
  there).
- PR detection on saved sets — v2.
- Edit add/remove exercises on a finished session — v1 edit is set-level only (correct mistakes, not
  restructure).
- Reverting a finished session to in-progress — never allowed.

## Module structure

```
feature/
├── home/                          # MODIFIED — was Stage 5.4 stub
│   └── src/main/kotlin/.../feature/home/
│       ├── di/                    # unchanged shape; new bindings for picker interactor methods
│       ├── domain/
│       │   ├── HomeInteractor.kt              # MODIFIED — adds observeRecent, observeRecentTrainings, startSessionFromTraining
│       │   └── HomeInteractorImpl.kt
│       ├── mvi/
│       │   ├── handler/
│       │   │   ├── ClickHandler.kt            # MODIFIED — Start CTA + recent row + picker selections
│       │   │   ├── CommonHandler.kt
│       │   │   ├── HomeComponent.kt
│       │   │   └── NavigationHandler.kt       # MODIFIED — adds OpenPastSession, OpenAllTrainings, OpenLiveWorkoutFresh
│       │   ├── mapper/
│       │   │   └── HomeUiMapper.kt            # NEW — ActiveSessionWithStats → ActiveSessionInfo,
│       │   │                                   # SessionDataModel → RecentSessionItem,
│       │   │                                   # TrainingListItem → PickerTrainingItem
│       │   ├── model/                         # NEW
│       │   │   ├── RecentSessionItem.kt
│       │   │   └── PickerTrainingItem.kt
│       │   └── store/
│       │       ├── HomeStore.kt               # MODIFIED — see MVI surface below
│       │       └── HomeStoreImpl.kt
│       └── ui/
│           ├── HomeGraph.kt                   # MODIFIED — wire haptic + bottom sheet host
│           ├── HomeScreen.kt                  # MODIFIED — Start CTA + recent list + picker
│           └── components/
│               ├── ActiveSessionBanner.kt     # unchanged from 5.4
│               ├── HomeStartCard.kt           # NEW
│               ├── RecentSessionRow.kt        # NEW
│               └── TrainingPickerSheet.kt     # NEW (private to feature/home)
│
├── past-session/                  # NEW MODULE
│   └── src/main/kotlin/.../feature/past_session/
│       ├── di/
│       │   ├── PastSessionFeature.kt          # exposes Feature<...>
│       │   ├── PastSessionHandlerStore.kt
│       │   ├── PastSessionHandlerStoreImpl.kt
│       │   └── PastSessionModule.kt
│       ├── domain/
│       │   ├── PastSessionInteractor.kt
│       │   └── PastSessionInteractorImpl.kt
│       ├── mvi/
│       │   ├── handler/
│       │   │   ├── ClickHandler.kt
│       │   │   ├── CommonHandler.kt
│       │   │   ├── InputHandler.kt
│       │   │   ├── NavigationHandler.kt
│       │   │   └── PastSessionComponent.kt
│       │   ├── mapper/
│       │   │   └── PastSessionUiMapper.kt
│       │   ├── model/
│       │   │   ├── PastExerciseUiModel.kt
│       │   │   ├── PastSetUiModel.kt
│       │   │   └── PastSessionUiModels.kt
│       │   └── store/
│       │       ├── PastSessionStore.kt
│       │       └── PastSessionStoreImpl.kt
│       └── ui/
│           ├── PastSessionGraph.kt
│           ├── PastSessionScreen.kt
│           └── components/
│               ├── PastSessionHeader.kt
│               ├── PastExerciseCard.kt
│               ├── PastSetEditRow.kt
│               └── DeleteConfirmDialog.kt
│
├── live-workout/                  # MODIFIED — finish flow rewired
│   └── ... (only NavigationHandler + ClickHandler changed)
│
└── single-training/               # MODIFIED — OpenSession stub wired
    └── ... (only NavigationHandler changed; one line)

core/
├── database/                      # MODIFIED
│   └── src/main/kotlin/.../core/database/
│       ├── session/
│       │   ├── SessionDao.kt              # +observeRecentWithStats(limit)
│       │   ├── RecentSessionRow.kt        # NEW (DTO for the new query)
│       │   └── SessionDetailRow.kt        # NEW (DTO for past session detail one-shot)
│       └── training/
│           └── TrainingDao.kt             # +observeRecentTemplates(limit)
│
├── exercise/                      # MODIFIED
│   └── src/main/kotlin/.../core/exercise/
│       ├── session/
│       │   ├── SessionRepository.kt       # +observeRecentWithStats, +getSessionDetail
│       │   ├── SessionRepositoryImpl.kt
│       │   ├── SetRepository.kt           # +updateSet, +deleteSet (already exposed?)
│       │   └── model/
│       │       ├── RecentSessionDataModel.kt   # NEW
│       │       └── SessionDetailDataModel.kt   # NEW (with nested exercises + sets)
│       └── training/
│           └── TrainingRepository.kt      # +observeRecentTemplates
│
└── ui/navigation/                 # MODIFIED — Navigator gets replaceTo
    └── src/main/kotlin/.../core/ui/navigation/
        ├── Navigator.kt                   # +fun replaceTo(screen: Screen)
        └── Screen.kt                      # +data class PastSession(sessionUuid: String)
```

App-level wiring:

- `app/app/.../navigation/NavigatorImpl.kt` — implement `replaceTo`.
- `app/app/.../host/AppNavigationHost.kt` — register `pastSessionGraph(...)`.
- `app/app/.../navigation/RootComponentImpl.kt` — produce `PastSessionComponent`.

## Core flow

### Home flow

```
                    ┌─────────────────────────────┐
                    │  Home (Bottom tab, default) │
                    └──────────────┬──────────────┘
                                   │
                    observeActiveSessionWithStats
                    observeRecentWithStats(limit=10)
                                   │
              ┌────────────────────┼─────────────────────┐
              │                    │                     │
       active session?     no active, no recent     no active, recent>0
              │                    │                     │
              ▼                    ▼                     ▼
     ┌──────────────┐   ┌──────────────────┐   ┌──────────────────────┐
     │ Banner +     │   │ Empty state      │   │ Start CTA + recent   │
     │ recent list  │   │ + Start CTA card │   │ list                 │
     └──────┬───────┘   └────────┬─────────┘   └──────────┬───────────┘
            │                    │                        │
            ▼ tap banner         ▼ tap Start              ▼ tap row / Start
     ┌──────────────┐   ┌──────────────────┐   ┌──────────────────────┐
     │ Live workout │   │ Picker bottom    │   │ Past session detail  │
     │ (resume)     │   │ sheet            │   │   or picker sheet    │
     └──────────────┘   └────────┬─────────┘   └──────────────────────┘
                                 │
                       tap a template
                                 │
                                 ▼
                       Live workout (fresh)
```

State machine for Home:

- `isLoading = true` until first emission of `observeActiveSessionWithStats` AND
  `observeRecentWithStats` both arrive.
- `activeSession` non-null → render banner; recent list still rendered below banner if
  `recent.isNotEmpty()`.
- `activeSession` null AND `recent.isEmpty()` → empty state (icon + headline + Start CTA card).
- `activeSession` null AND `recent.isNotEmpty()` → Start CTA card on top, then recent list.

The picker is a `ModalBottomSheet` with `pickerVisible: Boolean` in State. Selecting a template
emits `Action.Click.OnPickerTrainingSelected(trainingUuid)` →
`Action.Navigation.OpenLiveWorkoutFresh(trainingUuid)` →
`navigator.navTo(Screen.LiveWorkout(sessionUuid = null, trainingUuid = trainingUuid))`. The sheet
closes via `pickerVisible = false` on selection or dismiss.

### Past session detail flow

```
                ┌─────────────────────────┐
                │  Past session detail    │
                └────────────┬────────────┘
                             │
              load via getSessionDetail(uuid)
                             │
                ┌────────────┼─────────────┐
                │            │             │
            isLoading    Loaded(detail)  Error
                             │
            ┌────────────────┼─────────────┬──────────────┐
            │                │             │              │
       view sets         edit set       delete         back gesture
            │                │           session       (no dirty
            │                ▼             │           interception
            │       OnSetWeightChange      ▼           in v1 — see
            │       OnSetRepsChange   AppConfirmDialog discard rule
            │       OnSetTypeChange   confirm → delete  below)
            │                │        → popBack
            │           debounced
            │            persist
            │           via update
            └────────────────┴
```

On entry: Store calls `interactor.getSessionDetail(sessionUuid)` once, holds in State as
`detail: SessionDetailUiModel`. Edits flow through
`Action.Input.OnSetWeightChange/OnSetRepsChange/OnSetTypeChange` — handler updates the local State
row immediately (optimistic) and persists with debounce (300ms after last input on that field,
similar to existing input patterns; if no debounce primitive exists in repo, just persist on input
directly — the repo write is cheap).

Add-set / remove-set on a finished session is **out of scope for v1**. Edit is per-set (weight,
reps, type) only. Document this constraint in the spec for review.

Delete: `Action.Click.OnDeleteClick` → confirmation dialog → `Action.Click.OnDeleteConfirm` →
repository deletes session (CASCADE removes performed_exercise + set rows) →
`Action.Navigation.Back` → `navigator.popBack()`.

### Live workout finish (changed from 5.4)

Stage 5.4 currently:

```
processFinishConfirm() → repository.finishSession(...) → consumeOnMain(Action.Navigation.Back) → popBack to whichever screen entered Live workout
```

Stage 5.5:

```
processFinishConfirm() → repository.finishSession(...) → consumeOnMain(Action.Navigation.OpenPastSession(sessionUuid)) → navigator.replaceTo(Screen.PastSession(sessionUuid))
```

`replaceTo` is a new Navigator method that does the equivalent of `navTo` plus pop the current
destination. From Past session detail, the system back gesture or top-bar arrow lands on whatever
was below Live workout (Home or Training detail). This gives "complete a workout, immediately review
what you did" UX.

The existing `Action.Navigation.BackToHome` is removed — it was a 5.4 placeholder. Cancel session (
which already used `Navigation.Back`) keeps `popBack()`.

## Data layer additions

### `core/database/session/SessionDao.kt`

Add two queries.

**`observeRecentWithStats(limit)`** — Home recent list with everything the row needs in one shot:

```kotlin
@Query(
    """
    SELECT s.uuid AS session_uuid,
           s.training_uuid AS training_uuid,
           t.name AS training_name,
           t.is_adhoc AS is_adhoc,
           s.started_at AS started_at,
           s.finished_at AS finished_at,
           (SELECT COUNT(*) FROM performed_exercise_table pe
              WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS exercise_count,
           (SELECT COUNT(*) FROM set_table st
              JOIN performed_exercise_table pe2 ON pe2.uuid = st.performed_exercise_uuid
              WHERE pe2.session_uuid = s.uuid) AS set_count
    FROM session_table s
    INNER JOIN training_table t ON t.uuid = s.training_uuid
    WHERE s.state = 'FINISHED'
    ORDER BY s.finished_at DESC
    LIMIT :limit
    """,
)
fun observeRecentWithStats(limit: Int): Flow<List<RecentSessionRow>>
```

`RecentSessionRow` (new DTO):

```kotlin
data class RecentSessionRow(
    @ColumnInfo(name = "session_uuid") val sessionUuid: Uuid,
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "training_name") val trainingName: String,
    @ColumnInfo(name = "is_adhoc") val isAdhoc: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "exercise_count") val exerciseCount: Int,
    @ColumnInfo(name = "set_count") val setCount: Int,
)
```

Index check: `session(state, finished_at DESC)` is already covered by existing indices
`session(state)` + `session(finished_at)`. SQLite query planner can use either; for v1 limit=10 the
cost is negligible. If perf becomes an issue, add composite `session(state, finished_at)` later. *
*Do not add prematurely.**

**`getSessionDetail(sessionUuid)`** — one-shot for Past session detail. Returns the session row +
all performed_exercise rows + all set rows joined, deserialized into a hierarchical model. Three
options:

1. Three separate suspend queries (`getSession`, `getPerformedBySession`, `getSetsBySession`) —
   simplest.
2. `@Transaction`-decorated method that runs the three suspends in one DB transaction and assembles
   the result.
3. One mega-query with JOINs and deserialize manually.

**Pick option 2.** Avoids inconsistency window (finished_at could change between calls — unlikely
but cheap to defend against), avoids manual row-flattening of option 3.

```kotlin
@Transaction
suspend fun getSessionDetail(sessionUuid: Uuid): SessionDetailRow? {
    val session = getById(sessionUuid) ?: return null
    val performed = performedDao.getBySession(sessionUuid)
    val setsByPerformed = performed.associate { pe ->
        pe.uuid to setDao.getByPerformedExercise(pe.uuid)
    }
    val training = trainingDao.getById(session.trainingUuid)
    val exerciseNames = exerciseDao.getByIds(performed.map { it.exerciseUuid })
        .associateBy { it.uuid }
    return SessionDetailRow(session, performed, setsByPerformed, training, exerciseNames)
}
```

Where `SessionDetailRow` is a plain data holder (not an `@Entity`):

```kotlin
data class SessionDetailRow(
    val session: SessionEntity,
    val performed: List<PerformedExerciseEntity>,
    val setsByPerformed: Map<Uuid, List<SetEntity>>,
    val training: TrainingEntity?,
    val exerciseByUuid: Map<Uuid, ExerciseEntity>,
)
```

Note: this requires `SessionDao` to depend on `PerformedExerciseDao`, `SetDao`, `TrainingDao`,
`ExerciseDao`. Room doesn't allow @Dao classes to inject other DAOs directly. Two options:

- Move `getSessionDetail` to a higher level — implement in `SessionRepositoryImpl` which already has
  all four DAOs injected. **Pick this.**
- Use Room's transaction-via-AppDatabase (`db.withTransaction { ... }`).

So actual placement: `SessionRepositoryImpl.getSessionDetail(uuid): SessionDetailDataModel?` does
the `db.withTransaction { ... }` block. The DAO does NOT change for this part — only the new
`observeRecentWithStats` query.

### `core/database/training/TrainingDao.kt`

Add `observeRecentTemplates(limit)`:

```kotlin
@Query(
    """
    SELECT t.uuid, t.name, t.description, t.is_adhoc, t.archived, t.created_at, t.archived_at,
           (SELECT COUNT(*) FROM training_exercise_table WHERE training_uuid = t.uuid) AS exercise_count,
           (SELECT MAX(s.finished_at) FROM session_table s
              WHERE s.training_uuid = t.uuid AND s.state = 'FINISHED') AS last_session_at,
           (SELECT s.uuid FROM session_table s
              WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_uuid,
           (SELECT s.started_at FROM session_table s
              WHERE s.training_uuid = t.uuid AND s.state = 'IN_PROGRESS' LIMIT 1) AS active_session_started_at
    FROM training_table t
    WHERE t.archived = 0 AND t.is_adhoc = 0
    ORDER BY (last_session_at IS NULL), last_session_at DESC, t.name COLLATE NOCASE ASC
    LIMIT :limit
    """,
)
fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItemRow>>
```

Reuses existing `TrainingListItemRow`. Ordering: trainings with at least one finished session come
first, ordered by `last_session_at DESC`; trainings never used come after, ordered by name. The
picker shows top 5 by default.

### Repositories

`core/exercise/session/SessionRepository.kt` adds:

```kotlin
fun observeRecentWithStats(limit: Int): Flow<List<RecentSessionDataModel>>

suspend fun getSessionDetail(sessionUuid: String): SessionDetailDataModel?
```

`core/exercise/training/TrainingRepository.kt` adds:

```kotlin
fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItem>>
```

(reuses existing `TrainingListItem` domain model from Trainings tab).

`core/exercise/session/SetRepository.kt` — verify it exposes update + delete on a per-set basis. If
yes, leave alone. If no, add:

```kotlin
suspend fun updateSet(set: SetDataModel)
suspend fun deleteSet(setUuid: String)
```

(Audit during implementation. The existing `SetRepositoryImpl` may already cover this for
live-workout's add/delete-set actions — if so, reuse.)

### New domain models

`core/exercise/session/model/RecentSessionDataModel.kt`:

```kotlin
data class RecentSessionDataModel(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exerciseCount: Int,
    val setCount: Int,
)
```

`core/exercise/session/model/SessionDetailDataModel.kt`:

```kotlin
data class SessionDetailDataModel(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exercises: List<PerformedExerciseDetailDataModel>,
)

data class PerformedExerciseDetailDataModel(
    val performedExerciseUuid: String,
    val exerciseUuid: String,
    val exerciseName: String,
    val position: Int,
    val skipped: Boolean,
    val sets: List<SetDataModel>,
)
```

Note: domain models carry **only domain data**. Pre-formatted UI strings (relative time labels,
duration labels) live in UI types — `RecentSessionItem`, `PastSessionUiModel` — produced by mappers
in the consuming feature module. Per `architecture.md → UI types vs domain types` and the
canonical "pre-formatted UI fields belong in State" rule.

## Navigation surface

### `core/ui/navigation/Screen.kt`

Add:

```kotlin
@Serializable
data class PastSession(
    val sessionUuid: String,
) : Screen
```

### `core/ui/navigation/Navigator.kt`

Extend interface:

```kotlin
@Stable
interface Navigator {
    val navController: NavHostController
    fun navTo(screen: Screen)
    fun popBack()

    /**
     * Navigate to [screen] and remove the current destination from the back stack.
     * After this call, the back stack tip is [screen]; the back gesture from [screen]
     * lands on what was below the popped destination.
     *
     * Used when a screen finishes a one-shot operation and wants to redirect the user
     * forward without leaving the now-stale screen behind (e.g. Live workout → Past
     * session detail after finish).
     */
    fun replaceTo(screen: Screen)
}
```

`NavigatorImpl.replaceTo`:

```kotlin
override fun replaceTo(screen: Screen) {
    logger.d("replaceTo $screen")
    try {
        val currentRoute = holder.navigator.currentDestination?.route ?: return
        navController.navigate(screen) {
            popUpTo(currentRoute) {
                inclusive = true
                saveState = false
            }
            launchSingleTop = true
        }
    } catch (ignore: Exception) {
        logger.e(ignore, "screen: $screen")
    }
}
```

`saveState = false` is intentional — the popped destination is a finished Live workout, no point
saving its state. Distinguishes from `navTo` where `isSingleTop` saves state.

### Routes covered

```
Screen.BottomBar.Home  →  Screen.LiveWorkout(sessionUuid = activeSessionUuid)         (resume from banner; existing)
Screen.BottomBar.Home  →  Screen.LiveWorkout(trainingUuid = pickedTraining)            (fresh from picker; new)
Screen.BottomBar.Home  →  Screen.BottomBar.AllTrainings                                (picker "see all"; new)
Screen.BottomBar.Home  →  Screen.PastSession(sessionUuid)                              (recent row tap; new)
Screen.Training        →  Screen.PastSession(sessionUuid)                              (single-training pastSessions; new)
Screen.LiveWorkout     ⤳  Screen.PastSession(sessionUuid)  (replaceTo, post-finish)    (new, replaces 5.4 popBack)
```

The `Home → AllTrainings` transition uses `navTo(Screen.BottomBar.AllTrainings)` —
`isSingleTop = true` on `BottomBar` already handles the bottom-bar-tap semantics, but double-check
that arriving at `AllTrainings` from Home (not via the bottom bar tap) renders the correct selected
tab in `WorkeeperBottomAppBar`. If not, the bottom-bar selection tracking logic in
`NavHostControllerHolder` handles it because route changes flow through the same NavController.

## MVI surface — `feature/home`

### State

```kotlin
@Stable
data class State(
    val activeSession: ActiveSessionInfo?,
    val recent: ImmutableList<RecentSessionItem>,
    val isLoading: Boolean,
    val nowMillis: Long,
    val picker: PickerState,
) : Store.State {

    @Stable
    data class ActiveSessionInfo(
        val sessionUuid: String,
        val trainingUuid: String,
        val trainingName: String,
        val startedAt: Long,
        val doneCount: Int,
        val totalCount: Int,
        val elapsedDurationLabel: String,        // pre-formatted in mapper
    ) {
        fun elapsedMillis(now: Long): Long = (now - startedAt).coerceAtLeast(0L)
    }

    @Stable
    data class RecentSessionItem(
        val sessionUuid: String,
        val trainingName: String,                // localized "Ad-hoc workout" if isAdhoc
        val isAdhoc: Boolean,
        val finishedAtRelativeLabel: String,     // pre-formatted "2 days ago"
        val durationLabel: String,               // pre-formatted "47 min"
        val statsLabel: String,                  // pre-formatted "5 exercises · 18 sets"
    )

    @Stable
    sealed interface PickerState {
        data object Hidden : PickerState
        data class Visible(
            val templates: ImmutableList<PickerTrainingItem>,
            val isLoading: Boolean,
        ) : PickerState
    }

    @Stable
    data class PickerTrainingItem(
        val trainingUuid: String,
        val name: String,
        val exerciseCountLabel: String,          // pre-formatted "6 exercises"
        val lastSessionRelativeLabel: String?,   // pre-formatted "3 days ago" or null if never used
    )

    val showStartCta: Boolean get() = activeSession == null
    val showRecentList: Boolean get() = recent.isNotEmpty()
    val showEmptyState: Boolean get() = !isLoading && activeSession == null && recent.isEmpty()
    val showPicker: Boolean get() = picker is PickerState.Visible

    companion object {
        val INITIAL = State(
            activeSession = null,
            recent = persistentListOf(),
            isLoading = true,
            nowMillis = 0L,
            picker = PickerState.Hidden,
        )
    }
}
```

### Action

```kotlin
@Stable
sealed interface Action : Store.Action {

    sealed interface Click : Action {
        data object OnActiveSessionClick : Click
        data object OnChartsClick : Click                    // v2.2: Charts icon left of Settings
        data object OnSettingsClick : Click
        data class OnRecentSessionClick(val sessionUuid: String) : Click
        data object OnStartTrainingClick : Click             // opens picker
        data class OnPickerTrainingSelected(val trainingUuid: String) : Click
        data object OnPickerSeeAllClick : Click              // → AllTrainings
        data object OnPickerDismiss : Click
    }

    sealed interface Navigation : Action {
        data class OpenLiveWorkoutResume(val sessionUuid: String) : Navigation
        data class OpenLiveWorkoutFresh(val trainingUuid: String) : Navigation
        data class OpenPastSession(val sessionUuid: String) : Navigation
        data object OpenSettings : Navigation
        data object OpenCharts : Navigation                  // v2.2 → Screen.ExerciseChart(null)
        data object OpenAllTrainings : Navigation
    }

    sealed interface Common : Action {
        data object Init : Common
        data object TimerTick : Common
    }
}
```

### Event

Unchanged structure from 5.4:

```kotlin
@Stable
sealed interface Event : Store.Event {
    data class HapticClick(val type: HapticFeedbackType) : Event
}
```

### Handlers

- **`CommonHandler`** — on `Init`, launches three flows: `observeActiveSessionWithStats`,
  `observeRecentWithStats(limit = HOME_RECENT_LIMIT)` where `HOME_RECENT_LIMIT = 10`, and a tick
  flow at 1Hz for `nowMillis` (already exists from 5.4 — confirm it stays; nowMillis is for the
  active banner's elapsed label, not the recent list).
- **`ClickHandler`** — routes clicks. `OnStartTrainingClick` triggers picker load (calls
  `interactor.observeRecentTemplates(PICKER_LIMIT)` once, sets `picker = Visible(loading)` then
  populates), all `On*Click` haptics emit `Event.HapticClick`. `OnPickerTrainingSelected` closes
  picker and emits `Action.Navigation.OpenLiveWorkoutFresh`. `OnRecentSessionClick` emits
  `Action.Navigation.OpenPastSession`.
- **`NavigationHandler`** — exact same shape as 5.4 NavigationHandler, extended with the new
  Navigation variants.

```kotlin
@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.BottomBar.Home,
) : HomeComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.OpenLiveWorkoutResume ->
                navigator.navTo(
                    Screen.LiveWorkout(
                        sessionUuid = action.sessionUuid,
                        trainingUuid = null
                    )
                )
            is Action.Navigation.OpenLiveWorkoutFresh ->
                navigator.navTo(
                    Screen.LiveWorkout(
                        sessionUuid = null,
                        trainingUuid = action.trainingUuid
                    )
                )
            is Action.Navigation.OpenPastSession ->
                navigator.navTo(Screen.PastSession(action.sessionUuid))
            Action.Navigation.OpenSettings -> navigator.navTo(Screen.Settings)
            Action.Navigation.OpenCharts ->
                navigator.navTo(Screen.ExerciseChart(exerciseUuid = null))
            Action.Navigation.OpenAllTrainings -> navigator.navTo(Screen.BottomBar.AllTrainings)
        }
    }
}
```

### Picker loading note

The picker is loaded lazily on first open, NOT eagerly on `Init`. Reasoning:

- Most Home opens have an active session → Start CTA never shown → picker query never runs.
- When user taps Start CTA, sheet shows briefly with `isLoading = true`; query returns within a
  frame on warm DB.
- Avoids holding a hot Flow that updates the picker continuously when the sheet is closed.

Acceptable cost: ~50-100ms perceived latency on first picker open. If this becomes noticeable in QA,
switch to eager hot Flow.

## MVI surface — `feature/past-session` (new)

### Component

```kotlin
internal abstract class PastSessionComponent(
    val data: Screen.PastSession,
) : Component
```

### State

```kotlin
@Stable
data class State(
    val sessionUuid: String,                    // from route
    val phase: Phase,
    val deleteDialogVisible: Boolean,
) : Store.State {

    @Stable
    sealed interface Phase {
        data object Loading : Phase
        data class Loaded(val detail: PastSessionUiModel) : Phase
        data class Error(val errorType: ErrorType) : Phase
    }

    @Stable
    data class PastSessionUiModel(
        val trainingName: String,                // localized
        val isAdhoc: Boolean,
        val finishedAtAbsoluteLabel: String,     // pre-formatted "Mon, Apr 27, 19:42"
        val durationLabel: String,               // pre-formatted "47 min"
        val totalsLabel: String,                 // pre-formatted "5 exercises · 18 sets"
        val volumeLabel: String?,                // pre-formatted "1,250 kg total" or null if no weighted sets
        val exercises: ImmutableList<PastExerciseUiModel>,
    )

    @Stable
    data class PastExerciseUiModel(
        val performedExerciseUuid: String,
        val exerciseName: String,
        val position: Int,
        val skipped: Boolean,
        val isWeighted: Boolean,                 // from underlying ExerciseEntity.type
        val sets: ImmutableList<PastSetUiModel>,
    )

    @Stable
    data class PastSetUiModel(
        val setUuid: String,
        val position: Int,
        val type: SetTypeUiModel,                // reuse existing UI enum
        val weightInput: String,                 // editable raw text (stable for TextField)
        val repsInput: String,
        val weightError: Boolean,
        val repsError: Boolean,
        val isPersonalRecord: Boolean,           // v2.1 — true iff setUuid matches current PR
    )

    enum class ErrorType { SessionNotFound, LoadFailed }

    val canDelete: Boolean get() = phase is Phase.Loaded
}
```

**v2.1 PR badge.** `PastSessionInteractor.observeDetailWithPrs(sessionUuid)` returns a
combined `Flow<DetailWithPrs?>` that pairs the one-shot session detail with a reactive PR
map (`observePersonalRecords` per exercise). The mapper sets `isPersonalRecord = (prMap
[exerciseUuid]?.setUuid == set.uuid)`. Edits apply optimistically through `InputHandler`,
so the snapshot stays consistent between PR re-emissions; when the user saves an edit, the
underlying `set_table` change triggers Room to re-emit the PR map and the badge follows.

### Action

```kotlin
@Stable
sealed interface Action : Store.Action {

    sealed interface Click : Action {
        data object OnBackClick : Click
        data object OnDeleteClick : Click
        data object OnDeleteConfirm : Click
        data object OnDeleteDismiss : Click
        data class OnSetTypeChange(val setUuid: String, val type: SetTypeUiModel) : Click
        data class OnRetryLoad : Click
    }

    sealed interface Input : Action {
        data class OnSetWeightChange(val setUuid: String, val raw: String) : Input
        data class OnSetRepsChange(val setUuid: String, val raw: String) : Input
    }

    sealed interface Navigation : Action {
        data object Back : Navigation
    }

    sealed interface Common : Action {
        data object Init : Common
    }
}
```

### Event

```kotlin
@Stable
sealed interface Event : Store.Event {
    data class HapticClick(val type: HapticFeedbackType) : Event
    data class ShowError(val errorType: ErrorType) : Event   // enum-typed per architecture rule
    data object DeletedSnackbar : Event
}
```

### Handlers

- **`CommonHandler`** — `Init` calls `interactor.getSessionDetail(sessionUuid)` once and updates
  State. On null result, emit `Phase.Error(SessionNotFound)`. On exception,
  `Phase.Error(LoadFailed)`.
- **`InputHandler`** — `OnSetWeightChange` / `OnSetRepsChange` — parse raw to value, validate (
  weight ≥ 0 OR null for unweighted; reps > 0), update UI state (raw text for stability),
  debounce-persist on success. Validation errors flip per-row error flags but DO NOT block typing.
  The persist call uses `interactor.updateSet(...)` and ignores the result on success. On failure,
  emit `Event.ShowError(SaveFailed)` and revert the State row to its last known good value.
- **`ClickHandler`** — `OnDeleteClick` shows confirmation; `OnDeleteConfirm` calls
  `interactor.deleteSession(sessionUuid)` then `consumeOnMain(Action.Navigation.Back)` then
  `Event.DeletedSnackbar`. `OnSetTypeChange` updates type immediately (no debounce — user picks
  once). `OnRetryLoad` re-runs Init.
- **`NavigationHandler`** — `Back → navigator.popBack()`.

### Compose surface

`PastSessionScreen(state, consume)`:

- Top-level `Scaffold` with
  `AppTopAppBar(title = state.detail?.trainingName ?: stringResource(loading_title), navigationIcon = back, actions = { delete IconButton when canDelete })`.
- `when (state.phase)` switches between loading indicator, error state with retry button, loaded
  list.
- Loaded: `LazyColumn` with header item (`PastSessionHeader`) + per-exercise items (
  `PastExerciseCard` containing rows of `PastSetEditRow`).
- Delete dialog rendered conditionally when `state.deleteDialogVisible`.

`PastExerciseCard` shows position, name, skipped chip if skipped, then either "no sets" empty or
list of `PastSetEditRow`.

`PastSetEditRow` mirrors the live-workout set row visually: type chip, weight TextField, reps
TextField. No "mark done" checkbox here — every set is already done. Stable `key` per row for
TextField identity preservation (per architecture.md → TextField inputs).

### Discard / back gesture

Past session detail does NOT need `interceptBack`. Edits persist immediately (debounced). On back
gesture, the user's most recent edits may be in-flight; we accept that — repository write is fast.
If a stronger guarantee is desired, add `BackHandler(enabled = state.hasInflightWrites) { ... }`
later. Document this as v1 simplification.

## MVI surface — `feature/single-training` (one-line wire-up)

In `feature/single-training/.../mvi/handler/NavigationHandler.kt`, replace the no-op stub:

```kotlin
// Before:
is Action.Navigation.OpenSession -> Unit

// After:
is Action.Navigation.OpenSession ->
navigator.navTo(Screen.PastSession(action.sessionUuid))
```

Remove the inline TODO comment.

## MVI surface — `feature/live-workout` finish change

Two edits:

1. `LiveWorkoutStore.kt` — replace `Action.Navigation.BackToHome` with
   `Action.Navigation.OpenPastSession(val sessionUuid: String)`.
2. `ClickHandler.kt::processFinishConfirm` — change `consumeOnMain(Action.Navigation.Back)` (after
   `repository.finishSession(...)`) to
   `consumeOnMain(Action.Navigation.OpenPastSession(state.value.sessionUuid))`.
3. `NavigationHandler.kt` — add
   `is Action.Navigation.OpenPastSession -> navigator.replaceTo(Screen.PastSession(action.sessionUuid))`.
   Remove `BackToHome` branch.

The cancel-session path (which deletes the session in-progress) keeps
`Action.Navigation.Back → popBack()`. No PastSession transition there because the session was
deleted — no UUID to view.

## DataModel hygiene audit

Per `architecture.md → DataModel hygiene`, audit every `*DataModel` touched by 5.5:

- `SessionDataModel` — confirm no obsolete fields. Currently has uuid, trainingUuid, state,
  startedAt, finishedAt. All used. ✓
- `RecentSessionDataModel` (new) — no legacy. ✓ (designed greenfield)
- `SessionDetailDataModel` (new) — same. ✓
- `PerformedExerciseDataModel` — used by Live workout; check it doesn't carry phantom fields when
  consumed by past-session interactor. If it carries any field that 5.5 doesn't read AND that no
  other consumer reads, delete.
- `SetDataModel` — same audit.
- `TrainingListItem` — reused for picker. Check that picker's `PickerTrainingItem` only consumes the
  fields it needs (uuid, name, exerciseCount, lastSessionAt) — if `TrainingListItem` carries
  `activeSessionUuid` / `activeSessionStartedAt` and 5.5 picker mapper doesn't read them, that's
  fine because Trainings tab reads them. Don't delete cross-feature fields.

Surface findings in the PR description: "Phantom fields removed: X. Callsites updated: feature/Y,
feature/Z."

## Localization

Every user-facing string goes to `feature/home/src/main/res/values/strings.xml` and
`values-ru/strings.xml` (and same pair for `feature/past-session`). Per architecture.md →
Localization.

Strings to add (English; mirror in ru):

```xml
<!-- feature/home -->
<string name="feature_home_start_cta_title">Start a workout</string><string
name="feature_home_start_cta_subtitle">Pick a training template
</string><string name="feature_home_recent_section_title">Recent sessions</string><string
name="feature_home_recent_adhoc_label">Ad-hoc workout
</string><string name="feature_home_picker_title">Choose a training</string><string
name="feature_home_picker_see_all">See all templates
</string><string name="feature_home_picker_empty">No templates yet</string>

<plurals name="feature_home_recent_exercises_count">
<item quantity="one">%d exercise</item>
<item quantity="other">%d exercises</item>
</plurals><plurals name="feature_home_recent_sets_count">
<item quantity="one">%d set</item>
<item quantity="other">%d sets</item>
</plurals>

    <!-- feature/past-session -->
<string name="feature_past_session_loading_title">Past session</string><string
name="feature_past_session_error_not_found">Session not found
</string><string name="feature_past_session_error_load_failed">Could not load session
</string><string name="feature_past_session_action_retry">Retry</string><string
name="feature_past_session_action_delete">Delete
</string><string name="feature_past_session_delete_dialog_title">Delete session?</string><string
name="feature_past_session_delete_dialog_body">This permanently removes the session and all its
sets. The training template is unchanged.
</string><string name="feature_past_session_delete_dialog_confirm">Delete</string><string
name="feature_past_session_deleted_snackbar">Session deleted
</string><string name="feature_past_session_skipped_chip">Skipped</string><string
name="feature_past_session_no_sets">No sets logged
</string><string name="feature_past_session_volume_label">%1$s kg total</string><string
name="feature_past_session_save_failed_snackbar">Could not save change — try again
</string>
```

Russian forms must include `few` quantity for plurals (упражнение / упражнения / упражнений; сет /
сета / сетов).

Pre-formatted labels (`durationLabel`, `finishedAtRelativeLabel`, `finishedAtAbsoluteLabel`,
`statsLabel`, `volumeLabel`) are produced by mappers, not Composables. Use existing
`formatElapsedDuration` from `core/core/time` (already used in 5.4 banner) for durations. For
relative time, add `formatRelativeTime(nowMillis, eventMillis): String` to `core/core/time` if
absent, returning localized "just now / 5 min ago / 2 days ago / Mar 12" with `Locale.current`. For
absolute, use `DateTimeFormatter` with localized pattern.

If `core/core/time` doesn't yet have `formatRelativeTime`, add it as part of this stage (small,
well-tested utility).

## Testing requirements

### Unit tests

- `feature/home`:
    - `ClickHandlerTest` — every `Action.Click` variant produces correct State change + Navigation
      Action.
    - `CommonHandlerTest` — `Init` subscribes to active + recent flows; State updates correctly on
      combined emissions; `isLoading` flips correctly when both flows have emitted at least once.
    - `NavigationHandlerTest` — every Navigation variant calls correct `navigator.*` method.
    - `HomeUiMapperTest` — `RecentSessionDataModel → RecentSessionItem` mapping with adhoc +
      non-adhoc, with all stat combinations; `TrainingListItem → PickerTrainingItem` mapping with
      never-used + recently-used.
    - `HomeInteractorImplTest` — flows wire correctly to repository.
- `feature/past-session`:
    - `ClickHandlerTest` — delete flow (click → dialog → confirm → delete + back + snackbar); retry;
      type change.
    - `InputHandlerTest` — weight/reps validation, debounced persist, error revert.
    - `CommonHandlerTest` — Init success, Init not-found, Init error.
    - `NavigationHandlerTest` — Back → popBack.
    - `PastSessionUiMapperTest` — `SessionDetailDataModel → PastSessionUiModel` with all variants (
      skipped exercise, weighted vs unweighted, no sets exercise).
    - `PastSessionInteractorImplTest` — getSessionDetail/updateSet/deleteSession wire correctly.
- `feature/live-workout`:
    - Update `ClickHandlerTest::processFinishConfirm` — assert
      `Action.Navigation.OpenPastSession(uuid)` is consumed (was `Action.Navigation.Back`).
    - Update `NavigationHandlerTest` — `OpenPastSession` calls
      `navigator.replaceTo(Screen.PastSession(uuid))`.
- `feature/single-training`:
    - Update `NavigationHandlerTest` — `OpenSession` calls
      `navigator.navTo(Screen.PastSession(uuid))` (was no-op).
- `core/database`:
    - `SessionDaoTest::observeRecentWithStats` — verifies join + counts + ordering with mixed
      adhoc/non-adhoc + skipped exercises + sessions without sets.
    - `TrainingDaoTest::observeRecentTemplates` — never-used last, used-recently-first,
      archived/adhoc filtered out.
- `core/exercise`:
    - `SessionRepositoryImplTest::getSessionDetail` — returns null for unknown uuid, returns
      hierarchical model for known uuid.

### Compose tests

- `HomeScreenTest` — every state branch (loading, empty, recent-only, banner-only, banner+recent,
  picker-visible) renders correct elements; tapping recent row emits correct Action; picker
  selection emits correct Action.
- `PastSessionScreenTest` — loading, error, loaded, delete dialog visible/hidden; tapping delete
  shows dialog; confirm fires Action; weight/reps TextField emits Input.

### Integration tests

- `HomeIntegrationTest` (in `app/dev` or feature `androidTest`) — start with empty DB, see empty
  state; insert finished session via repo, see recent row; insert active session via repo, see
  banner.
- `PastSessionIntegrationTest` — open past session, edit a set's weight, leave screen, reopen —
  change persisted.

### Smoke / Regression annotation

Cover the canonical flow with `@Smoke`:

- Open Home with no data → empty state.
- Open Home with active session → banner.
- Tap banner → Live workout (resume).
- Finish workout → land on Past session detail.
- Back from Past session → land on entry point.

## Verification gate

Before marking PR ready for review:

- `./gradlew assembleDebug` passes (whole project).
- `./gradlew testDebugUnitTest` passes.
- `./gradlew detekt` passes (no new findings).
- `./gradlew lintDebug` passes (no new findings).
- App launches.
    - **Empty DB / no sessions yet:** Home shows empty state with Start CTA. Tap Start → picker
      opens with "No templates yet" if also no trainings, or with template list. Pick template →
      Live workout (fresh).
    - **One finished session, no active:** Home shows Start CTA + recent list with one row. Tap
      row → Past session detail loads.
    - **Active session in progress:** Home shows banner + recent list (if any prior finished). Tap
      banner → Live workout resume.
    - **Live workout finish flow:** Start a session, log sets, finish → land on Past session
      detail (NOT Training detail). Top-bar back → land on whatever entered Live workout (Home or
      Training).
    - **Past session edit:** Open past session, change weight on a set, leave screen, reopen →
      change persisted. Same for reps and type.
    - **Past session delete:** Open past session, tap delete, confirm → land on entry, snackbar
      visible. Verify session gone from Home recent list and from Training detail past sessions.
    - **Past session not found (manual test):** Navigate to
      `Screen.PastSession("00000000-0000-0000-0000-000000000000")` via deep test — error state with
      retry button.
    - **Picker:** Tap "see all templates" → switch to Trainings tab.
- In RU locale, all new strings translated; plurals correct.

## Stage outcomes

This spec merged without a final deliverables checklist section. Shipped
scope is captured by PR #66 and the verification gate above, so no
retroactive checkbox list was added in this hygiene pass.

## Constraints

- **Phantom shims related to DataModel audit are in scope** — must be removed.
- **Other phantom shims** stay (e.g. `AppDimension` legacy nested objects).
- **LICENSE / SPDX headers** — `SPDX-License-Identifier: GPL-3.0-only` on every new file.
- Do NOT implement add/remove exercises on a finished session (v2 — full restructure of finished
  session).
- Do NOT implement Session summary as a separate screen — Past session detail is the post-finish
  destination.
- Do NOT implement PR detection / personal records (v2).
- Do NOT implement quick notes inbox (v2).
- Do NOT implement Stats dashboard / weekly volume widget (v2).
