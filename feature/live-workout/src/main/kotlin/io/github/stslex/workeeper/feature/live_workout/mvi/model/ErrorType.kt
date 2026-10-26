// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

/** SP4: what failed; `ErrorTypeMapper` maps each value to its catalog key. */
internal enum class ErrorType {
    SetSaveFailed,
    SetDeleteFailed,
    ResetFailed,
    SkipFailed,
    FinishMissingSession,
    FinishFailed,
    CancelFailed,
    PlanSaveFailed,
    TrainingNameSaveFailed,
    AddExerciseFailed,
    CreateInlineExerciseFailed,
    DiscardSessionFailed,
    Unknown,
}
