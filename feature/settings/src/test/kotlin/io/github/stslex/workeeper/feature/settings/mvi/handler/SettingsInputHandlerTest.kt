// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SettingsInputHandlerTest {

    private val interactor = mockk<SettingsInteractor>(relaxed = true)
    private val initialState = State(
        themeMode = ThemeMode.SYSTEM,
        appVersion = "1.0.0",
        appVersionCode = 1,
    )
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
}
