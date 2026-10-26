// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy

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
    @StringRes
    fun DeleteExerciseCopyUiModel.titleRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_title
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> R.string.feature_live_workout_delete_plan_title
    }

    /** SP4: the delete sheet's body, a template the sheet fills with the exercise name. */
    @StringRes
    fun DeleteExerciseCopyUiModel.bodyRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_body
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION -> R.string.feature_live_workout_delete_plan_body_added
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED -> R.string.feature_live_workout_delete_plan_body_planned
    }

    /** SP4: the delete sheet's confirm label. */
    @StringRes
    fun DeleteExerciseCopyUiModel.confirmRes(): Int = when (this) {
        DeleteExerciseCopyUiModel.ADHOC_WORKOUT -> R.string.feature_live_workout_delete_workout_confirm
        DeleteExerciseCopyUiModel.TEMPLATE_ADDITION,
        DeleteExerciseCopyUiModel.TEMPLATE_PLANNED,
        -> R.string.feature_live_workout_delete_plan_confirm
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
