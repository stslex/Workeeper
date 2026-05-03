// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.model

internal data class ExerciseDomain(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDomain,
    val description: String?,
    val imagePath: String?,
    val archived: Boolean,
    val archivedAt: Long?,
    val timestamp: Long,
)
