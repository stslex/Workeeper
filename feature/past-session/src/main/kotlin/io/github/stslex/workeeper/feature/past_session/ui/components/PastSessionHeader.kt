// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun PastSessionHeader(
    detail: PastSessionUiModel,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            Text(
                text = detail.finishedAtAbsoluteLabel,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Text(
                text = detail.durationLabel,
                style = AppUi.typography.headlineSmall,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = detail.totalsLabel,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            detail.volumeLabel?.let { volume ->
                Text(
                    text = volume,
                    style = AppUi.typography.bodyMedium,
                    color = AppUi.colors.textSecondary,
                )
            }
        }
    }
}

@Preview(name = "Light")
@Composable
private fun PastSessionHeaderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastSessionHeader(detail = stubDetail())
    }
}

@Preview(name = "Dark")
@Composable
private fun PastSessionHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSessionHeader(detail = stubDetail())
    }
}

private fun stubDetail(): PastSessionUiModel = PastSessionUiModel(
    trainingName = "Push day",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "Mon, Apr 27, 19:42",
    durationLabel = "47 min",
    totalsLabel = "5 exercises · 18 sets",
    volumeLabel = "1,250 kg total",
    exercises = persistentListOf(),
)
