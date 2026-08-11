// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.mvi.model.ArchivedItemUi

internal object ArchiveUiMapper {

    fun ArchivedItem.Exercise.toUi(resourceWrapper: ResourceWrapper): ArchivedItemUi.Exercise =
        ArchivedItemUi.Exercise(
            item = this,
            metaLine = composeMetaLine(
                resourceWrapper = resourceWrapper,
                kindRes = R.string.feature_archive_kind_exercise,
            ),
        )

    fun ArchivedItem.Training.toUi(resourceWrapper: ResourceWrapper): ArchivedItemUi.Training =
        ArchivedItemUi.Training(
            item = this,
            metaLine = composeMetaLine(
                resourceWrapper = resourceWrapper,
                kindRes = R.string.feature_archive_kind_training,
            ),
        )

    /**
     * `kind · archived-since · tags` — the drawn fourth payload, «упражнение · в архиве с 3 июля».
     *
     * **The kind is a word at the head, not a badge and not a leading glyph.** `#s-list` draws four
     * payloads on one skeleton and this is the fourth; the rule that puts the type first on the
     * sibling screen puts the kind first here, for the identical stated reason — the line does not
     * wrap, so what truncates is always the tail, and the token that must survive goes at the head.
     *
     * It also means the row does **not** depend on the segmented control to be legible. The segment
     * is chrome and the row is content: a row that only makes sense under its filter stops making
     * sense the moment it is screenshotted, searched, or reused in another list.
     *
     * `type` (exercise) and `exerciseCount` (training) are carried by the sealed domain model and
     * are deliberately **absent** here — the drawn row spends its one line on kind and date, and
     * §0.1 gives that decision to the drawing.
     */
    private fun ArchivedItem.composeMetaLine(
        resourceWrapper: ResourceWrapper,
        kindRes: Int,
    ): String {
        val separator = " ${resourceWrapper.getString(R.string.feature_archive_meta_separator)} "
        val kind = resourceWrapper.getString(kindRes)
        return (listOf(kind, archivedSince(resourceWrapper)) + tags).joinToString(separator)
    }

    /**
     * «в архиве с 3 июля» — day and full month, via [ResourceWrapper.formatDayMonth], which orders
     * the two per locale rather than freezing one order.
     *
     * Deliberately **not** the abbreviated relative span this screen used to draw. The drawn phrase
     * is "since <date>", and "since 2 days ago" is not a sentence; the phrasing and the formatter
     * have to agree, so changing one changed the other. A missing timestamp degrades to the bare
     * word rather than to a wrong date.
     */
    private fun ArchivedItem.archivedSince(resourceWrapper: ResourceWrapper): String =
        if (archivedAt <= 0L) {
            resourceWrapper.getString(R.string.feature_archive_label_archived)
        } else {
            resourceWrapper.getString(
                R.string.feature_archive_label_archived_since_format,
                resourceWrapper.formatDayMonth(archivedAt),
            )
        }
}
