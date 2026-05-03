// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

internal data class SessionDomain(
    val uuid: String,
    val trainingUuid: String,
    val state: SessionStateDomain,
    val startedAt: Long,
    val finishedAt: Long?,
)
