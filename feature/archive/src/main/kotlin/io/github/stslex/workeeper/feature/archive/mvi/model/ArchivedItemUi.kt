// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem

/**
 * One archived row with its meta line already composed — `kind · archived-since · tags` (§26),
 * built in the mapper so `ArchiveMetaLineTest` can assert it without a composition.
 */
@Stable
sealed interface ArchivedItemUi {

    val item: ArchivedItem

    /** `kind · archived-since · tags`, pre-joined by `ArchiveUiMapper`. */
    val metaLine: String

    @Stable
    data class Exercise(
        override val item: ArchivedItem.Exercise,
        override val metaLine: String,
    ) : ArchivedItemUi

    @Stable
    data class Training(
        override val item: ArchivedItem.Training,
        override val metaLine: String,
    ) : ArchivedItemUi
}
