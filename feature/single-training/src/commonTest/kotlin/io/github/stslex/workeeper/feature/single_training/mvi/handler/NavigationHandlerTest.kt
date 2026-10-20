// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        assertEquals(1, navigator.popBacks)
    }

    @Test
    fun `OpenExerciseDetail navigates to Screen Exercise`() {
        handler.invoke(Action.Navigation.OpenExerciseDetail("ex-1"))
        assertEquals(1, navigator.navigated.count { it == Screen.Exercise(uuid = "ex-1") })
    }

    @Test
    fun `OpenSession navigates to Screen PastSession`() {
        handler.invoke(Action.Navigation.OpenSession(sessionUuid = "session-99"))
        assertEquals(1, navigator.navigated.count { it == Screen.PastSession(sessionUuid = "session-99") })
    }

    @Test
    fun `OpenLiveWorkout navigates to Screen LiveWorkout with both uuids`() {
        handler.invoke(
            Action.Navigation.OpenLiveWorkout(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            ),
        )
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.LiveWorkout(sessionUuid = "session-1", trainingUuid = "training-1")
            },
        )
    }

    @Test
    fun `OpenLiveWorkout passes a null sessionUuid to LiveWorkout when blank`() {
        handler.invoke(
            Action.Navigation.OpenLiveWorkout(sessionUuid = "", trainingUuid = "training-1"),
        )
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.LiveWorkout(sessionUuid = null, trainingUuid = "training-1")
            },
        )
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
