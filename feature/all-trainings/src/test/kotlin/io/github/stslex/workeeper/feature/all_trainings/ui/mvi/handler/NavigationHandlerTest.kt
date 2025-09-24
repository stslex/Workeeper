package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator)

    @Test
    fun `navigate to create training`() {
        handler.invoke(TrainingStore.Action.Navigation.CreateTraining)

        verify(exactly = 1) { navigator.navTo(Screen.Training(uuid = null)) }
    }

    @Test
    fun `navigate to open training with uuid`() {
        val uuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Navigation.OpenTraining(uuid))

        verify(exactly = 1) { navigator.navTo(Screen.Training(uuid = uuid)) }
    }

    @Test
    fun `navigate to different trainings`() {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Navigation.OpenTraining(uuid1))
        handler.invoke(TrainingStore.Action.Navigation.OpenTraining(uuid2))

        verify(exactly = 1) { navigator.navTo(Screen.Training(uuid = uuid1)) }
        verify(exactly = 1) { navigator.navTo(Screen.Training(uuid = uuid2)) }
    }
}
