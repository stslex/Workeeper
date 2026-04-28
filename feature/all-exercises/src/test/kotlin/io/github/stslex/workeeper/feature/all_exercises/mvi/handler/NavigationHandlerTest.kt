// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator)

    @Test
    fun `OpenDetail navigates to Screen Exercise with uuid`() {
        handler.invoke(Action.Navigation.OpenDetail("uuid-1"))
        verify(exactly = 1) { navigator.navTo(Screen.Exercise(uuid = "uuid-1")) }
    }

    @Test
    fun `OpenCreate navigates to Screen Exercise with null uuid`() {
        handler.invoke(Action.Navigation.OpenCreate)
        verify(exactly = 1) { navigator.navTo(Screen.Exercise(uuid = null)) }
    }
}
