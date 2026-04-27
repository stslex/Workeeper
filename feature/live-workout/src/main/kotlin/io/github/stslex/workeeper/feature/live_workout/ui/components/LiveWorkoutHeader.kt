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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.live_workout.R

@Composable
internal fun LiveWorkoutHeader(
    trainingName: String,
    elapsedMillis: Long,
    doneCount: Int,
    totalCount: Int,
    setsLogged: Int,
    modifier: Modifier = Modifier,
) {
    val safeTotal = totalCount.coerceAtLeast(1)
    val progress = doneCount.toFloat() / safeTotal.toFloat()
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
                text = trainingName.ifBlank { stringResource(R.string.feature_live_workout_status_no_plan) },
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = "•" + formatElapsed(elapsedMillis),
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.accent,
            )
        }
        Text(
            text = stringResource(
                R.string.feature_live_workout_progress_format,
                doneCount,
                totalCount,
                pluralStringResource(
                    id = R.plurals.feature_live_workout_set_count,
                    count = setsLogged,
                    setsLogged,
                ),
            ),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
        )
        Spacer(Modifier.height(AppDimension.Space.xs))
        Box(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimension.Space.xs),
                progress = { progress.coerceIn(0f, 1f) },
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
            trainingName = "Push Day",
            elapsedMillis = 23 * 60_000L + 14_000L,
            doneCount = 2,
            totalCount = 5,
            setsLogged = 16,
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutHeader(
            trainingName = "Push Day",
            elapsedMillis = 47 * 60_000L + 8_000L,
            doneCount = 4,
            totalCount = 5,
            setsLogged = 22,
        )
    }
}
