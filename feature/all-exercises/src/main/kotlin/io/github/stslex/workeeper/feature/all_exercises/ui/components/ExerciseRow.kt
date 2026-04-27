// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.swipe.AppSwipeAction
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel

private const val MAX_INLINE_TAGS = 3

@Composable
internal fun ExerciseRow(
    item: ExerciseUiModel,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSwipeAction(
        modifier = modifier.testTag("AllExercisesItemSwipe_${item.uuid}"),
        actionIcon = Icons.Filled.Archive,
        actionLabel = stringResource(R.string.feature_all_exercises_archive_action),
        actionTint = AppUi.colors.status.warning,
        onAction = onArchive,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppUi.shapes.medium)
                .background(AppUi.colors.surfaceTier1)
                .clickable(onClick = onClick)
                .testTag("AllExercisesItem_${item.uuid}")
                .padding(
                    horizontal = AppDimension.cardPadding,
                    vertical = AppDimension.cardPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            ExerciseTypeIcon(type = item.type)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
            ) {
                Text(
                    text = item.name,
                    style = AppUi.typography.bodyMedium,
                    color = AppUi.colors.textPrimary,
                )
                if (item.tags.isNotEmpty()) {
                    ExerciseRowTags(tags = item.tags)
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_all_exercises_session_count,
                        item.sessionCount,
                        item.sessionCount,
                    ),
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    R.string.feature_all_exercises_chevron_description,
                ),
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ExerciseRowTags(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val visible = tags.take(MAX_INLINE_TAGS)
    val overflow = tags.size - visible.size
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        visible.forEach { tag -> AppTagChip.Static(label = tag) }
        if (overflow > 0) {
            AppTagChip.Static(
                label = stringResource(
                    R.string.feature_all_exercises_overflow_format,
                    overflow,
                ),
            )
        }
    }
}
