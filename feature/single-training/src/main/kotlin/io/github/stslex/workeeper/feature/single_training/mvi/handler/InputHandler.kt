// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

private const val DESCRIPTION_MAX_LENGTH = 2000

@ViewModelScoped
internal class InputHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    store: SingleTrainingHandlerStore,
) : Handler<Action.Input>, SingleTrainingHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnNameChange -> updateState { current ->
                current.copy(name = action.value, nameError = false)
            }
            is Action.Input.OnDescriptionChange -> updateState { current ->
                current.copy(description = action.value.take(DESCRIPTION_MAX_LENGTH))
            }
            is Action.Input.OnTagSearchChange -> updateState { current ->
                current.copy(tagSearchQuery = action.value)
            }
            is Action.Input.OnPickerSearchChange -> processPickerSearch(action)
        }
    }

    private fun processPickerSearch(action: Action.Input.OnPickerSearchChange) {
        val current = state.value
        val picker = current.pickerState as? PickerState.Open ?: return
        // Refresh picker results in the background; the state update is non-blocking so
        // the search field stays responsive even when the active filter is wide.
        launch(
            onSuccess = { results ->
                updateStateImmediate { latest ->
                    val latestPicker = latest.pickerState as? PickerState.Open ?: return@updateStateImmediate latest
                    latest.copy(
                        pickerState = latestPicker.copy(
                            query = action.value,
                            results = results.map { exercise ->
                                PickerExerciseItem(
                                    uuid = exercise.uuid,
                                    name = exercise.name,
                                    type = exercise.type,
                                    tags = exercise.labels.toImmutableList(),
                                )
                            }.toImmutableList(),
                        ),
                    )
                }
            },
        ) {
            interactor.searchExercisesForPicker(
                query = action.value,
                excludeUuids = current.exercises.map { it.exerciseUuid }.toSet(),
            )
        }
        updateState { latest ->
            val latestPicker = latest.pickerState as? PickerState.Open ?: return@updateState latest
            latest.copy(pickerState = latestPicker.copy(query = action.value))
        }
        // suppress unused: picker reference kept for clarity at the call site.
        @Suppress("unused")
        val acknowledged = picker
    }
}
