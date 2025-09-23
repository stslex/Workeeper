package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyValid
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val store = mockk<ExerciseHandlerStore>(relaxed = true)

    private val initialState = ExerciseStore.State.INITIAL

    private val stateFlow = MutableStateFlow(initialState)
    private val handler = NavigationHandler(store)

    @Test
    fun `back action triggers navigation back`() {
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.Back)

        verify { store.consume(ExerciseStore.Action.Navigation.Back) }
    }

    @Test
    fun `back with confirmation when allowed navigates back for empty state`() {
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.BackWithConfirmation)

        verify { store.consume(ExerciseStore.Action.Navigation.Back) }
    }

    @Test
    fun `back with confirmation when not allowed shows dismiss snackbar`() {
        val modifiedState = initialState.copy(
            name = Property(
                type = PropertyType.NAME,
                value = "Changed",
                valid = PropertyValid.VALID
            )
        )
        stateFlow.value = modifiedState
        every { store.state } returns stateFlow

        handler.invoke(ExerciseStore.Action.NavigationMiddleware.BackWithConfirmation)

        verify { store.sendEvent(ExerciseStore.Event.Snackbar(SnackbarType.DISMISS)) }
        verify(exactly = 0) { store.consume(ExerciseStore.Action.Navigation.Back) }
    }
}