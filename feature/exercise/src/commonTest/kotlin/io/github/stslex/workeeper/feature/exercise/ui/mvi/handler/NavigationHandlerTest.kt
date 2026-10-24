// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
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

    @Test
    fun `OpenSession navigates to Screen PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenSession("session-uuid"))
        assertEquals(1, navigator.navigated.count { it == Screen.PastSession(sessionUuid = "session-uuid") })
    }

    @Test
    fun `OpenLiveWorkout navigates to Screen LiveWorkout with the resume session uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkout(sessionUuid = "session-7"))
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.LiveWorkout(sessionUuid = "session-7", trainingUuid = null)
            },
        )
    }

    @Test
    fun `OpenImageViewer carries the model arg and the caller's own capability`() {
        val model = "/data/user/0/app/files/exercise_images/abc.jpg"
        handler.invoke(Action.Navigation.OpenImageViewer(model, editable = true))
        assertEquals(
            1,
            navigator.navigated.count { it == Screen.ExerciseImage(model = model, editable = true) },
        )
    }

    /**
     * `editable` must reach the route, not be dropped on the way: it is what decides whether the
     * viewer offers verbs the caller cannot honour, and a handler that forwarded the model alone
     * would fall back to the parameter's `false` default and look correct on the read path only.
     */
    @Test
    fun `OpenImageViewer forwards a non-editable caller as non-editable`() {
        val model = "/data/user/0/app/files/exercise_images/abc.jpg"
        handler.invoke(Action.Navigation.OpenImageViewer(model, editable = false))
        assertEquals(
            1,
            navigator.navigated.count { it == Screen.ExerciseImage(model = model, editable = false) },
        )
    }

    @Test
    fun `OpenChart navigates to Screen ExerciseChart with the exercise uuid`() {
        handler.invoke(Action.Navigation.OpenChart(exerciseUuid = "ex-1"))
        assertEquals(
            1,
            navigator.navigated.count { it == Screen.ExerciseChart(exerciseUuid = "ex-1") },
        )
    }

    /** [navTo] and [popBack] record; the other four fail fast. */
    private class RecordingNavigator : Navigator {

        val navigated = mutableListOf<Screen>()
        var popBackCalls = 0
            private set

        override fun navTo(screen: Screen) {
            navigated.add(screen)
        }

        override fun popBack() {
            popBackCalls++
        }

        override fun <S, R : Any> popBackWithResult(
            destination: KClass<S>,
            result: R,
        ): Nothing where S : ScreenWithResult<R> = error("popBackWithResult must not be used here")

        override fun replaceTo(screen: Screen): Nothing = error("replaceTo must not be used here")

        override fun restartApp(): Nothing = error("restartApp must not be used here")

        override fun openRecovery(): Nothing = error("openRecovery must not be used here")
    }
}
