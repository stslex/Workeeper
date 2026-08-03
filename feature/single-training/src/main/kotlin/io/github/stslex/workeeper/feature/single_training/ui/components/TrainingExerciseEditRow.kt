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
import androidx.compose.material.icons.filled.DragHandle
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
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import kotlinx.collections.immutable.persistentListOf

/**
 * One exercise inside the training editor's list.
 *
 * **Reorder is a long-press drag on the trailing handle (§26, "Reorder is long-press drag").**
 * ONE handle, not a pair of arrow buttons: **the same glyph twice is not a control**, it is two
 * identical marks whose meaning is known only to whoever placed them. The handle and the gesture
 * are the kit's `ReorderableColumn`, which past-session already ships, so this is a second
 * consumer of a built component rather than a new mechanic — and up/down remain reachable as the
 * `CustomAccessibilityAction`s `reorderableColumnItem` registers, so the gesture is not the only
 * way in.
 *
 * The handle's glyph is `Icons.Filled.DragHandle`, which is B33(b) and undecided. Deleting the
 * pair and drawing one handle leaves **two** production sites in that family (here and
 * `PastSetEditRow`; the kit's third draw is inside a `@Preview`), which is a smaller population,
 * not a settled one.
 */
@Composable
internal fun TrainingExerciseEditRow(
    item: TrainingExerciseItem,
    onRemove: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
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
            // THE GESTURE IS ON THE CONTAINER, NOT ON THE GLYPH. A bare `pointerInput` gets none
            // of `IconButton`'s minimum-touch-target expansion, so an 18dp icon carrying the
            // detector is an 18dp hit area — and since the arrows went, this is the only pointer
            // affordance for reordering. `iconXl` is what the kit already gives an interactive
            // mark: `AppIconButton` puts the drawing's 44px `.icon-btn` at 48dp around a 21dp
            // glyph. The glyph here stays `iconSm`; only the target grows.
            Box(
                modifier = dragHandleModifier
                    .size(AppDimension.iconXl)
                    .testTag("TrainingExerciseEditRowDrag_${item.exerciseUuid}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.feature_training_edit_drag_handle),
                    tint = AppUi.colors.textDim,
                )
            }
            IconButton(
                modifier = Modifier
                    .size(AppDimension.heightXs)
                    .testTag("TrainingExerciseEditRowRemove_${item.exerciseUuid}"),
                onClick = onRemove,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    // B33(a): the stroke `✕` the kit already ships. Removing an EXERCISE from a
                    // training is untouched by §26's "the per-row ✕ goes" — that rules the SET
                    // row, whose ✕ becomes «− подход» in the card's foot.
                    imageVector = AppIcons.Close,
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
                exerciseType = ExerciseTypeUiModel.WEIGHTED,
                tags = persistentListOf("Push"),
                position = 1,
                planSets = null,
                planSummary = "",
            ),
            onRemove = {},
            onEditPlan = {},
        )
    }
}
