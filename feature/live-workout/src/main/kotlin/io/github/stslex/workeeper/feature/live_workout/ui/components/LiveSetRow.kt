// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel

private const val WEIGHT_COLUMN_FLEX = 1.2f

@Suppress("LongParameterList")
@Composable
internal fun LiveSetRow(
    set: LiveSetUiModel,
    isWeighted: Boolean,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onTypeChange: (SetTypeUiModel) -> Unit,
    onMarkDone: () -> Unit,
    onUncheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBg = if (set.isDone) AppUi.colors.surfaceTier2 else AppUi.colors.surfaceTier1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimension.heightLg)
            .background(rowBg)
            .padding(horizontal = AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            text = (set.position + 1).toString(),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
        if (isWeighted) {
            Box(modifier = Modifier.weight(WEIGHT_COLUMN_FLEX)) {
                AppNumberInput(
                    value = set.weight?.let { formatWeight(it) }.orEmpty(),
                    onValueChange = { input -> onWeightChange(input.toDoubleOrNull()) },
                    decimals = 2,
                    suffix = "kg",
                    enabled = !set.isDone,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            AppNumberInput(
                value = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { input -> onRepsChange(input.toIntOrNull()) },
                decimals = 0,
                suffix = "reps",
                enabled = !set.isDone,
            )
        }
        Box(
            modifier = Modifier.clickable { onTypeChange(set.type.next()) },
        ) {
            AppSetTypeChip(type = set.type.toUiKitType())
        }
        IconButton(
            onClick = { if (set.isDone) onUncheck() else onMarkDone() },
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconMd),
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (set.isDone) AppUi.colors.accent else AppUi.colors.textTertiary,
            )
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun SetTypeUiModel.next(): SetTypeUiModel {
    val all = SetTypeUiModel.entries
    val nextIndex = (ordinal + 1) % all.size
    return all[nextIndex]
}

@Preview
@Composable
private fun LiveSetRowPendingLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveSetRow(
            set = LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = false),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
        )
    }
}

@Preview
@Composable
private fun LiveSetRowDonePreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveSetRow(
            set = LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
        )
    }
}

@Preview
@Composable
private fun LiveSetRowWeightlessPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveSetRow(
            set = LiveSetUiModel(0, null, 12, SetTypeUiModel.WARMUP, isDone = false),
            isWeighted = false,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
        )
    }
}
