// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State

internal interface SettingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val themeMode: ThemeMode,
        val appVersion: String,
        val appVersionCode: Int,
    ) : Store.State {

        companion object {

            fun initial(
                appVersion: String,
                appVersionCode: Int,
            ): State = State(
                themeMode = ThemeMode.SYSTEM,
                appVersion = appVersion,
                appVersionCode = appVersionCode,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Click : Action {

            data object OnArchiveClick : Click
            data object OnGitHubClick : Click
            data object OnLicenseClick : Click
            data object OnPrivacyPolicyClick : Click
        }

        sealed interface Input : Action {

            data class OnThemeChange(val mode: ThemeMode) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            data object OpenArchive : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class ShowExternalLink(val url: String) : Event

        data class Haptic(val type: HapticFeedbackType) : Event
    }
}
