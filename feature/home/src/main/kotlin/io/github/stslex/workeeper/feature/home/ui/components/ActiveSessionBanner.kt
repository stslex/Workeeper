// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
internal fun ActiveSessionBanner(
    info: HomeStore.State.ActiveSessionInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
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
                        style = AppUi.typography.titleLarge,
                        color = AppUi.colors.textPrimary,
                    )
                    Text(
                        text = "•${info.elapsedDurationLabel}",
                        style = AppUi.typography.titleLarge,
                        color = AppUi.colors.accent,
                    )
                }
                Spacer(modifier = Modifier.size(AppDimension.Space.md))
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
                elapsedDurationLabel = "12:34",
            ),
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
                elapsedDurationLabel = "1:15:00",
            ),
            onClick = {},
        )
    }
}
