// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.domain.model

internal data class PlanSetDomain(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDomain,
)

internal enum class SetTypeDomain { WARMUP, WORK, FAILURE, DROP }
