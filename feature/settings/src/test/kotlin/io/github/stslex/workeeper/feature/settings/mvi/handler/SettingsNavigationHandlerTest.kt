// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class SettingsNavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = SettingsNavigationHandler(navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenArchive navigates to Screen Archive`() {
        handler.invoke(Action.Navigation.OpenArchive)
        verify(exactly = 1) { navigator.navTo(Screen.Archive) }
    }
}
