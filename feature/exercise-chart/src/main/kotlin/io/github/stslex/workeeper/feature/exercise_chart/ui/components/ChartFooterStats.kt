// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel

/**
 * Footer stats: vertical stack, one stat per row. Title sits left, value right,
 * baseline-aligned. Single-line, no ellipsis — values are short formatted numbers
 * (e.g. "250 кг × повт."). The pre-v2.4 three-column row glued long Russian
 * labels together; the vertical layout sidesteps width competition entirely. (5.6 / D.)
 */
@Composable
internal fun ChartFooterStats(
    stats: ChartFooterStatsUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .testTag("ChartFooterStats"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        StatRow(title = stats.minTitle, value = stats.minValue)
        StatRow(title = stats.maxTitle, value = stats.maxValue)
        StatRow(title = stats.lastTitle, value = stats.lastValue)
    }
}

@Composable
private fun StatRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = value,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Preview
@Composable
private fun ChartFooterStatsLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minTitle = "Min",
                    minValue = "80 kg",
                    maxTitle = "Max",
                    maxValue = "110 kg",
                    lastTitle = "Last",
                    lastValue = "105 kg",
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ChartFooterStatsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minTitle = "Мин",
                    minValue = "250 кг × повт.",
                    maxTitle = "Макс",
                    maxValue = "260 кг × повт.",
                    lastTitle = "Последнее",
                    lastValue = "255 кг × повт.",
                ),
            )
        }
    }
}
