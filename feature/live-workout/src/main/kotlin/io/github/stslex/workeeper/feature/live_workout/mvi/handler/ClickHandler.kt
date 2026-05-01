// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.sets.PrComparator
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
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

@Suppress("TooManyFunctions", "LongMethod", "LargeClass")
@ViewModelScoped
// TODO(tech-debt): v2.7 decomposition pass — this handler legitimately gained
// training-name + empty-finish + add-exercise dispatch in v2.3 (per spec); further
// splits (TrainingNameHandler, EmptyFinishHandler) belong with the rest of the
// live-workout feature decomposition.
internal class ClickHandler @Inject constructor(
    private val interactor: LiveWorkoutInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val pickerHandler: ExercisePickerHandler,
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
            Action.Click.OnDeleteSessionMenuClick -> processDeleteSessionMenuClick()
            Action.Click.OnDeleteSessionConfirm -> processDeleteSessionConfirm()
            Action.Click.OnDeleteSessionDismiss -> processDeleteSessionDismiss()
            is Action.Click.OnExerciseHeaderClick -> processExerciseHeaderClick(action)
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnTrainingNameTap -> processTrainingNameTap()
            is Action.Click.OnTrainingNameChange -> processTrainingNameChange(action)
            is Action.Click.OnTrainingNameSubmit -> processTrainingNameSubmit(action)
            Action.Click.OnTrainingNameDismiss -> processTrainingNameDismiss()
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            is Action.Click.PickerAction -> pickerHandler.invoke(action.action)
            Action.Click.OnEmptyFinishDiscard -> processEmptyFinishDiscard()
            Action.Click.OnEmptyFinishContinue -> processEmptyFinishContinue()
        }
    }

    private fun processAddExerciseClick() {
        val current = state.value
        if (!current.canAddExercise) return
        if (current.sessionUuid.isNullOrBlank() || current.trainingUuid.isNullOrBlank()) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        pickerHandler.open()
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        // Spec dismissal order: picker → empty-finish dialog → name edit → plan-editor
        // dirty → default back.
        val current = state.value
        if (current.isPickerVisible) {
            pickerHandler.invoke(ExercisePickerAction.OnDismiss)
            return
        }
        if (current.isEmptyFinishDialogVisible) {
            processEmptyFinishContinue()
            return
        }
        if (current.isTrainingNameEditing) {
            // Submit on back so the keyboard dismiss flow persists changes (per A1
            // "save on blur via tap-out, IME Done, or back-dismissed keyboard").
            processTrainingNameSubmit(Action.Click.OnTrainingNameSubmit(current.trainingNameDraft))
            return
        }
        if (current.isPlanEditorDirty) return
        consume(Action.Navigation.Back)
    }

    private fun processTrainingNameTap() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                isTrainingNameEditing = true,
                trainingNameDraft = current.trainingName,
            )
        }
    }

    private fun processTrainingNameChange(action: Action.Click.OnTrainingNameChange) {
        updateState { it.copy(trainingNameDraft = action.text) }
    }

    private fun processTrainingNameSubmit(action: Action.Click.OnTrainingNameSubmit) {
        val trimmed = action.text.trim()
        val current = state.value
        val trainingUuid = current.trainingUuid
        // Optimistic UI: persist trimmed value into State before the DB write so the header
        // stops bouncing back to the old name during the suspend round trip.
        val updatedLabel = trimmed.ifBlank {
            resourceWrapper.getString(R.string.feature_live_workout_training_name_placeholder)
        }
        updateState { latest ->
            latest.copy(
                trainingName = trimmed,
                trainingNameDraft = trimmed,
                trainingNameLabel = updatedLabel,
                isTrainingNameEditing = false,
            )
        }
        if (trainingUuid.isNullOrBlank() || trimmed == current.trainingName) return
        launch(
            onError = { _ -> sendError(ErrorType.TrainingNameSaveFailed) },
        ) {
            interactor.updateTrainingName(trainingUuid, trimmed)
        }
    }

    private fun processTrainingNameDismiss() {
        // Revert path — used when the keyboard is dismissed without commit (we currently
        // route every blur through Submit, so this fires only from explicit Cancel triggers
        // a future iteration may wire up).
        updateState { current ->
            current.copy(
                isTrainingNameEditing = false,
                trainingNameDraft = current.trainingName,
            )
        }
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
        val current = state.value
        if (current.isSessionEmpty) {
            // E1 lock — empty-finish branches into a confirm dialog. Discard CTA is enabled
            // only when the parent training is ad-hoc, so library training sessions get the
            // Continue-editing-only variant.
            updateState {
                it.copy(
                    emptyFinishDialog = State.EmptyFinishDialogState.Visible(
                        canDiscard = it.isAdhoc,
                    ),
                )
            }
            return
        }
        val stats = current.toFinishStats(resourceWrapper)
        updateState { it.copy(pendingFinishConfirm = stats) }
        sendEvent(Event.ShowFinishConfirmDialog)
    }

    private fun processEmptyFinishContinue() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(emptyFinishDialog = State.EmptyFinishDialogState.Hidden) }
    }

    private fun processEmptyFinishDiscard() {
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        val current = state.value
        val sessionUuid = current.sessionUuid
        val trainingUuid = current.trainingUuid
        // Defence: discard cascade only fires for ad-hoc trainings. Library sessions can't
        // reach this path (canDiscard = false in the dialog), but we double-check before
        // calling the cascade DAO write.
        if (!current.isAdhoc || sessionUuid.isNullOrBlank() || trainingUuid.isNullOrBlank()) {
            updateState { it.copy(emptyFinishDialog = State.EmptyFinishDialogState.Hidden) }
            return
        }
        updateState {
            it.copy(
                emptyFinishDialog = State.EmptyFinishDialogState.Hidden,
                isFinishInFlight = true,
            )
        }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ ->
                updateState { it.copy(isFinishInFlight = false) }
                sendError(ErrorType.DiscardSessionFailed)
            },
        ) {
            interactor.discardAdhocSession(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
            )
        }
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
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
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

    private fun processDeleteSessionMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(deleteDialogVisible = true) }
    }

    private fun processDeleteSessionConfirm() {
        val sessionUuid = state.value.sessionUuid ?: run {
            updateState { it.copy(deleteDialogVisible = false) }
            return
        }
        sendEvent(Event.HapticImpact(HapticFeedbackType.LongPress))
        updateState { it.copy(deleteDialogVisible = false) }
        launch(
            onSuccess = { consumeOnMain(Action.Navigation.Back) },
            onError = { _ -> sendError(ErrorType.CancelFailed) },
        ) {
            interactor.cancelSession(sessionUuid)
        }
    }

    private fun processDeleteSessionDismiss() {
        updateState { it.copy(deleteDialogVisible = false) }
    }

    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val exercise = current.findExercise(action.performedExerciseUuid)
                ?: return@updateState current
            when (exercise.status) {
                ExerciseStatusUiModel.SKIPPED -> current
                ExerciseStatusUiModel.PENDING -> {
                    // Promote to active. Status flips to CURRENT, card expands.
                    val activeNext = current.activeExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    val expandedNext = current.expandedExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    current.copy(
                        activeExerciseUuids = activeNext.toImmutableSet(),
                        expandedExerciseUuids = expandedNext.toImmutableSet(),
                    ).recomputeStatuses()
                }

                ExerciseStatusUiModel.CURRENT -> {
                    // Toggle expanded. If it's the auto-default (not yet in activeUuids),
                    // also promote to explicit-active so the user can later collapse it.
                    val expandedNext = current.expandedExerciseUuids.toMutableSet()
                    if (!expandedNext.add(action.performedExerciseUuid)) {
                        expandedNext.remove(action.performedExerciseUuid)
                    }
                    val activeNext = current.activeExerciseUuids.toMutableSet().apply {
                        add(action.performedExerciseUuid)
                    }
                    current.copy(
                        activeExerciseUuids = activeNext.toImmutableSet(),
                        expandedExerciseUuids = expandedNext.toImmutableSet(),
                    )
                }

                ExerciseStatusUiModel.DONE -> {
                    val expandedNext = current.expandedExerciseUuids.toMutableSet()
                    if (!expandedNext.add(action.performedExerciseUuid)) {
                        expandedNext.remove(action.performedExerciseUuid)
                    }
                    current.copy(expandedExerciseUuids = expandedNext.toImmutableSet())
                }
            }
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
            val baseline = preSessionPrSnapshot[exercise.exerciseUuid]
            val candidate = PlanSetDataModel(
                weight = draft.weight,
                reps = draft.reps,
                type = draft.type.toData(),
            )
            val isPr = PrComparator.beats(
                candidate = candidate,
                baselineWeight = baseline?.weight,
                baselineReps = baseline?.reps,
                type = exercise.exerciseType.toData(),
                hasBaseline = baseline != null,
            )
            val marked = draft.copy(
                position = position,
                isDone = true,
                isPersonalRecord = isPr,
            )
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
        // CURRENT marker walks past the now-skipped row, while honoring the user's
        // explicit active set.
        val rebuilt = exercises.toUiListAfterSkip(performedExerciseUuid, activeExerciseUuids)
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

    private fun ImmutableListOfExercise.toUiListAfterSkip(
        skippedUuid: String,
        activeUuids: Set<String>,
    ): ImmutableListOfExercise =
        map { exercise ->
            if (exercise.performedExerciseUuid == skippedUuid) {
                exercise.copy(
                    status = ExerciseStatusUiModel.SKIPPED,
                )
            } else {
                exercise
            }
        }.toImmutableList().recomputeOnly(activeUuids)

    private fun ImmutableListOfExercise.recomputeOnly(activeUuids: Set<String>): ImmutableListOfExercise {
        val computed = map { exercise ->
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
            Triple(exercise, isDone, skipped)
        }
        // Auto-default mirrors the mapper: if no exercise is explicitly active, the first
        // non-skipped non-done row stays CURRENT.
        val autoCurrentUuid = if (activeUuids.isEmpty()) {
            computed.firstOrNull { !it.third && !it.second }?.first?.performedExerciseUuid
        } else null
        return computed.map { (exercise, isDone, skipped) ->
            val nextStatus = when {
                skipped -> ExerciseStatusUiModel.SKIPPED
                isDone -> ExerciseStatusUiModel.DONE
                exercise.performedExerciseUuid in activeUuids ||
                    exercise.performedExerciseUuid == autoCurrentUuid ->
                    ExerciseStatusUiModel.CURRENT

                else -> ExerciseStatusUiModel.PENDING
            }
            exercise.copy(status = nextStatus)
        }.toImmutableList()
    }

    private fun State.recomputeStatuses(): State {
        val refreshed = exercises.recomputeOnly(activeExerciseUuids)
        // Prune expanded entries that no longer correspond to a DONE or CURRENT row.
        // SKIPPED/PENDING rows can't be expanded so any leftover entry is stale.
        val visibleUuids = refreshed
            .asSequence()
            .filter {
                it.status == ExerciseStatusUiModel.DONE ||
                    it.status == ExerciseStatusUiModel.CURRENT
            }
            .map { it.performedExerciseUuid }
            .toSet()
        return copy(
            exercises = refreshed,
            expandedExerciseUuids = expandedExerciseUuids
                .filter { it in visibleUuids }
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
