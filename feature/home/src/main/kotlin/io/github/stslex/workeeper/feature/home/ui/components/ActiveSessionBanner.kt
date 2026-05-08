// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.kit.theme.toDp
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore

private const val MAX_TEXT_TIME = "•999:99:99"

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

            val textMeasurer = rememberTextMeasurer()
            val titleLargeStyle = AppUi.typography.titleLarge
            val trainingNameMeasurement = remember {
                textMeasurer.measure(
                    text = MAX_TEXT_TIME,
                    style = titleLargeStyle,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = info.trainingName.ifBlank { stringResource(R.string.feature_home_active_session_label) },
                        style = titleLargeStyle,
                        color = AppUi.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(AppDimension.Space.md))
                    Text(
                        modifier = Modifier.width(trainingNameMeasurement.size.width.toDp),
                        text = "•${info.elapsedDurationLabel}",
                        style = titleLargeStyle,
                        color = AppUi.colors.accent,
                        textAlign = TextAlign.End,
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

@Preview(device = "id:pixel_9")
@Composable
private fun ActiveSessionBannerWithLongNameDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ActiveSessionBanner(
            info = HomeStore.State.ActiveSessionInfo(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Very Long Training Name That Should Be Truncated",
                startedAt = 0L,
                doneCount = 4,
                totalCount = 5,
                elapsedDurationLabel = "1:15:00",
            ),
            onClick = {},
        )
    }
}
