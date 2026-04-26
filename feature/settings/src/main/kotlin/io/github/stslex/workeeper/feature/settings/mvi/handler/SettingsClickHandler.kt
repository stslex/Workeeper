// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.about.AboutLinks
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class SettingsClickHandler @Inject constructor(
    store: SettingsHandlerStore,
) : Handler<Action.Click>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        when (action) {
            Action.Click.OnArchiveClick -> consume(Action.Navigation.OpenArchive)
            Action.Click.OnGitHubClick -> sendEvent(Event.ShowExternalLink(AboutLinks.GITHUB_URL))
            Action.Click.OnLicenseClick -> sendEvent(Event.ShowExternalLink(AboutLinks.LICENSE_URL))
            Action.Click.OnPrivacyPolicyClick -> sendEvent(
                Event.ShowExternalLink(AboutLinks.PRIVACY_POLICY_URL),
            )
        }
    }
}
