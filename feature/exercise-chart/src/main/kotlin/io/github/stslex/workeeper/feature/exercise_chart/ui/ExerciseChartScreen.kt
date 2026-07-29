// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ChartReadoutMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartCanvas
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartEmptyState
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartFooterStats
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartReadout
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ExerciseHeader
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ExercisePickerSheet
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricTabs
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.PresetChipsRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

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
            ChartTopBar(consume = consume)

            // The switcher lives below the topbar, not in it (extraction §4.2's hint: the
            // title is large and the topbar's one arrow means back). No exercise → no header;
            // the EXERCISE_NOT_FOUND empty state carries the picker affordance instead.
            state.selectedExercise?.let { exercise ->
                ExerciseHeader(
                    name = exercise.name,
                    actionLabel = stringResource(R.string.feature_exercise_chart_picker_open),
                    onClick = { consume(Action.Click.OnPickerOpen) },
                )
            }

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

/**
 * The v3 `.topbar` (extraction §4.1): back · spacer · nothing. No title — the exercise name
 * is the `.exhead` below. The mockup draws a trailing `⋮` with no handler and no drawn
 * target (§4.9: "no target drawn"), and the feature has no menu action to put behind one —
 * a dead control conforms to nothing, so the slot ships empty. Reported with the PR.
 */
@Composable
private fun ChartTopBar(
    consume: (Action) -> Unit,
) {
    AppTopBar(
        navigation = {
            AppIconButton(
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(
                    io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_action_back,
                ),
                onClick = { consume(Action.Click.OnBack) },
                modifier = Modifier.testTag("ExerciseChartBack"),
            )
        },
    )
}

@Composable
private fun ChartContent(
    state: State,
    consume: (Action) -> Unit,
) {
    when {
        state.isLoading && state.points.isEmpty() && state.emptyReason == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AppLoadingIndicator()
        }

        state.emptyReason != null -> EmptyContent(state = state, consume = consume)

        else -> ChartPopulated(state = state, consume = consume)
    }
}

@Composable
private fun EmptyContent(
    state: State,
    consume: (Action) -> Unit,
) {
    when (state.emptyReason) {
        EmptyReason.NO_FINISHED_SESSIONS -> ChartEmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AppDimension.Space.xl),
            title = stringResource(R.string.feature_exercise_chart_empty_no_sessions_title),
            subtitle = stringResource(R.string.feature_exercise_chart_empty_no_sessions_subtitle),
            ctaLabel = stringResource(R.string.feature_exercise_chart_empty_no_sessions_cta),
            onCta = { consume(Action.Click.OnEmptyCtaClick) },
            testTag = "ExerciseChartNoFinishedSessions",
        )

        EmptyReason.EXERCISE_NOT_FOUND -> ChartEmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AppDimension.Space.xl),
            title = stringResource(R.string.feature_exercise_chart_empty_not_found_title),
            subtitle = stringResource(R.string.feature_exercise_chart_empty_not_found_subtitle),
            ctaLabel = stringResource(R.string.feature_exercise_chart_empty_not_found_cta),
            onCta = { consume(Action.Click.OnPickerOpen) },
            testTag = "ExerciseChartExerciseNotFound",
        )

        EmptyReason.NO_DATA_FOR_EXERCISE -> Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Preset row stays — the user's recovery is to widen the window. No CTA on
            // the empty body itself, the chips at the top are the affordance.
            ChartControls(state = state, consume = consume)
            ChartEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppDimension.Space.xl),
                title = stringResource(R.string.feature_exercise_chart_empty_no_data_title),
                subtitle = stringResource(R.string.feature_exercise_chart_empty_no_data_subtitle),
                testTag = "ExerciseChartNoDataForExercise",
            )
        }

        null -> Unit
    }
}

@Composable
private fun ChartPopulated(
    state: State,
    consume: (Action) -> Unit,
) {
    // The record marking is derived, not stored: one source (the mapper's argmax) feeds the
    // readout's flag and the canvas's molten point alike.
    val recordIndex = remember(state.points) { ChartReadoutMapper.recordIndex(state.points) }

    // The mockup's vertical rhythm, spelled per element rather than one spacedBy: ranges
    // margin-bottom 14px + readout padding-top 18px = 32px → xxl (sum-of-parts, §0.2),
    // chartwrap margin-top 14px → md, statrows margin-top 26px → xl.
    Column(modifier = Modifier.fillMaxSize()) {
        ChartControls(state = state, consume = consume)
        state.readout?.let { readout ->
            Spacer(modifier = Modifier.height(AppDimension.Space.xxl))
            ChartReadout(readout = readout)
        }
        Spacer(modifier = Modifier.height(AppDimension.Space.md))
        ChartCanvas(
            points = state.points,
            activeIndex = state.activeIndex,
            recordIndex = recordIndex,
            onScrub = { consume(Action.Click.OnScrub(it)) },
        )
        state.footerStats?.let { stats ->
            Spacer(modifier = Modifier.height(AppDimension.Space.xl))
            ChartFooterStats(stats = stats)
        }
    }
}

@Composable
private fun ChartControls(
    state: State,
    consume: (Action) -> Unit,
) {
    // Mockup order (§4.1): .tabs above .ranges — metric first, window second. 16dp of air
    // ABOVE the block (`.tabs{margin:16px gutter 0}`; with tabs gated away the ranges'
    // inline `margin-top:16px` plays the same role), 16dp between tabs and ranges (the
    // inline override), still gated WEIGHTED (spec §11).
    Column(
        modifier = Modifier.padding(top = AppDimension.Space.lg),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
    ) {
        if (state.showMetricToggle) {
            MetricTabs(
                modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
                selected = state.metric,
                onSelect = { consume(Action.Click.OnMetricSelect(it)) },
            )
        }
        PresetChipsRow(
            selected = state.preset,
            onSelect = { consume(Action.Click.OnPresetSelect(it)) },
        )
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
                    ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, 80.0, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, 90.0, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, 95.0, 1),
                    ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, 105.0, 2),
                ).toImmutableList(),
                footerStats = ChartFooterStatsUiModel(
                    minTitle = "Minimum",
                    minValue = "80",
                    maxTitle = "Maximum",
                    maxValue = "105",
                    lastTitle = "Last",
                    lastValue = "105",
                    unit = "kg",
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
                emptyReason = EmptyReason.NO_FINISHED_SESSIONS,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenExerciseNotFoundPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseChartScreen(
            state = State.create(initialUuid = "missing-uuid").copy(
                isLoading = false,
                selectedExercise = null,
                recentExercises = persistentListOf(
                    ExercisePickerItemUiModel("uuid-1", "Bench press", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("uuid-2", "Squat", ExerciseTypeUiModel.WEIGHTED),
                ),
                emptyReason = EmptyReason.EXERCISE_NOT_FOUND,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseChartScreenNoDataForExercisePreview() {
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
                ),
                emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
            ),
            consume = {},
        )
    }
}
