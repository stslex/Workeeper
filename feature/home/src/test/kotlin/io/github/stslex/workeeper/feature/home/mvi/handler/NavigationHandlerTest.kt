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
    fun `OpenLiveWorkout navigates to LiveWorkout with the active session uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkout(sessionUuid = "session-7"))
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(
                    sessionUuid = "session-7",
                    trainingUuid = null,
                ),
            )
        }
    }

    @Test
    fun `OpenSettings navigates to Settings`() {
        handler.invoke(Action.Navigation.OpenSettings)
        verify(exactly = 1) { navigator.navTo(Screen.Settings) }
    }
}
