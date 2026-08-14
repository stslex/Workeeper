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
     * Live workout screen. Both uuids are nullable, and all three combinations are shipped
     * routes:
     *  - `sessionUuid` non-null: resume that in-progress session.
     *  - `sessionUuid` null + `trainingUuid` non-null: create a fresh session for that
     *    training.
     *  - both null: blank-init ad-hoc entry. The session and the training row behind it are
     *    created downstream, so nothing needs to exist before navigating. This is the
     *    destination `AllTrainingsStore.Action.Navigation.OpenBlankSession` and Home's
     *    blank-start row both ask for.
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
     *
     * **It also carries the picture's two verbs** (§26, "The image moves into the pushed top
     * bar"). The editor's form has no image row: replace and remove live *where the picture is*
     * rather than beside the 46dp stand-in for it in the bar. The viewer performs neither — it
     * pops back with an [ExerciseImageRequest] name, and the editor, which owns the permission
     * plumbing, the temp-URI dance and the uncommitted `PendingImage`, does the work.
     * **A request, not a result** — none of that machinery moves, and the viewer stays a viewer.
     *
     * The result is the request's [Enum.name] rather than the enum itself: what crosses a
     * destination boundary has to survive being written down, and a `String` is the shape both
     * the current transport and Nav3 can carry without a serializer. Resolving the name back to
     * the enum is the consumer's job, and belongs in its Store — see
     * `ExerciseStore.Action.Common.ImageRequestReceived`.
     */
    @Serializable
    data class ExerciseImage(
        val model: String,
        /**
         * Whether the caller can act on the request this viewer produces. The viewer offers
         * replace and remove only when this is `true`, because a request nobody can honour is
         * worse than no affordance: the exercise DETAIL screen opens this viewer too, and it has
         * no Save and no dirty interception, so a staged replacement there would look applied and
         * vanish on the way out. The caller states its own capability rather than the viewer
         * guessing at it.
         */
        val editable: Boolean = false,
    ) : Screen, ScreenWithResult<String>

    /**
     * The two verbs the image viewer can hand back. An enum rather than two booleans or a raw
     * string: the caller's `when` is then exhaustive, and a third verb cannot be added on one side
     * only.
     */
    enum class ExerciseImageRequest {
        /** Pick a new picture — the editor reopens its own source sheet. */
        REPLACE,

        /** Drop the picture. Staged like any other edit; the editor's Save is what commits it. */
        REMOVE,
    }

    /**
     * Full-screen plan editor, and it has ONE destination.
     *
     * [Existing] edits the plan attached to a persisted exercise / performed-exercise /
     * training-exercise row. PlanEditor saves directly to DB and hands back `true` on save.
     *
     * **Live-workout is the only consumer.** It reloads the session so the new plan shows.
     * Exercise and Single-training navigate here but never read the result back — a change
     * to their reload behaviour has to add the consumer first. That claim is now checkable:
     * the result type is declared here, so the compiler knows who reads it and as what.
     *
     * A back that is not a save produces no result, and the read yields `null`. Nothing
     * distinguishes "did not save" from "pressed back", and nothing ever did — see
     * [ScreenWithResult].
     *
     * **Creating an exercise does not route here.** A record with no persisted UUID is built on
     * the exercise form, which hosts `PlanEditorBody` inline — so there is no in-flight draft to
     * carry to another screen and hand back. Every destination here edits something that exists.
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
