// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.model

import androidx.compose.runtime.Stable

/**
 * Row in the training-picker bottom sheet. Pre-formatted strings come from
 * `HomeUiMapper`; the picker Composable renders them as-is.
 */
@Stable
data class PickerTrainingItem(
    val trainingUuid: String,
    val name: String,
    val exerciseCountLabel: String,
    val lastSessionRelativeLabel: String?,
)
