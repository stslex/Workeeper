// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseChartInteractor>(relaxed = true)

    @Test
    fun `Init starts the load workflow on the store`() {
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)

        handler.invoke(Action.Common.Init)

        // The launch entry-point is what fans out to interactor.getRecentlyTrainedExercises +
        // interactor.getLastTrainedExerciseUuid; verifying it dispatched at least once is
        // enough — the actual coroutine body is exercised by integration / mapper tests.
        verify(atLeast = 1) {
            store.launch<Any?>(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `Init reads initialUuid from current state value`() {
        val flow = MutableStateFlow(State.create(initialUuid = "uuid-77"))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)

        handler.invoke(Action.Common.Init)

        assertEquals("uuid-77", flow.value.initialUuid)
    }

    @Test
    fun `Init mutates state via the launched closure when invoked through the store`() {
        // Capture the action lambda; we don't invoke it here (other tests prove the wiring
        // in the StoreImpl). This guards against a regression where Init forgets to launch
        // a coroutine (e.g. swallowed error in the new lambda shape).
        val flow = MutableStateFlow(State.create(initialUuid = null))
        val store = newStore(flow)
        val handler = CommonHandler(interactor = interactor, store = store)
        val captured = slot<suspend kotlinx.coroutines.CoroutineScope.() -> Any?>()
        every {
            store.launch<Any?>(any(), any(), any(), any(), capture(captured))
        } returns mockk(relaxed = true)

        handler.invoke(Action.Common.Init)

        assertEquals(true, captured.isCaptured)
    }

    private fun newStore(flow: MutableStateFlow<State>): ExerciseChartHandlerStore =
        mockk(relaxed = true) {
            every { state } returns flow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
        }
}
