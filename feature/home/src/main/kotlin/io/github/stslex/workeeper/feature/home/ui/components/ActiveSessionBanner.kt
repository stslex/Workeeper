// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val SECONDS_IN_MINUTE = 60L

@Composable
internal fun ActiveSessionBanner(
    info: HomeStore.State.ActiveSessionInfo,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconLg),
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = AppUi.colors.accent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = info.trainingName.ifBlank { stringResource(R.string.feature_home_active_session_label) },
                        style = AppUi.typography.titleMedium,
                        color = AppUi.colors.textPrimary,
                    )
                    Text(
                        text = "•" + formatElapsed(info.elapsedMillis(nowMillis)),
                        style = AppUi.typography.titleMedium,
                        color = AppUi.colors.accent,
                    )
                }
                Text(
                    text = stringResource(R.string.feature_home_active_session_label) + " · " +
                        stringResource(
                            R.string.feature_home_active_session_progress_format,
                            info.doneCount,
                            info.totalCount,
                        ),
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textSecondary,
                )
            }
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val total = millis.coerceAtLeast(0L).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % SECONDS_IN_MINUTE
    val seconds = total.inWholeSeconds % SECONDS_IN_MINUTE
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Preview
@Composable
private fun ActiveSessionBannerLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ActiveSessionBanner(
            info = HomeStore.State.ActiveSessionInfo(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Push Day",
                startedAt = 0L,
                doneCount = 2,
                totalCount = 5,
            ),
            nowMillis = 12 * 60_000L + 34_000L,
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun ActiveSessionBannerDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ActiveSessionBanner(
            info = HomeStore.State.ActiveSessionInfo(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Pull Day",
                startedAt = 0L,
                doneCount = 4,
                totalCount = 5,
            ),
            nowMillis = 75 * 60_000L,
            onClick = {},
        )
    }
}
