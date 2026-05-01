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
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartCanvas
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartEmptyState
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartFooterStats
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartTooltipOverlay
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ExercisePickerSheet
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricToggle
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.PresetChipsRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
            ChartTopBar(state = state, consume = consume)

            Box(modifier = Modifier.weight(1f)) {
                ChartContent(state = state, consume = consume)
            }
        }

        if (state.isPickerOpen) {
            ExercisePickerSheet(
                items = state.recentExercises,
                selectedUuid = state.selectedExercise?.uuid,
                onDismiss = { consume(Action.Click.OnPickerDismiss) },
                onItemSelect = { consume(Action.Click.OnPickerItemSelect(it)) },
            )
        }
    }
}

@Composable
private fun ChartTopBar(
    state: State,
    consume: (Action) -> Unit,
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
        actions = {
            if (state.recentExercises.size > 1) {
                IconButton(
                    onClick = { consume(Action.Click.OnPickerOpen) },
                    modifier = Modifier.testTag("ExerciseChartPickerOpen"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = stringResource(R.string.feature_exercise_chart_picker_open),
                    )
                }
            }
        },
    )
}

@Composable
private fun ChartContent(
    state: State,
    consume: (Action) -> Unit,
) {
    when {
        state.isLoading && state.points.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AppLoadingIndicator()
        }

        state.selectedExercise == null -> ChartEmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AppDimension.Space.xl),
            headlineRes = R.string.feature_exercise_chart_no_finished_sessions_title,
            subtitleRes = R.string.feature_exercise_chart_no_finished_sessions_subtitle,
            onCtaClick = { consume(Action.Click.OnEmptyCtaClick) },
            testTag = "ExerciseChartNoFinishedSessions",
        )

        state.isEmpty -> Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            ChartControls(state = state, consume = consume)
            ChartEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppDimension.Space.xl),
                headlineRes = R.string.feature_exercise_chart_empty_title,
                subtitleRes = R.string.feature_exercise_chart_empty_subtitle,
                onCtaClick = { consume(Action.Click.OnEmptyCtaClick) },
                testTag = "ExerciseChartEmpty",
            )
        }

        else -> ChartPopulated(state = state, consume = consume)
    }
}

@Composable
private fun ChartPopulated(
    state: State,
    consume: (Action) -> Unit,
) {
    val (windowStartDay, windowEndDay) = state.preset.toWindow(state.points)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        ChartControls(state = state, consume = consume)
        ChartCanvas(
            points = state.points,
            windowStartDay = windowStartDay,
            windowEndDay = windowEndDay,
            onPointTap = { consume(Action.Click.OnPointTap(it)) },
            onCanvasTap = { consume(Action.Click.OnTooltipDismiss) },
        )
        state.activeTooltip?.let { tooltip ->
            ChartTooltipOverlay(
                tooltip = tooltip,
                onTooltipClick = { consume(Action.Click.OnTooltipTap) },
            )
        }
        state.footerStats?.let { stats ->
            ChartFooterStats(stats = stats)
        }
    }
}

@Composable
private fun ChartControls(
    state: State,
    consume: (Action) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        PresetChipsRow(
            selected = state.preset,
            onSelect = { consume(Action.Click.OnPresetSelect(it)) },
        )
        if (state.showMetricToggle) {
            Box(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
                MetricToggle(
                    selected = state.metric,
                    onSelect = { consume(Action.Click.OnMetricSelect(it)) },
                )
            }
        }
    }
}

private fun ChartPresetUiModel.toWindow(
    points: ImmutableList<ChartPointUiModel>,
): Pair<LocalDate, LocalDate> {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val windowEnd = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val windowStartMillis = windowStartMillis(now)
    val rawStart = windowStartMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    } ?: points.minOfOrNull { it.day } ?: windowEnd
    return rawStart to windowEnd
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

@Suppress("MagicNumber")
@Preview
@Composable
private fun ExerciseChartScreenPopulatedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = "uuid-1").copy(
                isLoading = false,
                selectedExercise = ExercisePickerItemUiModel(
                    "uuid-1",
                    "Bench press",
                    ExerciseTypeUiModel.WEIGHTED,
                ),
                recentExercises = persistentListOf(
                    ExercisePickerItemUiModel("uuid-1", "Bench press", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("uuid-2", "Squat", ExerciseTypeUiModel.WEIGHTED),
                ),
                points = listOf(
                    ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, 80.0, "s1", 80.0, 5, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, 90.0, "s2", 90.0, 5, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, 95.0, "s3", 95.0, 5, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, 105.0, "s4", 105.0, 3, 2),
                ).toImmutableList(),
                footerStats = ChartFooterStatsUiModel(
                    minLabel = "Min: 80 kg",
                    maxLabel = "Max: 105 kg",
                    lastLabel = "Last: 105 kg",
                ),
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = "uuid-1").copy(
                isLoading = false,
                isEmpty = true,
                selectedExercise = ExercisePickerItemUiModel(
                    "uuid-1",
                    "Bench press",
                    ExerciseTypeUiModel.WEIGHTED,
                ),
                recentExercises = persistentListOf(
                    ExercisePickerItemUiModel("uuid-1", "Bench press", ExerciseTypeUiModel.WEIGHTED),
                ),
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
