// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.live_workout.resources.Res
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_add_exercise_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_cancel_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_create_inline_exercise_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_discard_session_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_finish_missing_session
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_plan_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_reset_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_set_delete_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_set_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_skip_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_training_name_save_failed
import io.github.stslex.workeeper.feature.live_workout.resources.feature_live_workout_error_unknown
import org.jetbrains.compose.resources.StringResource

/** SP4: each [ErrorType]'s catalog key, read on use where a launch body reports the failure. */
internal object ErrorTypeMapper {

    fun ErrorType.messageRes(): StringResource = when (this) {
        ErrorType.SetSaveFailed -> Res.string.feature_live_workout_error_set_save_failed
        ErrorType.SetDeleteFailed -> Res.string.feature_live_workout_error_set_delete_failed
        ErrorType.ResetFailed -> Res.string.feature_live_workout_error_reset_failed
        ErrorType.SkipFailed -> Res.string.feature_live_workout_error_skip_failed
        ErrorType.FinishMissingSession -> Res.string.feature_live_workout_error_finish_missing_session
        ErrorType.FinishFailed -> Res.string.feature_live_workout_error_finish_failed
        ErrorType.CancelFailed -> Res.string.feature_live_workout_error_cancel_failed
        ErrorType.PlanSaveFailed -> Res.string.feature_live_workout_error_plan_save_failed
        ErrorType.TrainingNameSaveFailed -> Res.string.feature_live_workout_error_training_name_save_failed
        ErrorType.AddExerciseFailed -> Res.string.feature_live_workout_error_add_exercise_failed
        ErrorType.CreateInlineExerciseFailed -> Res.string.feature_live_workout_error_create_inline_exercise_failed
        ErrorType.DiscardSessionFailed -> Res.string.feature_live_workout_error_discard_session_failed
        ErrorType.Unknown -> Res.string.feature_live_workout_error_unknown
    }
}
