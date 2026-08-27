// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

/**
 * Reviewed count of concrete [Screen] leaves. Both registry oracles assert against this one
 * constant, so a route change updates a single reviewed number rather than two drifting lists.
 */
internal const val SCREEN_ROUTE_BASELINE = 12

/**
 * One sample per concrete route, shared by the JVM and Kotlin/Native registry oracles. The host
 * oracle asserts this class set equals its reflected sealed-leaf set, which is what stops the
 * Native catalog drifting away from the hierarchy.
 *
 * GUARD: samples are non-null and non-default throughout — a null field encodes as an absent key
 * and would let an asymmetric field slip through a round trip undetected.
 */
internal val screenSampleCatalog: List<Screen> = listOf(
    Screen.BottomBar.Home,
    Screen.BottomBar.AllExercises,
    Screen.BottomBar.AllTrainings,
    Screen.Training(uuid = "training-uuid"),
    Screen.Exercise(uuid = "exercise-uuid"),
    Screen.LiveWorkout(sessionUuid = "session-uuid", trainingUuid = "training-uuid"),
    Screen.Settings,
    Screen.Archive,
    Screen.PastSession(sessionUuid = "session-uuid"),
    Screen.ExerciseChart(exerciseUuid = "exercise-uuid"),
    Screen.ExerciseImage(model = "content://sample/image", editable = true),
    Screen.PlanEditor.Existing(
        performedExerciseUuid = "performed-exercise-uuid",
        exerciseUuid = "exercise-uuid",
        trainingUuid = "training-uuid",
    ),
)
