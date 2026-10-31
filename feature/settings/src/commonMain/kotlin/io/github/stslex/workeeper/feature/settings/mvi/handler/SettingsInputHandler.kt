// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.mapper.StartCardModeMapper.toDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.ThemeModeMapper.toDomain
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event

@SingleIn(SettingsScope::class)
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

            is Action.Input.OnStartCardModeChange -> {
                sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
                updateState { it.copy(dialogState = DialogState.Hidden) }
                // No optimistic state write: the row's sub-line and the sheet's check both
                // follow the DataStore round trip, same single-source rule as Home's card.
                launch { interactor.setStartCardMode(action.mode.toDomain()) }
            }
        }
    }
}
