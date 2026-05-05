// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tooltip.AppTooltip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList

// Weight column gets a slightly wider weight so the kg input stays legible at small
// widths; reps stays at flex 1 so the row balances at typical phone sizes.
private const val WEIGHT_COLUMN_FLEX = 1.2f

/**
 * Pure-Composable body of the plan editor — header, set rows, and add-set button. Hosted
 * by the full-screen `PlanEditorScreen` route. All state lives upstream; field changes
 * emit [PlanEditorBodyAction] back to the parent screen which maps them to store actions.
 */
@Composable
fun PlanEditorBody(
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (PlanEditorBodyAction) -> Unit,
    setTypeTooltipText: String? = null,
) {
    if (draft.isEmpty()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimension.Space.md)
                .testTag("PlanEditorBodyEmpty"),
            text = stringResource(R.string.core_ui_kit_plan_editor_empty_hint),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        draft.forEachIndexed { index, set ->
            PlanEditorRow(
                index = index,
                item = set,
                isWeighted = isWeighted,
                setTypeTooltipText = setTypeTooltipText,
                onAction = onAction,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PlanEditorRow(
    index: Int,
    item: PlanSetUiModel,
    isWeighted: Boolean,
    setTypeTooltipText: String?,
    onAction: (PlanEditorBodyAction) -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("PlanEditorBodyRow_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            modifier = Modifier.widthIn(min = 22.dp),
            text = "${index + 1}.",
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textTertiary,
        )
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier
                    .weight(WEIGHT_COLUMN_FLEX)
                    .testTag("PlanEditorBodyRowWeight_$index"),
                value = item.weight?.formatPlain().orEmpty(),
                onValueChange = { raw ->
                    onAction(PlanEditorBodyAction.OnSetWeightChange(index, raw.toDoubleOrNull()))
                },
                decimals = 2,
                suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorBodyRowReps_$index"),
            value = item.reps.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { raw ->
                onAction(PlanEditorBodyAction.OnSetRepsChange(index, raw.toIntOrNull() ?: 0))
            },
            decimals = 0,
            suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_reps),
        )
        Box {
            val chipBox: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .clip(AppUi.shapes.small)
                        .clickable { typeMenuOpen = true }
                        .padding(
                            horizontal = AppDimension.Space.xxs,
                            vertical = AppDimension.Space.xxs,
                        )
                        .testTag("PlanEditorBodyRowType_$index"),
                ) {
                    AppSetTypeChip(type = item.type.toUiKitType())
                }
            }
            if (setTypeTooltipText != null) {
                AppTooltip(text = setTypeTooltipText) { chipBox() }
            } else {
                chipBox()
            }
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                SetTypeUiModel.entries.forEach { type ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("PlanEditorBodyTypeOption_${type.name}"),
                        text = {
                            Text(
                                text = stringResource(type.labelRes),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = {
                            onAction(PlanEditorBodyAction.OnSetTypeChange(index, type))
                            typeMenuOpen = false
                        },
                    )
                }
            }
        }
        IconButton(
            modifier = Modifier
                .size(AppDimension.heightXs)
                .testTag("PlanEditorBodyRowRemove_$index"),
            onClick = { onAction(PlanEditorBodyAction.OnSetRemove(index)) },
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.core_ui_kit_plan_editor_remove_set),
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

internal fun Double.formatPlain(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    toString()
}
