// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event

@SingleIn(SettingsScope::class)
internal class SettingsClickHandler @Inject constructor(
    store: SettingsHandlerStore,
) : Handler<Action.Click>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        // Dismiss carries no haptic (the cancel/dismiss convention); every other click does.
        if (action != Action.Click.OnStartCardModeSheetDismiss) {
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        }
        when (action) {
            Action.Click.OnStartCardModeClick -> updateState {
                it.copy(dialogState = DialogState.StartCardModePicker)
            }

            Action.Click.OnStartCardModeSheetDismiss -> updateState {
                it.copy(dialogState = DialogState.Hidden)
            }

            Action.Click.OnArchiveClick -> consume(Action.Navigation.OpenArchive)
            Action.Click.OnGitHubClick -> sendEvent(Event.ShowExternalLink(AboutLinks.GITHUB_URL))
            Action.Click.OnLicenseClick -> sendEvent(Event.ShowExternalLink(AboutLinks.LICENSE_URL))
            Action.Click.OnPrivacyPolicyClick -> sendEvent(
                Event.ShowExternalLink(AboutLinks.PRIVACY_POLICY_URL),
            )
        }
    }
}
