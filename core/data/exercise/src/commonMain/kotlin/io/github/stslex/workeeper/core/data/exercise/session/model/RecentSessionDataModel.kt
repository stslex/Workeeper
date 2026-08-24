// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session.model

import io.github.stslex.workeeper.core.data.database.session.RecentSessionRow

/**
 * Domain model for the Home recent-sessions list. Mirrors [RecentSessionRow] but exposes
 * String UUIDs the way feature modules consume them — pre-formatted display strings live in
 * UI mappers, not here.
 */
data class RecentSessionDataModel(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exerciseCount: Int,
    val setCount: Int,
)

internal fun RecentSessionRow.toData(): RecentSessionDataModel = RecentSessionDataModel(
    sessionUuid = sessionUuid.toString(),
    trainingUuid = trainingUuid.toString(),
    trainingName = trainingName,
    isAdhoc = isAdhoc,
    startedAt = startedAt,
    finishedAt = finishedAt,
    exerciseCount = exerciseCount,
    setCount = setCount,
)
