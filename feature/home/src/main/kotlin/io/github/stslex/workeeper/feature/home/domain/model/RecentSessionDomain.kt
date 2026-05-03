// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

internal data class RecentSessionDomain(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exerciseCount: Int,
    val setCount: Int,
)
