// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Root of the app's typed destinations. Every concrete leaf must be registered in
 * [screenSavedStateConfiguration] or process death crashes at save time.
 */
@Serializable
@Stable
sealed interface Screen : NavKey {

    val isSingleTop: Boolean get() = false

    @Serializable
    sealed interface BottomBar : Screen {

        override val isSingleTop: Boolean
            get() = true

        @Serializable
        data object Home : BottomBar

        @Serializable
        data object AllExercises : BottomBar

        @Serializable
        data object AllTrainings : BottomBar
    }

    @Serializable
    data class Training(
        val uuid: String?,
    ) : Screen

    @Serializable
    data class Exercise(
        val uuid: String?,
    ) : Screen

    /**
     * Live workout. A non-null [sessionUuid] resumes; [trainingUuid] alone creates a session for
     * that training; both null is a blank ad-hoc entry created downstream.
     */
    @Serializable
    data class LiveWorkout(
        val sessionUuid: String?,
        val trainingUuid: String?,
    ) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Archive : Screen

    @Serializable
    data class PastSession(
        val sessionUuid: String,
    ) : Screen

    /** Per-exercise progress chart; null [exerciseUuid] resolves to the last trained exercise. */
    @Serializable
    data class ExerciseChart(
        val exerciseUuid: String?,
    ) : Screen

    /**
     * Full-screen image viewer; [model] is an opaque caller-owned string interpreted by Coil.
     * The viewer does not parse, normalize, copy, delete, or persist it, and pops back with an
     * [ExerciseImageRequest] name — the editor, not the viewer, performs the request.
     */
    @Serializable
    data class ExerciseImage(
        val model: String,
        /** Whether the caller can act on the request; the detail screen has no Save. */
        val editable: Boolean = false,
    ) : Screen, ScreenWithResult<String>

    /** The two verbs the image viewer can hand back. */
    enum class ExerciseImageRequest {
        REPLACE,
        REMOVE,
    }

    /**
     * Full-screen plan editor for an already-persisted row; saves to DB and hands back `true`.
     * A back that is not a save produces no result. Creating an exercise never routes here.
     */
    @Serializable
    @Stable
    sealed interface PlanEditor : Screen, ScreenWithResult<Boolean> {

        @Serializable
        data class Existing(
            val performedExerciseUuid: String?,
            val exerciseUuid: String?,
            val trainingUuid: String?,
        ) : PlanEditor
    }
}
