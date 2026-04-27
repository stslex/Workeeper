// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    store: HomeHandlerStore,
) : Handler<Action.Click>, HomeHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnActiveSessionClick -> processSessionClick()
            Action.Click.OnSettingsClick -> processSettingsClick()
        }
    }

    private fun processSessionClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val session = state.value.activeSession ?: return
        consume(Action.Navigation.OpenLiveWorkout(session.sessionUuid))
    }

    private fun processSettingsClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSettings)
    }
}
