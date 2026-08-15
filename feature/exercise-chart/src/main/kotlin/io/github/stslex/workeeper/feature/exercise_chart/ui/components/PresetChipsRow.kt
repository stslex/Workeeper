// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTag
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel

/**
 * The mockup's `.ranges` (extraction §4.4): four `.tag` chips at §3.2 geometry, gap 8dp. On
 * this screen the chips ARE interactive and the selected one wears `.tag.on` — the variant
 * `AppTag`'s KDoc commissions for exactly this row. The edit-form `AppTagChip` this row used
 * to borrow is the caption-rung sibling, a different component.
 */
@Composable
internal fun PresetChipsRow(
    selected: ChartPresetUiModel,
    onSelect: (ChartPresetUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        ChartPresetUiModel.entries.forEach { preset ->
            AppTag(
                modifier = Modifier.testTag("ChartPresetChip_${preset.name}"),
                label = stringResource(preset.labelRes),
                selected = preset == selected,
                onClick = { onSelect(preset) },
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
