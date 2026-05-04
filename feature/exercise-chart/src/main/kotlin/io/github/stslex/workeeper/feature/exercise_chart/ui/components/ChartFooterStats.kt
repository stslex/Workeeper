// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
 * Footer stats: three equal-weight columns with center-aligned text. The pre-v2.4
 * `Arrangement.SpaceBetween` row glued long Russian labels together
 * ("Последнее: 105 кг" overflowing into the adjacent stat); equal-weight + center +
 * `maxLines = 2 ellipsis` keeps each stat visually inside its third regardless of
 * locale length. (v2.4 5.6.)
 */
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
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        StatColumn(label = stats.minLabel)
        StatColumn(label = stats.maxLabel)
        StatColumn(label = stats.lastLabel)
    }
}

@Composable
private fun RowScope.StatColumn(label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
