// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        assertEquals(1, navigator.popBackCalls)
    }
}

/** Counts [popBack]; the members [NavigationHandler] never reaches fail fast. */
private class RecordingNavigator : Navigator {

    var popBackCalls: Int = 0
        private set

    override fun navTo(screen: Screen): Nothing = error("navTo must not be used by NavigationHandler")

    override fun popBack() {
        popBackCalls += 1
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
