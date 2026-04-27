// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.sets

import kotlinx.serialization.Serializable

@Serializable
data class PlanSetDataModel(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDataModel,
)
