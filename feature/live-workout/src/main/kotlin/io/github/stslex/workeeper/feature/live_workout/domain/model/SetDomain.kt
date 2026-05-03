// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class SetDomain(
    val uuid: String,
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDomain,
)
