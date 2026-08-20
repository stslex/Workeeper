// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Content
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseChartInteractor>()
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns emptyList()
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.selectedExercise)
        assertEquals(EmptyReason.NO_FINISHED_SESSIONS, flow.value.emptyReason)
        assertTrue(flow.value.recentExercises.isEmpty())
        assertEquals(false, flow.value.isLoading)
        coVerify(exactly = 0) { interactor.loadChartData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Init with null and last-trained available loads its chart`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns "uuid-1"
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(emptyList(), null)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        coVerify(exactly = 1) { interactor.loadChartData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart`() {
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("other", "Squat", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.selectedExercise)
        assertEquals(EmptyReason.EXERCISE_NOT_FOUND, flow.value.emptyReason)
        assertEquals(1, flow.value.recentExercises.size)
        coVerify(exactly = 0) { interactor.loadChartData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS`() {
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns emptyList()
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.NO_FINISHED_SESSIONS, flow.value.emptyReason)
    }

    @Test
    fun `Init with explicit valid uuid wins over getLastTrainedExerciseUuid`() {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
            RecentExerciseDomain("uuid-2", "Squat", ExerciseTypeDomain.WEIGHTED, 5_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns "uuid-2"
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(emptyList(), null)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        coVerify(exactly = 0) { interactor.getLastTrainedExerciseUuid() }
    }

    /**
     * The coherence guard, from the user's side: they tap a second metric before the first
     * load answers. The first response must not be applied — a chart that settles on the
     * losing request's data while the tab strip highlights the winner is wrong for as long
     * as the screen lives, since nothing rewrites `metric` afterwards.
     */
    @Test
    fun `a response whose metric no longer matches the live state is dropped`() {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(selectedExercise = benchItem),
        )
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        // The tap lands while this load is in flight.
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } answers {
            flow.value = flow.value.copy(metric = ChartMetricUiModel.VOLUME_PER_SESSION)
            ChartFoldDomain(points = listOf(pointDomain(), pointDomain()), footer = null)
        }

        handler.loadChart(benchItem)

        assertTrue(
            flow.value.points.isEmpty(),
            "a stale response was applied: the chart would show the losing metric's data",
        )
        assertEquals(ChartMetricUiModel.VOLUME_PER_SESSION, flow.value.metric)
    }

    /** The discriminator: an uncontested response is still applied. */
    @Test
    fun `a response that still matches the live state is applied`() {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(selectedExercise = benchItem),
        )
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(points = listOf(pointDomain(), pointDomain()), footer = null)

        handler.loadChart(benchItem)

        assertEquals(2, flow.value.points.size)
    }

    private fun pointDomain(): ChartPointDomain = ChartPointDomain(
        day = java.time.LocalDate.of(2026, 4, 1),
        dayMillis = 0L,
        value = 100.0,
        sessionUuid = "s",
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
    fun `loadChart with empty result sets NO_DATA_FOR_EXERCISE`() {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(emptyList(), null)

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
    }

    @Test
    fun `loadChart with a single point is sub-threshold — empty state, no readout`() {
        // §4.8: the chart appears after two recorded sessions. One point is no line.
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(
                emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
            ),
        )
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(
                points = listOf(chartPointDomain(day = java.time.LocalDate.of(2026, 4, 28))),
                footer = null,
            )

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertNull(flow.value.activeIndex)
        assertNull(flow.value.readout)
    }

    @Test
    fun `loadChart with two points clears emptyReason and scrubs the last point`() {
        val flow = MutableStateFlow(
            State.create(initialUuid = "uuid-1").copy(
                emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE,
            ),
        )
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(
                points = listOf(
                    chartPointDomain(day = java.time.LocalDate.of(2026, 4, 21)),
                    chartPointDomain(day = java.time.LocalDate.of(2026, 4, 28)),
                ),
                footer = null,
            )

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.emptyReason)
        assertEquals(2, flow.value.points.size)
        assertEquals(1, flow.value.activeIndex)
        assertNotNull(flow.value.readout)
    }

    private fun chartPointDomain(day: java.time.LocalDate): ChartPointDomain = ChartPointDomain(
        day = day,
        dayMillis = 0L,
        value = 100.0,
        sessionUuid = "s",
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

    /**
     * Build a [ExerciseChartHandlerStore] mock that runs `launch { ... }` synchronously and
     * applies `updateState` / `updateStateImmediate` directly to the captured `MutableStateFlow`.
     * This lets tests assert the post-launch state without spinning up a real coroutine.
     */
    @Test
    fun `a throwing recents read resolves to LOAD_FAILED instead of loading forever`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } throws IllegalStateException("db down")
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.LOAD_FAILED, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
        // The point of the reason: `content` is what the screen renders from, and an unresolved
        // failure leaves it Loading — which draws nothing, with no retry.
        assertEquals(Content.Empty(EmptyReason.LOAD_FAILED), flow.value.content)
    }

    @Test
    fun `a throwing chart read resolves to LOAD_FAILED`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, resourceWrapper = resources, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain("uuid-1", "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns "uuid-1"
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } throws
            IllegalStateException("db down")

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.LOAD_FAILED, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
    }

    private fun newStore(flow: MutableStateFlow<State>): ExerciseChartHandlerStore =
        mockk<ExerciseChartHandlerStore>(relaxed = true).also { store ->
            every { store.state } returns flow
            every { store.updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
            coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
                val update = firstArg<suspend (State) -> State>()
                flow.value = update(flow.value)
            }
            every {
                store.launch<Any?>(
                    onError = any(),
                    onSuccess = any(),
                    workDispatcher = any(),
                    eachDispatcher = any(),
                    action = any(),
                )
            } answers {
                val onError = arg<suspend (Throwable) -> Unit>(0)
                val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
                val action = arg<suspend CoroutineScope.() -> Any?>(4)
                runBlocking {
                    try {
                        // `supervisorScope`, not a bare `action()`: `processInit` fans out through
                        // two `async` children, and in a plain scope the first failure cancels the
                        // parent before this catch can run the handler. Production survives that
                        // through `AppCoroutineScopeImpl`'s `CoroutineExceptionHandler` backstop —
                        // its scope root is a `SupervisorJob`, so the handler is invoked and
                        // re-launches `onError` on a live scope. The supervisor reproduces the same
                        // observable — the action throws, `onError` runs — without modelling the
                        // backstop's plumbing.
                        val result = supervisorScope { action() }
                        onSuccess(result)
                    } catch (t: Throwable) {
                        onError(t)
                    }
                }
                mockk(relaxed = true)
            }
            every {
                store.launchDefault<Any?>(
                    onError = any(),
                    onSuccess = any(),
                    action = any(),
                )
            } answers {
                val onError = arg<suspend (Throwable) -> Unit>(0)
                val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
                val action = arg<suspend CoroutineScope.() -> Any?>(2)
                runBlocking {
                    try {
                        // Same backstop reproduction as the `launch` mock above.
                        val result = supervisorScope { action() }
                        onSuccess(result)
                    } catch (t: Throwable) {
                        onError(t)
                    }
                }
                mockk(relaxed = true)
            }
        }
}
