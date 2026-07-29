// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.ExerciseChartScreen
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartFooterStats
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricTabs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The chart golden suite. The BASELINE commit (C0) records the pre-rebuild surface —
 * AppTopAppBar + SwapHoriz, segmented metric toggle, L-axis canvas with accent-filled
 * points and the tap tooltip — so each Part-4 rebuild commit reads as an image diff.
 *
 * Fixture data mirrors `pass2d.html` §`s-chart` (`разведение ног`, seven sessions
 * 2 мая → 23 июля 2026, weights 49/49/56/63/63/63/77 with the record last) so the final
 * element-by-element pass holds golden beside mockup with no renaming.
 *
 * Out of model, per the harness KDoc: `ExercisePickerSheet`'s `ModalBottomSheet` window —
 * device checklist (§10.4). The in-canvas tooltip overlay is NOT a window and is recorded.
 */
internal class ExerciseChartGoldenTest {

    // --- Whole frame -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenPopulated(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(state = populatedState(), consume = {})
        }
    }

    /** The tap tooltip anchored to the record point — the pre-rebuild inspection surface. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenTooltip(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = populatedState().copy(
                    activeTooltip = ChartTooltipUiModel(
                        sessionUuid = "s-7",
                        exerciseName = "разведение ног",
                        dateLabel = "23 июля 2026 г.",
                        displayLabel = "77 кг × 15",
                        setCountLabel = "4 подхода в этот день",
                    ),
                ),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyNoData(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = populatedState().copy(
                    points = persistentListOf(),
                    footerStats = null,
                    emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
                ),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyNotFound(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = populatedState().copy(
                    selectedExercise = null,
                    points = persistentListOf(),
                    footerStats = null,
                    emptyReason = EmptyReason.EXERCISE_NOT_FOUND,
                ),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyNoSessions(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = State.create(initialUuid = null).copy(
                    isLoading = false,
                    emptyReason = EmptyReason.NO_FINISHED_SESSIONS,
                ),
                consume = {},
            )
        }
    }

    // --- Tabs ------------------------------------------------------------------------------

    /**
     * The sliding indicator's transient pair (§10.2): both travel endpoints. The tween's
     * midpoint is time-based and outside the gate — the pair pins where the thumb *rests*
     * at each stop, which is what a wrong offset computation would corrupt.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tabsIndicatorFirst(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            MetricTabs(selected = ChartMetricUiModel.HEAVIEST_WEIGHT, onSelect = {})
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tabsIndicatorSecond(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            MetricTabs(selected = ChartMetricUiModel.VOLUME_PER_SESSION, onSelect = {})
        }
    }

    // --- Footer ----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun footerStats(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ChartFooterStats(stats = footer())
        }
    }
}

private val DAYS = listOf(
    LocalDate.of(2026, 5, 2),
    LocalDate.of(2026, 5, 16),
    LocalDate.of(2026, 6, 1),
    LocalDate.of(2026, 6, 23),
    LocalDate.of(2026, 7, 11),
    LocalDate.of(2026, 7, 15),
    LocalDate.of(2026, 7, 23),
)

private val WEIGHTS = listOf(49.0, 49.0, 56.0, 63.0, 63.0, 63.0, 77.0)

private fun points(): List<ChartPointUiModel> = DAYS.mapIndexed { index, day ->
    ChartPointUiModel(
        day = day,
        dayMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        value = WEIGHTS[index],
        sessionUuid = "s-${index + 1}",
        weight = WEIGHTS[index],
        reps = 15,
        setCount = 4,
    )
}

private fun footer(): ChartFooterStatsUiModel = ChartFooterStatsUiModel(
    minTitle = "Min",
    minValue = "49 kg",
    maxTitle = "Max",
    maxValue = "77 kg",
    lastTitle = "Last",
    lastValue = "77 kg",
)

private fun exercise(): ExercisePickerItemUiModel = ExercisePickerItemUiModel(
    uuid = "ex-1",
    name = "разведение ног",
    type = ExerciseTypeUiModel.WEIGHTED,
)

private fun populatedState(): State = State.create(initialUuid = "ex-1").copy(
    isLoading = false,
    selectedExercise = exercise(),
    recentExercises = persistentListOf(exercise()),
    preset = ChartPresetUiModel.ALL,
    metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
    points = points().toImmutableList(),
    footerStats = footer(),
    windowStartDay = DAYS.first(),
    windowEndDay = DAYS.last(),
)
