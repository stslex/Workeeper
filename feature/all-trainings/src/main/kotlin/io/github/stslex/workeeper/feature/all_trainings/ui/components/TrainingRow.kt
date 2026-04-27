// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import kotlinx.collections.immutable.persistentListOf

private const val MAX_INLINE_TAGS = 3

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun TrainingRow(
    item: TrainingListItemUi,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (item.isActive) {
        AppUi.colors.surfaceTier3
    } else {
        AppUi.colors.surfaceTier1
    }
    val activeBorderModifier = if (item.isActive) {
        // Accent border highlights an in-progress session; the surface tint above already
        // visually separates the row.
        Modifier.border(
            width = AppDimension.Border.medium,
            color = AppUi.colors.accent,
            shape = AppUi.shapes.medium,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(backgroundColor)
            .then(activeBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .testTag("AllTrainingsItem_${item.uuid}")
            .padding(horizontal = AppDimension.cardPadding, vertical = AppDimension.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        TrainingRowLeading(item = item)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            TrainingRowName(item = item)
            TrainingRowTagsLine(item = item)
            TrainingRowStatusLine(item = item)
        }
        if (isSelectionMode) {
            Checkbox(
                modifier = Modifier
                    .size(AppDimension.iconMd)
                    .testTag("AllTrainingsItemCheckbox_${item.uuid}"),
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
                contentDescription = stringResource(R.string.feature_all_trainings_chevron_description),
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun TrainingRowLeading(item: TrainingListItemUi) {
    Box(
        modifier = Modifier
            .size(AppDimension.iconLg - AppDimension.Space.xxs)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⊞",
            style = AppUi.typography.titleMedium,
            color = if (item.isActive) AppUi.colors.accent else AppUi.colors.accentTintedForeground,
        )
    }
}

@Composable
private fun TrainingRowName(item: TrainingListItemUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = item.name,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.isActive) {
            AppTagChip.Static(
                modifier = Modifier.testTag("AllTrainingsItemActivePill_${item.uuid}"),
                label = stringResource(R.string.feature_all_trainings_active_pill),
            )
        }
    }
}

@Composable
private fun TrainingRowTagsLine(item: TrainingListItemUi) {
    val visibleTags = item.tags.take(MAX_INLINE_TAGS)
    val overflow = item.tags.size - visibleTags.size
    val countLabel = pluralStringResource(
        R.plurals.feature_all_trainings_exercise_count,
        item.exerciseCount,
        item.exerciseCount,
    )
    if (visibleTags.isEmpty() && overflow == 0) {
        Text(
            text = "· $countLabel",
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        visibleTags.forEach { tag -> AppTagChip.Static(label = tag) }
        if (overflow > 0) {
            AppTagChip.Static(
                label = stringResource(R.string.feature_all_trainings_overflow_format, overflow),
            )
        }
        Text(
            text = "· $countLabel",
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}

@Composable
private fun TrainingRowStatusLine(item: TrainingListItemUi) {
    val text = when {
        item.isActive && item.activeSessionStartedAt != null ->
            stringResource(
                R.string.feature_all_trainings_status_in_progress_format,
                rememberRelativeTimeLabel(item.activeSessionStartedAt),
            )
        item.lastSessionAt != null ->
            stringResource(
                R.string.feature_all_trainings_status_last_format,
                rememberRelativeTimeLabel(item.lastSessionAt),
            )
        else -> stringResource(R.string.feature_all_trainings_status_never)
    }
    Text(
        text = text,
        style = AppUi.typography.bodySmall,
        color = AppUi.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TrainingRowPreview() {
    val now = System.currentTimeMillis()
    val sample = listOf(
        TrainingListItemUi(
            uuid = "1",
            name = "Push day A",
            tags = persistentListOf("Push", "Chest", "Triceps"),
            exerciseCount = 6,
            lastSessionAt = now - 2 * 24 * 60 * 60 * 1000L,
            isActive = true,
            activeSessionStartedAt = now - 12 * 60 * 1000L,
        ),
        TrainingListItemUi(
            uuid = "2",
            name = "Pull day",
            tags = persistentListOf("Pull", "Back", "Biceps", "Forearms"),
            exerciseCount = 5,
            lastSessionAt = now - 5 * 24 * 60 * 60 * 1000L,
            isActive = false,
            activeSessionStartedAt = null,
        ),
        TrainingListItemUi(
            uuid = "3",
            name = "Legs",
            tags = persistentListOf(),
            exerciseCount = 0,
            lastSessionAt = null,
            isActive = false,
            activeSessionStartedAt = null,
        ),
    )
    AppTheme {
        LazyColumn(
            modifier = Modifier.background(AppUi.colors.surfaceTier0),
            contentPadding = PaddingValues(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            items(sample, key = { it.uuid }) { item ->
                TrainingRow(
                    item = item,
                    isSelectionMode = item.uuid == "2",
                    isSelected = item.uuid == "2",
                    onClick = {},
                    onLongPress = {},
                )
            }
        }
    }
}
