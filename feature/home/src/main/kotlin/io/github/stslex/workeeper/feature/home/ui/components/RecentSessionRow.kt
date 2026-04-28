// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

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
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem

@Composable
internal fun RecentSessionRow(
    item: RecentSessionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = item.trainingName,
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = item.statsLabel,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Text(
                // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
                text = "${item.finishedAtRelativeLabel} · ${item.durationLabel}",
                style = AppUi.typography.labelSmall,
                color = AppUi.colors.textTertiary,
            )
        }
    }
}

@Preview(name = "Light")
@Composable
private fun RecentSessionRowLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        RecentSessionRow(item = stubItem(), onClick = {})
    }
}

@Preview(name = "Dark")
@Composable
private fun RecentSessionRowDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        RecentSessionRow(item = stubItem(), onClick = {})
    }
}

private fun stubItem(): RecentSessionItem = RecentSessionItem(
    sessionUuid = "s",
    trainingName = "Push day",
    isAdhoc = false,
    finishedAtRelativeLabel = "2 days ago",
    durationLabel = "47:00",
    statsLabel = "5 exercises · 18 sets",
)
