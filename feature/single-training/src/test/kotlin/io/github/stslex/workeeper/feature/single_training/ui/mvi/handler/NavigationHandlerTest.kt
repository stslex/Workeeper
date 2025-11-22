package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(
        navigator = navigator,
        data = Screen.Training(uuid = null),
    )

    @Test
    fun `pop back action triggers navigator pop back`() {
        handler.invoke(Action.Navigation.PopBack)

        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `exercise navigation action triggers navigator to open current exercise`() {
        val expectedUuid = "expected_uuid"
        val trainingUuid = "training_uuid"
        handler.invoke(
            Action.Navigation.OpenExercise(
                exerciseUuid = expectedUuid,
                trainingUuid = trainingUuid,
            ),
        )

        verify(exactly = 1) {
            navigator.navTo(
                Screen.Exercise(
                    uuid = expectedUuid,
                    trainingUuid = trainingUuid,
                ),
            )
        }
    }

    @Test
    fun `create exercise navigation action triggers navigator to open new exercise screen`() {
        val trainingUuid = "training_uuid"
        handler.invoke(Action.Navigation.CreateExercise(trainingUuid = trainingUuid))

        verify(exactly = 1) {
            navigator.navTo(
                Screen.Exercise(
                    uuid = null,
                    trainingUuid = trainingUuid,
                ),
            )
        }
    }
}
