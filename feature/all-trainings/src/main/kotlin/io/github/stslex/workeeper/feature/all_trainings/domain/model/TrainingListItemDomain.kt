// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain.model

internal data class TrainingListItemDomain(
    val uuid: String,
    val name: String,
    val tags: List<String>,
    val exerciseCount: Int,
    val lastSessionAt: Long?,
    val isActive: Boolean,
    val activeSessionUuid: String?,
    val activeSessionStartedAt: Long?,
)
