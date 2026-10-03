// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable

/** A set the paired watch wrote, as the screen applies it (wear-live-sync.md §6.4). */
@Stable
data class ExternalSetUiModel(
    val performedExerciseUuid: String,
    val set: LiveSetUiModel,
)
