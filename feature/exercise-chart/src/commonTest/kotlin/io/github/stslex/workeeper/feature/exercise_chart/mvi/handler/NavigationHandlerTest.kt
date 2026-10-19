// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `PopBack triggers popBack`() {
        handler.invoke(Action.Navigation.PopBack)
        assertEquals(1, navigator.popBacks)
    }

    @Test
    fun `OpenHome navigates to BottomBar Home`() {
        handler.invoke(Action.Navigation.OpenHome)
        assertEquals(1, navigator.navigated.count { it == Screen.BottomBar.Home })
    }

    /** Records [navTo] and [popBack]; the members [NavigationHandler] never reaches fail fast. */
    private class RecordingNavigator : Navigator {

        val navigated = mutableListOf<Screen>()
        var popBacks: Int = 0
            private set

        override fun navTo(screen: Screen) {
            navigated.add(screen)
        }

        override fun popBack() {
            popBacks++
        }

        override fun <S, R : Any> popBackWithResult(
            destination: KClass<S>,
            result: R,
        ): Nothing where S : ScreenWithResult<R> =
            error("popBackWithResult must not be used by NavigationHandler")

        override fun replaceTo(screen: Screen): Nothing =
            error("replaceTo must not be used by NavigationHandler")

        override fun restartApp(): Nothing = error("restartApp must not be used by NavigationHandler")

        override fun openRecovery(): Nothing =
            error("openRecovery must not be used by NavigationHandler")
    }
}
