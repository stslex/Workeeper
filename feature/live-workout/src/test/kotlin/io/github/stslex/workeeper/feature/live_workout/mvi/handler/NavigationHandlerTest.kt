// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val screen = Screen.LiveWorkout(sessionUuid = "session-1", trainingUuid = "training-1")
    private val handler = NavigationHandler(navigator = navigator, data = screen)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenPastSession triggers replaceTo with PastSession route`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-1"))
        verify(exactly = 1) {
            navigator.replaceTo(Screen.PastSession(sessionUuid = "session-1"))
        }
    }
}
