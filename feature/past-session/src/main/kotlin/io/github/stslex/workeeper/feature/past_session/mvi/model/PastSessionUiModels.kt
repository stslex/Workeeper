// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList

/**
 * UI model for a finished session as rendered on the Past session detail screen.
 * Display fields are pre-formatted by the mapper layer per the architecture rule that
 * Composables render strings, not shape them.
 */
@Stable
data class PastSessionUiModel(
    val trainingName: String,
    val isAdhoc: Boolean,
    val finishedAtAbsoluteLabel: String,
    val durationLabel: String,
    val totalsLabel: String,
    val volumeLabel: String?,
    val exercises: ImmutableList<PastExerciseUiModel>,
)

@Stable
data class PastExerciseUiModel(
    val performedExerciseUuid: String,
    val exerciseName: String,
    val position: Int,
    val skipped: Boolean,
    val isWeighted: Boolean,
    val sets: ImmutableList<PastSetUiModel>,
)

@Stable
data class PastSetUiModel(
    val setUuid: String,
    val performedExerciseUuid: String,
    val position: Int,
    val type: SetTypeUiModel,
    val weightInput: String,
    val repsInput: String,
    val weightError: Boolean,
    val repsError: Boolean,
)

enum class ErrorType {
    SessionNotFound,
    LoadFailed,
    SaveFailed,
}
