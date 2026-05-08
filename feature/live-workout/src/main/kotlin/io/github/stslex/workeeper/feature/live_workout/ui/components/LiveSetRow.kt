// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordBadge
import io.github.stslex.workeeper.core.ui.kit.components.pr.personalRecordAccent
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tooltip.AppTooltip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
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
    editable: Boolean,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val rowBg = if (set.isDone) {
        AppUi.colors.surfaceTier4
    } else {
        AppUi.colors.surfaceTier1
    }
    val accentColor by animateColorAsState(
        targetValue = if (set.isPersonalRecord) {
            AppUi.colors.record.border
        } else {
            Color.Transparent
        },
        label = "pr-accent",
    )
    val rowModifier = modifier
        .fillMaxWidth()
        .height(AppDimension.heightLg)
        .background(rowBg)
        .personalRecordAccent(color = accentColor)
        .padding(horizontal = AppDimension.Space.sm)
    Row(
        modifier = rowModifier,
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
                    value = set.weightLabel,
                    onValueChange = { input -> onWeightChange(input.toDoubleOrNull()) },
                    decimals = 2,
                    suffix = "kg",
                    enabled = editable && !set.isDone,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            AppNumberInput(
                value = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { input -> onRepsChange(input.toIntOrNull()) },
                decimals = 0,
                suffix = "reps",
                enabled = editable && !set.isDone,
            )
        }
        AppTooltip(text = stringResource(R.string.feature_live_workout_set_type_tooltip)) {
            Box(
                modifier = Modifier
                    .let { base ->
                        if (testTagPrefix != null) base.testTag("${testTagPrefix}_TypeChip") else base
                    }
                    .clickable(enabled = editable) { onTypeChange(set.type) },
            ) {
                AppSetTypeChip(type = set.type.toUiKitType())
            }
        }
        if (set.isPersonalRecord && set.isDone.not()) {
            PersonalRecordBadge()
        }
        AppCheckmarkButton(
            modifier = if (testTagPrefix != null) {
                Modifier.testTag("${testTagPrefix}_Checkbox")
            } else {
                Modifier
            },
            isDone = set.isDone,
            enabled = true,
            onToggle = { if (set.isDone) onUncheck() else onMarkDone() },
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowPendingPreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = false),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowDonePreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowWeightlessPreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(0, null, 12, SetTypeUiModel.WARMUP, isDone = false),
            isWeighted = false,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}
