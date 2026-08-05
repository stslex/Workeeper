// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One exercise of the training editor's list, as a card — **collapsed by default** (ED14,
 * `v3-editors.md` §3.4).
 *
 * Collapsed is the drawn `#s-past` form — ordinal, type glyph, name, `.plan-line` summary —
 * plus the two controls the editor adds: the drag handle and the `✕`. Expanded adds
 * [PlanEditorBody]'s rows and `.setbar` under the same head; an open card takes the lifted
 * surface, the same `.card.open` signature the past session draws.
 *
 * **`onTypeChange = null` IS the rule**: type belongs to the exercise, not to a
 * training-scoped editor, and the exclusion is carried by the argument — the body draws no
 * toggle for a host that supplies no handler, with no `when` on a mode anywhere.
 *
 * ## The head's three targets, and why none swallows another
 *
 * Expansion is the head's tap ([onToggle], on the head Row's `clickable`), but the handle and
 * the `✕` are its children, and children see pointer events first:
 *
 *  - the `✕` is an [IconButton] — its own clickable node consumes the tap outright, so the
 *    head's `clickable` never fires under it;
 *  - the handle's box carries only the long-press drag detector ([dragHandleModifier]); a
 *    long-press starts the drag and cancels the head's tap, while a plain tap deliberately
 *    falls through to expansion — the handle's own gesture is the long-press, and it keeps it.
 *
 * Both are 48dp boxes ([AppDimension.iconXl]), so neither target shrinks to its glyph.
 */
@Composable
internal fun TrainingExerciseCard(
    item: TrainingExerciseItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onPlanAction: (PlanEditorBodyAction) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liftedSurface(shape = AppUi.shapes.medium, lifted = expanded)
            .clip(AppUi.shapes.medium)
            .testTag("TrainingExerciseCard_${item.exerciseUuid}"),
    ) {
        CardHead(
            item = item,
            onToggle = onToggle,
            onRemove = onRemove,
            dragHandleModifier = dragHandleModifier,
        )
        AnimatedVisibility(visible = expanded) {
            PlanEditorBody(
                modifier = Modifier.padding(
                    start = AppDimension.cardPadding,
                    end = AppDimension.cardPadding,
                    bottom = AppDimension.cardPadding,
                ),
                draft = item.planSets ?: persistentListOf(),
                isWeighted = item.exerciseType == ExerciseTypeUiModel.WEIGHTED,
                onAction = onPlanAction,
                scrollable = false,
                onTypeChange = null,
            )
        }
    }
}

@Composable
private fun CardHead(
    item: TrainingExerciseItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(AppDimension.cardPadding)
            .testTag("TrainingExerciseCardHead_${item.exerciseUuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            AppDimension.Space.sm,
        ),
    ) {
        Text(
            text = "${item.position + 1}.",
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textTertiary,
        )
        TypeIcon(type = item.exerciseType)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.exerciseName,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // `.plan-line` — the collapsed card's one-line summary (`#s-past`'s own form).
            Text(
                modifier = Modifier.testTag("TrainingExerciseCardPlanLine_${item.exerciseUuid}"),
                text = item.planSummary.ifBlank {
                    stringResource(R.string.feature_training_edit_no_plan)
                },
                style = AppUi.typography.mono.meta.copy(
                    fontStyle = if (item.planSummary.isBlank()) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                ),
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // THE GESTURE IS ON THE CONTAINER, NOT ON THE GLYPH — a bare `pointerInput` gets none
        // of `IconButton`'s minimum-touch-target expansion, so the box is the 48dp target and
        // the glyph stays small.
        Box(
            modifier = dragHandleModifier
                .size(AppDimension.iconXl)
                .testTag("TrainingExerciseCardDrag_${item.exerciseUuid}"),
            contentAlignment = Alignment.Center,
        ) {
            // `Icons.Filled.DragHandle`, exactly as the row it replaces drew it — B33(b) is
            // open and a new stroke glyph here would settle it by accident.
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.feature_training_edit_drag_handle),
                tint = AppUi.colors.textDim,
            )
        }
        IconButton(
            modifier = Modifier
                .size(AppDimension.iconXl)
                .testTag("TrainingExerciseCardRemove_${item.exerciseUuid}"),
            onClick = onRemove,
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                // Removes from THIS training only, immediately and unconfirmed (D-OPEN-11):
                // the draft is unsaved and Cancel stands behind it. Undo is S7's, in PR-C.
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.feature_training_edit_remove_exercise),
                tint = AppUi.colors.textTertiary,
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
private fun TrainingExerciseCardPreview() {
    AppTheme {
        Column {
            TrainingExerciseCard(
                item = TrainingExerciseItem(
                    exerciseUuid = "1",
                    exerciseName = "Bench press",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf("Push"),
                    position = 0,
                    planSets = listOf(
                        PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                    ).toImmutableList(),
                    planSummary = "60×10",
                ),
                expanded = true,
                onToggle = {},
                onRemove = {},
                onPlanAction = {},
            )
        }
    }
}
