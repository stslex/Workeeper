// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel

@Stable
sealed interface ArchivedItem {

    val uuid: String
    val name: String
    val tags: List<String>
    val archivedAt: Long

    @Stable
    data class Exercise(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val type: ExerciseTypeDataModel,
    ) : ArchivedItem

    @Stable
    data class Training(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val exerciseCount: Int,
    ) : ArchivedItem
}
