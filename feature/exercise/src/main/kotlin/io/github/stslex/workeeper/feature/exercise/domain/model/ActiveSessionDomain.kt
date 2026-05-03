// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

internal data class ActiveSessionDomain(
    val sessionUuid: String,
    val trainingUuid: String,
    val startedAt: Long,
)
