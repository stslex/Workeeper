// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = NavigationHandler(navigator)

    @Test
    fun `OpenDetail navigates to Screen Training with uuid`() {
        handler.invoke(Action.Navigation.OpenDetail("uuid-1"))
        assertEquals(1, navigator.screens.count { it == Screen.Training(uuid = "uuid-1") })
    }

    @Test
    fun `OpenCreate navigates to Screen Training with null uuid`() {
        handler.invoke(Action.Navigation.OpenCreate)
        assertEquals(1, navigator.screens.count { it == Screen.Training(uuid = null) })
    }
}

/** Records each [navTo] screen; the members [NavigationHandler] never reaches fail fast. */
private class RecordingNavigator : Navigator {

    val screens = mutableListOf<Screen>()

    override fun navTo(screen: Screen) {
        screens.add(screen)
    }

    override fun popBack(): Nothing = error("popBack must not be used by NavigationHandler")

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
