// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.settings.fixtures.RecordingSettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.fixtures.RecordingSettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SettingsInputHandlerTest {

    private val interactor = RecordingSettingsInteractor()
    private val initialState = State.initial(backupAvailable = true, appVersion = "1.0.0", appVersionCode = 1)
    private val stateFlow = MutableStateFlow(initialState)
    private val store = RecordingSettingsHandlerStore(stateFlow)
    private val handler = SettingsInputHandler(interactor, store)

    @Test
    fun `OnThemeChange emits ContextClick haptic and updates state`() {
        handler.invoke(Action.Input.OnThemeChange(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, stateFlow.value.themeMode)
        val captured = store.events.toList()
        assertEquals(1, captured.size)
        val event = captured.single()
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(HapticFeedbackType.ContextClick, (event as Event.Haptic).type)
    }

    @Test
    fun `OnStartCardModeChange closes the sheet and persists the MAPPED mode`() {
        stateFlow.value = initialState.copy(dialogState = DialogState.StartCardModePicker)
        // The persistence call and the UI→domain mapping live inside the launched coroutine;
        // this store RUNS it, so a swapped mapper arm or a dropped setStartCardMode call reds.

        handler.invoke(Action.Input.OnStartCardModeChange(StartCardModeUi.LAGGING_GROUPS))

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        // No optimistic write: the row's sub-line and the sheet's check follow DataStore.
        assertEquals(null, stateFlow.value.startCardMode)
        val captured = store.events.toList()
        assertEquals(1, captured.size)
        assertTrue(captured.single() is Event.Haptic)
        assertEquals(listOf(StartCardModeDomain.LAGGING_GROUPS), interactor.modes)
    }
}
