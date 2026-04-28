// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
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
    fun `OpenSession is wired but is a no-op until past session screen lands`() {
        handler.invoke(Action.Navigation.OpenSession("session-uuid"))
        verify(exactly = 0) { navigator.popBack() }
        verify(exactly = 0) { navigator.navTo(any()) }
    }

    @Test
    fun `OpenImageViewer navigates to Screen ExerciseImage with the model arg`() {
        val model = "/data/user/0/app/files/exercise_images/abc.jpg"
        handler.invoke(Action.Navigation.OpenImageViewer(model))
        verify(exactly = 1) { navigator.navTo(Screen.ExerciseImage(model)) }
    }
}
