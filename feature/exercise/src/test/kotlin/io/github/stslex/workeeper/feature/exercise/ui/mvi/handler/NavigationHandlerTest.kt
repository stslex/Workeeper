// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(
        navigator = navigator,
        data = Screen.Exercise(uuid = "uuid-1"),
    )

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenSession navigates to Screen PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenSession("session-uuid"))
        verify(exactly = 1) { navigator.navTo(Screen.PastSession(sessionUuid = "session-uuid")) }
    }

    @Test
    fun `OpenImageViewer navigates to Screen ExerciseImage with the model arg`() {
        val model = "/data/user/0/app/files/exercise_images/abc.jpg"
        handler.invoke(Action.Navigation.OpenImageViewer(model))
        verify(exactly = 1) { navigator.navTo(Screen.ExerciseImage(model)) }
    }
}
