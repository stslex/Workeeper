// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SettingsClickHandlerTest {

    private val store = mockk<SettingsHandlerStore>(relaxed = true)
    private val handler = SettingsClickHandler(store)

    @Test
    fun `OnArchiveClick emits NavigateToArchive`() {
        handler.invoke(Action.Click.OnArchiveClick)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(Event.NavigateToArchive, captured.captured)
    }

    @Test
    fun `OnGitHubClick emits ShowExternalLink with GitHub url`() {
        handler.invoke(Action.Click.OnGitHubClick)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(Event.ShowExternalLink(AboutLinks.GITHUB_URL), captured.captured)
    }

    @Test
    fun `OnLicenseClick emits ShowExternalLink with license url`() {
        handler.invoke(Action.Click.OnLicenseClick)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(Event.ShowExternalLink(AboutLinks.LICENSE_URL), captured.captured)
    }

    @Test
    fun `OnPrivacyPolicyClick emits ShowExternalLink with privacy url`() {
        handler.invoke(Action.Click.OnPrivacyPolicyClick)
        val captured = slot<Event>()
        verify(exactly = 1) { store.sendEvent(capture(captured)) }
        assertEquals(Event.ShowExternalLink(AboutLinks.PRIVACY_POLICY_URL), captured.captured)
    }
}
