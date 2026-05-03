// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class ExercisePickerEntry(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDomain,
)
