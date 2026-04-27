// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import kotlinx.collections.immutable.persistentListOf

@Suppress("LongMethod")
@Composable
internal fun TrainingExerciseEditRow(
    item: TrainingExerciseItem,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planSummary = item.planSets?.formatPlanSummary().orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .padding(AppDimension.cardPadding)
            .testTag("TrainingExerciseEditRow_${item.exerciseUuid}"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                text = "${item.position + 1}.",
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textTertiary,
            )
            TypeIcon(type = item.exerciseType)
            Text(
                modifier = Modifier.weight(1f),
                text = item.exerciseName,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Drag-to-reorder is offered as Up/Down arrows in v1 to avoid pulling in the
            // foundation reorderable lib; long-press anywhere on the header is documented
            // as the v2 fallback. Spec §"Reorder" explicitly allows this trade-off.
            ReorderControls(
                isFirst = isFirst,
                isLast = isLast,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
            )
            IconButton(
                modifier = Modifier
                    .size(AppDimension.heightXs)
                    .testTag("TrainingExerciseEditRowRemove_${item.exerciseUuid}"),
                onClick = onRemove,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.feature_training_edit_remove_exercise),
                    tint = AppUi.colors.textTertiary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = planSummary.ifBlank {
                    stringResource(R.string.feature_training_edit_no_plan)
                },
                style = AppUi.typography.bodySmall.copy(
                    fontStyle = if (planSummary.isBlank()) FontStyle.Italic else FontStyle.Normal,
                ),
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppButton.Tertiary(
                modifier = Modifier.testTag("TrainingExerciseEditRowEditPlan_${item.exerciseUuid}"),
                text = stringResource(
                    if (item.planSets.isNullOrEmpty()) {
                        R.string.feature_training_edit_plan_add
                    } else {
                        R.string.feature_training_edit_plan_edit
                    },
                ),
                onClick = onEditPlan,
                size = AppButtonSize.SMALL,
            )
        }
    }
}

@Composable
private fun ReorderControls(
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        IconButton(
            modifier = Modifier.size(AppDimension.heightXs),
            onClick = onMoveUp,
            enabled = !isFirst,
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.feature_training_edit_drag_handle),
                tint = if (isFirst) AppUi.colors.textDisabled else AppUi.colors.textSecondary,
            )
        }
        IconButton(
            modifier = Modifier.size(AppDimension.heightXs),
            onClick = onMoveDown,
            enabled = !isLast,
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.feature_training_edit_drag_handle),
                tint = if (isLast) AppUi.colors.textDisabled else AppUi.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TypeIcon(type: ExerciseTypeDataModel) {
    val isWeighted = type == ExerciseTypeDataModel.WEIGHTED
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier4),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconXs),
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

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TrainingExerciseEditRowPreview() {
    AppTheme {
        TrainingExerciseEditRow(
            item = TrainingExerciseItem(
                exerciseUuid = "1",
                exerciseName = "Bench press",
                exerciseType = ExerciseTypeDataModel.WEIGHTED,
                tags = persistentListOf("Push"),
                position = 1,
                planSets = null,
            ),
            isFirst = false,
            isLast = false,
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {},
            onEditPlan = {},
        )
    }
}
