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

    /** `kind · archived-since · tags`; no wrap, so the kind leads to survive truncation. */
    private fun ArchivedItem.composeMetaLine(
        resourceWrapper: ResourceWrapper,
        kindRes: Int,
    ): String {
        val separator = " ${resourceWrapper.getString(R.string.feature_archive_meta_separator)} "
        val kind = resourceWrapper.getString(kindRes)
        return (listOf(kind, archivedSince(resourceWrapper)) + tags).joinToString(separator)
    }

    /**
     * «в архиве с <date>» via [ResourceWrapper.formatDayMonth], never a relative span: phrasing and
     * formatter must agree. A missing timestamp degrades to the bare word rather than a wrong date.
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
