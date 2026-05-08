// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel

@Composable
internal fun TypeToggle(
    selected: ExerciseTypeUiModel,
    onSelect: (ExerciseTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PlanEditorTypeToggle"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        TypeOption(
            label = stringResource(ExerciseTypeUiModel.WEIGHTED.labelRes),
            isSelected = selected == ExerciseTypeUiModel.WEIGHTED,
            onClick = { onSelect(ExerciseTypeUiModel.WEIGHTED) },
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorTypeOption_WEIGHTED"),
        )
        TypeOption(
            label = stringResource(ExerciseTypeUiModel.WEIGHTLESS.labelRes),
            isSelected = selected == ExerciseTypeUiModel.WEIGHTLESS,
            onClick = { onSelect(ExerciseTypeUiModel.WEIGHTLESS) },
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorTypeOption_WEIGHTLESS"),
        )
    }
}

@Composable
private fun TypeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) AppUi.colors.accent else AppUi.colors.borderDefault
    val background = if (isSelected) {
        AppUi.colors.accentTintedBackground
    } else {
        AppUi.colors.surfaceTier1
    }
    val textColor = if (isSelected) {
        AppUi.colors.accentTintedForeground
    } else {
        AppUi.colors.textPrimary
    }
    Box(
        modifier = modifier
            .height(AppDimension.heightMd)
            .clip(AppUi.shapes.medium)
            .background(background)
            .border(
                width = AppDimension.borderHairline,
                color = borderColor,
                shape = AppUi.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimension.Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppUi.typography.labelLarge,
            color = textColor,
        )
    }
}

@Preview
@Composable
private fun TypeToggleWeightedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        TypeToggle(
            selected = ExerciseTypeUiModel.WEIGHTED,
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun TypeToggleWeightlessDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TypeToggle(
            selected = ExerciseTypeUiModel.WEIGHTLESS,
            onSelect = {},
        )
    }
}
