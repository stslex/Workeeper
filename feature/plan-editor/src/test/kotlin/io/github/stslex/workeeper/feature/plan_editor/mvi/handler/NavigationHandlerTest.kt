// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val data = Screen.PlanEditor(
        performedExerciseUuid = null,
        exerciseUuid = "exercise-1",
        trainingUuid = null,
    )
    private val handler = NavigationHandler(navigator, data)

    @Test
    fun `Back pops the navigation stack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `component data exposes the screen route arguments`() {
        // The Component subclass surfaces its `data` to the assisted-injected store so
        // route arguments survive recomposition without route re-parsing.
        assert(handler.data === data)
    }
}
