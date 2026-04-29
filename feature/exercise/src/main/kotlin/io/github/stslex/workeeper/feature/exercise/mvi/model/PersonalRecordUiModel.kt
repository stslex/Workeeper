// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.model

import androidx.compose.runtime.Stable

/**
 * Pre-formatted PR card payload. `displayLabel` is "weight × reps" or "N reps" depending
 * on the exercise type; `relativeDateLabel` is a localized "yesterday" / "12 апр" string.
 * `sessionUuid` is carried for the v2.2 chart entry point — unused in v2.1 UI but cheap to
 * keep so the v2.2 wiring doesn't ripple back through the mapper.
 */
@Stable
data class PersonalRecordUiModel(
    val sessionUuid: String,
    val displayLabel: String,
    val relativeDateLabel: String,
)
