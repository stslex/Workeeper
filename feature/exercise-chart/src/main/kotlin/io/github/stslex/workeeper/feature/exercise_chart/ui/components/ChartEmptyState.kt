// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * Empty-state body for the chart screen. The CTA pair is optional — the
 * `NO_DATA_FOR_EXERCISE` branch hides the CTA because the recovery affordance is the
 * preset chip row that stays rendered above this state.
 */
@Composable
internal fun ChartEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    testTag: String = "ChartEmptyState",
) {
    AppEmptyState(
        modifier = modifier
            .padding(AppDimension.screenEdge)
            .testTag(testTag),
        headline = title,
        supportingText = subtitle,
        icon = Icons.Outlined.Inventory2,
        actionLabel = ctaLabel.takeIf { onCta != null },
        onAction = onCta,
    )
}

@Preview
@Composable
private fun ChartEmptyStateLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            ChartEmptyState(
                title = "No finished workouts yet",
                subtitle = "Finish a session — your progress will appear here.",
                ctaLabel = "Start a workout",
                onCta = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartEmptyStateDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            ChartEmptyState(
                title = "Exercise unavailable",
                subtitle = "It may be archived or have no logged sets. Pick another from the list.",
                ctaLabel = "Choose exercise",
                onCta = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChartEmptyStateNoCtaPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            ChartEmptyState(
                title = "No data for this period",
                subtitle = "Try a wider date range.",
            )
        }
    }
}
