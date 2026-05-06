// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: ExerciseHandlerStore,
) : Handler<Action.Input>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnNameChange -> updateState { current ->
                current.copy(
                    name = action.value,
                    nameError = false,
                    nameDuplicateError = false,
                )
            }

            is Action.Input.OnDescriptionChange -> updateState { current ->
                current.copy(description = action.value.take(DESCRIPTION_MAX_LENGTH))
            }

            is Action.Input.OnTagSearchChange -> updateState { current ->
                current.copy(tagSearchQuery = action.value)
            }
        }
    }

    companion object {

        private const val DESCRIPTION_MAX_LENGTH = 2000
    }
}
