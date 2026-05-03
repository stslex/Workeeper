// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

import kotlin.uuid.Uuid

internal data class ExerciseChangeDomain(
    val uuid: Uuid,
    val name: String,
    val type: ExerciseTypeDomain,
    val description: String?,
    val imagePath: String?,
    val archived: Boolean,
    val timestamp: Long,
    val labels: List<String>,
    val lastAdhocSets: List<PlanSetDomain>?,
)
