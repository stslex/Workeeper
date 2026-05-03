// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

internal sealed interface ArchivedItem {

    val uuid: String
    val name: String
    val tags: List<String>
    val archivedAt: Long

    data class Exercise(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val type: ExerciseTypeDomain,
    ) : ArchivedItem

    data class Training(
        override val uuid: String,
        override val name: String,
        override val tags: List<String>,
        override val archivedAt: Long,
        val exerciseCount: Int,
    ) : ArchivedItem
}
