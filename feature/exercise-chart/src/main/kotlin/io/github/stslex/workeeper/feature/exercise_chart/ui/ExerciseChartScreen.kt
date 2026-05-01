// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun ExerciseChartScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("ExerciseChartScreen"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            AppTopAppBar(
                title = state.selectedExercise?.name
                    ?: stringResource(R.string.feature_exercise_chart_title),
                navigationIcon = {
                    IconButton(
                        onClick = { consume(Action.Click.OnBack) },
                        modifier = Modifier.testTag("ExerciseChartBack"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feature_exercise_chart_back),
                        )
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> AppLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.selectedExercise == null -> AppEmptyState(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(AppDimension.screenEdge)
                            .testTag("ExerciseChartNoFinishedSessions"),
                        headline = stringResource(R.string.feature_exercise_chart_no_finished_sessions_title),
                        supportingText = stringResource(R.string.feature_exercise_chart_no_finished_sessions_subtitle),
                        icon = Icons.Outlined.Inventory2,
                        actionLabel = stringResource(R.string.feature_exercise_chart_empty_cta),
                        onAction = { consume(Action.Click.OnEmptyCtaClick) },
                    )

                    state.isEmpty -> AppEmptyState(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(AppDimension.screenEdge)
                            .testTag("ExerciseChartEmpty"),
                        headline = stringResource(R.string.feature_exercise_chart_empty_title),
                        supportingText = stringResource(R.string.feature_exercise_chart_empty_subtitle),
                        icon = Icons.Outlined.Inventory2,
                        actionLabel = stringResource(R.string.feature_exercise_chart_empty_cta),
                        onAction = { consume(Action.Click.OnEmptyCtaClick) },
                    )

                    else -> Text(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(AppDimension.screenEdge),
                        text = "${state.points.size} points",
                        style = AppUi.typography.bodyLarge,
                        color = AppUi.colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExerciseChartScreenLoadingLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseChartScreen(
            state = State.create(initialUuid = null),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenLoadingDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = null),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = null).copy(
                isLoading = false,
                isEmpty = true,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenNoFinishedSessionsPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = null).copy(
                isLoading = false,
                selectedExercise = null,
                recentExercises = persistentListOf(),
            ),
            consume = {},
        )
    }
}
