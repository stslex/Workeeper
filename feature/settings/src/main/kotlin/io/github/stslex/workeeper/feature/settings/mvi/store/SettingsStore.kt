// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.store

import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedCountsUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State

interface SettingsStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val themeMode: ThemeMode,
        val appVersion: String,
        val appVersionCode: Int,
        val backupAuth: BackupAuthUi,
        val backupOperation: BackupOperationUi,
        val dialogState: DialogState,
        val backupInfo: BackupInfoUi,
        val backupPreferences: BackupPreferencesUi?,
        val restoreProgress: RestoreProgressUi,
        val canRevertLastRestore: Boolean,

        /**
         * The Archive row's drawn sub-line, or null until the counts arrive.
         *
         * §26 draws it («4 упражнения · 1 тренировка»). B15 held it open on the premise that no data
         * source existed; both counts have been `Flow<Int>` since the archive screen was built.
         */
        val archivedCounts: ArchivedCountsUi?,
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
                dialogState = DialogState.Hidden,
                backupInfo = BackupInfoUi.Unknown,
                backupPreferences = null,
                restoreProgress = RestoreProgressUi.Idle,
                canRevertLastRestore = false,
                archivedCounts = null,
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

            data object RestartApp : Navigation
        }

        sealed interface Backup : Action {

            data object ObserveAuth : Backup
            data object ObservePreferences : Backup
            data object ObserveRestoreState : Backup
            data object RequestRevertLastRestore : Backup
            data object SignIn : Backup
            data class HandleAuthResult(val resultIntent: Intent?) : Backup
            data object RequestSignOut : Backup
            data object ConfirmSignOut : Backup
            data object DismissSignOutConfirmation : Backup
            data object CreateBackup : Backup
            data object RequestRestore : Backup
            data object ConfirmRestore : Backup
            data object DismissRestoreDialog : Backup
            data object LoadBackupList : Backup
            data object OpenFrequencyPicker : Backup
            data object DismissFrequencyPicker : Backup
            data class SaveFrequency(
                val schedule: BackupScheduleUi,
                val allowOnMobileData: Boolean,
            ) : Backup

            data class UpdateFrequencyPickerSelection(
                val schedule: BackupScheduleUi,
                val allowOnMobileData: Boolean,
            ) : Backup

            data class ToggleAiExport(val enabled: Boolean) : Backup
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class ShowExternalLink(val url: String) : Event

        data class Haptic(val type: HapticFeedbackType) : Event

        data class AuthResolutionRequested(val intentSender: IntentSender) : Event

        data class ShowBackupError(val error: BackupErrorUi) : Event

        data object ShowBackupCreated : Event

        data object ShowAutoBackupEnabledSnackbarRequested : Event

        data object ShowAiExportAccessNeeded : Event
    }
}
