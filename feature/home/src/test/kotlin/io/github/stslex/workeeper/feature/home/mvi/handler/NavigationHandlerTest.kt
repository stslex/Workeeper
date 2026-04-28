// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `OpenLiveWorkoutResume navigates to LiveWorkout with the active session uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-7"))
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(sessionUuid = "session-7", trainingUuid = null),
            )
        }
    }

    @Test
    fun `OpenLiveWorkoutFresh navigates to LiveWorkout with the training uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkoutFresh(trainingUuid = "training-3"))
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(sessionUuid = null, trainingUuid = "training-3"),
            )
        }
    }

    @Test
    fun `OpenPastSession navigates to PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-9"))
        verify(exactly = 1) {
            navigator.navTo(Screen.PastSession(sessionUuid = "session-9"))
        }
    }

    @Test
    fun `OpenSettings navigates to Settings`() {
        handler.invoke(Action.Navigation.OpenSettings)
        verify(exactly = 1) { navigator.navTo(Screen.Settings) }
    }

    @Test
    fun `OpenAllTrainings navigates to AllTrainings bottom-bar destination`() {
        handler.invoke(Action.Navigation.OpenAllTrainings)
        verify(exactly = 1) { navigator.navTo(Screen.BottomBar.AllTrainings) }
    }
}
