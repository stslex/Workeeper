// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.toFinishStats
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.withPresentation
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongMethod")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
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
        current.findExercise(action.performedExerciseUuid) ?: return
        val seedDraft = current.draftFor(action.performedExerciseUuid, action.position)
        if (seedDraft.reps <= 0) {
            sendError(ErrorType.InvalidReps)
            return
        }
        val planSet = PlanSetDataModel(
            weight = seedDraft.weight,
            reps = seedDraft.reps,
            type = seedDraft.type.toData(),
        )
        // Optimistic UI: flip the row to done immediately so the checkbox tap feels snappy.
        updateState { latest ->
            latest.applySetMarked(
                action.performedExerciseUuid,
                action.position,
                seedDraft,
            )
        }
        launch(
            onError = { _ ->
                sendError(ErrorType.SetSaveFailed)
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
    }

    private fun processSetUncheck(action: Action.Click.OnSetUncheck) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            latest.applySetUnchecked(
                action.performedExerciseUuid,
                action.position,
            )
        }
        launch(
            onError = { _ -> sendError(ErrorType.SetDeleteFailed) },
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
                onError = { _ -> sendError(ErrorType.SetSaveFailed) },
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
        updateState { latest ->
            latest.applySetUnchecked(
                action.performedExerciseUuid,
                action.position,
            )
        }
        launch(
            onError = { _ -> sendError(ErrorType.SetDeleteFailed) },
        ) {
            interactor.deleteSet(action.performedExerciseUuid, action.position)
        }
    }

    private fun processAddSet(action: Action.Click.OnAddSet) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { latest ->
            val exercise =
                latest.findExercise(action.performedExerciseUuid) ?: return@updateState latest
            val nextPosition = latest.nextSetPosition(exercise)
            val seed = latest.lastKnownSetSeed(exercise)?.copy(
                position = nextPosition,
                isDone = false,
            ) ?: LiveSetUiModel(
                position = nextPosition,
                weight = null,
                reps = 0,
                type = SetTypeUiModel.WORK,
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
            val exercise =
                latest.findExercise(action.performedExerciseUuid) ?: return@updateState latest
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
        sendEvent(
            Event.ShowResetSetsConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_reset_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_reset_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_reset_dismiss),
                ),
            ),
        )
    }

    private fun processResetSetsConfirm(action: Action.Click.OnResetSetsConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> latest.applyResetSets(action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.ResetFailed) },
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
        sendEvent(
            Event.ShowSkipExerciseConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_skip_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_skip_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_skip_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_skip_dismiss),
                ),
            ),
        )
    }

    private fun processSkipExerciseConfirm(action: Action.Click.OnSkipExerciseConfirm) {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { latest -> latest.applySkip(action.performedExerciseUuid) }
        launch(
            onError = { _ -> sendError(ErrorType.SkipFailed) },
        ) {
            interactor.setSkipped(action.performedExerciseUuid, skipped = true)
        }
    }

    private fun processSkipExerciseDismiss() {
        updateState { it.copy(pendingSkipExerciseUuid = null) }
    }

    private fun processFinishClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val stats = state.value.toFinishStats(resourceWrapper)
        updateState { it.copy(pendingFinishConfirm = stats) }
        sendEvent(Event.ShowFinishConfirmDialog)
    }

    private fun processFinishConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.Confirm))
        val sessionUuid = state.value.sessionUuid ?: return
        launch(
            onSuccess = { result ->
                if (result == null) {
                    sendError(ErrorType.FinishMissingSession)
                    return@launch
                }
                sendEvent(
                    Event.ShowSessionSavedSnackbar(
                        message = resourceWrapper.getString(R.string.feature_live_workout_finish_success),
                    ),
                )
                consumeOnMain(Action.Navigation.OpenPastSession(sessionUuid = sessionUuid))
            },
            onError = { _ -> sendError(ErrorType.FinishFailed) },
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
        sendEvent(
            Event.ShowCancelSessionConfirmDialog(
                dialog = LiveWorkoutStore.ConfirmDialog(
                    title = resourceWrapper.getString(R.string.feature_live_workout_cancel_title),
                    body = resourceWrapper.getString(R.string.feature_live_workout_cancel_body),
                    confirmLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_confirm),
                    dismissLabel = resourceWrapper.getString(R.string.feature_live_workout_cancel_dismiss),
                ),
            ),
        )
    }

    private fun processCancelConfirm() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val sessionUuid = state.value.sessionUuid ?: run {
            consume(Action.Navigation.Back)
            return
        }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ -> sendError(ErrorType.CancelFailed) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processCancelDismiss() {
        updateState { it.copy(pendingCancelConfirm = false) }
    }

    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val exercise =
                current.findExercise(action.performedExerciseUuid) ?: return@updateState current
            if (exercise.status != ExerciseStatusUiModel.DONE) return@updateState current
            val next = current.expandedDoneExerciseUuids.toMutableSet()
            if (!next.add(action.performedExerciseUuid)) {
                next.remove(action.performedExerciseUuid)
            }
            current.copy(expandedDoneExerciseUuids = next.toImmutableSet())
        }
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
        return copy(exercises = rebuilt).withPresentation(resourceWrapper)
    }

    private fun State.nextSetPosition(exercise: LiveExerciseUiModel): Int {
        val draftMax = setDrafts.keys
            .filter { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxOfOrNull { it.position } ?: -1
        val doneMax = exercise.performedSets.maxOfOrNull { it.position } ?: -1
        val planMax = exercise.planSets.lastIndex
        return maxOf(draftMax, doneMax, planMax) + 1
    }

    private fun State.lastKnownSetSeed(exercise: LiveExerciseUiModel): LiveSetUiModel? {
        val draftMax = setDrafts
            .filterKeys { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxByOrNull { it.key.position }?.value
        val doneMax = exercise.performedSets.maxByOrNull { it.position }
        val planMax = exercise.planSets
            .withIndex()
            .lastOrNull()
            ?.let { (position, plan) ->
                LiveSetUiModel(
                    position = position,
                    weight = plan.weight,
                    reps = plan.reps,
                    type = plan.type,
                    isDone = false,
                )
            }
        return sequenceOf(draftMax, doneMax, planMax)
            .filterNotNull()
            .maxByOrNull { it.position }
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
            val performedByPosition = performed.associateBy { it.position }
            val isDone = !skipped && if (planSets.isEmpty()) {
                performed.any { it.isDone }
            } else {
                performed.size >= planSets.size &&
                    planSets.indices.all { index -> performedByPosition[index]?.isDone == true }
            }
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

    private fun State.recomputeStatuses(): State {
        val refreshed = exercises.recomputeOnly()
        val doneUuids = refreshed
            .asSequence()
            .filter { it.status == ExerciseStatusUiModel.DONE }
            .map { it.performedExerciseUuid }
            .toSet()
        return copy(
            exercises = refreshed,
            expandedDoneExerciseUuids = expandedDoneExerciseUuids
                .filter { it in doneUuids }
                .toImmutableSet(),
        ).withPresentation(resourceWrapper)
    }

    private fun sendError(type: ErrorType) {
        sendEvent(
            Event.ShowError(
                message = resourceWrapper.getString(type.msgRes),
            ),
        )
    }
}

private typealias ImmutableListOfExercise = kotlinx.collections.immutable.ImmutableList<LiveExerciseUiModel>

@Suppress("UNUSED_PARAMETER")
private fun PlanSetUiModel.unused() = Unit
