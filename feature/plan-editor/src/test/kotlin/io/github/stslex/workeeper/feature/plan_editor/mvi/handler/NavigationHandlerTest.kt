// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorDraftResultAttr
import io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorSavedAttr
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `Back pops the navigation stack with no result attributes`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `BackAfterSave pops with the plan-editor-saved attribute set to true`() {
        handler.invoke(Action.Navigation.BackAfterSave)
        verify(exactly = 1) {
            navigator.popBack(planEditorSavedAttr.toPairValue(true))
        }
    }

    @Test
    fun `BackAfterDraftSave pops with the draft-result attribute carrying the JSON payload`() {
        val payload = "{\"type\":\"WEIGHTED\",\"plan\":[]}"
        handler.invoke(Action.Navigation.BackAfterDraftSave(resultJson = payload))
        verify(exactly = 1) {
            navigator.popBack(planEditorDraftResultAttr.toPairValue(payload))
        }
    }
}
