// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
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

internal class ClickHandlerTest {

    private val interactor = mockk<ExerciseChartInteractor>(relaxed = true)
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

    @Test
    fun `OnPresetSelect to current preset is no-op`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.MONTHS_3))

        verify(exactly = 0) { store.sendEvent(any()) }
        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnPresetSelect changes state and emits haptic`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.YEAR_1))

        assertEquals(ChartPresetUiModel.YEAR_1, flow.value.preset)
        assertTrue(flow.value.isLoading)
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.SegmentTick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnMetricSelect changes metric and clears tooltip`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET))

        assertEquals(ChartMetricUiModel.VOLUME_PER_SET, flow.value.metric)
        assertNull(flow.value.activeTooltip)
    }

    @Test
    fun `OnPickerOpen sets isPickerOpen true`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPickerOpen)

        assertTrue(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerDismiss sets isPickerOpen false`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPickerDismiss)

        assertFalse(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerItemSelect to same exercise just dismisses picker`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-1"))

        assertFalse(flow.value.isPickerOpen)
        // selection unchanged
        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
    }

    @Test
    fun `OnPickerItemSelect to different exercise updates selection`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-2"))

        assertEquals("uuid-2", flow.value.selectedExercise?.uuid)
        assertFalse(flow.value.isPickerOpen)
        assertTrue(flow.value.isLoading)
    }

    @Test
    fun `OnPointTap sets activeTooltip`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)
        val point = ChartPointUiModel(
            day = LocalDate.of(2026, 4, 28),
            dayMillis = 0L,
            value = 100.0,
            sessionUuid = "session-1",
            weight = 100.0,
            reps = 5,
            setCount = 1,
        )

        handler.invoke(Action.Click.OnPointTap(point))

        val tooltip = flow.value.activeTooltip
        assertEquals("session-1", tooltip?.sessionUuid)
    }

    @Test
    fun `OnTooltipDismiss clears activeTooltip`() {
        val tooltip = stateWithSelected().copy(
            activeTooltip = io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel(
                sessionUuid = "session-1",
                exerciseName = "Bench",
                dateLabel = "today",
                displayLabel = "100kg × 5",
                setCountLabel = null,
            ),
        )
        val flow = MutableStateFlow(tooltip)
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnTooltipDismiss)

        assertNull(flow.value.activeTooltip)
    }

    @Test
    fun `OnTooltipTap consumes OpenPastSession with the tooltip session uuid`() {
        val tooltip = stateWithSelected().copy(
            activeTooltip = io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel(
                sessionUuid = "session-99",
                exerciseName = "Bench",
                dateLabel = "today",
                displayLabel = "100kg × 5",
                setCountLabel = null,
            ),
        )
        val flow = MutableStateFlow(tooltip)
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnTooltipTap)

        verify { store.consume(Action.Navigation.OpenPastSession("session-99")) }
    }

    @Test
    fun `OnEmptyCtaClick consumes OpenHome navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

        handler.invoke(Action.Click.OnEmptyCtaClick)

        verify { store.consume(Action.Navigation.OpenHome) }
    }

    @Test
    fun `OnBack consumes PopBack navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(interactor, resources, store)

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
