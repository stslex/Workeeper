// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session.model

/** Persisted progress snapshot used when an active session may be replaced. */
data class ActiveSessionProgressInfo(
    val sessionUuid: String,
    val trainingUuid: String,
    val startedAt: Long,
    val doneCount: Int,
    val totalCount: Int,
)
