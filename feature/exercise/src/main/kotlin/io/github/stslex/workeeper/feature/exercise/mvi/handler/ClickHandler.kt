// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.SaveResult
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MAX_TAGS_PER_EXERCISE = 10
private const val DEFAULT_NEW_REPS = 5

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    @MainImmediateDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    store: ExerciseHandlerStore,
) : Handler<Action.Click>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnEditClick -> processEditClick()
            Action.Click.OnArchiveMenuClick -> processArchiveClick()
            Action.Click.OnTrackNowClick -> processTrackNowClick()
            is Action.Click.OnHistoryRowClick -> processHistoryRowClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processCancelClick()
            is Action.Click.OnConfirmDiscard -> processConfirmDiscard(action.target)
            Action.Click.OnDismissDiscard -> processDismissDiscard()
            Action.Click.OnDismissArchiveBlocked -> Unit
            Action.Click.FlipToReadMode -> processFlipToReadMode()
            Action.Click.OnPermanentDeleteMenuClick -> processPermanentDeleteMenuClick()
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnDismissPermanentDelete -> Unit
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            is Action.Click.OnTypeSelect -> processTypeSelect(action)
            Action.Click.OnTypeChangeConfirm -> processTypeChangeConfirm()
            Action.Click.OnTypeChangeDismiss -> processTypeChangeDismiss()
            Action.Click.OnEditPlanClick -> processEditPlanClick()
            is Action.Click.OnPlanEditorSetWeight -> processPlanEditorSetWeight(action)
            is Action.Click.OnPlanEditorSetReps -> processPlanEditorSetReps(action)
            is Action.Click.OnPlanEditorSetType -> processPlanEditorSetType(action)
            is Action.Click.OnPlanEditorRemoveSet -> processPlanEditorRemoveSet(action)
            Action.Click.OnPlanEditorAddSet -> processPlanEditorAddSet()
            Action.Click.OnPlanEditorSave -> processPlanEditorSave()
            Action.Click.OnPlanEditorDismiss -> processPlanEditorDismiss()
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
        }
    }

    private fun processBackClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        // Plan editor is the inner-most surface — its draft takes priority over the
        // form-level dirty check. Same dialog UI as form discard, different commit.
        if (current.isPlanEditorDirty) {
            sendEvent(Event.ShowDiscardConfirmDialog(DiscardTarget.PLAN_EDITOR))
            return
        }
        val mode = current.mode
        if (mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        val target = if (mode.isCreate) DiscardTarget.POP_SCREEN else DiscardTarget.FLIP_TO_READ
        if (current.hasChanges) {
            sendEvent(Event.ShowDiscardConfirmDialog(target))
        } else {
            applyDiscardTarget(target)
        }
    }

    private fun processEditClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                mode = Mode.Edit(isCreate = false),
                originalSnapshot = State.Snapshot(
                    name = current.name,
                    type = current.type,
                    description = current.description,
                    tagUuids = current.tags.map { it.uuid },
                ),
            )
        }
    }

    private fun processArchiveClick() {
        val uuid = state.value.uuid ?: return
        val name = state.value.name
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        launch {
            when (val result = interactor.archive(uuid)) {
                ArchiveResult.Success -> {
                    sendEvent(Event.ShowArchiveSuccess(name = name, uuid = uuid))
                    // launch defaults to defaultDispatcher; navigator must be touched on Main.
                    withContext(mainDispatcher) {
                        consume(Action.Navigation.Back)
                    }
                }

                is ArchiveResult.Blocked ->
                    sendEvent(Event.ShowArchiveBlocked(name, result.activeTrainings))
            }
        }
    }

    private fun processTrackNowClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        sendEvent(Event.ShowTrackNowPending)
    }

    private fun processHistoryRowClick(action: Action.Click.OnHistoryRowClick) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSession(action.sessionUuid))
    }

    private fun processSaveClick() {
        val current = state.value
        if (current.name.isBlank()) {
            updateState { it.copy(nameError = true) }
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val snapshot = ExerciseChangeDataModel(
            uuid = current.uuid,
            name = current.name.trim(),
            type = current.type,
            description = current.description.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis(),
            labels = current.tags.map { it.name },
        )
        val mode = current.mode
        val isCreate = mode is Mode.Edit && mode.isCreate
        // HandlerStore.launch defaults eachDispatcher to defaultDispatcher, so onSuccess runs
        // on a background thread. Switch to mainDispatcher before consume(Action.Navigation.*)
        // so navigator.popBack() lands on the UI thread.
        launch(
            onSuccess = { result ->
                when (result) {
                    is SaveResult.Success -> handleSaveSuccess(
                        resolvedUuid = result.resolvedUuid,
                        isCreate = isCreate,
                        current = current,
                    )

                    SaveResult.DuplicateName -> updateStateImmediate {
                        it.copy(nameDuplicateError = true)
                    }
                }
            },
        ) {
            interactor.saveExercise(snapshot)
        }
    }

    private suspend fun handleSaveSuccess(
        resolvedUuid: String,
        isCreate: Boolean,
        current: State,
    ) {
        if (isCreate) {
            withContext(mainDispatcher) {
                consume(Action.Navigation.Back)
            }
        } else {
            val savedSnapshot = State.Snapshot(
                name = current.name.trim(),
                type = current.type,
                description = current.description,
                tagUuids = current.tags.map { it.uuid },
            )
            updateStateImmediate {
                it.copy(
                    uuid = resolvedUuid,
                    mode = Mode.Read,
                    originalSnapshot = savedSnapshot,
                )
            }
        }
    }

    private fun processCancelClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        val mode = current.mode
        if (mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        val target = if (mode.isCreate) DiscardTarget.POP_SCREEN else DiscardTarget.FLIP_TO_READ
        if (current.hasChanges) {
            sendEvent(Event.ShowDiscardConfirmDialog(target))
        } else {
            applyDiscardTarget(target)
        }
    }

    private fun processConfirmDiscard(target: DiscardTarget) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        applyDiscardTarget(target)
    }

    private fun processDismissDiscard() = Unit

    private fun processFlipToReadMode() {
        updateState { current ->
            val snapshot = current.originalSnapshot
            if (snapshot == null) {
                current.copy(mode = Mode.Read)
            } else {
                current.copy(
                    mode = Mode.Read,
                    name = snapshot.name,
                    nameError = false,
                    nameDuplicateError = false,
                    type = snapshot.type,
                    description = snapshot.description,
                    tags = current.availableTags
                        .filter { tag -> tag.uuid in snapshot.tagUuids }
                        .toImmutableList(),
                    tagSearchQuery = "",
                )
            }
        }
    }

    private fun applyDiscardTarget(target: DiscardTarget) {
        when (target) {
            DiscardTarget.POP_SCREEN -> consume(Action.Navigation.Back)
            DiscardTarget.FLIP_TO_READ -> processFlipToReadMode()
            DiscardTarget.PLAN_EDITOR -> updateState { it.copy(planEditorTarget = null) }
        }
    }

    private fun processPermanentDeleteMenuClick() {
        if (!state.value.canPermanentlyDelete) return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        sendEvent(Event.ShowPermanentDeleteConfirm(state.value.name))
    }

    private fun processConfirmPermanentDelete() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        launch(
            onSuccess = {
                sendEvent(Event.ShowPermanentDeleteSuccess)
                withContext(mainDispatcher) { consume(Action.Navigation.Back) }
            },
        ) {
            interactor.permanentlyDelete(uuid)
        }
    }

    private fun processUndoArchive(action: Action.Click.OnUndoArchive) {
        launch { interactor.restore(action.uuid) }
    }

    private fun processTypeSelect(action: Action.Click.OnTypeSelect) {
        val current = state.value
        if (current.type == action.type) return
        // Switching from WEIGHTED to WEIGHTLESS while weighted plan rows exist would
        // silently strand weight data once Live workout pre-fills. Surface a confirm so
        // the user opts in to the multi-row wipe (handled by `processTypeChangeConfirm`).
        val needsWeightWipe = action.type == ExerciseTypeDataModel.WEIGHTLESS &&
            current.type == ExerciseTypeDataModel.WEIGHTED &&
            (current.adhocPlan?.any { it.weight != null } == true)
        if (needsWeightWipe) {
            sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
            updateState { it.copy(pendingTypeChange = action.type) }
            sendEvent(Event.ShowTypeChangeConfirm)
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.SegmentTick))
        updateState { it.copy(type = action.type) }
    }

    private fun processTypeChangeConfirm() {
        val current = state.value
        val pending = current.pendingTypeChange ?: return
        val uuid = current.uuid
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { latest ->
            latest.copy(
                type = pending,
                pendingTypeChange = null,
                adhocPlan = latest.adhocPlan?.map { it.copy(weight = null) }?.toImmutableList(),
            )
        }
        if (uuid == null) return
        launch(
            onSuccess = { Unit },
        ) {
            interactor.clearWeightsFromAllPlansForExercise(uuid)
        }
    }

    private fun processTypeChangeDismiss() {
        updateState { it.copy(pendingTypeChange = null) }
    }

    private fun processEditPlanClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val initial = state.value.adhocPlan ?: persistentListOf()
        updateState {
            it.copy(
                planEditorTarget = State.PlanEditorTarget(
                    initialPlan = initial,
                    draft = initial,
                ),
            )
        }
    }

    private fun processPlanEditorDismiss() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        if (current.isPlanEditorDirty) {
            // Reuse the screen-level discard surface (POP_SCREEN target carries the
            // existing wording — same dialog used for unsaved-form dismissals).
            sendEvent(Event.ShowDiscardConfirmDialog(DiscardTarget.PLAN_EDITOR))
        } else {
            updateState { it.copy(planEditorTarget = null) }
        }
    }

    private fun processPlanEditorSetWeight(action: Action.Click.OnPlanEditorSetWeight) {
        updatePlanRow(action.index) { it.copy(weight = action.value) }
    }

    private fun processPlanEditorSetReps(action: Action.Click.OnPlanEditorSetReps) {
        updatePlanRow(action.index) { it.copy(reps = action.reps.coerceAtLeast(0)) }
    }

    private fun processPlanEditorSetType(action: Action.Click.OnPlanEditorSetType) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updatePlanRow(action.index) { it.copy(type = action.type) }
    }

    private fun processPlanEditorRemoveSet(action: Action.Click.OnPlanEditorRemoveSet) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val nextDraft = target.draft.toMutableList()
                .also { if (action.index in it.indices) it.removeAt(action.index) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = nextDraft))
        }
    }

    private fun processPlanEditorAddSet() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            val previous = target.draft.lastOrNull()
            val nextSet = previous?.copy(
                type = io.github.stslex.workeeper.core.database.sets.SetTypeDataModel.WORK,
            ) ?: io.github.stslex.workeeper.core.database.sets.PlanSetDataModel(
                weight = null,
                reps = DEFAULT_NEW_REPS,
                type = io.github.stslex.workeeper.core.database.sets.SetTypeDataModel.WORK,
            )
            current.copy(
                planEditorTarget = target.copy(
                    draft = (target.draft + nextSet).toImmutableList(),
                ),
            )
        }
    }

    private fun processPlanEditorSave() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        val target = current.planEditorTarget ?: return
        val uuid = current.uuid
        val nextPlan = target.draft.takeIf { it.isNotEmpty() }?.toImmutableList()
        updateState { it.copy(adhocPlan = nextPlan, planEditorTarget = null) }
        if (uuid == null) return
        launch(onSuccess = { Unit }) {
            interactor.setAdhocPlan(uuid, nextPlan)
        }
    }

    private inline fun updatePlanRow(
        index: Int,
        crossinline transform: (PlanSetDataModel) -> PlanSetDataModel,
    ) {
        updateState { current ->
            val target = current.planEditorTarget ?: return@updateState current
            if (index !in target.draft.indices) return@updateState current
            val next = target.draft.toMutableList().apply { this[index] = transform(this[index]) }
                .toImmutableList()
            current.copy(planEditorTarget = target.copy(draft = next))
        }
    }

    private fun processTagToggle(action: Action.Click.OnTagToggle) {
        val current = state.value
        val tag = current.availableTags.firstOrNull { it.uuid == action.tagUuid } ?: return
        val isSelected = current.tags.any { it.uuid == action.tagUuid }
        if (isSelected) {
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            updateState {
                it.copy(tags = it.tags.filterNot { existing -> existing.uuid == action.tagUuid }.toImmutableList())
            }
        } else {
            if (current.tags.size >= MAX_TAGS_PER_EXERCISE) {
                sendEvent(Event.ShowTagLimitReached)
                return
            }
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            updateState { it.copy(tags = (it.tags + tag).toImmutableList()) }
        }
    }

    private fun processTagRemove(action: Action.Click.OnTagRemove) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(tags = it.tags.filterNot { tag -> tag.uuid == action.tagUuid }.toImmutableList())
        }
    }

    private fun processTagCreate(action: Action.Click.OnTagCreate) {
        val current = state.value
        if (current.tags.size >= MAX_TAGS_PER_EXERCISE) {
            sendEvent(Event.ShowTagLimitReached)
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        launch(
            onSuccess = { tag ->
                updateStateImmediate { state ->
                    state.copy(
                        tags = (state.tags + TagUiModel(uuid = tag.uuid, name = tag.name)).toImmutableList(),
                        tagSearchQuery = "",
                    )
                }
            },
        ) {
            interactor.createTag(action.name.trim())
        }
    }
}
