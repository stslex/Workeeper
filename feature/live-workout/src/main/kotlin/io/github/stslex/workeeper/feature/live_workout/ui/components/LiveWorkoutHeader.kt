// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

@Composable
internal fun LiveWorkoutHeader(
    trainingNameLabel: String,
    elapsedLabel: String,
    progressLabel: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .padding(AppDimension.Space.lg),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = trainingNameLabel,
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = "•$elapsedLabel",
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.accent,
            )
        }
        Text(
            text = progressLabel,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
        )
        Spacer(Modifier.height(AppDimension.Space.xs))
        Box(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimension.Space.xs),
                progress = { progress },
                color = AppUi.colors.accent,
                trackColor = AppUi.colors.surfaceTier3,
            )
        }
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveWorkoutHeader(
            trainingNameLabel = "Push Day",
            elapsedLabel = "23:14",
            progressLabel = "2 of 5 done · 16 sets logged",
            progress = 0.4f,
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutHeader(
            trainingNameLabel = "Push Day",
            elapsedLabel = "47:08",
            progressLabel = "4 of 5 done · 22 sets logged",
            progress = 0.8f,
        )
    }
}
