package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.exercise.data.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HOME_SCOPE_NAME
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.model.toNavData
import io.github.stslex.workeeper.feature.home.ui.mvi.store.CalendarState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import kotlin.uuid.Uuid

@Scoped(binds = [ClickHandler::class])
@Scope(name = HOME_SCOPE_NAME)
internal class ClickHandler(
    private val repository: ExerciseRepository,
    @Named(HOME_SCOPE_NAME) store: HomeHandlerStore,
) : Handler<Action.Click>, HomeHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.FloatButtonClick -> processClickFloatingButton()
            is Action.Click.Item -> processClickItem(action)
            is Action.Click.LonkClick -> processLongClick(action)
            is Action.Click.Calendar -> processClickCalendar(action)
        }
    }

    private fun processClickCalendar(action: Action.Click.Calendar) {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey))

        val calendarState = when (action) {
            Action.Click.Calendar.Close -> CalendarState.Closed
            Action.Click.Calendar.EndDate -> CalendarState.Opened.EndDate
            Action.Click.Calendar.StartDate -> CalendarState.Opened.StartDate
        }

        updateState {
            it.copyCharts(
                calendarState = calendarState
            )
        }
    }

    private fun processLongClick(action: Action.Click.LonkClick) {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.LongPress))
        val newItems = state.value.allState.selectedItems.toMutableSet()
            .apply {
                if (state.value.allState.selectedItems.contains(action.item)) {
                    remove(action.item)
                } else {
                    add(action.item)
                }
            }
            .toImmutableSet()

        updateState {
            it.copyAll(
                selectedItems = newItems
            )
        }
    }

    private fun processClickFloatingButton() {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.Confirm))
        val selectedItems = state.value.allState.selectedItems
        if (selectedItems.isNotEmpty()) {
            launch(
                onSuccess = {
                    updateState {
                        it.copyAll(selectedItems = persistentSetOf())
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

    private fun processClickItem(action: Action.Click.Item) {
        sendEvent(HomeStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey))
        if (state.value.allState.selectedItems.isNotEmpty()) {
            consume(Action.Click.LonkClick(action.item))
        } else {
            consume(Action.Navigation.OpenExercise(action.item.toNavData()))
        }
    }
}