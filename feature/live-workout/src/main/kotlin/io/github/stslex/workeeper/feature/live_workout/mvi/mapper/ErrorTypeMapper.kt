// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import androidx.annotation.StringRes
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType

/** SP4: each [ErrorType]'s catalog key, read on use where a launch body reports the failure. */
internal object ErrorTypeMapper {

    @StringRes
    fun ErrorType.messageRes(): Int = when (this) {
        ErrorType.SetSaveFailed -> R.string.feature_live_workout_error_set_save_failed
        ErrorType.SetDeleteFailed -> R.string.feature_live_workout_error_set_delete_failed
        ErrorType.ResetFailed -> R.string.feature_live_workout_error_reset_failed
        ErrorType.SkipFailed -> R.string.feature_live_workout_error_skip_failed
        ErrorType.FinishMissingSession -> R.string.feature_live_workout_error_finish_missing_session
        ErrorType.FinishFailed -> R.string.feature_live_workout_error_finish_failed
        ErrorType.CancelFailed -> R.string.feature_live_workout_error_cancel_failed
        ErrorType.PlanSaveFailed -> R.string.feature_live_workout_error_plan_save_failed
        ErrorType.TrainingNameSaveFailed -> R.string.feature_live_workout_error_training_name_save_failed
        ErrorType.AddExerciseFailed -> R.string.feature_live_workout_error_add_exercise_failed
        ErrorType.CreateInlineExerciseFailed -> R.string.feature_live_workout_error_create_inline_exercise_failed
        ErrorType.DiscardSessionFailed -> R.string.feature_live_workout_error_discard_session_failed
        ErrorType.Unknown -> R.string.feature_live_workout_error_unknown
    }
}
