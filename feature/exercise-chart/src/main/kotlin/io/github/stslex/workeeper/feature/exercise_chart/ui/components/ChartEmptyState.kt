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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.R

@Composable
internal fun ChartEmptyState(
    headlineRes: Int,
    subtitleRes: Int,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "ChartEmptyState",
) {
    AppEmptyState(
        modifier = modifier
            .padding(AppDimension.screenEdge)
            .testTag(testTag),
        headline = stringResource(headlineRes),
        supportingText = stringResource(subtitleRes),
        icon = Icons.Outlined.Inventory2,
        actionLabel = stringResource(R.string.feature_exercise_chart_empty_cta),
        onAction = onCtaClick,
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
                headlineRes = R.string.feature_exercise_chart_empty_title,
                subtitleRes = R.string.feature_exercise_chart_empty_subtitle,
                onCtaClick = {},
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
                headlineRes = R.string.feature_exercise_chart_no_finished_sessions_title,
                subtitleRes = R.string.feature_exercise_chart_no_finished_sessions_subtitle,
                onCtaClick = {},
            )
        }
    }
}
