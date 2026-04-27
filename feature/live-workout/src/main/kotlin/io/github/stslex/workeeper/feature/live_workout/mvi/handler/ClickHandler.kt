// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongMethod")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    store: LiveWorkoutHandlerStore,
) : Handler<Action.Click>, LiveWorkoutHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnSetMarkDone -> processSetMarkDone(action)
            is Action.Click.OnSetUncheck -> processSetUncheck(action)
            is Action.Click.OnSetTypeSelect -> processSetTypeSelect(action)
            is Action.Click.OnSetRemove -> processSetRemove(action)
            is Action.Click.OnAddSet -> processAddSet(action)
            is Action.Click.OnEditPlan -> processEditPlan(action)
            is Action.Click.OnResetSets -> processResetSetsAsk(action)
            is Action.Click.OnResetSetsConfirm -> processResetSetsConfirm(action)
            Action.Click.OnResetSetsDismiss -> processResetSetsDismiss()
            is Action.Click.OnSkipExercise -> processSkipExerciseAsk(action)
            is Action.Click.OnSkipExerciseConfirm -> processSkipExerciseConfirm(action)
            Action.Click.OnSkipExerciseDismiss -> processSkipExerciseDismiss()
            Action.Click.OnFinishClick -> processFinishClick()
            Action.Click.OnFinishConfirm -> processFinishConfirm()
            Action.Click.OnFinishDismiss -> processFinishDismiss()
            Action.Click.OnCancelSessionClick -> processCancelClick()
            Action.Click.OnCancelSessionConfirm -> processCancelConfirm()
            Action.Click.OnCancelSessionDismiss -> processCancelDismiss()
            is Action.Click.OnExerciseHeaderClick -> processExerciseHeaderClick(action)
            Action.Click.OnBackClick -> processBackClick()
        }
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        if (state.value.isPlanEditorDirty) return
        consume(Action.Navigation.Back)
    }

    private fun processSetMarkDone(action: Action.Click.OnSetMarkDone) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val current = state.value
        val exercise = current.findExercise(action.performedExerciseUuid) ?: return
        val seedDraft = current.draftFor(action.performedExerciseUuid, action.position)
        if (seedDraft.reps <= 0) {
            sendEvent(Event.ShowError(message = "invalid_reps"))
            return
        }
        val planSet = PlanSetDataModel(
            weight = seedDraft.weight,
            reps = seedDraft.reps,
            type = seedDraft.type.toData(),
        )
        // Optimistic UI: flip the row to done immediately so the checkbox tap feels snappy.
        updateState { latest -> latest.applySetMarked(action.performedExerciseUuid, action.position, seedDraft) }
        launch(
            onError = { _ ->
                sendEvent(Event.ShowError(message = "set_save_failed"))
                // Revert to a clean reload-shaped state by rebuilding statuses.
                updateState { latest -> latest.recomputeStatuses() }
            },
        ) {
            interactor.upsertSet(
                performedExerciseUuid = action.performedExerciseUuid,
                position = action.position,
                set = planSet,
            )
        }
        @Suppress("UNUSED_VARIABLE")
        val handled = exercise
    }

    private fun processSetUncheck(action: Action.Click.OnSetUncheck) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest -> latest.applySetUnchecked(action.performedExerciseUuid, action.position) }
        launch(
            onError = { _ -> sendEvent(Event.ShowError(message = "set_delete_failed")) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, action.position)
        }
    }

    private fun processSetTypeSelect(action: Action.Click.OnSetTypeSelect) {
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        val current = state.value
        val exercise = current.findExercise(action.performedExerciseUuid) ?: return
        val performed = exercise.performedSets.firstOrNull { it.position == action.position }
        if (performed != null && performed.isDone) {
            // For a checked set, type changes are persisted immediately so the saved set
            // matches what the user sees. The optimistic UI update below keeps it instant.
            updateState { latest ->
                latest.applySetTypeChange(
                    action.performedExerciseUuid,
                    action.position,
                    action.type,
                )
            }
            launch(
                onError = { _ -> sendEvent(Event.ShowError(message = "set_save_failed")) },
            ) {
                interactor.upsertSet(
                    performedExerciseUuid = action.performedExerciseUuid,
                    position = action.position,
                    set = PlanSetDataModel(
                        weight = performed.weight,
                        reps = performed.reps,
                        type = action.type.toData(),
                    ),
                )
            }
        } else {
            updateState { latest ->
                latest.applyDraftTypeChange(
                    action.performedExerciseUuid,
                    action.position,
                    action.type,
                )
            }
        }
    }

    private fun processSetRemove(action: Action.Click.OnSetRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest -> latest.applySetUnchecked(action.performedExerciseUuid, action.position) }
        launch(
            onError = { _ -> sendEvent(Event.ShowError(message = "set_delete_failed")) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, action.position)
        }
    }

    private fun processAddSet(action: Action.Click.OnAddSet) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            val exercise = latest.findExercise(action.performedExerciseUuid) ?: return@updateState latest
            val nextPosition = exercise.performedSets.size
            val seed = exercise.performedSets.lastOrNull()?.copy(position = nextPosition, isDone = false)
                ?: LiveSetUiModel(
                    position = nextPosition,
                    weight = exercise.planSets.getOrNull(nextPosition)?.weight,
                    reps = exercise.planSets.getOrNull(nextPosition)?.reps ?: 0,
                    type = exercise.planSets.getOrNull(nextPosition)?.type ?: SetTypeUiModel.WORK,
                    isDone = false,
                )
            val key = State.DraftKey(action.performedExerciseUuid, nextPosition)
            latest.copy(
                setDrafts = (latest.setDrafts + (key to seed)).toImmutableMap(),
            )
        }
    }

    private fun processEditPlan(action: Action.Click.OnEditPlan) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            val exercise = latest.findExercise(action.performedExerciseUuid) ?: return@updateState latest
            val initial = exercise.planSets
            latest.copy(
                planEditorTarget = State.PlanEditorTarget(
                    performedExerciseUuid = exercise.performedExerciseUuid,
                    exerciseUuid = exercise.exerciseUuid,
                    exerciseName = exercise.exerciseName,
                    exerciseType = exercise.exerciseType,
                    initialPlan = initial,
                    draft = initial,
                ),
            )
        }
    }

    private fun processResetSetsAsk(action: Action.Click.OnResetSets) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(pendingResetExerciseUuid = action.performedExerciseUuid) }
        sendEvent(Event.ShowResetSetsConfirmDialog)
    }

    private fun processResetSetsConfirm(action: Action.Click.OnResetSetsConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> latest.applyResetSets(action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendEvent(Event.ShowError(message = "reset_failed")) },
        ) {
            interactor.resetExerciseSets(action.performedExerciseUuid)
        }
    }

    private fun processResetSetsDismiss() {
        updateState { it.copy(pendingResetExerciseUuid = null) }
    }

    private fun processSkipExerciseAsk(action: Action.Click.OnSkipExercise) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(pendingSkipExerciseUuid = action.performedExerciseUuid) }
        sendEvent(Event.ShowSkipExerciseConfirmDialog)
    }

    private fun processSkipExerciseConfirm(action: Action.Click.OnSkipExerciseConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> latest.applySkip(action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendEvent(Event.ShowError(message = "skip_failed")) },
        ) {
            interactor.setSkipped(action.performedExerciseUuid, skipped = true)
        }
    }

    private fun processSkipExerciseDismiss() {
        updateState { it.copy(pendingSkipExerciseUuid = null) }
    }

    private fun processFinishClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val stats = State.FinishStats(
            durationMillis = current.elapsedMillis,
            doneCount = current.exercises.count {
                it.status == ExerciseStatusUiModel.DONE
            },
            totalCount = current.exercises.size,
            skippedCount = current.exercises.count {
                it.status == ExerciseStatusUiModel.SKIPPED
            },
            setsLogged = current.exercises.sumOf { it.performedSets.count { set -> set.isDone } },
        )
        updateState { it.copy(pendingFinishConfirm = stats) }
        sendEvent(Event.ShowFinishConfirmDialog)
    }

    private fun processFinishConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val sessionUuid = state.value.sessionUuid ?: return
        launch(
            onSuccess = { result ->
                if (result == null) {
                    sendEvent(Event.ShowError(message = "finish_missing_session"))
                    return@launch
                }
                val stats = State.FinishStats(
                    durationMillis = result.durationMillis,
                    doneCount = result.doneCount,
                    totalCount = result.totalCount,
                    skippedCount = result.skippedCount,
                    setsLogged = result.setsLogged,
                )
                sendEvent(Event.ShowSessionSavedSnackbar(stats))
                consume(Action.Navigation.Back)
            },
            onError = { _ -> sendEvent(Event.ShowError(message = "finish_failed")) },
        ) {
            interactor.finishSession(sessionUuid)
        }
    }

    private fun processFinishDismiss() {
        updateState { it.copy(pendingFinishConfirm = null) }
    }

    private fun processCancelClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(pendingCancelConfirm = true) }
        sendEvent(Event.ShowCancelSessionConfirmDialog)
    }

    private fun processCancelConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val sessionUuid = state.value.sessionUuid ?: run {
            consume(Action.Navigation.Back)
            return
        }
        launch(
            onSuccess = { consume(Action.Navigation.Back) },
            onError = { _ -> sendEvent(Event.ShowError(message = "cancel_failed")) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processCancelDismiss() {
        updateState { it.copy(pendingCancelConfirm = false) }
    }

    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        // Reserved for collapse/expand toggling on completed exercises. Stage 5.4 keeps
        // this as a no-op so the click acts as a stable anchor for analytics.
        @Suppress("UNUSED_VARIABLE")
        val ignored = action
    }

    private fun State.findExercise(performedExerciseUuid: String): LiveExerciseUiModel? =
        exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }

    private fun State.draftFor(performedExerciseUuid: String, position: Int): LiveSetUiModel {
        val key = State.DraftKey(performedExerciseUuid, position)
        setDrafts[key]?.let { return it }
        val exercise = findExercise(performedExerciseUuid)
        val performed = exercise?.performedSets?.firstOrNull { it.position == position }
        if (performed != null) return performed
        val plan = exercise?.planSets?.getOrNull(position)
        return LiveSetUiModel(
            position = position,
            weight = plan?.weight,
            reps = plan?.reps ?: 0,
            type = plan?.type ?: SetTypeUiModel.WORK,
            isDone = false,
        )
    }

    private fun State.applySetMarked(
        performedExerciseUuid: String,
        position: Int,
        draft: LiveSetUiModel,
    ): State {
        val nextDrafts = setDrafts.toMutableMap()
        nextDrafts.remove(State.DraftKey(performedExerciseUuid, position))
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets.toMutableList()
            val existingIdx = nextSets.indexOfFirst { it.position == position }
            val marked = draft.copy(position = position, isDone = true)
            if (existingIdx >= 0) {
                nextSets[existingIdx] = marked
            } else {
                nextSets.add(marked)
                nextSets.sortBy { it.position }
            }
            exercise.copy(performedSets = nextSets.toImmutableList())
        }.toImmutableList()
        return copy(
            exercises = updated,
            setDrafts = nextDrafts.toImmutableMap(),
        ).recomputeStatuses()
    }

    private fun State.applySetUnchecked(
        performedExerciseUuid: String,
        position: Int,
    ): State {
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets
                .filterNot { it.position == position }
                .mapIndexed { idx, set -> set.copy(position = idx) }
                .toImmutableList()
            exercise.copy(performedSets = nextSets)
        }.toImmutableList()
        return copy(exercises = updated).recomputeStatuses()
    }

    private fun State.applySetTypeChange(
        performedExerciseUuid: String,
        position: Int,
        type: SetTypeUiModel,
    ): State {
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets.map { set ->
                if (set.position == position) set.copy(type = type) else set
            }.toImmutableList()
            exercise.copy(performedSets = nextSets)
        }.toImmutableList()
        return copy(exercises = updated)
    }

    private fun State.applyDraftTypeChange(
        performedExerciseUuid: String,
        position: Int,
        type: SetTypeUiModel,
    ): State {
        val key = State.DraftKey(performedExerciseUuid, position)
        val existing = setDrafts[key] ?: LiveSetUiModel(
            position = position,
            weight = null,
            reps = 0,
            type = type,
            isDone = false,
        )
        return copy(
            setDrafts = (setDrafts + (key to existing.copy(type = type))).toImmutableMap(),
        )
    }

    private fun State.applyResetSets(performedExerciseUuid: String): State {
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            exercise.copy(performedSets = persistentListOf())
        }.toImmutableList()
        val nextDrafts = setDrafts.filterKeys { it.performedExerciseUuid != performedExerciseUuid }
            .toImmutableMap()
        return copy(
            exercises = updated,
            setDrafts = nextDrafts,
            pendingResetExerciseUuid = null,
        ).recomputeStatuses()
    }

    private fun State.applySkip(performedExerciseUuid: String): State {
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            exercise.copy(performedSets = persistentListOf())
        }.toImmutableList()
        val nextDrafts = setDrafts.filterKeys { it.performedExerciseUuid != performedExerciseUuid }
            .toImmutableMap()
        return copy(
            exercises = updated,
            setDrafts = nextDrafts,
            pendingSkipExerciseUuid = null,
        ).markSkipped(performedExerciseUuid)
    }

    private fun State.markSkipped(performedExerciseUuid: String): State {
        // Reproduce the snapshot → status pipeline against the in-memory state. The
        // exercise's own `status` field is recomputed alongside everything else so the
        // CURRENT marker walks past the now-skipped row.
        val rebuilt = exercises.toUiListAfterSkip(performedExerciseUuid)
        return copy(exercises = rebuilt)
    }

    private fun ImmutableListOfExercise.toUiListAfterSkip(skippedUuid: String): ImmutableListOfExercise =
        map { exercise ->
            if (exercise.performedExerciseUuid == skippedUuid) {
                exercise.copy(
                    status = ExerciseStatusUiModel.SKIPPED,
                )
            } else {
                exercise
            }
        }.toImmutableList().recomputeOnly()

    private fun ImmutableListOfExercise.recomputeOnly(): ImmutableListOfExercise {
        var foundCurrent = false
        return map { exercise ->
            val planSets = exercise.planSets
            val performed = exercise.performedSets
            val skipped = exercise.status == ExerciseStatusUiModel.SKIPPED
            val isDone = !skipped &&
                planSets.isNotEmpty() &&
                performed.size >= planSets.size &&
                performed.all { it.isDone }
            val nextStatus = when {
                skipped -> ExerciseStatusUiModel.SKIPPED
                isDone -> ExerciseStatusUiModel.DONE
                !foundCurrent -> {
                    foundCurrent = true
                    ExerciseStatusUiModel.CURRENT
                }

                else -> ExerciseStatusUiModel.PENDING
            }
            exercise.copy(status = nextStatus)
        }.toImmutableList()
    }

    private fun State.recomputeStatuses(): State = copy(exercises = exercises.recomputeOnly())
}

private typealias ImmutableListOfExercise = kotlinx.collections.immutable.ImmutableList<LiveExerciseUiModel>

@Suppress("UNUSED_PARAMETER")
private fun PlanSetUiModel.unused() = Unit
