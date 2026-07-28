// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * Pre-formatted record-hero payload (`.prhero`, extraction §3.3). [weightLabel] is the
 * trimmed weight for weighted records and `null` for weightless ones — the hero composes
 * `{weight}×{reps}` or `{reps} + unit` from the split parts because the `×` cannot travel
 * through the Archivo charset (spec §4 C2). [absoluteDateLabel] is the medium-format date
 * the mockup draws; the drawn second term (`· {training}`) is NOT here — the PR flow does
 * not carry the training name (see the PR delta table's stop-and-report item).
 * [sessionUuid] is carried for the chart entry point and the history-row record match.
 */
@Stable
data class PersonalRecordUiModel(
    val sessionUuid: String,
    val weightLabel: String?,
    val repsLabel: String,
    val absoluteDateLabel: String,
)
