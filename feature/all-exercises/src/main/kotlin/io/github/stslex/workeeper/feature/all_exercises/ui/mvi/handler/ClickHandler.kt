package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.toNavData
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import kotlin.uuid.Uuid

@Scoped(binds = [ClickHandler::class])
@Scope(name = EXERCISE_SCOPE_NAME)
internal class ClickHandler(
    private val repository: ExerciseRepository,
    @Named(EXERCISE_SCOPE_NAME) store: ExerciseHandlerStore,
) : Handler<Action.Click>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.FloatButtonClick -> processClickFloatingButton()
            is Action.Click.Item -> processClickItem(action)
            is Action.Click.LonkClick -> processLongClick(action)
        }
    }

    private fun processLongClick(action: Action.Click.LonkClick) {
        sendEvent(Event.HapticFeedback(HapticFeedbackType.LongPress))
        val newItems = state.value.selectedItems.toMutableSet()
            .apply {
                if (state.value.selectedItems.contains(action.item)) {
                    remove(action.item)
                } else {
                    add(action.item)
                }
            }
            .toImmutableSet()

        updateState { it.copy(selectedItems = newItems) }
    }

    private fun processClickFloatingButton() {
        sendEvent(Event.HapticFeedback(HapticFeedbackType.Confirm))
        val selectedItems = state.value.selectedItems
        if (selectedItems.isNotEmpty()) {
            launch(
                onSuccess = { updateState { it.copy(selectedItems = persistentSetOf()) } }
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
        sendEvent(Event.HapticFeedback(HapticFeedbackType.VirtualKey))
        if (state.value.selectedItems.isNotEmpty()) {
            consume(Action.Click.LonkClick(action.item))
        } else {
            consume(Action.Navigation.OpenExercise(action.item.toNavData()))
        }
    }
}