package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val store: ExerciseHandlerStore = mockk<ExerciseHandlerStore>(relaxed = true)
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State.INITIAL)
    private val handler: NavigationHandler = NavigationHandler(store)

    @Test
    fun `back action triggers navigation back`() {
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.Back)

        verify(exactly = 1) { store.consume(ExerciseStore.Action.Navigation.Back) }
    }

    @Test
    fun `back with confirmation when allowed navigates back for empty state`() {
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.BackWithConfirmation)

        verify(exactly = 1) { store.consume(ExerciseStore.Action.Navigation.Back) }
    }

    @Test
    fun `back with confirmation when not allowed shows dismiss snackbar`() {
        val modifiedState = stateFlow.value.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "Changed"),
        )
        stateFlow.value = modifiedState
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.BackWithConfirmation)

        verify(exactly = 1) { store.sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS)) }
        verify(exactly = 0) { store.consume(ExerciseStore.Action.Navigation.Back) }
    }
}
