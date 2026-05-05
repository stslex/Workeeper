// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

internal sealed interface SettingsBodyAction {

    data object BackClick : SettingsBodyAction

    data object ArchiveClick : SettingsBodyAction

    data object GitHubClick : SettingsBodyAction

    data object LicenseClick : SettingsBodyAction

    data object PrivacyPolicyClick : SettingsBodyAction

    data class ThemeChange(val mode: ThemeMode) : SettingsBodyAction
}

internal fun SettingsBodyAction.toAction(): Action = when (this) {
    SettingsBodyAction.BackClick -> Action.Navigation.Back
    SettingsBodyAction.ArchiveClick -> Action.Click.OnArchiveClick
    SettingsBodyAction.GitHubClick -> Action.Click.OnGitHubClick
    SettingsBodyAction.LicenseClick -> Action.Click.OnLicenseClick
    SettingsBodyAction.PrivacyPolicyClick -> Action.Click.OnPrivacyPolicyClick
    is SettingsBodyAction.ThemeChange -> Action.Input.OnThemeChange(mode)
}
