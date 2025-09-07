package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.model.toNavData
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import kotlin.uuid.Uuid

@Factory
@Scope(HomeScope::class)
@Scoped
class ClickHandler(
    private val repository: ExerciseRepository
) : Handler<Action.Click, HomeHandlerStore> {

    override fun HomeHandlerStore.invoke(action: Action.Click) {
        when (action) {
            Action.Click.FloatButtonClick -> processClickFloatingButton()
            is Action.Click.Item -> processClickItem(action)
            is Action.Click.LonkClick -> processLongClick(action)
        }
    }

    private fun HomeHandlerStore.processLongClick(action: Action.Click.LonkClick) {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.LongPress))
        val newItems = state.value.selectedItems.toMutableSet()
            .apply {
                if (state.value.selectedItems.contains(action.item)) {
                    remove(action.item)
                } else {
                    add(action.item)
                }
            }
            .toImmutableSet()

        updateState {
            it.copy(
                selectedItems = newItems
            )
        }
    }

    private fun HomeHandlerStore.processClickFloatingButton() {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.Confirm))
        val selectedItems = state.value.selectedItems
        if (selectedItems.isNotEmpty()) {
            launch(
                onSuccess = {
                    updateState {
                        it.copy(selectedItems = persistentSetOf())
                    }
                }
            ) {
                repository.deleteAllItems(
                    selectedItems.asyncMap { Uuid.parse(it.uuid) }
                )
            }
        } else {
            consume(Action.Navigation.CreateExerciseDialog)
        }
    }

    private fun HomeHandlerStore.processClickItem(action: Action.Click.Item) {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey))
        if (state.value.selectedItems.isNotEmpty()) {
            consume(Action.Click.LonkClick(action.item))
        } else {
            consume(Action.Navigation.OpenExercise(action.item.toNavData()))
        }
    }
}