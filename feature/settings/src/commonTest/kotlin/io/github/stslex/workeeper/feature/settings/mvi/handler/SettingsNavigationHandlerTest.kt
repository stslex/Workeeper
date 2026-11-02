// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.settings.fixtures.RecordingNavigator
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SettingsNavigationHandlerTest {

    private val navigator = RecordingNavigator()
    private val handler = SettingsNavigationHandler(navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `OpenArchive navigates to Screen Archive`() {
        handler.invoke(Action.Navigation.OpenArchive)
        assertEquals<List<Screen>>(listOf(Screen.Archive), navigator.screens)
    }
}
