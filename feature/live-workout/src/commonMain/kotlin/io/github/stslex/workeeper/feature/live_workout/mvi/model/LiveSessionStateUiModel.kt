// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class LiveSessionStateUiModel(
    val trainingName: String,
    val elapsedMillis: Long,
    val doneCount: Int,
    val totalCount: Int,
    val setsLogged: Int,
)
