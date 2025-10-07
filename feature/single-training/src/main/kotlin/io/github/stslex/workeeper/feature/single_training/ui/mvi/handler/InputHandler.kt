package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.reset
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: TrainingHandlerStore,
) : Handler<Action.Input>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.Date -> inputDate(action)
            is Action.Input.Name -> inputName(action)
        }
    }

    private fun inputDate(action: Action.Input.Date) {
        updateState {
            it.copy(
                training = it.training.copy(
                    date = it.training.date.reset(action.timestamp),
                ),
            )
        }
    }

    private fun inputName(action: Action.Input.Name) {
        updateState {
            it.copy(
                training = it.training.copy(
                    name = it.training.name.reset(action.value),
                ),
            )
        }
    }
}
