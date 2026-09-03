// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setbar.AppSetBar
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tooltip.AppTooltip
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_empty_hint
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_kg
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_setbar_add
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_setbar_remove
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.resources.core_ui_plan_editor_read_plan_empty
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import io.github.stslex.workeeper.core.ui.plan_editor.resources.Res as PlanEditorRes

// Wider than the reps column so the kg input stays legible at small widths.
private const val WEIGHT_COLUMN_FLEX = 1.2f

/**
 * The plan's set list as a card, shared by the plan editor and the exercise read screen.
 * `onAction == null` is the read-only host: no caret, no picker, no `.setbar` (ED2, D-OPEN-6).
 */
@Composable
fun PlanSetCard(
    plan: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    modifier: Modifier = Modifier,
    setTypeTooltipText: String? = null,
    scrollable: Boolean = false,
    onAction: ((PlanEditorBodyAction) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.medium))
            .background(AppUi.colors.surfaceTier1),
    ) {
        val rowsModifier = if (scrollable) {
            Modifier
                .fillMaxWidth()
                .heightIn(max = SCROLLABLE_ROWS_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }
        if (plan.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimension.cardPadding)
                    .testTag("PlanEditorBodyEmpty"),
                text = if (onAction == null) {
                    stringResource(PlanEditorRes.string.core_ui_plan_editor_read_plan_empty)
                } else {
                    stringResource(Res.string.core_ui_kit_plan_editor_empty_hint)
                },
                style = AppUi.typography.text.body,
                color = AppUi.colors.textSecondary,
            )
        } else {
            Column(
                modifier = rowsModifier.padding(AppDimension.cardPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                plan.forEachIndexed { index, set ->
                    PlanSetRow(
                        index = index,
                        item = set,
                        isWeighted = isWeighted,
                        setTypeTooltipText = setTypeTooltipText,
                        onAction = onAction,
                    )
                }
            }
        }
        if (onAction != null) {
            AppSetBar(
                addLabel = stringResource(Res.string.core_ui_kit_setbar_add),
                removeLabel = stringResource(Res.string.core_ui_kit_setbar_remove),
                onAdd = { onAction(PlanEditorBodyAction.OnAddSet) },
                // The foot removes the last set; the shared reducer still addresses a row.
                onRemove = { onAction(PlanEditorBodyAction.OnSetRemove(plan.lastIndex)) },
                removeEnabled = plan.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun PlanSetRow(
    index: Int,
    item: PlanSetUiModel,
    isWeighted: Boolean,
    setTypeTooltipText: String?,
    onAction: ((PlanEditorBodyAction) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("PlanEditorBodyRow_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            modifier = Modifier.widthIn(min = ORDINAL_MIN_WIDTH),
            text = "${index + 1}.",
            style = AppUi.typography.text.body,
            color = AppUi.colors.textTertiary,
        )
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier
                    .weight(WEIGHT_COLUMN_FLEX)
                    .testTag("PlanEditorBodyRowWeight_$index"),
                value = item.weight?.formatPlain().orEmpty(),
                onValueChange = { raw ->
                    onAction?.invoke(PlanEditorBodyAction.OnSetWeightChange(index, raw.toDoubleOrNull()))
                },
                decimals = 2,
                suffix = stringResource(Res.string.core_ui_kit_plan_editor_unit_kg),
                enabled = onAction != null,
                isLogged = true,
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorBodyRowReps_$index"),
            value = item.reps.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { raw ->
                onAction?.invoke(PlanEditorBodyAction.OnSetRepsChange(index, raw.toIntOrNull() ?: 0))
            },
            decimals = 0,
            suffix = stringResource(Res.string.core_ui_kit_plan_editor_unit_reps),
            enabled = onAction != null,
            isLogged = true,
        )
        SetTypeSlot(
            index = index,
            type = item.type,
            setTypeTooltipText = setTypeTooltipText,
            onAction = onAction,
        )
    }
}

/**
 * The row's trailing `.tchip`, and the picker behind it when there is one.
 * GUARD: the padding box is drawn in read-only mode too — dropping it shifts the column by 2dp.
 */
@Composable
private fun SetTypeSlot(
    index: Int,
    type: SetTypeUiModel,
    setTypeTooltipText: String?,
    onAction: ((PlanEditorBodyAction) -> Unit)?,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    Box {
        val chipBox: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .clip(AppUi.shapes.small)
                    .then(
                        if (onAction != null) {
                            Modifier.clickable { typeMenuOpen = true }
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        horizontal = AppDimension.Space.xxs,
                        vertical = AppDimension.Space.xxs,
                    )
                    .testTag("PlanEditorBodyRowType_$index"),
            ) {
                AppSetTypeChip(type = type.toUiKitType())
            }
        }
        if (setTypeTooltipText != null && onAction != null) {
            AppTooltip(text = setTypeTooltipText) { chipBox() }
        } else {
            chipBox()
        }
        if (onAction != null) {
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                SetTypeUiModel.entries.forEach { entry ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("PlanEditorBodyTypeOption_${entry.name}"),
                        text = {
                            Text(
                                text = stringResource(entry.labelRes),
                                style = AppUi.typography.text.body,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = {
                            onAction(PlanEditorBodyAction.OnSetTypeChange(index, entry))
                            typeMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/** The row's `.set-i` column — wide enough for a two-digit ordinal plus its full stop. */
private val ORDINAL_MIN_WIDTH = 22.dp

/** The scrollable host's cap: the route embeds the body above its own action bar. */
private val SCROLLABLE_ROWS_MAX_HEIGHT = 360.dp

internal fun Double.formatPlain(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    toString()
}
