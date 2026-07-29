// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotNull

internal class ClickHandlerTest {

    private val commonHandler = mockk<CommonHandler>(relaxed = true)
    private val resources = mockk<ResourceWrapper>(relaxed = true).apply {
        every { getString(any(), *anyVararg()) } returns "label"
        every { formatMediumDate(any()) } returns "date"
        every { getQuantityString(any(), any(), *anyVararg()) } returns "plural"
    }

    private val benchExercise = ExercisePickerItemUiModel(
        uuid = "uuid-1",
        name = "Bench",
        type = ExerciseTypeUiModel.WEIGHTED,
    )

    private fun stateWithSelected(): State = State.create(initialUuid = "uuid-1").copy(
        isLoading = false,
        selectedExercise = benchExercise,
        recentExercises = persistentListOf(
            benchExercise,
            ExercisePickerItemUiModel("uuid-2", "Squat", ExerciseTypeUiModel.WEIGHTED),
        ),
    )

    private fun stateWithPoints(activeIndex: Int): State = stateWithSelected().copy(
        points = persistentListOf(
            ChartPointUiModel(LocalDate.of(2026, 4, 21), 0L, 90.0, 1),
            ChartPointUiModel(LocalDate.of(2026, 4, 28), 0L, 100.0, 1),
        ),
        activeIndex = activeIndex,
    )

    @Test
    fun `OnPresetSelect to current preset is no-op`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(stateWithSelected().preset))

        verify(exactly = 0) { store.sendEvent(any()) }
        verify(exactly = 0) { store.consume(any()) }
        verify(exactly = 0) { commonHandler.loadChart(any()) }
    }

    @Test
    fun `OnPresetSelect changes state, KEEPS emptyReason, and delegates load`() {
        val flow = MutableStateFlow(
            stateWithSelected().copy(emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE),
        )
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.YEAR_1))

        assertEquals(ChartPresetUiModel.YEAR_1, flow.value.preset)
        assertTrue(flow.value.isLoading)
        // The reason describes THIS exercise and the exercise has not changed, so it
        // stands until loadChart resolves the new window. Clearing it here is what used
        // to drop the screen out of its resolved empty state mid-reload — see
        // ChartContentResolutionTest.
        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        verify(exactly = 1) { commonHandler.loadChart(benchExercise) }
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.SegmentTick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnMetricSelect changes metric, KEEPS emptyReason, and delegates load`() {
        val flow = MutableStateFlow(
            stateWithSelected().copy(emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE),
        )
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET))

        assertEquals(ChartMetricUiModel.VOLUME_PER_SET, flow.value.metric)
        // Same exercise — see the preset test above.
        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        verify(exactly = 1) { commonHandler.loadChart(benchExercise) }
    }

    @Test
    fun `OnPickerOpen sets isPickerOpen true`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPickerOpen)

        assertTrue(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerDismiss sets isPickerOpen false`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPickerDismiss)

        assertFalse(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerItemSelect to same exercise just dismisses picker`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-1"))

        assertFalse(flow.value.isPickerOpen)
        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        verify(exactly = 0) { commonHandler.loadChart(any()) }
    }

    @Test
    fun `OnPickerItemSelect to different exercise clears emptyReason and triggers load`() {
        val flow = MutableStateFlow(
            stateWithSelected().copy(
                isPickerOpen = true,
                emptyReason = EmptyReason.EXERCISE_NOT_FOUND,
            ),
        )
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-2"))

        assertEquals("uuid-2", flow.value.selectedExercise?.uuid)
        assertFalse(flow.value.isPickerOpen)
        assertTrue(flow.value.isLoading)
        // EXERCISE_NOT_FOUND clears immediately, before loadChart finishes — picker
        // dismissal must not flash the not-found state for the new selection.
        assertNull(flow.value.emptyReason)
        verify(exactly = 1) {
            commonHandler.loadChart(
                ExercisePickerItemUiModel("uuid-2", "Squat", ExerciseTypeUiModel.WEIGHTED),
            )
        }
    }

    @Test
    fun `OnScrub to a new index moves the readout and ticks the haptic`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnScrub(0))

        assertEquals(0, flow.value.activeIndex)
        assertNotNull(flow.value.readout)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.SegmentTick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnScrub to the current index is a no-op — the haptic is a tick per CROSSED point`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnScrub(1))

        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnScrub outside the point range is ignored`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnScrub(5))

        assertEquals(1, flow.value.activeIndex)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnEmptyCtaClick consumes OpenHome navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnEmptyCtaClick)

        verify { store.consume(Action.Navigation.OpenHome) }
    }

    @Test
    fun `OnBack consumes PopBack navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(commonHandler, resources, store)

        handler.invoke(Action.Click.OnBack)

        verify { store.consume(Action.Navigation.PopBack) }
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
