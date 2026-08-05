// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorUIMapper.formatPlanSummary
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingScope
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.TagUiMapper.toDomain
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.TagUiMapper.toUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LongMethod")
@SingleIn(SingleTrainingScope::class)
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
            Action.Click.OnPermanentDeleteDismiss -> processCloseDialog()
            Action.Click.OnStartSessionClick -> processStartSession()
            Action.Click.OnConflictResume -> processConflictResume()
            Action.Click.OnConflictDeleteAndStart -> processConflictDeleteAndStart()
            Action.Click.OnConflictDismiss -> processCloseDialog()
            is Action.Click.OnExerciseRowClick -> processExerciseRowClick(action)
            is Action.Click.OnPastSessionClick -> processPastSessionClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processBackClick()
            Action.Click.OnConfirmDiscard -> processConfirmDiscard()
            Action.Click.OnDismissDiscard -> processCloseDialog()
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            is Action.Click.OnExerciseRemove -> processExerciseRemove(action)
            is Action.Click.OnExerciseReorder -> processExerciseReorder(action)
            is Action.Click.OnExerciseCardToggle -> processExerciseCardToggle(action)
            is Action.Click.OnExercisePlanAction -> processExercisePlanAction(action)
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
            Action.Click.OnPickerDismiss -> processPickerDismiss()
            is Action.Click.OnPickerToggle -> processPickerToggle(action)
            Action.Click.OnPickerConfirm -> processPickerConfirm()
        }
    }

    private fun processCloseDialog() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        // Back gesture dismisses the topmost dialog before propagating to navigation.
        if (current.dialogState !is DialogState.Hidden) {
            updateState { it.copy(dialogState = DialogState.Hidden) }
            return
        }
        if (current.mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        if (current.hasChanges) {
            updateState { it.copy(dialogState = DialogState.DiscardConfirm) }
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
        val current = state.value
        if (!current.canPermanentlyDelete) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        // Pre-resolve display strings outside the updateState lambda — Rule 1 of
        // compose-state-discipline (no ResourceWrapper calls inside the lambda body).
        val title = resourceWrapper.getString(
            R.string.feature_training_detail_permanent_delete_title,
            current.name,
        )
        val body = resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_body)
        val impactSummary =
            resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_impact)
        val confirmLabel =
            resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_confirm)
        updateState {
            it.copy(
                dialogState = DialogState.PermanentDeleteConfirm(
                    title = title,
                    body = body,
                    impactSummary = impactSummary,
                    confirmLabel = confirmLabel,
                ),
            )
        }
    }

    private fun processPermanentDeleteConfirm() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
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
                StartSessionConflict.ProceedFresh -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(
                        sessionUuid = "",
                        trainingUuid = trainingUuid,
                    ),
                )

                is StartSessionConflict.SilentResume -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(
                        sessionUuid = resolution.sessionUuid,
                        trainingUuid = trainingUuid,
                    ),
                )

                is StartSessionConflict.NeedsUserChoice -> {
                    val activeName = current.name.takeIf { it.isNotBlank() }
                        ?: resourceWrapper.getString(
                            R.string.feature_training_detail_conflict_unnamed,
                        )
                    val progressLabel = resourceWrapper.getString(
                        R.string.feature_training_detail_conflict_progress_format,
                        0,
                        0,
                    )
                    val sessionUuid = resolution.active.sessionUuid
                    updateStateImmediate {
                        it.copy(
                            dialogState = DialogState.ActiveSessionConflict(
                                sessionUuid = sessionUuid,
                                activeSessionName = activeName,
                                progressLabel = progressLabel,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun processConflictResume() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val info = current.dialogState as? DialogState.ActiveSessionConflict ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        consume(
            Action.Navigation.OpenLiveWorkout(
                sessionUuid = info.sessionUuid,
                trainingUuid = current.uuid,
            ),
        )
    }

    private fun processConflictDeleteAndStart() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val current = state.value
        val info = current.dialogState as? DialogState.ActiveSessionConflict ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch {
            interactor.deleteSession(info.sessionUuid)
            consumeOnMain(
                Action.Navigation.OpenLiveWorkout(
                    sessionUuid = "",
                    trainingUuid = state.value.uuid,
                ),
            )
        }
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
        val snapshot = TrainingChangeDomain(
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
        val plans = current.exercises.map { item ->
            item.exerciseUuid to item.planSets?.map { it.toDomain() }
        }
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
            // The training row first — `setPlanForExercise` lands on the
            // `training_exercise_table` rows `saveTraining` just wrote, which is what lets a
            // CREATE flow carry plans at all (ED1; the deleted route had to no-op there).
            interactor.saveTraining(snapshot)
            plans.forEach { (exerciseUuid, plan) ->
                interactor.setPlanForExercise(
                    trainingUuid = resolvedUuid,
                    exerciseUuid = exerciseUuid,
                    plan = plan,
                )
            }
        }
    }

    private fun processConfirmDiscard() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
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
            expandedExerciseUuid = null,
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

    // Immediate, and deliberately unconfirmed (D-OPEN-11): the draft is unsaved and Cancel
    // stands behind it. The undo snackbar is S7's work — nothing here half-builds it.
    private fun processExerciseRemove(action: Action.Click.OnExerciseRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                exercises = current.exercises
                    .filterNot { it.exerciseUuid == action.exerciseUuid }
                    .mapIndexed { index, item -> item.copy(position = index) }
                    .toImmutableList(),
                expandedExerciseUuid = current.expandedExerciseUuid
                    .takeIf { it != action.exerciseUuid },
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

    private fun processExerciseCardToggle(action: Action.Click.OnExerciseCardToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                expandedExerciseUuid = action.exerciseUuid
                    .takeIf { it != current.expandedExerciseUuid },
            )
        }
    }

    /**
     * The expanded card's plan edit (ED1): reduce against THAT exercise's rows and write the
     * item back. In memory only — Save persists every plan alongside the training, which is
     * also what lets a not-yet-saved training edit its plans at all (the route this replaces
     * had to no-op there: it needed a `training_exercise_table` row to write to).
     */
    private fun processExercisePlanAction(action: Action.Click.OnExercisePlanAction) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.exercises.firstOrNull { it.exerciseUuid == action.exerciseUuid }
                ?: return@updateState current
            val nextDraft = PlanDraftReducer.reduce(
                draft = target.planSets ?: persistentListOf(),
                action = action.action,
                isWeighted = target.exerciseType == ExerciseTypeUiModel.WEIGHTED,
            )
            // Empty normalizes back to null: `plan_sets IS NULL` is attached-with-no-plan, the
            // persisted shape, and an empty list would be a third value the row never stores.
            val nextPlan = nextDraft.takeIf { it.isNotEmpty() }
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.exerciseUuid == action.exerciseUuid) {
                        item.copy(
                            planSets = nextPlan,
                            planSummary = nextPlan?.formatPlanSummary().orEmpty(),
                        )
                    } else {
                        item
                    }
                }.toImmutableList(),
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
                            // null, not an empty list: attached-with-no-plan is what the row
                            // will persist as, and the dirty signature compares this value.
                            planSets = null,
                            planSummary = "",
                        )
                    }
                    latest.copy(
                        exercises = (latest.exercises + nextItems).toImmutableList(),
                        pickerState = PickerState.Closed,
                        // D-OPEN-8: an insert is an addressed gesture whose next step is the
                        // plan, so the inserted card opens — the FIRST only on a multi-insert.
                        expandedExerciseUuid = nextItems.firstOrNull()?.exerciseUuid
                            ?: latest.expandedExerciseUuid,
                    )
                }
            },
        ) {
            interactor.resolveExercises(picker.selectedUuids)
        }
    }
}
