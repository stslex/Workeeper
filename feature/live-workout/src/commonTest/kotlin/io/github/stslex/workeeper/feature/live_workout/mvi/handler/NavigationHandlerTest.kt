// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
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
    fun `OpenPastSession triggers replaceTo with PastSession route`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-1"))
        assertEquals(
            1,
            navigator.replaced.count { it == Screen.PastSession(sessionUuid = "session-1") },
        )
    }

    @Test
    fun `OpenPlanEditor navigates to Screen PlanEditor Existing with the live-workout scope`() {
        handler.invoke(
            Action.Navigation.OpenPlanEditor(
                performedExerciseUuid = "performed-1",
                exerciseUuid = "ex-1",
                trainingUuid = "training-1",
            ),
        )
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.PlanEditor.Existing(
                    performedExerciseUuid = "performed-1",
                    exerciseUuid = "ex-1",
                    trainingUuid = "training-1",
                )
            },
        )
    }

    @Test
    fun `OpenPlanEditor for an adhoc session uses null trainingUuid`() {
        handler.invoke(
            Action.Navigation.OpenPlanEditor(
                performedExerciseUuid = "performed-1",
                exerciseUuid = "ex-1",
                trainingUuid = null,
            ),
        )
        assertEquals(
            1,
            navigator.navigated.count {
                it == Screen.PlanEditor.Existing(
                    performedExerciseUuid = "performed-1",
                    exerciseUuid = "ex-1",
                    trainingUuid = null,
                )
            },
        )
    }

    /** Records what the relaxed mock was verified on; the members no identity reaches fail fast. */
    private class RecordingNavigator : Navigator {

        val navigated = mutableListOf<Screen>()
        val replaced = mutableListOf<Screen>()
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

        override fun replaceTo(screen: Screen) {
            replaced.add(screen)
        }

        override fun restartApp(): Nothing = error("restartApp must not be used here")

        override fun openRecovery(): Nothing = error("openRecovery must not be used here")
    }
}
