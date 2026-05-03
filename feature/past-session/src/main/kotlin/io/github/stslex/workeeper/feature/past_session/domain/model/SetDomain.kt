// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain.model

internal data class SetDomain(
    val uuid: String,
    val reps: Int,
    val weight: Double?,
    val type: SetTypeDomain,
)
