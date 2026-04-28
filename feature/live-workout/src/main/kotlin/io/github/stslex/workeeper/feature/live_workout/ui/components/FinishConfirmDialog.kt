// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore

@Composable
internal fun FinishConfirmDialog(
    stats: LiveWorkoutStore.State.FinishStats,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.feature_live_workout_finish_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.feature_live_workout_finish_body),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_duration),
                value = stats.durationLabel,
            )
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_exercises),
                value = stats.exercisesSummaryLabel,
            )
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_sets),
                value = stats.setsLoggedLabel,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
            ) {
                AppButton.Tertiary(
                    text = stringResource(R.string.feature_live_workout_finish_keep_going),
                    onClick = onDismiss,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Primary(
                    text = stringResource(R.string.feature_live_workout_finish_confirm),
                    onClick = onConfirm,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = value,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
    }
}

@Preview
@Composable
private fun FinishConfirmDialogAllDoneLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        FinishConfirmDialog(
            stats = LiveWorkoutStore.State.FinishStats(
                durationMillis = 47 * 60_000L + 8_000L,
                durationLabel = "47:08",
                exercisesSummaryLabel = "5 of 5 done",
                setsLoggedLabel = "22",
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun FinishConfirmDialogPartialDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        FinishConfirmDialog(
            stats = LiveWorkoutStore.State.FinishStats(
                durationMillis = 32 * 60_000L,
                durationLabel = "32:00",
                exercisesSummaryLabel = "3 of 5 done · 1 skipped",
                setsLoggedLabel = "12",
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
