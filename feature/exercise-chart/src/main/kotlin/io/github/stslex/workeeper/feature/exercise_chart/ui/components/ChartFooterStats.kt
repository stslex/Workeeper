// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel

@Composable
internal fun ChartFooterStats(
    stats: ChartFooterStatsUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .testTag("ChartFooterStats"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatLabel(stats.minLabel)
        StatLabel(stats.maxLabel)
        StatLabel(stats.lastLabel)
    }
}

@Composable
private fun StatLabel(text: String) {
    Text(
        text = text,
        style = AppUi.typography.bodySmall,
        color = AppUi.colors.textSecondary,
    )
}

@Preview
@Composable
private fun ChartFooterStatsLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minLabel = "Min: 80 kg",
                    maxLabel = "Max: 110 kg",
                    lastLabel = "Last: 105 kg",
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ChartFooterStatsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minLabel = "Мин: 80 кг",
                    maxLabel = "Макс: 110 кг",
                    lastLabel = "Последнее: 105 кг",
                ),
            )
        }
    }
}
