package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator)

    @Test
    fun `create exercise dialog action navigates to new exercise screen`() {
        handler.invoke(ExercisesStore.Action.Navigation.CreateExerciseDialog)

        verify(exactly = 1) { navigator.navTo(Screen.Exercise(uuid = null, trainingUuid = null)) }
    }

    @Test
    fun `open exercise action navigates to exercise screen with data`() {
        val exerciseUuid = Uuid.random().toString()

        handler.invoke(ExercisesStore.Action.Navigation.OpenExercise(exerciseUuid))

        verify(exactly = 1) {
            navigator.navTo(
                Screen.Exercise(
                    uuid = exerciseUuid,
                    trainingUuid = null
                )
            )
        }
    }

    @Test
    fun `multiple navigation actions work correctly`() {
        val exerciseUuid1 = Uuid.random().toString()
        val exerciseUuid2 = Uuid.random().toString()

        handler.invoke(ExercisesStore.Action.Navigation.CreateExerciseDialog)
        handler.invoke(ExercisesStore.Action.Navigation.OpenExercise(exerciseUuid1))
        handler.invoke(ExercisesStore.Action.Navigation.OpenExercise(exerciseUuid2))

        verify(exactly = 1) { navigator.navTo(Screen.Exercise(uuid = null, trainingUuid = null)) }
        verify(exactly = 1) {
            navigator.navTo(
                Screen.Exercise(
                    uuid = exerciseUuid1,
                    trainingUuid = null
                )
            )
        }
        verify(exactly = 1) {
            navigator.navTo(
                Screen.Exercise(
                    uuid = exerciseUuid2,
                    trainingUuid = null
                )
            )
        }
    }
}