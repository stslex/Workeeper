// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable

/** A write the paired watch made, as the screen applies it (wear-live-sync.md §6.4). */
@Stable
sealed interface ExternalWriteUiModel {
    val performedExerciseUuid: String
}

/** A set the paired watch wrote. */
@Stable
data class ExternalSetUiModel(
    override val performedExerciseUuid: String,
    val set: LiveSetUiModel,
) : ExternalWriteUiModel

/** An exercise the paired watch skipped or returned to the session (wear-phase-2-exercise-list.md §6.5). */
@Stable
data class ExternalSkipUiModel(
    override val performedExerciseUuid: String,
    val skipped: Boolean,
) : ExternalWriteUiModel
