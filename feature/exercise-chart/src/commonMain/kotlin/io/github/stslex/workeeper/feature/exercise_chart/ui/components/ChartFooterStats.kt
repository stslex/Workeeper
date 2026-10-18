// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel

/**
 * The three `.statrow`s (extraction §4.7): rules run full-bleed, content sits inside the gutter,
 * `.meta` on the left and `.val` plus its dimmer `.unit` on the right.
 */
@Composable
internal fun ChartFooterStats(
    stats: ChartFooterStatsUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ChartFooterStats"),
    ) {
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        StatRow(title = stats.minTitle, value = stats.minValue, unit = stats.unit)
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        StatRow(title = stats.maxTitle, value = stats.maxValue, unit = stats.unit)
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        StatRow(title = stats.lastTitle, value = stats.lastValue, unit = stats.unit)
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
    }
}

@Composable
private fun StatRow(
    title: String,
    value: String,
    unit: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = value,
                style = AppUi.typography.mono.body.copy(fontWeight = FontWeight.Medium),
                color = AppUi.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            unit?.let {
                Text(
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = AppDimension.Space.xs),
                    text = it,
                    style = AppUi.typography.mono.caption,
                    color = AppUi.colors.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ChartFooterStatsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minTitle = "Минимум",
                    minValue = "2 940",
                    maxTitle = "Максимум",
                    maxValue = "4 620",
                    lastTitle = "Последний",
                    lastValue = "4 620",
                    unit = "кг",
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ChartFooterStatsWeightlessLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ChartFooterStats(
                stats = ChartFooterStatsUiModel(
                    minTitle = "Minimum",
                    minValue = "8 reps",
                    maxTitle = "Maximum",
                    maxValue = "12 reps",
                    lastTitle = "Last",
                    lastValue = "12 reps",
                    unit = null,
                ),
            )
        }
    }
}
