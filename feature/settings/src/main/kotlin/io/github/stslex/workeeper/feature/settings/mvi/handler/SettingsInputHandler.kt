// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.mapper.ThemeModeMapper.toDomain
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class SettingsInputHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    store: SettingsHandlerStore,
) : Handler<Action.Input>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnThemeChange -> {
                sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
                updateState { it.copy(themeMode = action.mode) }
                launch { interactor.setThemeMode(action.mode.toDomain()) }
            }
        }
    }
}
