// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
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
    fun `OpenSession navigates to Screen PastSession with the session uuid`() {
        handler.invoke(Action.Navigation.OpenSession("session-uuid"))
        verify(exactly = 1) { navigator.navTo(Screen.PastSession(sessionUuid = "session-uuid")) }
    }

    @Test
    fun `OpenLiveWorkout navigates to Screen LiveWorkout with the resume session uuid`() {
        handler.invoke(Action.Navigation.OpenLiveWorkout(sessionUuid = "session-7"))
        verify(exactly = 1) {
            navigator.navTo(
                Screen.LiveWorkout(sessionUuid = "session-7", trainingUuid = null),
            )
        }
    }

    @Test
    fun `OpenImageViewer carries the model arg and the caller's own capability`() {
        val model = "/data/user/0/app/files/exercise_images/abc.jpg"
        handler.invoke(Action.Navigation.OpenImageViewer(model, editable = true))
        verify(exactly = 1) {
            navigator.navTo(Screen.ExerciseImage(model = model, editable = true))
        }
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
        verify(exactly = 1) {
            navigator.navTo(Screen.ExerciseImage(model = model, editable = false))
        }
    }

    @Test
    fun `OpenChart navigates to Screen ExerciseChart with the exercise uuid`() {
        handler.invoke(Action.Navigation.OpenChart(exerciseUuid = "ex-1"))
        verify(exactly = 1) {
            navigator.navTo(Screen.ExerciseChart(exerciseUuid = "ex-1"))
        }
    }

    @Test
    fun `OpenPlanEditorExisting navigates to Screen PlanEditor Existing with exercise scope`() {
        handler.invoke(Action.Navigation.OpenPlanEditorExisting(exerciseUuid = "ex-1"))
        verify(exactly = 1) {
            navigator.navTo(
                Screen.PlanEditor.Existing(
                    performedExerciseUuid = null,
                    exerciseUuid = "ex-1",
                    trainingUuid = null,
                ),
            )
        }
    }
}
