// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
@Stable
sealed interface Screen {

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
     * Live workout screen. At least one of [sessionUuid] / [trainingUuid] must be non-null:
     *  - `sessionUuid` non-null: resume the in-progress session.
     *  - `sessionUuid` null + `trainingUuid` non-null: create a fresh session for that
     *    training.
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

    /**
     * Per-exercise progress chart (v2.2). When [exerciseUuid] is `null` the screen resolves
     * to the user's most recently trained exercise. The picker on the chart screen lets the
     * user switch to any other exercise that has finished-session history.
     */
    @Serializable
    data class ExerciseChart(
        val exerciseUuid: String?,
    ) : Screen

    /**
     * Full-screen image viewer. [model] is either an absolute file path
     * (`filesDir/exercise_images/<uuid>.jpg`) or a content URI string
     * (e.g. from `PickVisualMedia`); Coil resolves both transparently.
     */
    @Serializable
    data class ExerciseImage(
        val model: String,
    ) : Screen

    /**
     * Full-screen plan editor (v2.4 D1). Replaces the bottom-sheet plan editor.
     *
     *  - `performedExerciseUuid` non-null: edit the plan attached to a live workout's
     *    `performed_exercise_table` row. Backed by `training_exercise_table.plan_sets`
     *    when `trainingUuid` non-null, or by `exercise_table.last_adhoc_sets` when adhoc.
     *  - `exerciseUuid` non-null: edit the default plan attached to an exercise (used
     *    by Exercise detail screen "Edit default plan" action).
     *
     * Exactly one of the two must be non-null.
     */
    @Serializable
    data class PlanEditor(
        val performedExerciseUuid: String?,
        val exerciseUuid: String?,
        val trainingUuid: String?,
    ) : Screen {

        companion object {

            private const val SAVED_STATE_PLAN_EDITOR_SAVED: String = "plan-editor-saved"

            val planEditorSavedAttr = SaveHandlerAttr(SAVED_STATE_PLAN_EDITOR_SAVED, false)
        }
    }

    companion object {

        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        fun Screen.isCurrentScreen(
            route: String,
        ): Boolean = this::class.serializer().descriptor.serialName == route
    }
}

inline fun <reified S : Screen> NavGraphBuilder.navScreen(
    noinline content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}

inline fun <reified S : Screen> NavGraphBuilder.navScreenWithState(
    noinline content: @Composable AnimatedContentScope.(S, SavedStateHandle) -> Unit,
) {
    composable<S> { backStackEntry ->
        content(backStackEntry.toRoute(), backStackEntry.savedStateHandle)
    }
}
