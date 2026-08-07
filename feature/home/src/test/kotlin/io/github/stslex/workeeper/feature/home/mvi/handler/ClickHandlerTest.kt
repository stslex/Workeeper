// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.emptyPagingState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val baseState = emptyPagingState().copy(isActiveLoaded = true)
    private val interactor = mockk<HomeInteractor>(relaxed = true)
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `OnSettingsClick consumes OpenSettings navigation and emits HapticClick`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnSettingsClick)

        verify(exactly = 1) { store.consume(Action.Navigation.OpenSettings) }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(true, captured.captured is Event.HapticClick)
    }

    @Test
    fun `OnActiveSessionClick is a no-op when no active session`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid`() {
        val active = State.ActiveSessionInfo(
            sessionUuid = "session-7",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            startedAt = 0L,
            doneCount = 1,
            totalCount = 3,
            elapsedDurationLabel = "00:10",
        )
        val store = newStore(baseState.copy(activeSession = active))
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnActiveSessionClick)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-7"))
        }
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(HapticFeedbackType.ContextClick, (captured.captured as Event.HapticClick).type)
    }

    @Test
    fun `OnRecentSessionClick consumes OpenPastSession with the session uuid`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnRecentSessionClick(sessionUuid = "session-22"))

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenPastSession(sessionUuid = "session-22"))
        }
    }

    @Test
    fun `OnPickerSeeAllClick hides picker and consumes OpenAllTrainings`() {
        val visiblePicker = BottomSheetState.TrainingPicker(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val store = newStore(baseState.copy(bottomSheet = visiblePicker))
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnPickerSeeAllClick)

        verify(exactly = 1) { store.consume(Action.Navigation.OpenAllTrainings) }
    }

    @Test
    fun `OnModeLabelClick opens the mode sheet with a haptic`() {
        val flow = MutableStateFlow(baseState)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnModeLabelClick)

        assertEquals(BottomSheetState.StartModePicker, flow.value.bottomSheet)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(true, captured.captured is Event.HapticClick)
    }

    @Test
    fun `OnModeLabelClick while the training picker is up replaces the sheet`() {
        val flow = MutableStateFlow(
            baseState.copy(
                bottomSheet = BottomSheetState.TrainingPicker(
                    templates = kotlinx.collections.immutable.persistentListOf(),
                    isLoading = false,
                ),
            ),
        )
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnModeLabelClick)

        assertEquals(BottomSheetState.StartModePicker, flow.value.bottomSheet)
    }

    @Test
    fun `OnModeSelected hides the sheet and persists the MAPPED mode through the interactor`() {
        val flow = MutableStateFlow(baseState.copy(bottomSheet = BottomSheetState.StartModePicker))
        // A store whose launch actually RUNS its body: the persistence call and the UI→domain
        // mapping live inside that coroutine, and a stubbed no-op launch would wave a swapped
        // mapper arm straight through.
        val store = newStoreWithFlow(flow, executeLaunch = true)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnModeSelected(StartCardModeUi.LAGGING_GROUPS))

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
        // startCardMode is NOT written here — the DataStore round trip owns it, so head and
        // body swap together when the new mode's first readout lands.
        assertEquals(StartCardModeUi.WEEK, flow.value.startCardMode)
        coVerify(exactly = 1) {
            interactor.setStartCardMode(StartCardModeDomain.LAGGING_GROUPS)
        }
    }

    @Test
    fun `OnModeSheetDismiss hides the sheet without a haptic`() {
        val flow = MutableStateFlow(baseState.copy(bottomSheet = BottomSheetState.StartModePicker))
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnModeSheetDismiss)

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnPickerTrainingSelected hides the picker before resolving conflict`() {
        val visiblePicker = BottomSheetState.TrainingPicker(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val flow = MutableStateFlow(baseState.copy(bottomSheet = visiblePicker))
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnPickerTrainingSelected(trainingUuid = "tpl-1"))

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
    }

    @Test
    fun `OnStartForgottenTraining emits a haptic and starts the conflict resolution`() {
        val store = newStore(baseState)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnStartForgottenTraining(trainingUuid = "tpl-9"))

        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(true, captured.captured is Event.HapticClick)
        // The resolution itself runs inside the launched coroutine (stubbed here); what this
        // pins is that the action goes through the resolver path, not straight to navigation.
        verify(exactly = 1) {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Unit>())
        }
    }

    @Test
    fun `OnConflictDismiss clears pendingConflict`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnConflictDismiss)

        assertEquals(null, flow.value.pendingConflict)
    }

    @Test
    fun `OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(interactor = interactor, resourceWrapper = resources, store = store)

        handler.invoke(Action.Click.OnConflictResume)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-1"))
        }
        assertEquals(null, flow.value.pendingConflict)
    }

    private fun newStore(state: State): HomeHandlerStore =
        newStoreWithFlow(MutableStateFlow(state))

    private fun newStoreWithFlow(
        flow: MutableStateFlow<State>,
        executeLaunch: Boolean = false,
    ): HomeHandlerStore =
        mockk(relaxed = true) {
            every { this@mockk.state } returns flow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                flow.value = update(flow.value)
            }
            every {
                launch(
                    any(),
                    any(),
                    any(),
                    any(),
                    any<suspend CoroutineScope.() -> Unit>(),
                )
            } answers {
                if (executeLaunch) {
                    runBlocking { arg<suspend CoroutineScope.() -> Unit>(4).invoke(this) }
                }
                mockk(relaxed = true)
            }
        }
}
