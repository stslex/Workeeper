// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.FoldResult
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseChartInteractor>()

    @Test
    fun `Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
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
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDataModel("uuid-1", "Bench", ExerciseTypeDataModel.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns "uuid-1"
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            FoldResult(persistentListOf(), null)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        // emptyReason transitions to NO_DATA_FOR_EXERCISE after loadChart returns empty —
        // the load DID run, which is the assertion that matters here.
        coVerify(exactly = 1) { interactor.loadChartData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart`() {
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDataModel("other", "Squat", ExerciseTypeDataModel.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.selectedExercise)
        assertEquals(EmptyReason.EXERCISE_NOT_FOUND, flow.value.emptyReason)
        assertEquals(1, flow.value.recentExercises.size) // picker stays populated
        coVerify(exactly = 0) { interactor.loadChartData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS`() {
        // Empty-recents case wins over not-found: we have nothing to recover to, so the
        // user sees the "fresh install" CTA, not a misleading "not found" + "pick another"
        // CTA over an empty picker.
        val flow = MutableStateFlow(State.create(initialUuid = "missing-uuid"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns emptyList()
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null

        handler.invoke(Action.Common.Init)

        assertEquals(EmptyReason.NO_FINISHED_SESSIONS, flow.value.emptyReason)
    }

    @Test
    fun `Init with explicit valid uuid wins over getLastTrainedExerciseUuid`() {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDataModel("uuid-1", "Bench", ExerciseTypeDataModel.WEIGHTED, 1_000L),
            RecentExerciseDataModel("uuid-2", "Squat", ExerciseTypeDataModel.WEIGHTED, 5_000L),
        )
        // Different from initialUuid — handler must NOT consult this when initialUuid is set.
        coEvery { interactor.getLastTrainedExerciseUuid() } returns "uuid-2"
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            FoldResult(persistentListOf(), null)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        coVerify(exactly = 0) { interactor.getLastTrainedExerciseUuid() }
    }

    @Test
    fun `loadChart with empty result sets NO_DATA_FOR_EXERCISE`() {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-1"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDataModel("uuid-1", "Bench", ExerciseTypeDataModel.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns
            FoldResult(persistentListOf(), null)

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
        val handler = CommonHandler(interactor = interactor, store = store)
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDataModel("uuid-1", "Bench", ExerciseTypeDataModel.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns null
        // Non-empty fold result → emptyReason should clear to null.
        val nonEmptyPoints = persistentListOf(
            io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel(
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
            FoldResult(nonEmptyPoints, null)

        handler.invoke(Action.Common.Init)

        assertNull(flow.value.emptyReason)
        assertEquals(1, flow.value.points.size)
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
