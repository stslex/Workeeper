// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
data class TrainingListItemUi(
    val uuid: String,
    val name: String,
    val tags: ImmutableList<String>,
    val exerciseCount: Int,
    val isActive: Boolean,
    val statusLabel: String,
)
