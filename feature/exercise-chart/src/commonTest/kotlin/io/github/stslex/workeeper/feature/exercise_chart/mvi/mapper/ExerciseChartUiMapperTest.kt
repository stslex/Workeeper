// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toUi
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.resources.Res
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_last
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_max
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_footer_min
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_heaviest
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_session
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_metric_name_volume
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_readout_record
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_readout_sets
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_unit_kg
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_unit_reps
import io.github.stslex.workeeper.feature.exercise_chart.resources.feature_exercise_chart_value_reps
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ExerciseChartUiMapperTest {

    /** The date stays on [ResourceWrapper] (S4); every other member is unreached. */
    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = error("not used")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("not used")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = error("not used")

        override fun formatDayMonth(timestamp: Long): String = "dm($timestamp)"
    }

    private val resolver = SentinelStringResolver()

    @Test
    fun `domain point keeps session identity in the UI model`() {
        val point = ChartPointDomain(
            day = LocalDate(2026, 4, 28),
            dayMillis = 0L,
            value = 100.0,
            sessionUuid = "evening",
            weight = 100.0,
            reps = 3,
            setCount = 1,
        )

        assertEquals("evening", point.toUi().sessionUuid)
        assertEquals(3, point.toUi().reps)
    }

    @Test
    fun `readout for the record point carries the flag and the grouped whole-number value`() = runTest {
        val points = listOf(
            chartPoint(value = 2940.0, setCount = 4),
            chartPoint(value = 4620.4, setCount = 4).copy(day = LocalDate(2026, 7, 23)),
        )
        val readout = ChartReadoutMapper.toReadout(
            points = points,
            activeIndex = 1,
            metric = ChartMetricUiModel.VOLUME_PER_SESSION,
            type = ExerciseTypeUiModel.WEIGHTED,
            copy = ChartReadoutMapper.toReadoutCopy(points, resolver),
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
            resolver.getString(Res.string.feature_exercise_chart_readout_record),
            parts.last(),
        )
    }

    @Test
    fun `readout off the record point has two caption parts and no flag`() = runTest {
        val points = listOf(
            chartPoint(value = 49.0, setCount = 4),
            chartPoint(value = 77.0, setCount = 4).copy(day = LocalDate(2026, 7, 23)),
        )
        val readout = ChartReadoutMapper.toReadout(
            points = points,
            activeIndex = 0,
            metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
            type = ExerciseTypeUiModel.WEIGHTED,
            copy = ChartReadoutMapper.toReadoutCopy(points, resolver),
            resourceWrapper = resources,
        )

        assertNotNull(readout)
        assertTrue(!readout.isRecord)
        assertEquals(2, readout.caption.split(" · ").size)
        assertEquals("49", readout.value)
    }

    @Test
    fun `volume record indices keep the earliest point on a value tie`() {
        val points = listOf(
            chartPoint(value = 77.0, reps = 5),
            chartPoint(value = 77.0, reps = 6).copy(day = LocalDate(2026, 7, 23)),
        )

        listOf(
            ChartMetricUiModel.VOLUME_PER_SET,
            ChartMetricUiModel.VOLUME_PER_SESSION,
        ).forEach { metric ->
            assertEquals(0, ChartReadoutMapper.recordIndex(points, metric), metric.name)
        }
    }

    @Test
    fun `weight record index follows the PR reps tiebreak across sessions`() {
        val points = listOf(
            ChartPointDomain(
                day = LocalDate(2026, 4, 28),
                dayMillis = 0L,
                value = 100.0,
                sessionUuid = "morning",
                weight = 100.0,
                reps = 5,
                setCount = 1,
            ).toUi(),
            ChartPointDomain(
                day = LocalDate(2026, 4, 28),
                dayMillis = 0L,
                value = 100.0,
                sessionUuid = "evening",
                weight = 100.0,
                reps = 6,
                setCount = 1,
            ).toUi(),
        )

        assertEquals(
            1,
            ChartReadoutMapper.recordIndex(points, ChartMetricUiModel.HEAVIEST_WEIGHT),
        )
    }

    @Test
    fun `weight record index keeps the earliest session when weight and reps tie`() {
        val points = listOf(
            chartPoint(value = 100.0, reps = 6),
            chartPoint(value = 100.0, reps = 6).copy(day = LocalDate(2026, 7, 23)),
        )

        assertEquals(
            0,
            ChartReadoutMapper.recordIndex(points, ChartMetricUiModel.HEAVIEST_WEIGHT),
        )
    }

    @Test
    fun `readout is null without an active index`() = runTest {
        val points = listOf(chartPoint())
        assertNull(
            ChartReadoutMapper.toReadout(
                points = points,
                activeIndex = null,
                metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                type = ExerciseTypeUiModel.WEIGHTED,
                copy = ChartReadoutMapper.toReadoutCopy(points, resolver),
                resourceWrapper = resources,
            ),
        )
    }

    @Test
    fun `weighted footer values are the readout's grouped form with one shared unit`() = runTest {
        // A session point has weight null / reps 0 by contract; the footer must format
        // `value` — the mockup's fmt(): rounded, NBSP-grouped, unit as its own span.
        val stats = footerDomain(value = 4620.4)

        val ui = stats.toUi(
            type = ExerciseTypeDomain.WEIGHTED,
            stringResolver = resolver,
        )

        assertEquals("4\u00A0620", ui.maxValue)
        assertNotNull(ui.unit)
    }

    @Test
    fun `weightless footer is a reps plural over the value with no separable unit`() = runTest {
        val stats = footerDomain(value = 20.0)

        val ui = stats.toUi(
            type = ExerciseTypeDomain.WEIGHTLESS,
            stringResolver = resolver,
        )

        assertTrue(ui.maxValue.startsWith("plural("))
        assertTrue(ui.maxValue.endsWith(";20;20)"))
        assertNull(ui.unit)
    }

    private fun footerDomain(value: Double): ChartFooterStatsDomain {
        val point = ChartPointDomain(
            day = LocalDate(2026, 4, 28),
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
        reps: Int = 0,
    ): ChartPointUiModel = ChartPointUiModel(
        day = LocalDate(2026, 4, 28),
        dayMillis = 0L,
        sessionUuid = "session-${value.toInt()}",
        value = value,
        setCount = setCount,
        reps = reps,
    )

    /**
     * Strict: answers today's sentinel shapes, keyed by the resource's own name, for exactly the
     * resources the mapper reads, and only for the arguments production passes — none for a
     * string, the quantity alone for a plural. Anything else throws.
     */
    private class SentinelStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            val name = when (resource) {
                Res.string.feature_exercise_chart_metric_name_heaviest -> "feature_exercise_chart_metric_name_heaviest"
                Res.string.feature_exercise_chart_metric_name_session -> "feature_exercise_chart_metric_name_session"
                Res.string.feature_exercise_chart_metric_name_volume -> "feature_exercise_chart_metric_name_volume"
                Res.string.feature_exercise_chart_unit_kg -> "feature_exercise_chart_unit_kg"
                Res.string.feature_exercise_chart_unit_reps -> "feature_exercise_chart_unit_reps"
                Res.string.feature_exercise_chart_readout_record -> "feature_exercise_chart_readout_record"
                Res.string.feature_exercise_chart_footer_min -> "feature_exercise_chart_footer_min"
                Res.string.feature_exercise_chart_footer_max -> "feature_exercise_chart_footer_max"
                Res.string.feature_exercise_chart_footer_last -> "feature_exercise_chart_footer_last"
                else -> error("the mapper must not read $resource")
            }
            check(formatArgs.isEmpty()) { "$name is read verbatim; got ${formatArgs.toList()}" }
            return "string($name;)"
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String {
            val name = when (resource) {
                Res.plurals.feature_exercise_chart_readout_sets -> "feature_exercise_chart_readout_sets"
                Res.plurals.feature_exercise_chart_value_reps -> "feature_exercise_chart_value_reps"
                else -> error("the mapper must not read $resource")
            }
            check(formatArgs.toList() == listOf(quantity)) {
                "$name passes its quantity as its one format argument; got ${formatArgs.toList()}"
            }
            return "plural($name;$quantity;${formatArgs.joinToString(",")})"
        }
    }
}
