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
            archivedAtLabel = toArchivedAtLabel(resourceWrapper),
        )

    fun ArchivedItem.Training.toUi(resourceWrapper: ResourceWrapper): ArchivedItemUi.Training =
        ArchivedItemUi.Training(
            item = this,
            archivedAtLabel = toArchivedAtLabel(resourceWrapper),
        )

    private fun ArchivedItem.toArchivedAtLabel(
        resourceWrapper: ResourceWrapper,
    ): String = if (archivedAt <= 0L) {
        resourceWrapper.getString(R.string.feature_archive_label_archived)
    } else {
        val relative = resourceWrapper.getAbbreviatedRelativeTime(archivedAt)
        resourceWrapper.getString(R.string.feature_archive_label_archived_relative_format, relative)
    }
}
