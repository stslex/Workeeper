// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.ExerciseChartScreen
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartCanvas
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartFooterStats
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartReadout
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ExercisePickerSheetContent
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricTabs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.ZoneOffset

/** The chart golden suite; fixture data mirrors `pass2d.html` §`s-chart` (§10.4 out of model). */
internal class ExerciseChartGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenPopulated(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(state = populatedState(), consume = {})
        }
    }

    /** The record point under the scrub: the disc stays molten, the readout takes `· рекорд`. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenRecordActive(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = populatedState().copy(
                    activeIndex = 6,
                    readout = ChartReadoutUiModel(
                        metricName = "Максимальный вес",
                        isRecord = true,
                        caption = "23 июля 2026 · 4 подхода · рекорд",
                        value = "77",
                        unit = "кг",
                    ),
                ),
                consume = {},
            )
        }
    }

    /** The weightless render AS IT STANDS — coverage, not endorsement (B11); never fixed here. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            val exercise = ExercisePickerItemUiModel(
                uuid = "ex-w",
                name = "подтягивания",
                type = ExerciseTypeUiModel.WEIGHTLESS,
            )
            val reps = listOf(8, 10, 10, 12)
            val points = DAYS.take(reps.size).mapIndexed { index, day ->
                ChartPointUiModel(
                    day = day,
                    dayMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    sessionUuid = "weightless-$index",
                    value = reps[index].toDouble(),
                    setCount = 3,
                )
            }
            ExerciseChartScreen(
                state = State.create(initialUuid = "ex-w").copy(
                    isLoading = false,
                    selectedExercise = exercise,
                    recentExercises = persistentListOf(exercise),
                    preset = ChartPresetUiModel.ALL,
                    metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                    points = points.toImmutableList(),
                    footerStats = ChartFooterStatsUiModel(
                        minTitle = "Минимум",
                        minValue = "8 повторений",
                        maxTitle = "Максимум",
                        maxValue = "12 повторений",
                        lastTitle = "Последний",
                        lastValue = "12 повторений",
                        unit = null,
                    ),
                    activeIndex = 3,
                    readout = ChartReadoutUiModel(
                        metricName = "Максимальный вес",
                        isRecord = true,
                        caption = "23 июня 2026 · 3 подхода · рекорд",
                        value = "12",
                        unit = "повт.",
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

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenLoadFailed(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseChartScreen(
                state = State.create(initialUuid = null).copy(
                    isLoading = false,
                    emptyReason = EmptyReason.LOAD_FAILED,
                ),
                consume = {},
            )
        }
    }

    /** Mid-scrub: plain donuts, the solid active disc, the molten record at the end. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun canvasMidScrub(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ChartCanvas(
                points = points().toImmutableList(),
                activeIndex = 4,
                recordIndex = 6,
                onScrub = {},
            )
        }
    }

    /** Record under scrub: the disc keeps its molten fill and only grows to r5.5. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun canvasRecordActive(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ChartCanvas(
                points = points().toImmutableList(),
                activeIndex = 6,
                recordIndex = 6,
                onScrub = {},
            )
        }
    }

    /** `sh-pick`'s CONTENT; the `ModalBottomSheet` window stays on the device checklist (§10.4). */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun pickerSheetContent(theme: GoldenTheme, testInfo: TestInfo) {
        // Sheet content sits on surfaceTier3 in production, as every sibling sheet golden pins.
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            ExercisePickerSheetContent(
                items = persistentListOf(
                    ExercisePickerItemUiModel("ex-1", "разведение ног", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("ex-2", "жим платформы (узко)", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("ex-3", "румынская тяга", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("ex-4", "подтягивания", ExerciseTypeUiModel.WEIGHTLESS),
                ),
                selectedUuid = "ex-1",
                query = "",
                onQueryChange = {},
                onItemSelect = {},
            )
        }
    }

    /** The `.readout` pair: plain, and the record variant with the `.mdot` + `рекорд` suffix. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readoutPlain(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ChartReadout(readout = readoutAt4())
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readoutRecord(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ChartReadout(
                readout = ChartReadoutUiModel(
                    metricName = "Объём за сессию",
                    isRecord = true,
                    caption = "23 июля 2026 · 4 подхода · рекорд",
                    value = "4\u00A0620",
                    unit = "кг",
                ),
            )
        }
    }

    /** The indicator's travel endpoints (§10.2); the tween's midpoint is outside the gate. */
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
        sessionUuid = "weighted-$index",
        value = WEIGHTS[index],
        setCount = 4,
    )
}

private fun footer(): ChartFooterStatsUiModel = ChartFooterStatsUiModel(
    minTitle = "Минимум",
    minValue = "49",
    maxTitle = "Максимум",
    maxValue = "77",
    lastTitle = "Последний",
    lastValue = "77",
    unit = "кг",
)

private fun exercise(): ExercisePickerItemUiModel = ExercisePickerItemUiModel(
    uuid = "ex-1",
    name = "разведение ног",
    type = ExerciseTypeUiModel.WEIGHTED,
)

/** The mockup demo's `active=4` readout — `11 июля`, value 63, not the record. */
private fun readoutAt4(): ChartReadoutUiModel = ChartReadoutUiModel(
    metricName = "Максимальный вес",
    isRecord = false,
    caption = "11 июля 2026 · 4 подхода",
    value = "63",
    unit = "кг",
)

private fun populatedState(): State = State.create(initialUuid = "ex-1").copy(
    isLoading = false,
    selectedExercise = exercise(),
    recentExercises = persistentListOf(exercise()),
    preset = ChartPresetUiModel.ALL,
    metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
    points = points().toImmutableList(),
    footerStats = footer(),
    // The mockup demo's initial state: active mid-series, so the scrub story is visible.
    activeIndex = 4,
    readout = readoutAt4(),
)
