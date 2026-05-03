// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

internal data class ActiveSessionWithStatsDomain(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val totalCount: Int,
    val doneCount: Int,
)
