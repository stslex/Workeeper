// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `OpenLiveWorkoutResume navigates to LiveWorkout with the active session uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-7"))
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.LiveWorkout(sessionUuid = "session-7", trainingUuid = null)
            },
        )
    }

    @Test
    fun `OpenLiveWorkoutFresh navigates to LiveWorkout with the training uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkoutFresh(trainingUuid = "training-3"))
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.LiveWorkout(sessionUuid = null, trainingUuid = "training-3")
            },
        )
    }

    @Test
    fun `OpenPastSession navigates to PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-9"))
        assertEquals(
            1,
            navigator.navigated.count { it == Screen.PastSession(sessionUuid = "session-9") },
        )
    }

    @Test
    fun `OpenSettings navigates to Settings`() {
        handler.invoke(Action.Navigation.OpenSettings)
        assertEquals(1, navigator.navigated.count { it == Screen.Settings })
    }

    @Test
    fun `OpenAllTrainings navigates to AllTrainings bottom-bar destination`() {
        handler.invoke(Action.Navigation.OpenAllTrainings)
        assertEquals(1, navigator.navigated.count { it == Screen.BottomBar.AllTrainings })
    }

    /** Records [navTo]; the members [NavigationHandler] never reaches fail fast. */
    private class RecordingNavigator : Navigator {

        val navigated = mutableListOf<Screen>()

        override fun navTo(screen: Screen) {
            navigated.add(screen)
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
}
