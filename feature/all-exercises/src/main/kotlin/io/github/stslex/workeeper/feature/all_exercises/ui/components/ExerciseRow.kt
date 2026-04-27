// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.components.swipe.AppSwipeAction
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import kotlinx.collections.immutable.persistentListOf

private const val MAX_INLINE_TAGS = 3

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongParameterList")
@Composable
internal fun ExerciseRow(
    item: ExerciseUiModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppUi.shapes.medium)
                .background(AppUi.colors.surfaceTier1)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
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
            if (isSelectionMode) {
                Checkbox(
                    modifier = Modifier
                        .size(AppDimension.iconMd)
                        .testTag("AllExercisesItemCheckbox_${item.uuid}"),
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppUi.colors.accent,
                        uncheckedColor = AppUi.colors.borderStrong,
                        checkmarkColor = AppUi.colors.onAccent,
                    ),
                )
            } else {
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

    if (isSelectionMode) {
        // Disable swipe-to-archive while in selection mode — bulk actions take over.
        rowContent()
    } else {
        AppSwipeAction(
            modifier = modifier.testTag("AllExercisesItemSwipe_${item.uuid}"),
            actionIcon = Icons.Filled.Archive,
            actionLabel = stringResource(R.string.feature_all_exercises_archive_action),
            actionTint = AppUi.colors.status.warning,
            onAction = onArchive,
        ) {
            rowContent()
        }
    }
}

@Composable
private fun ExerciseRowTags(
    tags: kotlinx.collections.immutable.ImmutableList<String>,
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

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExerciseRowPreview() {
    val sample = listOf(
        ExerciseUiModel(
            uuid = "1",
            name = "Bench press",
            type = ExerciseTypeDataModel.WEIGHTED,
            tags = persistentListOf("Push", "Chest"),
            sessionCount = 12,
        ),
        ExerciseUiModel(
            uuid = "2",
            name = "Pull-up",
            type = ExerciseTypeDataModel.WEIGHTLESS,
            tags = persistentListOf("Pull", "Back", "Calisthenics", "Upper"),
            sessionCount = 4,
        ),
        ExerciseUiModel(
            uuid = "3",
            name = "Squat",
            type = ExerciseTypeDataModel.WEIGHTED,
            tags = persistentListOf(),
            sessionCount = 0,
        ),
    )
    AppTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppUi.colors.surfaceTier0),
            contentPadding = PaddingValues(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            items(items = sample, key = { it.uuid }) { item ->
                ExerciseRow(
                    item = item,
                    isSelectionMode = item.uuid == "2",
                    isSelected = item.uuid == "2",
                    onClick = {},
                    onLongPress = {},
                    onArchive = {},
                )
            }
        }
    }
}
