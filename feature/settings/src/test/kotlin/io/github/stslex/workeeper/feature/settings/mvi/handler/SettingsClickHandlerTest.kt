// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SettingsClickHandlerTest {

    private val store = mockk<SettingsHandlerStore>(relaxed = true)
    private val handler = SettingsClickHandler(store)

    @Test
    fun `OnArchiveClick emits Haptic ContextClick then consumes OpenArchive`() {
        handler.invoke(Action.Click.OnArchiveClick)
        val captured = mutableListOf<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.single(), HapticFeedbackType.ContextClick)
        verify(exactly = 1) { store.consume(Action.Navigation.OpenArchive) }
    }

    @Test
    fun `OnGitHubClick emits Haptic ContextClick then ShowExternalLink GitHub url`() {
        handler.invoke(Action.Click.OnGitHubClick)
        val captured = mutableListOf<Event>()
        verify(exactly = 2) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.GITHUB_URL), captured.last())
    }

    @Test
    fun `OnLicenseClick emits Haptic ContextClick then ShowExternalLink license url`() {
        handler.invoke(Action.Click.OnLicenseClick)
        val captured = mutableListOf<Event>()
        verify(exactly = 2) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.LICENSE_URL), captured.last())
    }

    @Test
    fun `OnPrivacyPolicyClick emits Haptic ContextClick then ShowExternalLink privacy url`() {
        handler.invoke(Action.Click.OnPrivacyPolicyClick)
        val captured = mutableListOf<Event>()
        verify(exactly = 2) { store.sendEvent(capture(captured)) }
        assertHaptic(captured.first(), HapticFeedbackType.ContextClick)
        assertEquals(Event.ShowExternalLink(AboutLinks.PRIVACY_POLICY_URL), captured.last())
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
