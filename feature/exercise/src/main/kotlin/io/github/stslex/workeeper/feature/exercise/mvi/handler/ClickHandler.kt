// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

private const val MAX_TAGS_PER_EXERCISE = 10

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
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
            Action.Click.OnConfirmDiscard -> processConfirmDiscard()
            Action.Click.OnDismissDiscard -> processDismissDiscard()
            Action.Click.OnDismissArchiveBlocked -> Unit
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            is Action.Click.OnTypeSelect -> processTypeSelect(action)
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
        }
    }

    private fun processBackClick() {
        if (state.value.mode is Mode.Edit && state.value.hasChanges) {
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            sendEvent(Event.ShowDiscardConfirmDialog)
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.Back)
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
                    consume(Action.Navigation.Back)
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
        launch(
            onSuccess = { resolvedUuid ->
                val savedSnapshot = State.Snapshot(
                    name = current.name.trim(),
                    type = current.type,
                    description = current.description,
                    tagUuids = current.tags.map { it.uuid },
                )
                if (current.mode is Mode.Edit && current.mode.isCreate) {
                    consume(Action.Navigation.Back)
                } else {
                    updateStateImmediate {
                        it.copy(
                            uuid = resolvedUuid,
                            mode = Mode.Read,
                            originalSnapshot = savedSnapshot,
                        )
                    }
                }
            },
        ) {
            interactor.saveExercise(snapshot)
        }
    }

    private fun processCancelClick() {
        val current = state.value
        if (current.mode is Mode.Edit && current.hasChanges) {
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            sendEvent(Event.ShowDiscardConfirmDialog)
            return
        }
        leaveEditMode()
    }

    private fun processConfirmDiscard() {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        leaveEditMode()
    }

    private fun processDismissDiscard() = Unit

    private fun leaveEditMode() {
        val current = state.value
        val mode = current.mode
        if (mode is Mode.Edit && mode.isCreate) {
            consume(Action.Navigation.Back)
            return
        }
        val snapshot = current.originalSnapshot
        if (snapshot != null) {
            updateState {
                it.copy(
                    mode = Mode.Read,
                    name = snapshot.name,
                    nameError = false,
                    type = snapshot.type,
                    description = snapshot.description,
                    tags = it.availableTags
                        .filter { tag -> tag.uuid in snapshot.tagUuids }
                        .toImmutableList(),
                    tagSearchQuery = "",
                )
            }
        } else {
            updateState { it.copy(mode = Mode.Read) }
        }
    }

    private fun processUndoArchive(action: Action.Click.OnUndoArchive) {
        launch { interactor.restore(action.uuid) }
    }

    private fun processTypeSelect(action: Action.Click.OnTypeSelect) {
        if (state.value.type == action.type) return
        sendEvent(Event.Haptic(HapticFeedbackType.SegmentTick))
        updateState { it.copy(type = action.type) }
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
