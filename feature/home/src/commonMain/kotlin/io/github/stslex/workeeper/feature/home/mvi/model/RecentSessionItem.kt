// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.model

import androidx.compose.runtime.Stable

/**
 * Pre-formatted UI row for the Home recent-sessions list. All display text is shaped by
 * `HomeUiMapper`; the Composable just renders the strings.
 */
@Stable
data class RecentSessionItem(
    val sessionUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val finishedAtRelativeLabel: String,
    val durationLabel: String,
    val statsLabel: String,
)
