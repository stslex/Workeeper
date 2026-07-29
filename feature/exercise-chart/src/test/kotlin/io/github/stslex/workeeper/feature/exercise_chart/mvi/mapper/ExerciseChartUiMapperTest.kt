// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toTooltip
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotNull

internal class ExerciseChartUiMapperTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            "string($id;${args.joinToString(",")})"

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            "plural($id;$quantity;${args.joinToString(",")})"

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = "date($timestamp)"

        override fun formatDayMonth(timestamp: Long): String =
            error("Not used on this screen")
    }

    @Test
    fun `volume tooltip display label includes reps count`() {
        val point = chartPoint(value = 250.0, reps = 5)
        val tooltip = toTooltip(
            point = point,
            exercise = ExercisePickerItemUiModel("e", "Bench", ExerciseTypeUiModel.WEIGHTED),
            metric = ChartMetricUiModel.VOLUME_PER_SET,
            resourceWrapper = resources,
        )

        assertTrue(tooltip.displayLabel.endsWith(";250,5)"))
    }

    @Test
    fun `tooltip lifts setCount label only when setCount greater than one`() {
        val basePoint = chartPoint(setCount = 1)
        val multiPoint = basePoint.copy(setCount = 3)
        val singleTooltip = toTooltip(
            point = basePoint,
            exercise = ExercisePickerItemUiModel("e", "Bench", ExerciseTypeUiModel.WEIGHTED),
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            resourceWrapper = resources,
        )
        val multiTooltip = toTooltip(
            point = multiPoint,
            exercise = ExercisePickerItemUiModel("e", "Bench", ExerciseTypeUiModel.WEIGHTED),
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            resourceWrapper = resources,
        )

        assertNull(singleTooltip.setCountLabel)
        assertNotNull(multiTooltip.setCountLabel)
        assertEquals("Bench", multiTooltip.exerciseName)
        assertEquals("s1", multiTooltip.sessionUuid)
    }

    @Test
    fun `session tooltip display label reads the aggregate value not the dead weight-reps pair`() {
        // A VOLUME_PER_SESSION point is an aggregate (weight null, reps 0 by contract).
        // The display must be built from `value` alone — falling through to the weighted
        // "weight × reps" branch would print "0 kg × 0".
        val point = chartPoint(value = 1300.0, weight = null, reps = 0, setCount = 3)
        val tooltip = toTooltip(
            point = point,
            exercise = ExercisePickerItemUiModel("e", "Bench", ExerciseTypeUiModel.WEIGHTED),
            metric = ChartMetricUiModel.VOLUME_PER_SESSION,
            resourceWrapper = resources,
        )

        assertTrue(tooltip.displayLabel.endsWith(";1300)"))
    }

    @Test
    fun `weightless session tooltip display label is a reps plural over the summed value`() {
        val point = chartPoint(value = 20.0, weight = null, reps = 0, setCount = 2)
        val tooltip = toTooltip(
            point = point,
            exercise = ExercisePickerItemUiModel("e", "Pullups", ExerciseTypeUiModel.WEIGHTLESS),
            metric = ChartMetricUiModel.VOLUME_PER_SESSION,
            resourceWrapper = resources,
        )

        assertTrue(tooltip.displayLabel.startsWith("plural("))
        assertTrue(tooltip.displayLabel.endsWith(";20;20)"))
    }

    @Test
    fun `tooltip with null exercise falls back to weighted display label`() {
        val point = chartPoint(weight = 100.0, reps = 5)
        val tooltip = toTooltip(
            point = point,
            exercise = null,
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            resourceWrapper = resources,
        )

        assertEquals("", tooltip.exerciseName)
        assertTrue(tooltip.displayLabel.endsWith(";100,5)"))
    }

    private fun chartPoint(
        value: Double = 80.0,
        weight: Double? = 80.0,
        reps: Int = 5,
        setCount: Int = 1,
    ): ChartPointUiModel = ChartPointUiModel(
        day = LocalDate.of(2026, 4, 28),
        dayMillis = 0L,
        value = value,
        sessionUuid = "s1",
        weight = weight,
        reps = reps,
        setCount = setCount,
    )
}
