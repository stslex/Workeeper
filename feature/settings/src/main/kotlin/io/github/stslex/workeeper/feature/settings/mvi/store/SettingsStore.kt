// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreConfirmationUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State

internal interface SettingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val themeMode: ThemeMode,
        val appVersion: String,
        val appVersionCode: Int,
        val backupAuth: BackupAuthUi,
        val backupOperation: BackupOperationUi,
        val restoreConfirmation: RestoreConfirmationUi?,
    ) : Store.State {

        companion object {

            fun initial(
                appVersion: String,
                appVersionCode: Int,
            ): State = State(
                themeMode = ThemeMode.SYSTEM,
                appVersion = appVersion,
                appVersionCode = appVersionCode,
                backupAuth = BackupAuthUi.NotAuthenticated,
                backupOperation = BackupOperationUi.Idle,
                restoreConfirmation = null,
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

        sealed interface Backup : Action {

            data object ObserveAuth : Backup
            data object SignIn : Backup
            data class HandleAuthResult(val resultIntent: Intent?) : Backup
            data object SignOut : Backup
            data object CreateBackup : Backup
            data object RequestRestore : Backup
            data object ConfirmRestore : Backup
            data object DismissRestoreDialog : Backup
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class ShowExternalLink(val url: String) : Event

        data class Haptic(val type: HapticFeedbackType) : Event

        data class AuthResolutionRequested(val intentSender: IntentSender) : Event

        data class ShowBackupError(val error: BackupErrorUi) : Event

        data object ShowBackupCreated : Event

        data object AppRestartRequested : Event
    }
}
