// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

/** Payload-free render fixtures. They contain no protocol identity, lease, or mutation callback. */
internal object SyntheticSurfaceFixtures {
    const val EXTRA_ID = "wear_surface_fixture"
    const val ACTIVE_BOUNDARY = "active_boundary"
    const val WEIGHTLESS = "weightless"
    const val REFRESH_REQUIRED = "refresh_required"
    const val NO_SETS = "no_sets"
    const val COMPLETE = "complete"

    fun find(id: String?): WearSurfaceModel? = when (id) {
        ACTIVE_BOUNDARY -> WearSurfaceModel(
            kind = WearSurfaceKind.ACTIVE,
            trainingName = "Full body strength",
            exerciseName = "Single-arm dumbbell shoulder press",
            completedExercises = 8,
            totalExercises = 12,
            setOrdinal = 4,
            totalSets = 4,
            reps = 999,
            weightHundredthsKg = 99_999,
            weighted = true,
            controlsVisible = true,
            controlsEnabled = true,
            completeEnabled = true,
        )
        WEIGHTLESS -> WearSurfaceModel(
            kind = WearSurfaceKind.ACTIVE,
            trainingName = "Mobility",
            exerciseName = "Plank",
            completedExercises = 0,
            totalExercises = 1,
            setOrdinal = 1,
            totalSets = 2,
            reps = 12,
            weighted = false,
            controlsVisible = true,
            controlsEnabled = true,
            completeEnabled = true,
        )
        REFRESH_REQUIRED -> WearSurfaceModel(
            kind = WearSurfaceKind.REFRESH_REQUIRED,
            trainingName = "Full body strength",
            exerciseName = "Deadlift",
            completedExercises = 2,
            totalExercises = 6,
            setOrdinal = 2,
            totalSets = 5,
            reps = 8,
            weightHundredthsKg = 10_000,
            weighted = true,
            controlsVisible = true,
        )
        NO_SETS -> WearSurfaceModel(
            kind = WearSurfaceKind.PHONE_ACTION_NO_SETS,
            exerciseName = "Bulgarian split squat",
        )
        COMPLETE -> WearSurfaceModel(
            kind = WearSurfaceKind.WORKOUT_COMPLETE,
            trainingName = "Full body strength",
            completedExercises = 12,
            totalExercises = 12,
        )
        else -> null
    }
}
