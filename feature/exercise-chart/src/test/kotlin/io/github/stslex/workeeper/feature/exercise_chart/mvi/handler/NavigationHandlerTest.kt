// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `PopBack triggers popBack`() {
        handler.invoke(Action.Navigation.PopBack)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenHome navigates to BottomBar Home`() {
        handler.invoke(Action.Navigation.OpenHome)
        verify(exactly = 1) { navigator.navTo(Screen.BottomBar.Home) }
    }

    @Test
    fun `OpenPastSession navigates to Screen PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-1"))
        verify(exactly = 1) {
            navigator.navTo(Screen.PastSession(sessionUuid = "session-1"))
        }
    }
}
