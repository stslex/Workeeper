// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toUi
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
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

        override fun formatDayMonth(timestamp: Long): String = "dm($timestamp)"
    }

    @Test
    fun `readout for the record point carries the flag and the grouped whole-number value`() {
        val points = listOf(
            chartPoint(value = 2940.0, setCount = 4),
            chartPoint(value = 4620.4, setCount = 4).copy(day = LocalDate.of(2026, 7, 23)),
        )
        val readout = ChartReadoutMapper.toReadout(
            points = points,
            activeIndex = 1,
            metric = ChartMetricUiModel.VOLUME_PER_SESSION,
            type = ExerciseTypeUiModel.WEIGHTED,
            resourceWrapper = resources,
        )

        assertNotNull(readout)
        assertTrue(readout.isRecord)
        // Math.round + thousand grouping with a PLAIN space (the Archivo charset constraint).
        assertEquals("4\u00A0620", readout.value)
        // date · sets-plural · record — three parts, dot-separated, record last.
        val parts = readout.caption.split(" · ")
        assertEquals(3, parts.size)
        assertEquals(
            resources.getString(
                io.github.stslex.workeeper.feature.exercise_chart.R.string.feature_exercise_chart_readout_record,
            ),
            parts.last(),
        )
    }

    @Test
    fun `readout off the record point has two caption parts and no flag`() {
        val points = listOf(
            chartPoint(value = 49.0, setCount = 4),
            chartPoint(value = 77.0, setCount = 4).copy(day = LocalDate.of(2026, 7, 23)),
        )
        val readout = ChartReadoutMapper.toReadout(
            points = points,
            activeIndex = 0,
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            type = ExerciseTypeUiModel.WEIGHTED,
            resourceWrapper = resources,
        )

        assertNotNull(readout)
        assertTrue(!readout.isRecord)
        assertEquals(2, readout.caption.split(" · ").size)
        assertEquals("49", readout.value)
    }

    @Test
    fun `record index is the earliest point on a value tie`() {
        val points = listOf(
            chartPoint(value = 77.0),
            chartPoint(value = 77.0).copy(day = LocalDate.of(2026, 7, 23)),
        )

        assertEquals(0, ChartReadoutMapper.recordIndex(points))
    }

    @Test
    fun `readout is null without an active index`() {
        assertNull(
            ChartReadoutMapper.toReadout(
                points = listOf(chartPoint()),
                activeIndex = null,
                metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                type = ExerciseTypeUiModel.WEIGHTED,
                resourceWrapper = resources,
            ),
        )
    }

    @Test
    fun `weighted footer values are the readout's grouped form with one shared unit`() {
        // A session point has weight null / reps 0 by contract; the footer must format
        // `value` — the mockup's fmt(): rounded, NBSP-grouped, unit as its own span.
        val stats = footerDomain(value = 4620.4)

        val ui = stats.toUi(
            type = ExerciseTypeDomain.WEIGHTED,
            resourceWrapper = resources,
        )

        assertEquals("4\u00A0620", ui.maxValue)
        assertNotNull(ui.unit)
    }

    @Test
    fun `weightless footer is a reps plural over the value with no separable unit`() {
        val stats = footerDomain(value = 20.0)

        val ui = stats.toUi(
            type = ExerciseTypeDomain.WEIGHTLESS,
            resourceWrapper = resources,
        )

        assertTrue(ui.maxValue.startsWith("plural("))
        assertTrue(ui.maxValue.endsWith(";20;20)"))
        assertNull(ui.unit)
    }

    private fun footerDomain(value: Double): ChartFooterStatsDomain {
        val point = ChartPointDomain(
            day = LocalDate.of(2026, 4, 28),
            dayMillis = 0L,
            value = value,
            sessionUuid = "s1",
            weight = null,
            reps = 0,
            setCount = 2,
        )
        return ChartFooterStatsDomain(min = point, max = point, last = point)
    }

    private fun chartPoint(
        value: Double = 80.0,
        setCount: Int = 1,
    ): ChartPointUiModel = ChartPointUiModel(
        day = LocalDate.of(2026, 4, 28),
        dayMillis = 0L,
        value = value,
        setCount = setCount,
    )
}
