// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
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
     * Full-screen plan editor. Two destinations:
     *
     *  - [Existing]: edit the plan attached to a persisted exercise / performed-exercise /
     *    training-exercise row. PlanEditor saves directly to DB and signals the caller via
     *    [planEditorSavedAttr] = true so the caller can perform a partial reload of
     *    `(type, plan)` (Exercise) or a full reload (Single-training, Live-workout) on
     *    resume.
     *
     *  - [Draft]: edit the plan for an in-flight exercise that is still being created
     *    (no persisted UUID yet). PlanEditor does NOT touch the DB; on Done it pops back
     *    with the serialized [PlanDraftResult] JSON via [planEditorDraftResultAttr]. The
     *    caller merges the result into local state; final persistence happens on the
     *    caller's own Save.
     *
     * Type ownership lives in PlanEditor for both destinations — the WEIGHTED ↔ WEIGHTLESS
     * toggle and the type-change confirm dialog (with weight-wipe semantics) are the plan
     * editor's responsibility.
     */
    @Serializable
    @Stable
    sealed interface PlanEditor : Screen {

        @Serializable
        data class Existing(
            val performedExerciseUuid: String?,
            val exerciseUuid: String?,
            val trainingUuid: String?,
        ) : PlanEditor

        @Serializable
        data class Draft(
            val initialType: ExerciseTypeUiModel,
            val initialPlanJson: String?,
        ) : PlanEditor

        companion object {

            private const val SAVED_STATE_PLAN_EDITOR_SAVED: String = "plan-editor-saved"
            private const val SAVED_STATE_PLAN_EDITOR_DRAFT_RESULT: String =
                "plan-editor-draft-result"

            val planEditorSavedAttr = SaveHandlerAttr(SAVED_STATE_PLAN_EDITOR_SAVED, false)

            /**
             * Carries the serialized [PlanDraftResult] JSON back to a Draft-mode caller.
             * Default value is `null` so the consumer's `LaunchedEffect(attrValue)` only
             * fires after a real Done-click writes the JSON in.
             */
            val planEditorDraftResultAttr: SaveHandlerAttr<String> =
                SaveHandlerAttr(SAVED_STATE_PLAN_EDITOR_DRAFT_RESULT, null)
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
