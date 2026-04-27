// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import kotlinx.collections.immutable.persistentListOf

private const val MAX_INLINE_TAGS = 3

@Composable
internal fun TrainingExerciseRow(
    item: TrainingExerciseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .clickable(onClick = onClick)
            .testTag("TrainingExerciseRow_${item.exerciseUuid}")
            .padding(horizontal = AppDimension.cardPadding, vertical = AppDimension.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        TypeIcon(type = item.exerciseType)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = "${item.position + 1}. ${item.exerciseName}",
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.tags.isNotEmpty()) {
                ExerciseRowTags(tags = item.tags)
            }
            Text(
                text = item.planSummary.ifBlank {
                    stringResource(R.string.feature_training_edit_no_plan)
                },
                style = AppUi.typography.bodySmall.copy(
                    fontStyle = if (item.planSummary.isBlank()) FontStyle.Italic else FontStyle.Normal,
                ),
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.feature_training_detail_chevron),
            tint = AppUi.colors.textTertiary,
        )
    }
}

@Composable
internal fun TypeIcon(type: ExerciseTypeUiModel) {
    val isWeighted = type == ExerciseTypeUiModel.WEIGHTED
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier4),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = if (isWeighted) Icons.Filled.FitnessCenter else Icons.Filled.AccessibilityNew,
            contentDescription = null,
            tint = if (isWeighted) {
                AppUi.colors.accentTintedForeground
            } else {
                AppUi.colors.setType.warmupForeground
            },
        )
    }
}

@Composable
private fun ExerciseRowTags(tags: kotlinx.collections.immutable.ImmutableList<String>) {
    val visible = tags.take(MAX_INLINE_TAGS)
    val overflow = tags.size - visible.size
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        visible.forEach { tag -> AppTagChip.Static(label = tag) }
        if (overflow > 0) {
            AppTagChip.Static(label = "+$overflow")
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
private fun TrainingExerciseRowPreview() {
    AppTheme {
        TrainingExerciseRow(
            item = TrainingExerciseItem(
                exerciseUuid = "1",
                exerciseName = "Bench press",
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                tags = persistentListOf("Push", "Chest"),
                position = 0,
                planSets = null,
                planSummary = "100×5 · 100×5 · 102.5×5",
            ),
            onClick = {},
        )
    }
}
