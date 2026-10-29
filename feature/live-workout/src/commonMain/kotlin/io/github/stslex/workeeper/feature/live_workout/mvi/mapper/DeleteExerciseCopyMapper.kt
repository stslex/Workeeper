// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy
import io.github.stslex.workeeper.feature.live_workout.resources.Res
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_added
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_body_planned
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_plan_title
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_body
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_confirm
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_delete_workout_title
import org.jetbrains.compose.resources.StringResource

internal object DeleteExerciseCopyMapper {

    fun map(
        isAdhocSession: Boolean,
        isMidSessionAdded: Boolean,
    ): DeleteExerciseCopyUiModel = when {
        isAdhocSession -> DeleteExerciseCopyUiModel.ADHOC_WORKOUT
        isMidSessionAdded -> DeleteExerciseCopyUiModel.TEMPLATE_ADDITION
        else -> DeleteExerciseCopyUiModel.TEMPLATE_PLANNED
    }

    /** SP4: the delete sheet's title, read in composition. */
    fun DeleteExerciseCopyUiModel.titleRes(): StringResource = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> Res.string.feature_live_workout_delete_workout_title
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> Res.string.feature_live_workout_delete_plan_title
    }

    /** SP4: the delete sheet's body, a template the sheet fills with the exercise name. */
    fun DeleteExerciseCopyUiModel.bodyRes(): StringResource = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> Res.string.feature_live_workout_delete_workout_body
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION -> Res.string.feature_live_workout_delete_plan_body_added
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED -> Res.string.feature_live_workout_delete_plan_body_planned
    }

    /** SP4: the delete sheet's confirm label. */
    fun DeleteExerciseCopyUiModel.confirmRes(): StringResource = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> Res.string.feature_live_workout_delete_workout_confirm
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> Res.string.feature_live_workout_delete_plan_confirm
    }

    /** SP4 and D6: the removal toast's template, taken from the copy in State. */
    fun toastTemplate(
        copy: DeleteExerciseCopyUiModel,
        liveCopy: LiveWorkoutCopy,
    ): String = when (copy) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> liveCopy.toastExerciseRemovedFromWorkoutTemplate
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> liveCopy.toastExerciseRemovedTemplate
    }
}
