// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Composable
internal fun TagFilterRow(
    tags: ImmutableList<TagUiModel>,
    activeTagFilter: ImmutableSet<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("AllExercisesTagFilter"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        contentPadding = PaddingValues(
            horizontal = AppDimension.screenEdge,
            vertical = AppDimension.Space.sm,
        ),
    ) {
        items(items = tags, key = { it.uuid }) { tag ->
            AppTagChip.Selectable(
                modifier = Modifier.testTag("AllExercisesTagFilter_${tag.uuid}"),
                label = tag.name,
                selected = tag.uuid in activeTagFilter,
                onSelectedChange = { onToggle(tag.uuid) },
            )
        }
    }
}
