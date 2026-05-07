// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
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
    fun `OpenExerciseDetail navigates to Screen Exercise`() {
        handler.invoke(Action.Navigation.OpenExerciseDetail("ex-1"))
        verify(exactly = 1) { navigator.navTo(Screen.Exercise(uuid = "ex-1")) }
    }

    @Test
    fun `OpenSession navigates to Screen PastSession`() {
        handler.invoke(Action.Navigation.OpenSession(sessionUuid = "session-99"))
        verify(exactly = 1) { navigator.navTo(Screen.PastSession(sessionUuid = "session-99")) }
    }

    @Test
    fun `OpenLiveWorkout navigates to Screen LiveWorkout with both uuids`() {
        handler.invoke(
            Action.Navigation.OpenLiveWorkout(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            ),
        )
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(sessionUuid = "session-1", trainingUuid = "training-1"),
            )
        }
    }

    @Test
    fun `OpenLiveWorkout passes a null sessionUuid to LiveWorkout when blank`() {
        handler.invoke(
            Action.Navigation.OpenLiveWorkout(sessionUuid = "", trainingUuid = "training-1"),
        )
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(sessionUuid = null, trainingUuid = "training-1"),
            )
        }
    }

    @Test
    fun `OpenPlanEditor navigates to Screen PlanEditor with the training scope`() {
        handler.invoke(
            Action.Navigation.OpenPlanEditor(
                trainingUuid = "training-1",
                exerciseUuid = "ex-1",
            ),
        )
        verify(exactly = 1) {
            navigator.navTo(
                Screen.PlanEditor(
                    performedExerciseUuid = null,
                    exerciseUuid = "ex-1",
                    trainingUuid = "training-1",
                ),
            )
        }
    }
}
