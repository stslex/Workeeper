// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

internal data class ExerciseDomain(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDomain,
    val description: String?,
    val imagePath: String?,
)
