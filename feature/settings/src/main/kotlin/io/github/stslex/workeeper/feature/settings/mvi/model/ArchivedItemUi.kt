// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem

@Stable
internal sealed interface ArchivedItemUi {

    val item: ArchivedItem
    val archivedAtLabel: String

    @Stable
    data class Exercise(
        override val item: ArchivedItem.Exercise,
        override val archivedAtLabel: String,
    ) : ArchivedItemUi

    @Stable
    data class Training(
        override val item: ArchivedItem.Training,
        override val archivedAtLabel: String,
    ) : ArchivedItemUi
}
