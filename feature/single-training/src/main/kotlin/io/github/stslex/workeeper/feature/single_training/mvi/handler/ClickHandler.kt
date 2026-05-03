// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LongMethod")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    private val resourceWrapper: ResourceWrapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    store: SingleTrainingHandlerStore,
) : Handler<Action.Click>, SingleTrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnEditClick -> processEditClick()
            Action.Click.OnArchiveClick -> processArchiveClick()
            Action.Click.OnPermanentDeleteClick -> processPermanentDeleteMenu()
            Action.Click.OnPermanentDeleteConfirm -> processPermanentDeleteConfirm()
            Action.Click.OnPermanentDeleteDismiss -> Unit
            Action.Click.OnStartSessionClick -> processStartSession()
            Action.Click.OnConflictResume -> processConflictResume()
            Action.Click.OnConflictDeleteAndStart -> processConflictDeleteAndStart()
            Action.Click.OnConflictDismiss -> processConflictDismiss()
            is Action.Click.OnExerciseRowClick -> processExerciseRowClick(action)
            is Action.Click.OnPastSessionClick -> processPastSessionClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processBackClick()
            Action.Click.OnConfirmDiscard -> processConfirmDiscard()
            Action.Click.OnDismissDiscard -> Unit
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            is Action.Click.OnExerciseRemove -> processExerciseRemove(action)
            is Action.Click.OnExerciseReorder -> processExerciseReorder(action)
            is Action.Click.OnEditPlanClick -> processEditPlanClick(action)
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
            Action.Click.OnPickerDismiss -> processPickerDismiss()
            is Action.Click.OnPickerToggle -> processPickerToggle(action)
            Action.Click.OnPickerConfirm -> processPickerConfirm()
        }
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        // Plan editor is the inner-most surface — its draft takes priority over the
        // training-level dirty check. Both share the same Event.ShowDiscardConfirmDialog
        // surface; the OnConfirmDiscard handler routes to the right surface from state.
        if (current.isPlanEditorDirty) {
            sendEvent(Event.ShowDiscardConfirmDialog)
            return
        }
        if (current.mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        if (current.hasChanges) {
            sendEvent(Event.ShowDiscardConfirmDialog)
        } else {
            applyDiscard()
        }
    }

    private fun processEditClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                mode = Mode.Edit(isCreate = false),
                originalSnapshot = current.toSnapshot(),
            )
        }
    }

    private fun processArchiveClick() {
        val uuid = state.value.uuid ?: return
        val name = state.value.name
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        launch(
            onSuccess = { result ->
                when (result) {
                    ArchiveResult.Success -> {
                        sendEvent(
                            Event.ShowArchiveSuccess(
                                message = resourceWrapper.getString(
                                    R.string.feature_training_detail_archive_success_format,
                                    name,
                                ),
                            ),
                        )
                        withContext(mainDispatcher) { consume(Action.Navigation.Back) }
                    }

                    is ArchiveResult.Blocked ->
                        sendEvent(
                            Event.ShowArchiveBlocked(
                                message = resourceWrapper.getString(
                                    R.string.feature_training_detail_archive_blocked,
                                ),
                            ),
                        )
                }
            },
        ) {
            interactor.archive(uuid)
        }
    }

    private fun processPermanentDeleteMenu() {
        if (!state.value.canPermanentlyDelete) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        sendEvent(
            Event.ShowPermanentDeleteConfirmDialog(
                title = resourceWrapper.getString(
                    R.string.feature_training_detail_permanent_delete_title,
                    state.value.name,
                ),
                body = resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_body),
                impactSummary = resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_impact),
                confirmLabel = resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_confirm),
            ),
        )
    }

    private fun processPermanentDeleteConfirm() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        launch(
            onSuccess = {
                withContext(mainDispatcher) { consume(Action.Navigation.Back) }
            },
        ) {
            interactor.permanentlyDelete(uuid)
        }
    }

    private fun processStartSession() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val trainingUuid = current.uuid ?: return
        launch {
            when (val resolution = interactor.resolveStartSessionConflict(trainingUuid)) {
                SessionConflictResolver.Resolution.ProceedFresh -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(sessionUuid = ""),
                )

                is SessionConflictResolver.Resolution.SilentResume -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(sessionUuid = resolution.sessionUuid),
                )

                is SessionConflictResolver.Resolution.NeedsUserChoice -> {
                    val info = State.ConflictInfo(
                        sessionUuid = resolution.active.sessionUuid,
                        activeSessionName = current.name.takeIf { it.isNotBlank() }
                            ?: resourceWrapper.getString(
                                R.string.feature_training_detail_conflict_unnamed,
                            ),
                        progressLabel = resourceWrapper.getString(
                            R.string.feature_training_detail_conflict_progress_format,
                            0,
                            0,
                        ),
                    )
                    updateStateImmediate { it.copy(pendingConflict = info) }
                    sendEvent(
                        Event.ShowActiveSessionConflict(
                            activeSessionName = info.activeSessionName,
                            progressLabel = info.progressLabel,
                        ),
                    )
                }
            }
        }
    }

    private fun processConflictResume() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val info = state.value.pendingConflict ?: return
        updateState { it.copy(pendingConflict = null) }
        consume(Action.Navigation.OpenLiveWorkout(sessionUuid = info.sessionUuid))
    }

    private fun processConflictDeleteAndStart() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val info = state.value.pendingConflict ?: return
        updateState { it.copy(pendingConflict = null) }
        launch {
            interactor.deleteSession(info.sessionUuid)
            consumeOnMain(Action.Navigation.OpenLiveWorkout(sessionUuid = ""))
        }
    }

    private fun processConflictDismiss() {
        updateState { it.copy(pendingConflict = null) }
    }

    private fun processExerciseRowClick(action: Action.Click.OnExerciseRowClick) {
        if (state.value.mode is Mode.Edit) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenExerciseDetail(action.exerciseUuid))
    }

    private fun processPastSessionClick(action: Action.Click.OnPastSessionClick) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSession(action.sessionUuid))
    }

    private fun processSaveClick() {
        val current = state.value
        if (current.name.isBlank()) {
            updateState { it.copy(nameError = true) }
            return
        }
        if (current.exercises.isEmpty()) {
            sendEvent(
                Event.ShowSaveError(
                    message = resourceWrapper.getString(R.string.feature_training_edit_error_no_exercises),
                ),
            )
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val resolvedUuid = current.uuid?.takeIf { it.isNotBlank() } ?: Uuid.random().toString()
        val snapshot = TrainingChangeDataModel(
            uuid = resolvedUuid,
            name = current.name.trim(),
            description = current.description.takeIf { it.isNotBlank() },
            isAdhoc = false,
            archived = false,
            timestamp = System.currentTimeMillis(),
            labels = current.tags.map { it.name },
            exerciseUuids = current.exercises.sortedBy { it.position }.map { it.exerciseUuid },
        )
        val isCreate = current.isCreate
        launch(
            onSuccess = {
                if (isCreate) {
                    withContext(mainDispatcher) { consume(Action.Navigation.Back) }
                } else {
                    updateStateImmediate { latest ->
                        latest.copy(
                            uuid = resolvedUuid,
                            mode = Mode.Read,
                            originalSnapshot = latest.toSnapshot(),
                        )
                    }
                }
            },
        ) {
            interactor.saveTraining(snapshot)
        }
    }

    private fun processConfirmDiscard() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        // Same OnConfirmDiscard action covers both the training-level form and the
        // plan-editor sheet; route based on which is currently dirty.
        if (state.value.isPlanEditorDirty) {
            updateState { it.copy(planEditorTarget = null) }
            return
        }
        applyDiscard()
    }

    private fun applyDiscard() {
        val current = state.value
        val mode = current.mode as? Mode.Edit ?: run {
            consume(Action.Navigation.Back)
            return
        }
        if (mode.isCreate) {
            consume(Action.Navigation.Back)
        } else {
            // Roll the form back to the loaded snapshot and flip into Read mode.
            updateState { latest -> latest.applySnapshotOrPop() }
        }
    }

    private fun State.applySnapshotOrPop(): State {
        val snapshot = originalSnapshot ?: return copy(mode = Mode.Read)
        val matchedTags = availableTags
            .filter { tag -> tag.uuid in snapshot.tagUuids }
            .toImmutableList()
        return copy(
            mode = Mode.Read,
            name = snapshot.name,
            nameError = false,
            description = snapshot.description,
            tags = matchedTags,
            tagSearchQuery = "",
            // Drop in-progress order/exercises by re-snapshotting; the loader already
            // cached the canonical set in `originalSnapshot`. We resolve the exercises
            // list lazily by keeping the existing order matching the snapshot signature.
            exercises = exercises
                .filter { exercise ->
                    snapshot.exerciseSignature.any { it.exerciseUuid == exercise.exerciseUuid }
                }
                .sortedBy { exercise ->
                    snapshot.exerciseSignature
                        .firstOrNull { it.exerciseUuid == exercise.exerciseUuid }
                        ?.position
                        ?: Int.MAX_VALUE
                }
                .toImmutableList(),
        )
    }

    private fun processAddExerciseClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        launch(
            onSuccess = { results ->
                updateStateImmediate { latest ->
                    latest.copy(
                        pickerState = PickerState.Open(
                            query = "",
                            results = results.map { picker ->
                                PickerExerciseItem(
                                    uuid = picker.exercise.uuid,
                                    name = picker.exercise.name,
                                    type = picker.exercise.type.toUi(),
                                    tags = picker.labels.toImmutableList(),
                                )
                            }.toImmutableList(),
                            selectedUuids = persistentListOf(),
                        ),
                    )
                }
            },
        ) {
            interactor.searchExercisesForPicker(
                query = "",
                excludeUuids = current.exercises.map { it.exerciseUuid }.toSet(),
            )
        }
    }

    private fun processExerciseRemove(action: Action.Click.OnExerciseRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                exercises = current.exercises
                    .filterNot { it.exerciseUuid == action.exerciseUuid }
                    .mapIndexed { index, item -> item.copy(position = index) }
                    .toImmutableList(),
            )
        }
    }

    private fun processExerciseReorder(action: Action.Click.OnExerciseReorder) {
        if (action.from == action.to) return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { current ->
            val mutable = current.exercises.toMutableList()
            val from = action.from.coerceIn(0, mutable.lastIndex)
            val to = action.to.coerceIn(0, mutable.lastIndex)
            val removed = mutable.removeAt(from)
            mutable.add(to, removed)
            current.copy(
                exercises = mutable
                    .mapIndexed { index, item -> item.copy(position = index) }
                    .toImmutableList(),
            )
        }
    }

    private fun processEditPlanClick(action: Action.Click.OnEditPlanClick) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val target = state.value.exercises.firstOrNull { it.exerciseUuid == action.exerciseUuid }
            ?: return
        val initial = target.planSets ?: persistentListOf()
        updateState { current ->
            current.copy(
                planEditorTarget = State.PlanEditorTarget(
                    exerciseUuid = target.exerciseUuid,
                    exerciseName = target.exerciseName,
                    exerciseType = target.exerciseType,
                    initialPlan = initial,
                    draft = initial,
                ),
            )
        }
    }

    private fun processTagToggle(action: Action.Click.OnTagToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val tag = current.availableTags.firstOrNull { it.uuid == action.tagUuid }
                ?: return@updateState current
            val tags = if (current.tags.any { it.uuid == action.tagUuid }) {
                current.tags.filterNot { it.uuid == action.tagUuid }.toImmutableList()
            } else {
                (current.tags + tag).toImmutableList()
            }
            current.copy(tags = tags)
        }
    }

    private fun processTagRemove(action: Action.Click.OnTagRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                tags = current.tags.filterNot { it.uuid == action.tagUuid }.toImmutableList(),
            )
        }
    }

    private fun processTagCreate(action: Action.Click.OnTagCreate) {
        if (action.name.isBlank()) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        launch(
            onSuccess = { tag ->
                updateStateImmediate { current ->
                    current.copy(
                        tags = (
                            current.tags + TagUiModel(
                                uuid = tag.uuid,
                                name = tag.name,
                            )
                            ).toImmutableList(),
                        tagSearchQuery = "",
                    )
                }
            },
        ) {
            interactor.createTag(action.name.trim())
        }
    }

    private fun processPickerDismiss() {
        updateState { current -> current.copy(pickerState = PickerState.Closed) }
    }

    private fun processPickerToggle(action: Action.Click.OnPickerToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val picker = current.pickerState as? PickerState.Open ?: return@updateState current
            val nextSelection = if (action.uuid in picker.selectedUuids) {
                picker.selectedUuids - action.uuid
            } else {
                picker.selectedUuids + action.uuid
            }
            current.copy(pickerState = picker.copy(selectedUuids = nextSelection.toImmutableList()))
        }
    }

    private fun processPickerConfirm() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val picker = current.pickerState as? PickerState.Open ?: return
        if (picker.selectedUuids.isEmpty()) {
            updateState { it.copy(pickerState = PickerState.Closed) }
            return
        }
        launch(
            onSuccess = { resolved ->
                updateStateImmediate { latest ->
                    val nextItems = resolved.mapIndexed { localIndex, picker ->
                        TrainingExerciseItem(
                            exerciseUuid = picker.exercise.uuid,
                            exerciseName = picker.exercise.name,
                            exerciseType = picker.exercise.type.toUi(),
                            tags = picker.labels.toImmutableList(),
                            position = latest.exercises.size + localIndex,
                            planSets = persistentListOf(),
                            planSummary = "",
                        )
                    }
                    latest.copy(
                        exercises = (latest.exercises + nextItems).toImmutableList(),
                        pickerState = PickerState.Closed,
                    )
                }
            },
        ) {
            interactor.resolveExercises(picker.selectedUuids)
        }
    }
}
