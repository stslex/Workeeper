// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
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
            ChartFoldDomain(emptyList(), null, null, null)

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
            ChartFoldDomain(emptyList(), null, null, null)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        coVerify(exactly = 0) { interactor.getLastTrainedExerciseUuid() }
    }

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
            ChartFoldDomain(emptyList(), null, null, null)

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertEquals(false, flow.value.isLoading)
    }

    @Test
    fun `loadChart with non-empty result clears emptyReason`() {
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
        val nonEmptyPoints = listOf(
            ChartPointDomain(
                day = java.time.LocalDate.of(2026, 4, 28),
                dayMillis = 0L,
                value = 100.0,
                sessionUuid = "s",
                weight = 100.0,
                reps = 5,
                setCount = 1,
            ),
        )
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            ChartFoldDomain(
                points = nonEmptyPoints,
                footer = null,
                windowStartDay = java.time.LocalDate.of(2026, 4, 28),
                windowEndDay = java.time.LocalDate.of(2026, 5, 12),
            )

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.emptyReason)
        assertEquals(1, flow.value.points.size)
        assertEquals(java.time.LocalDate.of(2026, 4, 28), flow.value.windowStartDay)
        assertEquals(java.time.LocalDate.of(2026, 5, 12), flow.value.windowEndDay)
    }

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
                        val result = action()
                        onSuccess(result)
                    } catch (t: Throwable) {
                        onError(t)
                    }
                }
                mockk(relaxed = true)
            }
        }
}
