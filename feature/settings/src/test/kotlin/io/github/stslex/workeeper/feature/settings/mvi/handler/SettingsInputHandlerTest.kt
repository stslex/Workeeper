// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SettingsInputHandlerTest {

    private val interactor = mockk<SettingsInteractor>(relaxed = true)
    private val initialState = State.initial(appVersion = "1.0.0", appVersionCode = 1)
    private val stateFlow = MutableStateFlow(initialState)
    private val store = mockk<SettingsHandlerStore>(relaxed = true).apply {
        every { state } returns stateFlow
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            stateFlow.value = update(stateFlow.value)
        }
    }
    private val handler = SettingsInputHandler(interactor, store)

    @Test
    fun `OnThemeChange emits ContextClick haptic and updates state`() {
        handler.invoke(Action.Input.OnThemeChange(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, stateFlow.value.themeMode)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        val event = captured.captured
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(HapticFeedbackType.ContextClick, (event as Event.Haptic).type)
    }

    @Test
    fun `OnStartCardModeChange closes the sheet and persists the MAPPED mode`() {
        stateFlow.value = initialState.copy(dialogState = DialogState.StartCardModePicker)
        // The persistence call and the UI→domain mapping live inside the launched coroutine;
        // this store RUNS it, so a swapped mapper arm or a dropped setStartCardMode call reds.
        every {
            store.launch(any(), any(), any(), any(), any<suspend CoroutineScope.() -> Unit>())
        } answers {
            runBlocking { arg<suspend CoroutineScope.() -> Unit>(4).invoke(this) }
            mockk(relaxed = true)
        }

        handler.invoke(Action.Input.OnStartCardModeChange(StartCardModeUi.LAGGING_GROUPS))

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        // No optimistic write: the row's sub-line and the sheet's check follow DataStore.
        assertEquals(null, stateFlow.value.startCardMode)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertTrue(captured.captured is Event.Haptic)
        coVerify(exactly = 1) {
            interactor.setStartCardMode(StartCardModeDomain.LAGGING_GROUPS)
        }
    }
}
