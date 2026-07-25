// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

data class ActiveSessionDomain(
    val sessionUuid: String,
    val trainingUuid: String,
    val startedAt: Long,
)
