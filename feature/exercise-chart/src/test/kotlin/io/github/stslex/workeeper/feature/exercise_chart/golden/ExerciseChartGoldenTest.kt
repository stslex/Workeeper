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
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.ExerciseChartScreen
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartCanvas
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartFooterStats
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartReadout
import io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricTabs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The chart golden suite. The BASELINE commit (C0) recorded the pre-rebuild surface; each
 * Part-4 rebuild commit reads as an image diff against the previous one. The tooltip
 * scenario retired with its surface — the readout + scrub replaced it (§4.5/§4.6).
 *
 * Fixture data mirrors `pass2d.html` §`s-chart` (`разведение ног`, seven sessions
 * 2 мая → 23 июля 2026, weights 49/49/56/63/63/63/77 with the record last, demo scrub at
 * index 4) so the final element-by-element pass holds golden beside mockup with no renaming.
 *
 * Out of model, per the harness KDoc: `ExercisePickerSheet`'s `ModalBottomSheet` window —
 * device checklist (§10.4). Both canvas animations (scrub travel, metric morph) are
 * time-based and outside the gate; the goldens pin their endpoints.
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

    /**
     * The record point under the scrub: the disc stays molten (`.pt.pr.act`), the readout
     * takes the `.mdot` + `· рекорд` treatment. Replaces the retired tooltip scenario as
     * the "inspecting the record" frame.
     */
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

    // --- Canvas ----------------------------------------------------------------------------

    /**
     * The canvas point pair. Mid-scrub: plain donuts, the solid `--max` active disc at
     * r5.5 with the dashed scrub under the series, the molten record at the end.
     */
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

    // --- Readout ---------------------------------------------------------------------------

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
                    value = "4 620",
                    unit = "кг",
                ),
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
    // The mockup demo's initial state: active mid-series, so the frame golden holds beside
    // pass2d with the scrub story visible (readout on `11 июля`, record elsewhere).
    activeIndex = 4,
    readout = readoutAt4(),
)
