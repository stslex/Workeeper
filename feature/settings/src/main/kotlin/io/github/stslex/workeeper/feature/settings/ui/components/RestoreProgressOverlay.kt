// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi

@Composable
internal fun RestoreProgressOverlay(
    state: RestoreProgressUi,
    modifier: Modifier = Modifier,
) {
    if (state is RestoreProgressUi.Idle) return

    BackHandler(enabled = true) { /* Block back navigation during restore. */ }

    val scrimColor = if (AppUi.colors.isDark) {
        Color.Black.copy(alpha = SCRIM_ALPHA_DARK)
    } else {
        Color.Black.copy(alpha = SCRIM_ALPHA_LIGHT)
    }
    val cardColor = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .pointerInput(state) { /* Swallow touch events to block UI under overlay. */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(AppUi.shapes.medium)
                .background(cardColor)
                .padding(AppDimension.Space.xl),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                RestoreProgressUi.Restoring -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimension.iconLg),
                        color = AppUi.colors.accent,
                    )
                    Text(
                        text = stringResource(R.string.feature_settings_backup_restore_in_progress),
                        style = AppUi.typography.bodyMedium,
                        color = AppUi.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                RestoreProgressUi.Completed -> {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconLg),
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AppUi.colors.status.success,
                    )
                    Text(
                        text = stringResource(R.string.feature_settings_backup_restore_completed),
                        style = AppUi.typography.bodyMedium,
                        color = AppUi.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                RestoreProgressUi.Idle -> Unit
            }
        }
    }
}

private const val SCRIM_ALPHA_DARK = 0.72f
private const val SCRIM_ALPHA_LIGHT = 0.48f
