// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.fixtures.RecordingSettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SettingsClickHandlerTest {

    private val store = RecordingSettingsHandlerStore()
    private val handler = SettingsClickHandler(store)

    @Test
    fun `OnArchiveClick emits Haptic ContextClick then consumes OpenArchive`() {
        handler.invoke(Action.Click.OnArchiveClick)
        val captured = store.events.toList()
        assertEquals(1, captured.size)
        assertHaptic(captured.single(), HapticFeedbackType.ContextClick)
        assertEquals<List<Action>>(listOf(Action.Navigation.OpenArchive), store.consumedActions)
    }

    @Test
    fun `OnGitHubClick emits Haptic ContextClick then ShowExternalLink GitHub url`() {
        handler.invoke(Action.Click.OnGitHubClick)
        val captured = store.events.toList()
        assertEquals(2, captured.size)
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.GITHUB_URL), captured.last())
    }

    @Test
    fun `OnLicenseClick emits Haptic ContextClick then ShowExternalLink license url`() {
        handler.invoke(Action.Click.OnLicenseClick)
        val captured = store.events.toList()
        assertEquals(2, captured.size)
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.LICENSE_URL), captured.last())
    }

    @Test
    fun `OnPrivacyPolicyClick emits Haptic ContextClick then ShowExternalLink privacy url`() {
        handler.invoke(Action.Click.OnPrivacyPolicyClick)
        val captured = store.events.toList()
        assertEquals(2, captured.size)
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.PRIVACY_POLICY_URL), captured.last())
    }

    @Test
    fun `OnStartCardModeClick emits a haptic and opens the mode sheet`() {
        val stateFlow =
            MutableStateFlow(State.initial(backupAvailable = true, appVersion = "1.0.0", appVersionCode = 1))
        val statefulStore = RecordingSettingsHandlerStore(stateFlow)
        val statefulHandler = SettingsClickHandler(statefulStore)

        statefulHandler.invoke(Action.Click.OnStartCardModeClick)

        assertEquals(DialogState.StartCardModePicker, stateFlow.value.dialogState)
        val captured = statefulStore.events.toList()
        assertEquals(1, captured.size)
        assertHaptic(captured.single(), HapticFeedbackType.ContextClick)
    }

    @Test
    fun `OnStartCardModeClick while the frequency picker is up replaces the dialog`() {
        val stateFlow = MutableStateFlow(
            State.initial(backupAvailable = true, appVersion = "1.0.0", appVersionCode = 1).copy(
                dialogState = DialogState.SignOutConfirmation,
            ),
        )
        val statefulStore = RecordingSettingsHandlerStore(stateFlow)
        val statefulHandler = SettingsClickHandler(statefulStore)

        statefulHandler.invoke(Action.Click.OnStartCardModeClick)

        assertEquals(DialogState.StartCardModePicker, stateFlow.value.dialogState)
    }

    @Test
    fun `OnStartCardModeSheetDismiss hides the sheet and emits no haptic`() {
        val stateFlow = MutableStateFlow(
            State.initial(backupAvailable = true, appVersion = "1.0.0", appVersionCode = 1).copy(
                dialogState = DialogState.StartCardModePicker,
            ),
        )
        val statefulStore = RecordingSettingsHandlerStore(stateFlow)
        val statefulHandler = SettingsClickHandler(statefulStore)

        statefulHandler.invoke(Action.Click.OnStartCardModeSheetDismiss)

        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
        assertEquals(emptyList<Event>(), statefulStore.events)
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
