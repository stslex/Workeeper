// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordBadge
import io.github.stslex.workeeper.core.ui.kit.components.pr.personalRecordAccent
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel

private val WeightFieldMinWidth = 96.dp
private val RepsFieldMinWidth = 72.dp

@Composable
internal fun PastSetEditRow(
    set: PastSetUiModel,
    isWeighted: Boolean,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    @Suppress("UnusedParameter") onTypeChange: (SetTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor by animateColorAsState(
        targetValue = if (set.isPersonalRecord) AppUi.colors.record.border else Color.Transparent,
        label = "pr-accent",
    )
    val rowModifier = modifier
        .fillMaxWidth()
        .personalRecordAccent(color = accentColor)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        AppSetTypeChip(type = set.type.toUiKitType())
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = WeightFieldMinWidth),
                value = set.weightInput,
                onValueChange = onWeightChange,
                decimals = 1,
                isError = set.weightError,
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = RepsFieldMinWidth),
            value = set.repsInput,
            onValueChange = onRepsChange,
            decimals = 0,
            isError = set.repsError,
        )
        if (set.isPersonalRecord) {
            PersonalRecordBadge()
        }
    }
}

@Preview(name = "Weighted Light")
@Composable
private fun PastSetEditRowWeightedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastSetEditRow(
            set = stubSet(),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
        )
    }
}

@Preview(name = "Weightless Dark")
@Composable
private fun PastSetEditRowWeightlessDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSetEditRow(
            set = stubSet().copy(weightInput = "", type = SetTypeUiModel.WARMUP),
            isWeighted = false,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun PastSetEditRowErrorPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSetEditRow(
            set = stubSet().copy(repsInput = "", repsError = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
        )
    }
}

private fun stubSet(): PastSetUiModel = PastSetUiModel(
    setUuid = "s-1",
    performedExerciseUuid = "pe-1",
    position = 0,
    type = SetTypeUiModel.WORK,
    weightInput = "100",
    repsInput = "5",
    weightError = false,
    repsError = false,
    isPersonalRecord = false,
)
