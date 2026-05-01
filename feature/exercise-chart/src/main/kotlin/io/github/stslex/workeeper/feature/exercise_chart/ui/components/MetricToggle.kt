// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun MetricToggle(
    selected: ChartMetricUiModel,
    onSelect: (ChartMetricUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = persistentListOf(
        stringResource(ChartMetricUiModel.HEAVIEST_WEIGHT.labelRes),
        stringResource(ChartMetricUiModel.VOLUME_PER_SET.labelRes),
    )
    val selectedIndex = ChartMetricUiModel.entries.indexOf(selected)
    AppSegmentedControl(
        modifier = modifier.testTag("ChartMetricToggle"),
        items = items,
        selected = selectedIndex,
        onSelectedChange = { index -> onSelect(ChartMetricUiModel.entries[index]) },
    )
}

@Preview
@Composable
private fun MetricToggleLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            MetricToggle(
                selected = ChartMetricUiModel.HEAVIEST_WEIGHT,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun MetricToggleDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            MetricToggle(
                selected = ChartMetricUiModel.VOLUME_PER_SET,
                onSelect = {},
            )
        }
    }
}
