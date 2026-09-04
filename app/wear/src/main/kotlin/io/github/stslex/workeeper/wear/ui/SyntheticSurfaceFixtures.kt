// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.NumericField

/** Payload-free render fixtures. They contain no protocol identity, lease, or mutation callback. */
internal object SyntheticSurfaceFixtures {
    const val EXTRA_ID = "wear_surface_fixture"
    const val ACTIVE_BOUNDARY = "active_boundary"
    const val WEIGHTLESS = "weightless"
    const val FIELD_ERROR = "field_error"
    const val REFRESH_REQUIRED = "refresh_required"
    const val DISCONNECTED = "disconnected"
    const val NO_SETS = "no_sets"
    const val UNSUPPORTED = "unsupported"
    const val PAYLOAD_TOO_LARGE = "payload_too_large"
    const val COMPLETE = "complete"
    const val RETRYABLE = "retryable"
    const val PROTOCOL_MISMATCH = "protocol_mismatch"
    const val NO_SESSION = "no_session"
    const val LOADING = "loading"

    /**
     * One fixture per [WearSurfaceKind] (both ACTIVE shapes), in the §6 table order. The
     * redesign gates iterate this list so a kind cannot fall out of coverage silently.
     */
    fun allKinds(): List<WearSurfaceModel> = listOf(
        ACTIVE_BOUNDARY, WEIGHTLESS, FIELD_ERROR, REFRESH_REQUIRED, DISCONNECTED, NO_SETS,
        UNSUPPORTED, PAYLOAD_TOO_LARGE, COMPLETE, RETRYABLE, PROTOCOL_MISMATCH, NO_SESSION,
        LOADING,
    ).map { requireNotNull(find(it)) }

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
        // A validation error on the ACTIVE surface: the only fixture that renders the
        // `field_error` line, which no fixture reached before (#284 round 5 audit).
        FIELD_ERROR -> WearSurfaceModel(
            kind = WearSurfaceKind.ACTIVE,
            trainingName = "Full body strength",
            exerciseName = "Front squat",
            completedExercises = 1,
            totalExercises = 5,
            setOrdinal = 1,
            totalSets = 3,
            reps = 0,
            weightHundredthsKg = 6_000,
            weighted = true,
            controlsVisible = true,
            controlsEnabled = true,
            fieldError = NumericField.REPS,
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
        DISCONNECTED -> WearSurfaceModel(
            kind = WearSurfaceKind.DISCONNECTED,
            trainingName = "Full body strength",
            exerciseName = "Romanian deadlift with dumbbells",
            completedExercises = 3,
            totalExercises = 6,
            setOrdinal = 1,
            totalSets = 3,
            reps = 10,
            weightHundredthsKg = 7_250,
            weighted = true,
            controlsVisible = true,
        )
        NO_SETS -> WearSurfaceModel(
            kind = WearSurfaceKind.PHONE_ACTION_NO_SETS,
            exerciseName = "Bulgarian split squat",
        )
        UNSUPPORTED -> WearSurfaceModel(
            kind = WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
            exerciseName = "Weighted dips",
            fieldError = NumericField.WEIGHT,
        )
        PAYLOAD_TOO_LARGE -> WearSurfaceModel(kind = WearSurfaceKind.PAYLOAD_TOO_LARGE)
        COMPLETE -> WearSurfaceModel(
            kind = WearSurfaceKind.WORKOUT_COMPLETE,
            trainingName = "Full body strength",
            completedExercises = 12,
            totalExercises = 12,
        )
        RETRYABLE -> WearSurfaceModel(
            kind = WearSurfaceKind.RETRYABLE_ERROR,
            retryEnabled = true,
        )
        PROTOCOL_MISMATCH -> WearSurfaceModel(kind = WearSurfaceKind.PROTOCOL_MISMATCH)
        NO_SESSION -> WearSurfaceModel(kind = WearSurfaceKind.NO_SESSION)
        LOADING -> WearSurfaceModel(kind = WearSurfaceKind.LOADING)
        else -> null
    }
}
