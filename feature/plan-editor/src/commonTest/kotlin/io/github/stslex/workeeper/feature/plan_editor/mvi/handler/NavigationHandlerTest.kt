// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    @Test
    fun `Back pops the navigation stack with no result attributes`() {
        val navigator = RecordingNavigator()
        val handler = NavigationHandler(navigator = navigator)

        handler.invoke(Action.Navigation.Back)

        assertEquals(1, navigator.popBackCount)
        assertEquals(emptyList(), navigator.results)
    }

    @Test
    fun `BackAfterSave pops handing true back to the PlanEditor destination`() {
        val navigator = RecordingNavigator()
        val handler = NavigationHandler(navigator = navigator)

        handler.invoke(Action.Navigation.BackAfterSave)

        assertEquals(0, navigator.popBackCount)
        assertEquals(
            listOf(NavigationResult(Screen.PlanEditor::class, true)),
            navigator.results,
        )
    }
}

private data class NavigationResult(
    val destination: KClass<*>,
    val result: Any,
)

private class RecordingNavigator : Navigator {

    var popBackCount: Int = 0
        private set
    val results = mutableListOf<NavigationResult>()

    override fun popBack() {
        popBackCount += 1
    }

    override fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R> {
        results += NavigationResult(destination, result)
    }

    override fun navTo(screen: Screen): Nothing =
        error("navTo must not be used by NavigationHandler")

    override fun replaceTo(screen: Screen): Nothing =
        error("replaceTo must not be used by NavigationHandler")

    override fun restartApp(): Nothing =
        error("restartApp must not be used by NavigationHandler")

    override fun openRecovery(): Nothing =
        error("openRecovery must not be used by NavigationHandler")
}
