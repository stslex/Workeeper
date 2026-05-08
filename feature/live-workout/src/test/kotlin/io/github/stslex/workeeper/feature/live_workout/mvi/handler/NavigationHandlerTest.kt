// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator = navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenPastSession triggers replaceTo with PastSession route`() {
        handler.invoke(Action.Navigation.OpenPastSession(sessionUuid = "session-1"))
        verify(exactly = 1) {
            navigator.replaceTo(Screen.PastSession(sessionUuid = "session-1"))
        }
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
        verify(exactly = 1) {
            navigator.navTo(
                Screen.PlanEditor.Existing(
                    performedExerciseUuid = "performed-1",
                    exerciseUuid = "ex-1",
                    trainingUuid = "training-1",
                ),
            )
        }
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
        verify(exactly = 1) {
            navigator.navTo(
                Screen.PlanEditor.Existing(
                    performedExerciseUuid = "performed-1",
                    exerciseUuid = "ex-1",
                    trainingUuid = null,
                ),
            )
        }
    }
}
