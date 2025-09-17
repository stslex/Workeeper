package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped


@Scoped(binds = [ClickHandler::class])
@Scope(name = TRAINING_SCOPE_NAME)
internal class ClickHandler(
    private val repository: TrainingRepository,
    @Named(TRAINING_SCOPE_NAME) store: TrainingHandlerStore
) : Handler<Action.Click>, TrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.TrainingItemClick -> processTrainingItemClick(action)
            is Action.Click.TrainingItemLongClick -> processTrainingItemLongClick(action)
            Action.Click.ActionButton -> processActionButtonClick()
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
                }
            ) {
                repository.removeTrainings(state.value.selectedItems)
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
                selectedItems = items.toImmutableSet()
            )
        }
    }
}