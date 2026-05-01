// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel

@Composable
internal fun ChartTooltipOverlay(
    tooltip: ChartTooltipUiModel,
    onTooltipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .clip(AppUi.shapes.medium)
                .background(AppUi.colors.surfaceTier3)
                .clickable(onClick = onTooltipClick)
                .padding(AppDimension.cardPadding)
                .testTag("ChartTooltip"),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = tooltip.exerciseName,
                style = AppUi.typography.titleSmall,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = tooltip.dateLabel,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textSecondary,
            )
            Text(
                text = tooltip.displayLabel,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.accent,
            )
            tooltip.setCountLabel?.let { label ->
                Text(
                    text = label,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ChartTooltipOverlayLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartTooltipOverlay(
                tooltip = ChartTooltipUiModel(
                    sessionUuid = "session-1",
                    exerciseName = "Bench press",
                    dateLabel = "Apr 26, 2026",
                    displayLabel = "105 kg × 3",
                    setCountLabel = "2 sets this day",
                ),
                onTooltipClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartTooltipOverlayDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            ChartTooltipOverlay(
                tooltip = ChartTooltipUiModel(
                    sessionUuid = "session-1",
                    exerciseName = "Pull-ups",
                    dateLabel = "26 апр. 2026",
                    displayLabel = "12 повторений",
                    setCountLabel = null,
                ),
                onTooltipClick = {},
            )
        }
    }
}
