// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel

@Composable
internal fun PresetChipsRow(
    selected: ChartPresetUiModel,
    onSelect: (ChartPresetUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        ChartPresetUiModel.entries.forEach { preset ->
            AppTagChip.Selectable(
                modifier = Modifier.testTag("ChartPresetChip_${preset.name}"),
                label = stringResource(preset.labelRes),
                selected = preset == selected,
                onSelectedChange = { onSelect(preset) },
            )
        }
    }
}

@Preview
@Composable
private fun PresetChipsRowLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            PresetChipsRow(
                selected = ChartPresetUiModel.MONTHS_3,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun PresetChipsRowDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            PresetChipsRow(
                selected = ChartPresetUiModel.YEAR_1,
                onSelect = {},
            )
        }
    }
}
