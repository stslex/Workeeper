// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

internal data class PlanSetDomain(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDomain,
)
