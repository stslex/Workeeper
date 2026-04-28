// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedItemUi

internal fun ArchivedItem.Exercise.toUi(resourceWrapper: ResourceWrapper): ArchivedItemUi.Exercise =
    ArchivedItemUi.Exercise(
        item = this,
        archivedAtLabel = toArchivedAtLabel(resourceWrapper),
    )

internal fun ArchivedItem.Training.toUi(resourceWrapper: ResourceWrapper): ArchivedItemUi.Training =
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
