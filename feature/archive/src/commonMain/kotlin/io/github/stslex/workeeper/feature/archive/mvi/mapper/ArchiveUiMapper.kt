// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.mapper

import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi

/**
 * The meta line's static copy, resolved by `ArchivePagingHandler` before mapping so the mapper
 * stays a pure join that `ArchiveMetaLineTest` can drive without resources.
 */
internal data class ArchiveMetaCopy(
    val exerciseKind: String,
    val trainingKind: String,
    val archived: String,
    val separator: String,
)

internal object ArchiveUiMapper {

    /** [archivedSince] is this item's resolved «в архиве с <date>», dated by `formatDayMonth`. */
    fun ArchivedItem.Exercise.toUi(
        copy: ArchiveMetaCopy,
        archivedSince: String,
    ): ArchivedItemUi.Exercise = ArchivedItemUi.Exercise(
        item = this,
        metaLine = composeMetaLine(
            kind = copy.exerciseKind,
            copy = copy,
            archivedSince = archivedSince,
        ),
    )

    /** [archivedSince] is this item's resolved «в архиве с <date>», dated by `formatDayMonth`. */
    fun ArchivedItem.Training.toUi(
        copy: ArchiveMetaCopy,
        archivedSince: String,
    ): ArchivedItemUi.Training = ArchivedItemUi.Training(
        item = this,
        metaLine = composeMetaLine(
            kind = copy.trainingKind,
            copy = copy,
            archivedSince = archivedSince,
        ),
    )

    /**
     * `kind · archived-since · tags`; no wrap, so the kind leads to survive truncation. A missing
     * timestamp degrades to the bare word rather than a wrong date.
     */
    private fun ArchivedItem.composeMetaLine(
        kind: String,
        copy: ArchiveMetaCopy,
        archivedSince: String,
    ): String {
        val archivedPhrase = if (archivedAt <= 0L) copy.archived else archivedSince
        return (listOf(kind, archivedPhrase) + tags).joinToString(" ${copy.separator} ")
    }
}
