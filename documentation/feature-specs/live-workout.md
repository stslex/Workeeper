# Feature spec — Live workout (Stage 5.4)

**Status:** Merged in Stage 5.4 (PR #63). For current architecture, see [architecture.md](../architecture.md). This spec is preserved as a historical record of the planning state.

This is the Stage 5.4 feature spec — the fourth v1 feature implementation, after Settings + Archive (5.1), Exercises (5.2), Trainings (5.3). It builds on:

- [product.md](../product.md)
- [ux-architecture.md](../ux-architecture.md) — Live workout section
- [data-needs.md](../data-needs.md)
- [db-redesign.md](../db-redesign.md) and [db-redesign-plan-model.md](../db-redesign-plan-model.md) — plan-first model in v5 schema
- [design-system.md](../design-system.md)
- [architecture.md](../architecture.md) — Navigation flow, Localization, Back gesture handling, Compose UI conventions, **DataModel hygiene**
- [feature-specs/trainings.md](trainings.md) — patterns to mirror

## Scope

One large screen + one minimal Home addition:

- **Live workout screen** — a dedicated screen showing the in-progress session with all exercises, set entry inline, finish flow.
- **Active session banner on Home** — minimal Home addition showing "session in progress" card if any. Tap → resume in Live workout. Full Home dashboard ships in Stage 5.5.

This PR also creates a **new feature module** `feature/live-workout` and adds a **Home stub screen** sufficient to host the active-session banner. Full Home dashboard is Stage 5.5; here Home renders only the banner (when applicable) plus an empty state otherwise.

## Module structure

```
feature/live-workout/                — NEW
  src/main/kotlin/.../
    di/LiveWorkoutModule.kt + HandlerStore + Processor + EntryPoint
    domain/LiveWorkoutInteractor[Impl].kt
    ui/
      LiveWorkoutGraph.kt
      LiveWorkoutScreen.kt
      components/
        LiveWorkoutHeader.kt          — name + timer + progress bar
        LiveExerciseCard.kt           — collapsed/expanded card per exercise
        LiveSetRow.kt                 — single set row with inputs + checkbox
        FinishConfirmDialog.kt        — confirm dialog with stats
      mvi/
        store/LiveWorkoutStore[Impl].kt
        handler/
          ClickHandler.kt
          InputHandler.kt
          NavigationHandler.kt
          CommonHandler.kt
          PlanEditActionHandler.kt    — reuses core/ui/plan-editor wrapper
          LiveWorkoutComponent.kt
      mvi/model/                      — UI types
        LiveExerciseUiModel.kt
        LiveSetUiModel.kt
        LiveSessionStateUiModel.kt
        ExerciseStatusUiModel.kt      — DONE / CURRENT / PENDING / SKIPPED
      mvi/mapper/
        LiveWorkoutMapper.kt          — domain ↔ UI
  src/main/res/values/strings.xml + values-ru/strings.xml

feature/home/                        — NEW (minimal — full version is Stage 5.5)
  src/main/kotlin/.../
    di/HomeModule.kt + HandlerStore + Processor
    domain/HomeInteractor[Impl].kt   — only "is there an active session"
    ui/
      HomeGraph.kt
      HomeScreen.kt
      components/
        ActiveSessionBanner.kt
        HomeEmptyState.kt
      mvi/
        store/HomeStore[Impl].kt
        handler/
          ClickHandler.kt
          NavigationHandler.kt
          CommonHandler.kt
          HomeComponent.kt
  src/main/res/values/strings.xml + values-ru/strings.xml

core/ui/navigation/
  Screen.kt                          — add Screen.LiveWorkout(sessionUuid: String?)
                                       (null sessionUuid = create from training, non-null = resume)
                                       and Screen.BottomBar.Home (already exists or add)
```

## Core flow

When the user taps "Start session" on a Training detail (Stage 5.3), the navigator opens `Screen.LiveWorkout(sessionUuid = null, trainingUuid = ...)` (or via dedicated factory). The screen creates a session via `interactor.startSession(trainingUuid)`, which:

1. Creates a `SessionEntity` with `state = IN_PROGRESS`, `startedAt = now`.
2. For each exercise in the training (ordered by `training_exercise.position`), creates a `PerformedExerciseEntity` with `skipped = false` and the corresponding `position`. Sets are NOT created upfront — they're created lazily as the user marks them done (or the user explicitly adds sets via "Add set").
3. Returns the new session uuid.

The screen then loads the in-progress session (`sessionUuid` becomes non-null in the route) and renders. From this point forward, the session lives in DB with `state = IN_PROGRESS` until the user finishes it.

When user taps "Resume session" on Trainings tab/Training detail/Home — same screen opens with the existing `sessionUuid`. State loads from DB.

## DataModel audit (mandatory before implementation)

The following DataModel types currently have stale fields. Stage 5.4 implementation must remove them as part of the work — see [architecture.md → DataModel hygiene](../architecture.md#datamodel-hygiene).

### `ExerciseDataModel` — remove 3 phantom fields

```kotlin
data class ExerciseDataModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDataModel,
    val description: String?,
    val imagePath: String?,
    val archived: Boolean,
    val archivedAt: Long?,
    val timestamp: Long,
    val lastAdhocSets: List<PlanSetDataModel>?,
    // REMOVE: trainingUuid - exercises aren't training-bound in v3+
    // REMOVE: sets - sets live in SetEntity via performed_exercise
    // REMOVE: labels - tags come from exercise_tag join, not denormalized here
)
```

Audit all callsites of `trainingUuid`, `sets`, `labels` on `ExerciseDataModel`. Each callsite either reads `null` / `emptyList()` (already broken in v3) or projects the field into something else. Remove the field, fix the callsites.

### `SetsDataModel.weight` — fix nullability

Currently `weight: Double` (non-nullable). Mapping from `SetEntity.weight: Double?` does `weight ?: 0.0`, losing the distinction between "no weight" (weightless exercise) and "0 kg" (impossible value).

Fix: `SetsDataModel.weight: Double?`. Update mapping in `SetEntity.toData()` to preserve null. Audit consumers — they currently treat 0.0 as "no weight" which works accidentally; explicit null is correct.

### `PerformedExerciseDataModel` — verify

Inspect for any phantom fields. Current shape (uuid, sessionUuid, exerciseUuid, position, skipped) looks clean — no shims expected. Confirm during implementation.

### `SessionDataModel` — verify

Inspect for any phantom fields. Current shape (uuid, trainingUuid, state, startedAt, finishedAt) looks clean — no shims expected. Confirm.

The PR description must list which fields were removed and from which DataModel, with callsite count touched.

## Live workout screen

### Header card

Top of screen (sticky or scrollable — sticky preferred for visibility during long workouts):

```
[ Push Day                                  •23:14 ]
[ 2 of 5 done · 16 sets logged                    ]
[ ████████░░░░░░░░░░░░░░ ]
```

- **Training name** — `bodyMedium` weight 500.
- **Timer** — accent color, animated dot prefix, `LL:MM` format. Live updating once per second via a coroutine launched in CommonHandler.Init. Driven by `state.startedAt` + current time. Format helper in `core/core/...time/DurationFormat.kt`.
- **Progress text** — `bodySmall` muted: "X of N done · M sets logged" (X = exercises with at least one done set OR explicitly marked completed). Pluralized strings.
- **Progress bar** — fill % = `doneExercises / totalExercises`. `AppLinearProgressIndicator` from kit (or simple Box if not available — verify in core/ui/kit).

### Exercise card states

Each exercise is rendered as a `LiveExerciseCard`. Four states based on user activity:

1. **DONE** — all planned sets checked done OR no plan + at least one set logged + user moved on (heuristic: card auto-collapses when next exercise becomes current). Visual: opacity 0.55, collapsed (header only). Status line: "Completed · X sets". Tap header → expand for read-only review.

2. **CURRENT** — the lowest-position exercise that is not DONE or SKIPPED. Visual: 1dp accent border, expanded showing all sets. Status line: "X of N sets" or "Plan: ..." if zero sets done yet.

3. **PENDING** — not yet current. Visual: default `bgSecondary`, collapsed. Status line: "Plan: 100×5 · 100×5 · 102.5×5" or "no plan" if null.

4. **SKIPPED** — user explicitly skipped via overflow → "Skip exercise". Visual: opacity 0.4, collapsed. Status line: italic accent-warning "Skipped".

#### State derivation

**There is no explicit "this is current" field in DB.** Current state is derived in the UI mapper:

```kotlin
fun deriveStatuses(exercises: List<PerformedExerciseDataModel>, sets: Map<Uuid, List<...>>): List<LiveExerciseUiModel> {
    var foundCurrent = false
    return exercises.sortedBy { it.position }.map { pe ->
        val status = when {
            pe.skipped -> ExerciseStatusUiModel.SKIPPED
            isExerciseDone(pe, sets) -> ExerciseStatusUiModel.DONE
            !foundCurrent -> { foundCurrent = true; ExerciseStatusUiModel.CURRENT }
            else -> ExerciseStatusUiModel.PENDING
        }
        // ...
    }
}
```

`isExerciseDone(pe, sets)` returns true if:
- The exercise has a plan AND every plan set has a corresponding "checked done" set in DB, OR
- User explicitly tapped "Mark exercise complete" in overflow (we add this Action — see below).

The simpler rule: an exercise is DONE iff all of its plan rows are checked. For exercises without a plan, "done" requires explicit user action.

#### `LiveExerciseCard` header layout

Same row layout invariant from Stage 5.3 — 3 lines max:

```
[22dp position] [22dp type icon] [name + status line] [22dp overflow ⋯]
```

Status line in line 2 (just below the name) varies by state per the four states above.

### Set entry inline

CURRENT exercise card shows all set rows inline, populated from plan (pre-filled inputs) or empty if no plan. Each row:

```
[18dp pos #] [weight kg input] [reps input] [56dp type chip] [22dp checkbox]
```

For weightless exercises:

```
[18dp pos #] [reps input] [56dp type chip] [22dp checkbox]
```

(weight column omitted entirely — the layout is 4-column not 5-column.)

#### Done state

When user taps the checkbox:
- Action `OnSetMarkDone(performedExerciseUuid, position)` emitted.
- Handler creates a `SetEntity` if it doesn't exist yet (or updates the existing one) with the **current** values from the input fields. The values are read from State at the moment of the tap.
- Visual: row gets accent left border 2dp, inputs lose underline (transition to read-only-style but still tappable), checkbox filled.
- Tapping again toggles back to undone (deletes the SetEntity).

#### Editing values

Tap input → keyboard. User types. Each character emits `OnSetWeightChange(performedExerciseUuid, position, value)` or `OnSetRepsChange(...)`. Handler updates **draft state** for that set in `state.setDrafts: ImmutableMap<Pair<String, Int>, LiveSetUiModel>`.

The set is NOT persisted on every keystroke. It's persisted when:
- User taps the checkbox to mark done → write SetEntity with current draft values.
- User starts a new exercise (focuses inputs of next exercise) → flush all dirty drafts of current exercise to DB as IN_PROGRESS sets (uncheck'd). Actually: this is over-complicated. **Simpler rule:** drafts only persist on explicit checkbox tap. If user types but never checks, the data is lost on screen exit. This is acceptable v1 behavior — explicit marking is the contract.

#### Add set / Reset / Skip

In CURRENT exercise card:
- "+ Add set" button below set list — appends a new row at the next position. Default values: copy of last set if exists, else empty + WORK type.
- Overflow ⋯ menu items:
  - "Edit plan" — opens `AppPlanEditor` sheet for this exercise's `training_exercise.plan_sets` (or `last_adhoc_sets` for ad-hoc). Same component reused from Stage 5.3.
  - "Reset sets" — confirmation dialog → deletes all SetEntity rows for this performed_exercise. Sets become uncheck'd, values revert to plan-prefill.
  - "Skip exercise" — confirmation dialog → sets `performed_exercise.skipped = true`, deletes any existing sets for this exercise. Status flips to SKIPPED, card collapses.

### Bottom finish bar

Sticky bottom bar, full-width "Finish session" button (`AppButton.Primary`).

Tap → opens `FinishConfirmDialog`:

```
Finish session?
Your sets will be logged and plans will update with what you actually performed.

Duration:        47:08
Exercises:       4 of 5 done · 1 skipped
Sets logged:     22

[Keep going]  [Finish]
```

On Confirm:

1. Mark `session.state = FINISHED`, set `finishedAt = now`.
2. For each non-skipped performed_exercise, apply `PlanUpdateRule` (from Stage 4.6):
   - Read existing plan from `training_exercise.plan_sets` (or `exercise.last_adhoc_sets` for ad-hoc).
   - Read performed sets from `set_table` filtered by `performed_exercise_uuid`, ordered by position.
   - Apply hybrid rule.
   - Write back via `trainingExerciseRepo.setPlan` or `exerciseRepo.setAdhocPlan`.
3. Navigate back to Training detail (or Home if session was ad-hoc — based on entry point).
4. Show snackbar "Session saved" with stats summary.

The whole finish operation is a single transaction. If any step fails, rollback session state to IN_PROGRESS, surface error snackbar.

### Back gesture / exit without finishing

Per architecture.md "Back gesture handling":

- `interceptBack: Boolean` derived flag = false (system back exits silently).
- Session stays IN_PROGRESS in DB.
- User can resume via Trainings tab / Training detail / Home banner.

This is a deliberate decision — session is persistent state, exit is not "discarding work". No discard dialog.

If implementation discovers user testing wants confirmation, that's a v1.5 follow-up — don't add it preemptively.

### Header overflow `⋯`

Session-level actions:
- **Cancel session** — destructive: deletes the session and all its performed_exercises and sets (CASCADE). Confirmation dialog. Plans untouched.
- **Edit training** (read-only convenience) — navigate to Training detail in edit mode. Session continues in background. **Defer to v2** — too tangential for v1 implementation.

For Stage 5.4: only "Cancel session" in header overflow.

## Home shortcut

### Home screen layout

Home is a bottom-nav tab. Stage 5.4 implementation is **minimal** — just the active-session banner if any, else empty state.

Layout:

1. `AppTopAppBar` — title `feature_home_title` ("Home"), trailing icon for Settings (per IA — settings reachable from Home top bar).
2. **Active session banner** (if `observeActiveSession()` emits non-null):
   - `AppCard` with accent tinted background (`bgSecondary` + accent overlay).
   - Animated dot + "Session in progress" + training name.
   - Timer (live updating, same format as Live workout header).
   - Progress: "X of N exercises done".
   - Trailing chevron.
   - Tap → navigate to Live workout, resuming session.
3. **Empty state** (if no active session) — for now, simple `AppEmptyState`:
   - Icon: `Icons.Filled.FitnessCenter`
   - Headline: `feature_home_empty_headline` ("Ready when you are")
   - Supporting: `feature_home_empty_supporting` ("Start a training from the Trainings tab")

Stage 5.5 will replace the empty state with the full dashboard (recent sessions, "Start training" picker, weekly stats, etc).

### Active session banner — anatomy

```
[ ⊞  Push Day                       •12:34 ]
[    Session in progress · 2 of 5 done    ]
                                          ›
```

- Leading: `AppShapes.medium` 28dp box with type icon (training generic icon).
- Line 1: training name + animated dot timer (right aligned).
- Line 2: status — "Session in progress · X of N done" (muted, with dot pulse).
- Trailing chevron.

This row follows the row layout invariant from Stage 5.3 (3-line shape, 16dp padding, ellipsis).

## MVI surface

### LiveWorkoutStore

```kotlin
interface LiveWorkoutStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val sessionUuid: String?,                // null while loading initial create
        val trainingUuid: String,
        val trainingName: String,
        val isAdhoc: Boolean,
        val startedAt: Long,
        val nowMillis: Long,                     // for live timer rendering
        val exercises: ImmutableList<LiveExerciseUiModel>,
        val setDrafts: ImmutableMap<DraftKey, LiveSetUiModel>,
        val planEditorTarget: PlanEditorTarget?,
        val pendingFinishConfirm: FinishStats?,
        val pendingResetExerciseUuid: String?,
        val pendingSkipExerciseUuid: String?,
        val pendingCancelConfirm: Boolean,
        val isLoading: Boolean,
    ) : Store.State {

        data class DraftKey(val performedExerciseUuid: String, val position: Int)
        data class FinishStats(
            val durationMillis: Long,
            val doneCount: Int,
            val totalCount: Int,
            val skippedCount: Int,
            val setsLogged: Int,
        )
        data class PlanEditorTarget(
            val performedExerciseUuid: String,
            val exerciseName: String,
            val exerciseType: ExerciseTypeUiModel,
            val initialPlan: ImmutableList<PlanSetUiModel>,
            val draft: ImmutableList<PlanSetUiModel>,
        )

        val elapsedMillis: Long get() = nowMillis - startedAt
        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } ?: false
        val interceptBack: Boolean get() = isPlanEditorDirty   // sheet only
    }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data class OnSetMarkDone(val performedExerciseUuid: String, val position: Int) : Click
            data class OnSetUncheck(val performedExerciseUuid: String, val position: Int) : Click
            data class OnSetTypeSelect(val performedExerciseUuid: String, val position: Int, val type: SetTypeUiModel) : Click
            data class OnSetRemove(val performedExerciseUuid: String, val position: Int) : Click
            data class OnAddSet(val performedExerciseUuid: String) : Click
            data class OnEditPlan(val performedExerciseUuid: String) : Click
            data class OnResetSets(val performedExerciseUuid: String) : Click
            data class OnResetSetsConfirm(val performedExerciseUuid: String) : Click
            data object OnResetSetsDismiss : Click
            data class OnSkipExercise(val performedExerciseUuid: String) : Click
            data class OnSkipExerciseConfirm(val performedExerciseUuid: String) : Click
            data object OnSkipExerciseDismiss : Click
            data object OnFinishClick : Click
            data object OnFinishConfirm : Click
            data object OnFinishDismiss : Click
            data object OnCancelSessionClick : Click
            data object OnCancelSessionConfirm : Click
            data object OnCancelSessionDismiss : Click
            data class OnExerciseHeaderClick(val performedExerciseUuid: String) : Click  // expand done card
            data object OnBackClick : Click
        }
        sealed interface Input : Action {
            data class OnSetWeightChange(val performedExerciseUuid: String, val position: Int, val value: Double?) : Input
            data class OnSetRepsChange(val performedExerciseUuid: String, val position: Int, val value: Int?) : Input
        }
        sealed interface Navigation : Action {
            data object Back : Navigation
            data object BackToHome : Navigation
        }
        sealed interface Common : Action {
            data class Init(val sessionUuid: String?, val trainingUuid: String?) : Common
            data object TimerTick : Common  // every 1s, updates state.nowMillis
        }
        data class PlanEditAction(val action: AppPlanEditorAction) : Action
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class HapticImpact(val type: HapticFeedbackType) : Event   // for set-checked feedback
        data class ShowSessionSavedSnackbar(val stats: State.FinishStats) : Event
        data class ShowError(val message: String) : Event
        data object ShowFinishConfirmDialog : Event
        data object ShowResetSetsConfirmDialog : Event
        data object ShowSkipExerciseConfirmDialog : Event
        data object ShowCancelSessionConfirmDialog : Event
    }
}
```

#### Handler responsibilities

- `ClickHandler` — most state mutations. Set checkbox toggle = create/delete SetEntity from drafts. Add set = append empty PlanSet to current draft list. Skip/reset = update DB + state.
- `InputHandler` — text field changes, write to setDrafts only (not DB).
- `CommonHandler` — Init loads session + performed exercises + sets. TimerTick coroutine updates `nowMillis` once per second.
- `NavigationHandler` — internal class with `@Inject Navigator`.
- `PlanEditActionHandler` — receives `AppPlanEditorAction` wrapper from `core/ui/plan-editor`. Same pattern as Stage 5.3 single-training. Updates draft, on Save persists.

**Set persistence rule** (concrete):

When `OnSetMarkDone(performedExerciseUuid, position)` arrives:
1. Read draft from `state.setDrafts[(performedExerciseUuid, position)]` (or fallback to plan values if no draft).
2. Validate: weight may be null for weightless exercise; reps must be positive.
3. If valid: launch background coroutine, call `setRepository.upsert(performedExerciseUuid, position, weight, reps, type)` (need new repo method).
4. On success: `withContext(immediateDispatcher)` → update state's exercise sets list, remove draft entry, recompute exercise status (may flip to DONE).
5. Emit `Event.HapticImpact` for tactile feedback.

When `OnSetUncheck(performedExerciseUuid, position)`:
1. Background: `setRepository.deleteByPerformedAndPosition(performedExerciseUuid, position)`.
2. On success: remove from state's sets list, restore draft from previous values.

### HomeStore

```kotlin
interface HomeStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val activeSession: ActiveSessionInfo?,    // null = no active session
        val nowMillis: Long,
        val isLoading: Boolean,
    ) : Store.State {
        @Stable
        data class ActiveSessionInfo(
            val sessionUuid: String,
            val trainingUuid: String,
            val trainingName: String,
            val startedAt: Long,
            val doneCount: Int,
            val totalCount: Int,
        ) {
            fun elapsedMillis(now: Long): Long = now - startedAt
        }
    }

    @Stable
    sealed interface Action : Store.Action {
        sealed interface Click : Action {
            data object OnActiveSessionClick : Click
            data object OnSettingsClick : Click
        }
        sealed interface Navigation : Action {
            data class OpenLiveWorkout(val sessionUuid: String) : Navigation
            data object OpenSettings : Navigation
        }
        sealed interface Common : Action {
            data object Init : Common
            data object TimerTick : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
```

`HomeInteractor.observeActiveSession()` is a hot Flow combining session_table + training_table + performed_exercise count. Provides `ActiveSessionInfo` or null. Reuses existing `SessionRepository.observeAnyActiveSession()` from Stage 5.3 — augment if needed.

## Domain layer

### `LiveWorkoutInteractor`

```kotlin
interface LiveWorkoutInteractor {
    suspend fun startSession(trainingUuid: String): String  // returns new session uuid
    suspend fun loadSession(sessionUuid: String): SessionSnapshot
    fun observeSession(sessionUuid: String): Flow<SessionSnapshot>  // optional reactive
    suspend fun upsertSet(performedExerciseUuid: String, position: Int, set: PlanSetDataModel)
    suspend fun deleteSet(performedExerciseUuid: String, position: Int)
    suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean)
    suspend fun resetExerciseSets(performedExerciseUuid: String)
    suspend fun finishSession(sessionUuid: String): FinishResult
    suspend fun cancelSession(sessionUuid: String)
    suspend fun setPlanForExercise(trainingUuid: String, exerciseUuid: String, plan: List<PlanSetDataModel>?)
    suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDataModel>?)
}

data class SessionSnapshot(
    val session: SessionDataModel,
    val trainingName: String,
    val isAdhoc: Boolean,
    val exercises: List<PerformedExerciseSnapshot>,
)

data class PerformedExerciseSnapshot(
    val performed: PerformedExerciseDataModel,
    val exerciseName: String,
    val exerciseType: ExerciseTypeDataModel,
    val planSets: List<PlanSetDataModel>?,        // from training_exercise.plan_sets
    val performedSets: List<PlanSetDataModel>,    // from set_table, ordered by position
)

data class FinishResult(
    val durationMillis: Long,
    val doneCount: Int,
    val totalCount: Int,
    val skippedCount: Int,
    val setsLogged: Int,
)
```

`finishSession` is the heavy lift:

```kotlin
suspend fun finishSession(sessionUuid: String): FinishResult = withTransaction {
    val session = sessionRepo.getById(sessionUuid) ?: error("session missing")
    val performedExercises = performedExerciseRepo.getBySession(sessionUuid)
    val now = System.currentTimeMillis()

    // For each non-skipped exercise, apply PlanUpdateRule
    for (pe in performedExercises) {
        if (pe.skipped) continue
        val performedSets = setRepo.getByPerformedExercise(pe.uuid)
            .map { it.toPlanSet() }
        val existingPlan = if (session.isAdhoc) {
            exerciseRepo.getAdhocPlan(pe.exerciseUuid)
        } else {
            trainingExerciseRepo.getPlan(session.trainingUuid, pe.exerciseUuid)
        }
        val newPlan = PlanUpdateRule.update(existingPlan, performedSets)
        if (session.isAdhoc) {
            exerciseRepo.setAdhocPlan(pe.exerciseUuid, newPlan)
        } else {
            trainingExerciseRepo.setPlan(session.trainingUuid, pe.exerciseUuid, newPlan)
        }
    }

    sessionRepo.finishSession(sessionUuid, now)

    FinishResult(
        durationMillis = now - session.startedAt,
        doneCount = performedExercises.count { !it.skipped && setRepo.hasAnyForPerformed(it.uuid) },
        totalCount = performedExercises.size,
        skippedCount = performedExercises.count { it.skipped },
        setsLogged = performedExercises.sumOf { setRepo.countByPerformedExercise(it.uuid) },
    )
}
```

This is one transaction. `withTransaction` from Room, wraps everything atomically. If any step fails, the session stays IN_PROGRESS.

### `HomeInteractor`

```kotlin
interface HomeInteractor {
    fun observeActiveSession(): Flow<HomeStore.State.ActiveSessionInfo?>
}
```

Uses `SessionRepository.observeAnyActiveSession()` (from Stage 5.3) + joins for trainingName + computes done/total counts via queries.

## Data layer additions

### `SetRepository` — add upsert and helpers

```kotlin
interface SetRepository {
    suspend fun getByPerformedExercise(performedExerciseUuid: String): List<SetsDataModel>
    suspend fun upsert(performedExerciseUuid: String, position: Int, weight: Double?, reps: Int, type: SetTypeDataModel)
    suspend fun deleteByPerformedAndPosition(performedExerciseUuid: String, position: Int)
    suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String)
    suspend fun hasAnyForPerformed(performedExerciseUuid: String): Boolean
    suspend fun countByPerformedExercise(performedExerciseUuid: String): Int
    // existing:
    suspend fun insert(performedExerciseUuid: String, position: Int, set: SetsDataModel)
    suspend fun update(performedExerciseUuid: String, position: Int, set: SetsDataModel)
    suspend fun delete(uuid: String)
    @Deprecated(...) suspend fun getLastFinishedSet(exerciseUuid: String): SetsDataModel?
}
```

`upsert` is the key new method — it inserts or updates a set at `(performedExerciseUuid, position)`. Used by Live workout when user taps the checkbox.

`SetsDataModel` field cleanup (per DataModel audit): `weight` becomes nullable. This propagates to the SetEntity mapping which already handles nullable weight in Room — just remove the `?: 0.0` coalesce.

### `SessionDao` — verify `observeActive` returns required projection

Stage 5.3 added `observeAnyActiveSession`. For Home banner we need `ActiveSessionInfo` projection (sessionUuid + trainingUuid + trainingName + startedAt + doneCount + totalCount). Add a query that joins:

```kotlin
@Query("""
    SELECT s.uuid, s.training_uuid, t.name AS training_name,
           t.is_adhoc,
           s.started_at,
           (SELECT COUNT(*) FROM performed_exercise_table pe WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS total_count,
           (SELECT COUNT(DISTINCT pe.uuid) FROM performed_exercise_table pe
            INNER JOIN set_table st ON st.performed_exercise_uuid = pe.uuid
            WHERE pe.session_uuid = s.uuid AND pe.skipped = 0) AS done_count
    FROM session_table s
    INNER JOIN training_table t ON t.uuid = s.training_uuid
    WHERE s.state = 'IN_PROGRESS'
    LIMIT 1
""")
fun observeActiveSessionWithStats(): Flow<ActiveSessionWithStatsRow?>
```

`done_count` heuristic: an exercise is "done" if it has at least one set logged. This is the cheapest correct query — perfect plan-tracking would require joining plan_sets and set_table, which is overkill for a banner.

### `PerformedExerciseRepository` — extend

```kotlin
suspend fun getBySession(sessionUuid: String): List<PerformedExerciseDataModel>  // exists
suspend fun setSkipped(uuid: String, skipped: Boolean)  // exists
suspend fun insertForSession(sessionUuid: String, exerciseUuids: List<Pair<String, Int>>)  // NEW (uuid + position pairs)
```

The `insertForSession` is called by `LiveWorkoutInteractor.startSession` when creating a session — for each training_exercise row in the training, create a corresponding performed_exercise.

## Localization

EN strings file `feature/live-workout/src/main/res/values/strings.xml`:

```xml
<resources>
    <!-- Header -->
    <string name="feature_live_workout_progress_format">%1$d of %2$d done · %3$s</string>
    <plurals name="feature_live_workout_set_count">
        <item quantity="one">%d set logged</item>
        <item quantity="other">%d sets logged</item>
    </plurals>

    <!-- Exercise card status -->
    <string name="feature_live_workout_status_completed_format">Completed · %1$s</string>
    <plurals name="feature_live_workout_status_set_count">
        <item quantity="one">%d set</item>
        <item quantity="other">%d sets</item>
    </plurals>
    <string name="feature_live_workout_status_progress_format">%1$d of %2$d sets</string>
    <string name="feature_live_workout_status_skipped">Skipped</string>
    <string name="feature_live_workout_status_plan_format">Plan: %1$s</string>
    <string name="feature_live_workout_status_no_plan">no plan</string>

    <!-- Exercise overflow -->
    <string name="feature_live_workout_action_edit_plan">Edit plan</string>
    <string name="feature_live_workout_action_reset_sets">Reset sets</string>
    <string name="feature_live_workout_action_skip">Skip exercise</string>

    <!-- Add set -->
    <string name="feature_live_workout_add_set">+ Add set</string>

    <!-- Reset confirmation -->
    <string name="feature_live_workout_reset_title">Reset sets?</string>
    <string name="feature_live_workout_reset_body">All logged sets for this exercise will be deleted. This cannot be undone.</string>
    <string name="feature_live_workout_reset_confirm">Reset</string>

    <!-- Skip confirmation -->
    <string name="feature_live_workout_skip_title">Skip this exercise?</string>
    <string name="feature_live_workout_skip_body">Logged sets for this exercise will be deleted and the exercise marked as skipped.</string>
    <string name="feature_live_workout_skip_confirm">Skip</string>

    <!-- Finish dialog -->
    <string name="feature_live_workout_finish">Finish session</string>
    <string name="feature_live_workout_finish_title">Finish session?</string>
    <string name="feature_live_workout_finish_body">Your sets will be logged and plans will update with what you actually performed.</string>
    <string name="feature_live_workout_finish_stat_duration">Duration</string>
    <string name="feature_live_workout_finish_stat_exercises">Exercises</string>
    <string name="feature_live_workout_finish_stat_sets">Sets logged</string>
    <string name="feature_live_workout_finish_stat_exercises_format">%1$d of %2$d done</string>
    <string name="feature_live_workout_finish_stat_exercises_with_skipped_format">%1$d of %2$d done · %3$d skipped</string>
    <string name="feature_live_workout_finish_keep_going">Keep going</string>
    <string name="feature_live_workout_finish_confirm">Finish</string>
    <string name="feature_live_workout_finish_success">Session saved</string>

    <!-- Header overflow -->
    <string name="feature_live_workout_session_overflow_cancel">Cancel session</string>
    <string name="feature_live_workout_cancel_title">Cancel session?</string>
    <string name="feature_live_workout_cancel_body">All progress will be deleted. This cannot be undone.</string>
    <string name="feature_live_workout_cancel_confirm">Cancel session</string>
    <string name="feature_live_workout_cancel_dismiss">Keep going</string>
</resources>
```

RU translation `values-ru/strings.xml`:

```xml
<resources>
    <string name="feature_live_workout_progress_format">%1$d из %2$d · %3$s</string>
    <plurals name="feature_live_workout_set_count">
        <item quantity="one">%d сет записан</item>
        <item quantity="few">%d сета записано</item>
        <item quantity="many">%d сетов записано</item>
        <item quantity="other">%d сетов записано</item>
    </plurals>

    <string name="feature_live_workout_status_completed_format">Готово · %1$s</string>
    <plurals name="feature_live_workout_status_set_count">
        <item quantity="one">%d сет</item>
        <item quantity="few">%d сета</item>
        <item quantity="many">%d сетов</item>
        <item quantity="other">%d сетов</item>
    </plurals>
    <string name="feature_live_workout_status_progress_format">%1$d из %2$d сетов</string>
    <string name="feature_live_workout_status_skipped">Пропущено</string>
    <string name="feature_live_workout_status_plan_format">План: %1$s</string>
    <string name="feature_live_workout_status_no_plan">плана нет</string>

    <string name="feature_live_workout_action_edit_plan">Изменить план</string>
    <string name="feature_live_workout_action_reset_sets">Сбросить сеты</string>
    <string name="feature_live_workout_action_skip">Пропустить упражнение</string>

    <string name="feature_live_workout_add_set">+ Добавить сет</string>

    <string name="feature_live_workout_reset_title">Сбросить сеты?</string>
    <string name="feature_live_workout_reset_body">Все записанные сеты этого упражнения будут удалены. Это нельзя отменить.</string>
    <string name="feature_live_workout_reset_confirm">Сбросить</string>

    <string name="feature_live_workout_skip_title">Пропустить упражнение?</string>
    <string name="feature_live_workout_skip_body">Записанные сеты будут удалены, упражнение отмечено как пропущенное.</string>
    <string name="feature_live_workout_skip_confirm">Пропустить</string>

    <string name="feature_live_workout_finish">Завершить сессию</string>
    <string name="feature_live_workout_finish_title">Завершить сессию?</string>
    <string name="feature_live_workout_finish_body">Сеты будут записаны, планы обновятся под фактически выполненные значения.</string>
    <string name="feature_live_workout_finish_stat_duration">Длительность</string>
    <string name="feature_live_workout_finish_stat_exercises">Упражнения</string>
    <string name="feature_live_workout_finish_stat_sets">Сетов</string>
    <string name="feature_live_workout_finish_stat_exercises_format">%1$d из %2$d</string>
    <string name="feature_live_workout_finish_stat_exercises_with_skipped_format">%1$d из %2$d · %3$d пропущено</string>
    <string name="feature_live_workout_finish_keep_going">Продолжить</string>
    <string name="feature_live_workout_finish_confirm">Завершить</string>
    <string name="feature_live_workout_finish_success">Сессия сохранена</string>

    <string name="feature_live_workout_session_overflow_cancel">Отменить сессию</string>
    <string name="feature_live_workout_cancel_title">Отменить сессию?</string>
    <string name="feature_live_workout_cancel_body">Весь прогресс будет удалён. Это нельзя отменить.</string>
    <string name="feature_live_workout_cancel_confirm">Отменить сессию</string>
    <string name="feature_live_workout_cancel_dismiss">Продолжить</string>
</resources>
```

EN strings for `feature/home/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="feature_home_title">Home</string>
    <string name="feature_home_active_session_label">Session in progress</string>
    <string name="feature_home_active_session_progress_format">%1$d of %2$d done</string>
    <string name="feature_home_empty_headline">Ready when you are</string>
    <string name="feature_home_empty_supporting">Start a training from the Trainings tab</string>
</resources>
```

RU translation:

```xml
<resources>
    <string name="feature_home_title">Главная</string>
    <string name="feature_home_active_session_label">Сессия в процессе</string>
    <string name="feature_home_active_session_progress_format">%1$d из %2$d</string>
    <string name="feature_home_empty_headline">Готовы тренироваться</string>
    <string name="feature_home_empty_supporting">Начните тренировку на вкладке Тренировки</string>
</resources>
```

## Navigation

`Screen.LiveWorkout(sessionUuid: String? = null, trainingUuid: String? = null)` — at least one of (sessionUuid, trainingUuid) must be non-null. If sessionUuid → resume. If trainingUuid only → create new session for this training.

`Screen.BottomBar.Home` — verify exists in app navigation. If not, add. Replace any existing default tab if needed.

App-level navigation: bottom bar tabs are Home / Trainings / Exercises (3 tabs). Confirm Home tab is wired.

## Edge cases and decisions

- **Exit while typing values without checkbox tap.** Drafts in state are lost. Acceptable v1 behavior — explicit confirmation via checkbox is the contract. No "save draft" between exits.
- **Resume after exit.** State.exercises rebuilds from DB. Drafts are not persisted — they were never DB state.
- **Two Live workout instances simultaneously?** Should be impossible per "exactly one active session" invariant. If somehow possible (process kill / restart), use the IN_PROGRESS session.
- **Session for ad-hoc training.** Stage 5.2 introduced ad-hoc training pattern (Track now from Exercise detail). For Live workout, ad-hoc means `session.isAdhoc = true`, plans persist via `exercise.last_adhoc_sets` instead of `training_exercise.plan_sets`. UI is identical except after finish, navigate back to Exercise detail (not Training detail).
- **Plan editor open during set marking.** Plan editor is a modal sheet — interactions outside sheet are blocked. User must close sheet first.
- **Exercise has no plan AND user adds sets manually.** First "+ Add set" creates first set with default values (empty weight if weighted, empty reps, type WORK). User edits. Marks done. Next finish → plan becomes those performed sets via PlanUpdateRule.
- **TimerTick coroutine.** Launched in CommonHandler.Init, runs `while (true) { delay(1000); consume(Action.Common.TimerTick) }`. Cancelled on store cleared. The dispatcher matters — must not block main thread.
- **Set type chip dropdown.** Tap chip → show small `AppMenu` with 4 options. On select, emit `OnSetTypeSelect`. Type changes for unchecked sets only update draft; for checked sets, also update DB row.

## Testing

Unit tests:

- `LiveWorkoutClickHandlerTest` — including all confirmation paths (reset, skip, finish, cancel) and mark/uncheck flows.
- `LiveWorkoutInputHandlerTest`.
- `LiveWorkoutCommonHandlerTest` — Init, TimerTick.
- `LiveWorkoutInteractorImplTest` — including the multi-step finishSession transaction with PlanUpdateRule applied per exercise.
- `HomeClickHandlerTest`, `HomeCommonHandlerTest`.
- `HomeInteractorImplTest`.
- DAO tests for new queries.
- `SetRepositoryImplTest` for upsert / hasAny / count helpers.
- Mapper tests for `LiveWorkoutMapper` — status derivation across all 4 states with various inputs.

Compose previews:
- Per architecture.md "Composable previews", every public/internal Composable in feature/live-workout and feature/home has at least one @Preview.
- `LiveExerciseCard` previews cover: DONE, CURRENT-with-plan, CURRENT-no-plan, PENDING-with-plan, PENDING-no-plan, SKIPPED, weighted vs weightless variants.
- `LiveSetRow` previews cover: pending unchecked, pending edited, done, weighted vs weightless, all 4 types.
- `ActiveSessionBanner` previews cover: short timer, long timer, RU locale.
- `FinishConfirmDialog` previews cover: all done, partial done, with skipped.

## Stage 5.4 deliverables checklist

- [ ] `feature/live-workout` new module, fully implemented per spec.
- [ ] `feature/home` new minimal module — banner + empty state.
- [ ] `Screen.LiveWorkout(sessionUuid, trainingUuid)` route added.
- [ ] `Screen.BottomBar.Home` wired in app navigation.
- [ ] DataModel audit completed:
  - [ ] `ExerciseDataModel.trainingUuid` removed
  - [ ] `ExerciseDataModel.sets` removed
  - [ ] `ExerciseDataModel.labels` removed
  - [ ] `SetsDataModel.weight` made nullable
  - [ ] `PerformedExerciseDataModel` and `SessionDataModel` audited (likely clean)
- [ ] `SetRepository.upsert / deleteByPerformedAndPosition / hasAny / count / deleteAll` added.
- [ ] `SessionDao.observeActiveSessionWithStats` added.
- [ ] `PerformedExerciseRepository.insertForSession` added.
- [ ] `LiveWorkoutInteractor.finishSession` is one transaction, applies PlanUpdateRule per non-skipped exercise.
- [ ] `LiveWorkoutInteractor.startSession` creates session + performed_exercise rows in one transaction.
- [ ] All EN + RU strings per spec.
- [ ] Canonical NavigationHandler pattern.
- [ ] Conditional BackHandler via `interceptBack` derived flag (only when plan editor is dirty).
- [ ] Haptics: light click on most actions, medium impact on set checkbox tap, medium impact on finish/skip/cancel destructive confirms.
- [ ] Plan editor wrapper pattern (`Action.PlanEditAction(AppPlanEditorAction)`) applied to live-workout store.
- [ ] Composable @Previews for every public/internal Composable.
- [ ] Unit tests for handlers, interactors, mappers, new DAO queries.

## Live workout depends on Stage 5.3 in dev

Stage 5.3 must be in dev before this Code session runs. Specifically:
- `core/ui/plan-editor` module exists.
- `SessionRepository.observeAnyActiveSession` exists.
- `TrainingExerciseRepository.getPlan / setPlan` exist.
- `ExerciseRepository.getAdhocPlan / setAdhocPlan` exist.
- `PlanUpdateRule` exists.

Verify with `grep -r "AppPlanEditor" --include="*.kt"` returns hits in `core/ui/plan-editor`.
