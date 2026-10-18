// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.store

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Content
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The empty-state flash, pinned in the state model: drive the real handlers over unplottable
 * data and assert that no emitted state resolves to [Content.Plot].
 */
internal class ChartContentResolutionTest {

    private val interactor = FoldInteractor()
    private val resources = DayMonthOnlyResourceWrapper()
    private val strings = LoadStringResolver()

    @Test
    fun `one-point exercise never resolves to Plot across metric and preset switches`() = runTest {
        val emissions = driveSwitches(fold = fold(pointCount = 1))

        assertTrue(emissions.isNotEmpty(), "the drive produced no emissions at all")
        val plotted = emissions.filter { it.content == Content.Plot }
        assertTrue(
            plotted.isEmpty(),
            "no emitted state may compose the canvas for a sub-threshold dataset, but " +
                "${plotted.size} of ${emissions.size} did: " +
                plotted.joinToString { "points=${it.points.size}/reason=${it.emptyReason}" },
        )
    }

    @Test
    fun `zero-point exercise never resolves to Plot across metric and preset switches`() = runTest {
        val emissions = driveSwitches(fold = fold(pointCount = 0))

        assertTrue(emissions.isNotEmpty(), "the drive produced no emissions at all")
        assertTrue(
            emissions.none { it.content == Content.Plot },
            "an empty dataset reached Content.Plot",
        )
    }

    /**
     * The discriminator: without it the assertions above would pass on a `content` that never
     * plots anything.
     */
    @Test
    fun `two-point exercise does resolve to Plot`() = runTest {
        val emissions = driveSwitches(fold = fold(pointCount = 2))

        assertTrue(
            emissions.any { it.content == Content.Plot },
            "plottable data never reached Content.Plot — the invariant is vacuous",
        )
        assertEquals(Content.Plot, emissions.last().content)
    }

    /** A reload that resolves to nothing lands on Empty without a Loading frame between. */
    @Test
    fun `switching a resolved empty state never passes through Loading`() = runTest {
        val emissions = driveSwitches(fold = fold(pointCount = 1))
        val afterFirstResolve = emissions
            .dropWhile { it.content !is Content.Empty }

        assertTrue(afterFirstResolve.isNotEmpty(), "never resolved to Empty")
        assertTrue(
            afterFirstResolve.none { it.content == Content.Loading },
            "a resolved empty state fell back to Loading mid-reload, taking the recovery " +
                "chips off screen: " + afterFirstResolve.map { it.content },
        )
    }

    /**
     * Init, then a metric switch, then a preset switch, each run to completion before the next.
     * Returns every state the handlers emitted, in order.
     */
    private fun TestScope.driveSwitches(fold: ChartFoldDomain): List<State> {
        val flow = MutableStateFlow(State.create(initialUuid = EXERCISE_UUID))
        val emissions = mutableListOf<State>()
        val store = RecordingStore(flow, emissions, this)
        val commonHandler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        val clickHandler = ClickHandler(
            chartLoader = commonHandler,
            resourceWrapper = resources,
            store = store,
        )
        interactor.recents = listOf(
            RecentExerciseDomain(EXERCISE_UUID, "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        interactor.lastTrained = EXERCISE_UUID
        interactor.chartFold = fold

        commonHandler.invoke(Action.Common.Init)
        advanceUntilIdle()
        clickHandler.invoke(Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET))
        advanceUntilIdle()
        clickHandler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.MONTH_1))
        advanceUntilIdle()

        return emissions.toList()
    }

    private fun fold(pointCount: Int): ChartFoldDomain {
        val points = (0 until pointCount).map { index ->
            ChartPointDomain(
                day = LocalDate(2026, 4, index + 1),
                dayMillis = index * DAY_MILLIS,
                value = 100.0 + index,
                sessionUuid = "s$index",
                weight = 100.0 + index,
                reps = 5,
                setCount = 2,
            )
        }
        // The one-point case carries a non-null footer in production, as `toFooter` does too.
        val footer = points.firstOrNull()?.let { first ->
            ChartFooterStatsDomain(min = first, max = points.last(), last = points.last())
        }
        return ChartFoldDomain(points = points, footer = footer)
    }

    /**
     * Records every State the handlers write ([updateState] and the update-lambda
     * [updateStateImmediate] apply and append), runs [launchDefault]'s action and then
     * `onSuccess` on the identity's scheduler and routes a throw to `onError`, and records
     * [sendEvent] (the click handler's haptics). Every other member fails fast.
     */
    private class RecordingStore(
        private val flow: MutableStateFlow<State>,
        private val emissions: MutableList<State>,
        private val scope: CoroutineScope,
    ) : ExerciseChartHandlerStore {

        val events = mutableListOf<Event>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by the handlers")

        override val logger: Logger
            get() = error("logger must not be read by the handlers")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action): Nothing = error("consume must not be used by the drive")

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by the handlers")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
            emissions.add(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
            emissions.add(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by the handlers")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launch must not be used by the handlers")

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                onSuccess(action())
            } catch (t: Throwable) {
                onError(t)
            }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by the handlers")
    }

    /** The strict interactor of the drive: the recents, the last-trained id and the one fold. */
    private class FoldInteractor : ExerciseChartInteractor {

        var recents: List<RecentExerciseDomain>? = null
        var lastTrained: String? = null
        var chartFold: ChartFoldDomain? = null

        override suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDomain> =
            checkNotNull(recents) { "getRecentlyTrainedExercises was not set up" }

        override suspend fun getLastTrainedExerciseUuid(): String? =
            checkNotNull(lastTrained) { "getLastTrainedExerciseUuid was not set up" }

        override suspend fun loadChartData(
            exerciseUuid: String,
            preset: ChartPresetDomain,
            metric: ChartMetricDomain,
            type: ExerciseTypeDomain,
            now: Long,
        ): ChartFoldDomain = checkNotNull(chartFold) { "loadChartData was not set up" }
    }

    /** The relaxed mock answered `formatDayMonth` with `""`; nothing else is reached. */
    private class DayMonthOnlyResourceWrapper : ResourceWrapper {

        override fun getString(id: Int, vararg args: Any): String = error("not used")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("not used")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = error("not used")

        override fun formatDayMonth(timestamp: Long): String = ""
    }

    /**
     * Strict: answers `""`, the relaxed mock's default, for exactly what a load resolves — the
     * readout copy (D3) and the weighted footer — and only for the arguments production passes:
     * none for a string, the quantity alone for the set plural. Anything else throws.
     */
    private class LoadStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(resource in LOAD_STRINGS) { "a load must not read $resource" }
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            return ""
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String {
            check(resource == Res.plurals.feature_exercise_chart_readout_sets) {
                "a load must not read $resource"
            }
            check(formatArgs.toList() == listOf(quantity)) {
                "the set plural passes its quantity as its one format argument; got ${formatArgs.toList()}"
            }
            return ""
        }

        private companion object {

            val LOAD_STRINGS = setOf(
                Res.string.feature_exercise_chart_metric_name_heaviest,
                Res.string.feature_exercise_chart_metric_name_session,
                Res.string.feature_exercise_chart_metric_name_volume,
                Res.string.feature_exercise_chart_unit_kg,
                Res.string.feature_exercise_chart_unit_reps,
                Res.string.feature_exercise_chart_readout_record,
                Res.string.feature_exercise_chart_footer_min,
                Res.string.feature_exercise_chart_footer_max,
                Res.string.feature_exercise_chart_footer_last,
            )
        }
    }

    private companion object {

        const val EXERCISE_UUID = "uuid-1"
        const val DAY_MILLIS = 86_400_000L
    }
}
