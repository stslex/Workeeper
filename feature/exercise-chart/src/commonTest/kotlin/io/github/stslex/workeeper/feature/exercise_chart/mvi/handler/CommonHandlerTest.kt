// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Content
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.resources.Res
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CommonHandlerTest {

    private val interactor = FakeChartInteractor()
    private val resources = DayMonthOnlyResourceWrapper()
    private val strings = ReadoutStringResolver()

    @Test
    fun `Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = { emptyList() }
        interactor.lastTrained = { null }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertNull(flow.value.selectedExercise)
        assertEquals(EmptyReason.NO_FINISHED_SESSIONS, flow.value.emptyReason)
        assertTrue(flow.value.recentExercises.isEmpty())
        assertEquals(false, flow.value.isLoading)
        assertEquals(0, interactor.loadChartDataCalls)
    }

    @Test
    fun `Init with null and last-trained available loads its chart`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { "uuid-1" }
        interactor.chartData = { ChartFoldDomain(emptyList(), null) }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        assertEquals(1, interactor.loadChartDataCalls)
    }

    @Test
    fun `Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("other", "Squat", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { null }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertNull(flow.value.selectedExercise)
        assertEquals(EmptyReason.EXERCISE_NOT_FOUND, flow.value.emptyReason)
        assertEquals(1, flow.value.recentExercises.size)
        assertEquals(0, interactor.loadChartDataCalls)
    }

    @Test
    fun `Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = { emptyList() }
        interactor.lastTrained = { null }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(EmptyReason.NO_FINISHED_SESSIONS, flow.value.emptyReason)
    }

    @Test
    fun `Init with explicit valid uuid wins over getLastTrainedExerciseUuid`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(
                RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
                RecentExerciseDomain("uuid-2", "Squat", ExerciseTypeDomain.WEIGHTED, 5_000L),
            )
        }
        interactor.lastTrained = { "uuid-2" }
        interactor.chartData = { ChartFoldDomain(emptyList(), null) }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        assertEquals(0, interactor.lastTrainedCalls)
    }

    /**
     * The coherence guard from the user's side: a second metric tapped before the first load
     * answers. The losing response must not be applied.
     */
    @Test
    fun `a response whose metric no longer matches the live state is dropped`() = runTest {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(selectedExercise = benchItem),
        )
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        // The tap lands while this load is in flight.
        interactor.chartData = {
            flow.value = flow.value.copy(metric = ChartMetricUiModel.VOLUME_PER_SESSION)
            ChartFoldDomain(
                points = listOf(pointDomain("session-1"), pointDomain("session-2")),
                footer = null,
            )
        }

        handler.loadChart(benchItem)
        advanceUntilIdle()

        assertTrue(
            flow.value.points.isEmpty(),
            "a stale response was applied: the chart would show the losing metric's data",
        )
        assertEquals(ChartMetricUiModel.VOLUME_PER_SESSION, flow.value.metric)
    }

    /** The discriminator: an uncontested response is still applied. */
    @Test
    fun `a response that still matches the live state is applied`() = runTest {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(selectedExercise = benchItem),
        )
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.chartData = {
            ChartFoldDomain(
                points = listOf(pointDomain("session-1"), pointDomain("session-2")),
                footer = null,
            )
        }

        handler.loadChart(benchItem)
        advanceUntilIdle()

        assertEquals(2, flow.value.points.size)
    }

    private fun pointDomain(sessionUuid: String): ChartPointDomain = ChartPointDomain(
        day = LocalDate(2026, 4, 1),
        dayMillis = 0L,
        value = 100.0,
        sessionUuid = sessionUuid,
        weight = 100.0,
        reps = 5,
        setCount = 1,
    )

    private val benchItem = ExercisePickerItemUiModel(
        uuid = "uuid-1",
        name = "Bench",
        type = io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.WEIGHTED,
    )

    @Test
    fun `loadChart with empty result sets NO_DATA_FOR_EXERCISE`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { null }
        interactor.chartData = { ChartFoldDomain(emptyList(), null) }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
    }

    @Test
    fun `loadChart with a single point is sub-threshold — empty state no readout`() = runTest {
        // §4.8: the chart appears after two recorded sessions. One point is no line.
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(
                emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
            ),
        )
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { null }
        interactor.chartData = {
            ChartFoldDomain(
                points = listOf(chartPointDomain(day = LocalDate(2026, 4, 28))),
                footer = null,
            )
        }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertNull(flow.value.activeIndex)
        assertNull(flow.value.readout)
    }

    @Test
    fun `loadChart with two same-day sessions plots and scrubs the later session`() = runTest {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(
                emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
            ),
        )
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { null }
        interactor.chartData = {
            ChartFoldDomain(
                points = listOf(
                    chartPointDomain(
                        day = LocalDate(2026, 4, 28),
                        sessionUuid = "morning",
                    ),
                    chartPointDomain(
                        day = LocalDate(2026, 4, 28),
                        sessionUuid = "evening",
                    ),
                ),
                footer = null,
            )
        }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertNull(flow.value.emptyReason)
        assertEquals(2, flow.value.points.size)
        assertEquals(listOf("morning", "evening"), flow.value.points.map { it.sessionUuid })
        assertEquals(1, flow.value.activeIndex)
        assertNotNull(flow.value.readout)
    }

    private fun chartPointDomain(
        day: LocalDate,
        sessionUuid: String = "session",
    ): ChartPointDomain = ChartPointDomain(
        day = day,
        dayMillis = 0L,
        value = 100.0,
        sessionUuid = sessionUuid,
        weight = 100.0,
        reps = 5,
        setCount = 1,
    )

    @Test
    fun `default state preset is ALL`() {
        assertEquals(
            io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel.ALL,
            State.create(initialUuid = null).preset,
        )
    }

    @Test
    fun `a throwing recents read resolves to LOAD_FAILED instead of loading forever`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = { throw IllegalStateException("db down") }
        interactor.lastTrained = { null }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(EmptyReason.LOAD_FAILED, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
        // `content` is what the screen renders from; an unresolved failure leaves it Loading.
        assertEquals(Content.Empty(EmptyReason.LOAD_FAILED), flow.value.content)
    }

    @Test
    fun `a throwing chart read resolves to LOAD_FAILED`() = runTest {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = FakeCommonHandlerStore(flow, this)
        val handler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )
        interactor.recents = {
            listOf(RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L))
        }
        interactor.lastTrained = { "uuid-1" }
        interactor.chartData = { throw IllegalStateException("db down") }

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(EmptyReason.LOAD_FAILED, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
    }

    /**
     * The store as the MockK store answered it: [state] is the identity's flow, [updateState]
     * and the update-lambda [updateStateImmediate] apply to it, and [launch] / [launchDefault]
     * each launch on the identity's `runTest` scheduler, run the action inside `supervisorScope`
     * (processInit fans out through two `async` children whose first failure would cancel a bare
     * scope), then `onSuccess`, and route a throw from either to `onError`. Every other member
     * fails fast, [logger] included.
     */
    private class FakeCommonHandlerStore(
        private val flow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : ExerciseChartHandlerStore {

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by CommonHandler")

        override val logger: Logger
            get() = error("logger must not be read by CommonHandler")

        override fun sendEvent(event: Event): Nothing =
            error("sendEvent must not be used by CommonHandler")

        override fun consume(action: Action): Nothing = error("consume must not be used by CommonHandler")

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by CommonHandler")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by CommonHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = launchRouted(onError, onSuccess, action)

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = launchRouted(onError, onSuccess, action)

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by CommonHandler")

        private fun <T> launchRouted(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                val result = supervisorScope { action() }
                onSuccess(result)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    /**
     * The strict interactor: each member answers what the identity set up — a value, a throw, or
     * a write to the live state — and counts its calls; a member nobody set up throws.
     */
    private class FakeChartInteractor : ExerciseChartInteractor {

        var recents: suspend () -> List<RecentExerciseDomain> = { error("getRecentlyTrainedExercises was not set up") }
        var lastTrained: suspend () -> String? = { error("getLastTrainedExerciseUuid was not set up") }
        var chartData: suspend () -> ChartFoldDomain = { error("loadChartData was not set up") }

        var recentsCalls: Int = 0
            private set
        var lastTrainedCalls: Int = 0
            private set
        var loadChartDataCalls: Int = 0
            private set

        override suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDomain> {
            recentsCalls++
            return recents()
        }

        override suspend fun getLastTrainedExerciseUuid(): String? {
            lastTrainedCalls++
            return lastTrained()
        }

        override suspend fun loadChartData(
            exerciseUuid: String,
            preset: ChartPresetDomain,
            metric: ChartMetricDomain,
            type: ExerciseTypeDomain,
            now: Long,
        ): ChartFoldDomain {
            loadChartDataCalls++
            return chartData()
        }
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
     * Strict: answers `""`, the relaxed mock's default, for exactly the readout copy a load
     * resolves (D3), and only for the arguments production passes — none for the six strings,
     * the quantity alone for the set plural. Any other resource or argument list throws.
     */
    private class ReadoutStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(resource in READOUT_STRINGS) { "CommonHandler's loads must not read $resource" }
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            return ""
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String {
            check(resource == Res.plurals.feature_exercise_chart_readout_sets) {
                "CommonHandler's loads must not read $resource"
            }
            check(formatArgs.toList() == listOf(quantity)) {
                "the set plural passes its quantity as its one format argument; got ${formatArgs.toList()}"
            }
            return ""
        }

        private companion object {

            val READOUT_STRINGS = setOf(
                Res.string.feature_exercise_chart_metric_name_heaviest,
                Res.string.feature_exercise_chart_metric_name_session,
                Res.string.feature_exercise_chart_metric_name_volume,
                Res.string.feature_exercise_chart_unit_kg,
                Res.string.feature_exercise_chart_unit_reps,
                Res.string.feature_exercise_chart_readout_record,
            )
        }
    }
}
