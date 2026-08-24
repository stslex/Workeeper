// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * Pre-formatted record-hero payload (`.prhero`, extraction §3.3). [weightLabel] is `null` for
 * weightless records; the hero composes the parts because `×` is not in the Archivo charset.
 */
@Stable
data class PersonalRecordUiModel(
    val sessionUuid: String,
    val weightLabel: String?,
    val repsLabel: String,
    val absoluteDateLabel: String,
)
