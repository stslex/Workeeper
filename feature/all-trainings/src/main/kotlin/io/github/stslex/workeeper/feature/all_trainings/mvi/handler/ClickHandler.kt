package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Action
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: AllTrainingsInteractor,
    store: TrainingHandlerStore,
) : Handler<Action.Click>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm))
        when (action) {
            is Action.Click.TrainingItemClick -> processTrainingItemClick(action)
            is Action.Click.TrainingItemLongClick -> processTrainingItemLongClick(action)
            Action.Click.ActionButton -> processActionButtonClick()
            Action.Click.BackHandler -> processBackHandler()
        }
    }

    private fun processBackHandler() {
        if (state.value.selectedItems.isNotEmpty()) {
            updateState { it.copy(selectedItems = persistentSetOf()) }
        }
        if (state.value.query.isNotEmpty()) {
            updateState { it.copy(query = "") }
        }
    }

    private fun processActionButtonClick() {
        if (state.value.selectedItems.isEmpty()) {
            consume(Action.Navigation.CreateTraining)
        } else {
            launch(
                onSuccess = {
                    updateStateImmediate {
                        it.copy(selectedItems = persistentSetOf())
                    }
                },
            ) {
                interactor.deleteAll(state.value.selectedItems.toList())
            }
        }
    }

    private fun processTrainingItemClick(action: Action.Click.TrainingItemClick) {
        val isSelectedState = state.value.selectedItems.isNotEmpty()
        if (isSelectedState) {
            addOrRemoveSelected(action.itemUuid)
        } else {
            consume(Action.Navigation.OpenTraining(action.itemUuid))
        }
    }

    private fun processTrainingItemLongClick(action: Action.Click.TrainingItemLongClick) {
        addOrRemoveSelected(action.itemUuid)
    }

    private fun addOrRemoveSelected(itemUuid: String) {
        val items = state.value.selectedItems.toMutableList()
        if (items.contains(itemUuid)) {
            items.remove(itemUuid)
        } else {
            items.add(itemUuid)
        }
        updateState {
            it.copy(
                selectedItems = items.toImmutableSet(),
            )
        }
    }
}
