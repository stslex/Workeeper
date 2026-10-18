// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutCopyUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClickHandlerTest {

    private val chartLoader = RecordingChartLoader()
    private val resources = DayMonthOnlyResourceWrapper()

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
            ChartPointUiModel(LocalDate(2026, 4, 21), 0L, "session-1", 90.0, 1),
            ChartPointUiModel(LocalDate(2026, 4, 28), 0L, "session-2", 100.0, 1),
        ),
        activeIndex = activeIndex,
        readoutCopy = readoutCopy(1),
    )

    /** D3: the copy a load writes beside its points, in today's stubs: `label` and `plural`. */
    private fun readoutCopy(vararg setCounts: Int): ChartReadoutCopyUiModel = ChartReadoutCopyUiModel(
        heaviestWeightName = "label",
        volumePerSessionName = "label",
        volumePerSetName = "label",
        kgUnit = "label",
        repsUnit = "label",
        recordLabel = "label",
        setCountLabels = setCounts.toList().associateWith { "plural" }.toImmutableMap(),
    )

    @Test
    fun `OnPresetSelect to current preset is no-op`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(stateWithSelected().preset))

        assertTrue(store.events.isEmpty())
        assertTrue(store.consumed.isEmpty())
        assertTrue(chartLoader.loads.isEmpty())
    }

    @Test
    fun `OnPresetSelect changes state KEEPS emptyReason and delegates load`() {
        val flow = MutableStateFlow(
            stateWithSelected().copy(emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE),
        )
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.YEAR_1))

        assertEquals(ChartPresetUiModel.YEAR_1, flow.value.preset)
        assertTrue(flow.value.isLoading)
        // The exercise has not changed, so the reason stands until loadChart resolves the window.
        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertEquals(1, chartLoader.loads.count { it == benchExercise })
        assertTrue(store.events.isNotEmpty())
        val captured = store.events.last()
        assertEquals(HapticFeedbackType.SegmentTick, (captured as Event.HapticClick).type)
    }

    @Test
    fun `OnMetricSelect changes metric KEEPS emptyReason and delegates load`() {
        val flow = MutableStateFlow(
            stateWithSelected().copy(emptyReason = EmptyReason.NO_DATA_FOR_EXERCISE),
        )
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET))

        assertEquals(ChartMetricUiModel.VOLUME_PER_SET, flow.value.metric)
        // Same exercise — see the preset test above.
        assertEquals(EmptyReason.NO_DATA_FOR_EXERCISE, flow.value.emptyReason)
        assertEquals(1, chartLoader.loads.count { it == benchExercise })
    }

    @Test
    fun `OnPickerOpen sets isPickerOpen true`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPickerOpen)

        assertTrue(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerQueryChange stores the query and the window resets it both ways`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPickerOpen)
        handler.invoke(Action.Click.OnPickerQueryChange("тяг"))

        assertEquals("тяг", flow.value.pickerQuery)

        // Closing clears it: reopening on a forgotten filter looks like a list that lost rows.
        handler.invoke(Action.Click.OnPickerDismiss)
        assertEquals("", flow.value.pickerQuery)

        handler.invoke(Action.Click.OnPickerQueryChange("жим"))
        handler.invoke(Action.Click.OnPickerOpen)
        assertEquals("", flow.value.pickerQuery)
    }

    @Test
    fun `OnPickerDismiss sets isPickerOpen false`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPickerDismiss)

        assertFalse(flow.value.isPickerOpen)
    }

    @Test
    fun `OnPickerItemSelect to same exercise just dismisses picker`() {
        val flow = MutableStateFlow(stateWithSelected().copy(isPickerOpen = true))
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-1"))

        assertFalse(flow.value.isPickerOpen)
        assertEquals("uuid-1", flow.value.selectedExercise?.uuid)
        assertTrue(chartLoader.loads.isEmpty())
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
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnPickerItemSelect("uuid-2"))

        assertEquals("uuid-2", flow.value.selectedExercise?.uuid)
        assertFalse(flow.value.isPickerOpen)
        assertTrue(flow.value.isLoading)
        // Clears before loadChart finishes: dismissal must not flash not-found for the new pick.
        assertNull(flow.value.emptyReason)
        assertEquals(
            1,
            chartLoader.loads.count {
                it == ExercisePickerItemUiModel("uuid-2", "Squat", ExerciseTypeUiModel.WEIGHTED)
            },
        )
    }

    @Test
    fun `OnScrub to a new index moves the readout and ticks the haptic`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnScrub(0))

        assertEquals(0, flow.value.activeIndex)
        assertNotNull(flow.value.readout)
        assertTrue(store.events.isNotEmpty())
        val captured = store.events.last()
        assertEquals(HapticFeedbackType.SegmentTick, (captured as Event.HapticClick).type)
    }

    @Test
    fun `OnScrub can read both sessions completed on the same day`() {
        val day = LocalDate(2026, 4, 28)
        val flow = MutableStateFlow(
            stateWithSelected().copy(
                points = persistentListOf(
                    ChartPointUiModel(day, 0L, "morning", 80.0, 2),
                    ChartPointUiModel(day, 0L, "evening", 100.0, 1),
                ),
                activeIndex = 1,
                readoutCopy = readoutCopy(2, 1),
            ),
        )
        val handler = ClickHandler(chartLoader, resources, newStore(flow))

        handler.invoke(Action.Click.OnScrub(0))
        assertEquals("80", flow.value.readout?.value)

        handler.invoke(Action.Click.OnScrub(1))
        assertEquals("100", flow.value.readout?.value)
    }

    @Test
    fun `OnScrub to the current index is a no-op — the haptic is a tick per CROSSED point`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnScrub(1))

        assertTrue(store.events.isEmpty())
    }

    @Test
    fun `OnScrub outside the point range is ignored`() {
        val flow = MutableStateFlow(stateWithPoints(activeIndex = 1))
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnScrub(5))

        assertEquals(1, flow.value.activeIndex)
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun `OnEmptyCtaClick consumes OpenHome navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnEmptyCtaClick)

        assertTrue(store.consumed.count { it == Action.Navigation.OpenHome } >= 1)
    }

    @Test
    fun `OnBack consumes PopBack navigation`() {
        val flow = MutableStateFlow(stateWithSelected())
        val store = newStore(flow)
        val handler = ClickHandler(chartLoader, resources, store)

        handler.invoke(Action.Click.OnBack)

        assertTrue(store.consumed.count { it == Action.Navigation.PopBack } >= 1)
    }

    private fun newStore(flow: MutableStateFlow<State>): FakeClickHandlerStore = FakeClickHandlerStore(flow)

    /**
     * The store as the relaxed MockK store answered it: [state] is the identity's flow,
     * [updateState] applies to it, and [sendEvent] / [consume] record. Every other member
     * [ClickHandler] never reaches fails fast, [logger] included.
     */
    private class FakeClickHandlerStore(
        private val flow: MutableStateFlow<State>,
    ) : ExerciseChartHandlerStore {

        val events = mutableListOf<Event>()
        val consumed = mutableListOf<Action>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by ClickHandler")

        override val logger: Logger
            get() = error("logger must not be read by ClickHandler")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by ClickHandler")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
            error("updateStateImmediate must not be used by ClickHandler")

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate must not be used by ClickHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launch must not be used by ClickHandler")

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by ClickHandler")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by ClickHandler")
    }

    /** D2: records every reload [ClickHandler] asks for. */
    private class RecordingChartLoader : ChartLoader {

        val loads = mutableListOf<ExercisePickerItemUiModel>()

        override fun loadChart(exercise: ExercisePickerItemUiModel) {
            loads.add(exercise)
        }
    }

    /**
     * The scrub's caption reads only the date, which the relaxed mock answered with its default
     * `""`; the copy comes from State (D3), so every other member throws.
     */
    private class DayMonthOnlyResourceWrapper : ResourceWrapper {

        override fun getString(id: Int, vararg args: Any): String = error("not used")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("not used")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = error("not used")

        override fun formatDayMonth(timestamp: Long): String = ""
    }
}
