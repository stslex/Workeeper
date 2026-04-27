// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Stable
data class TrainingListItemUi(
    val uuid: String,
    val name: String,
    val tags: ImmutableList<String>,
    val exerciseCount: Int,
    val lastSessionAt: Long?,
    val isActive: Boolean,
    val activeSessionStartedAt: Long?,
)

internal fun TrainingListItem.toUi(): TrainingListItemUi = TrainingListItemUi(
    uuid = data.uuid,
    name = data.name,
    tags = data.labels.toImmutableList(),
    exerciseCount = exerciseCount,
    lastSessionAt = lastSessionAt,
    isActive = isActive,
    activeSessionStartedAt = activeSessionStartedAt,
)
