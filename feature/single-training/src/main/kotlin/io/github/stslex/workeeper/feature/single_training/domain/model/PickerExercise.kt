// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.model

internal data class PickerExercise(
    val exercise: ExerciseDomain,
    val labels: List<String>,
)
