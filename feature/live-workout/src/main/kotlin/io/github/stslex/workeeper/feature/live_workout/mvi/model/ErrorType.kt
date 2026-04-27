// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import io.github.stslex.workeeper.feature.live_workout.R

internal enum class ErrorType(val msgRes: Int) {
    InvalidReps(R.string.feature_live_workout_error_invalid_reps),
    SetSaveFailed(R.string.feature_live_workout_error_set_save_failed),
    SetDeleteFailed(R.string.feature_live_workout_error_set_delete_failed),
    ResetFailed(R.string.feature_live_workout_error_reset_failed),
    SkipFailed(R.string.feature_live_workout_error_skip_failed),
    FinishMissingSession(R.string.feature_live_workout_error_finish_missing_session),
    FinishFailed(R.string.feature_live_workout_error_finish_failed),
    CancelFailed(R.string.feature_live_workout_error_cancel_failed),
    PlanSaveFailed(R.string.feature_live_workout_error_plan_save_failed),
    Unknown(R.string.feature_live_workout_error_unknown)
}