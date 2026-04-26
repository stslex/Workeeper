// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

@Suppress("MviHandlerConstructorRule")
internal class SettingsNavigationHandler(
    private val navigator: Navigator,
) : SettingsComponent(), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.OnBackClick -> navigator.popBack()
        }
    }
}
