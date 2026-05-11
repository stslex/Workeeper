// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toConfirmation
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreConfirmationUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class BackupClickHandler @Inject constructor(
    private val interactor: BackupInteractor,
    @ApplicationContext private val context: Context,
    store: SettingsHandlerStore,
) : Handler<Action.Backup>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Backup) {
        when (action) {
            Action.Backup.ObserveAuth -> observeAuth()
            Action.Backup.SignIn -> signIn()
            is Action.Backup.HandleAuthResult -> handleAuthResult(action.resultIntent)
            Action.Backup.SignOut -> signOut()
            Action.Backup.CreateBackup -> createBackup()
            Action.Backup.RequestRestore -> requestRestore()
            Action.Backup.ConfirmRestore -> confirmRestore()
            Action.Backup.DismissRestoreDialog -> dismissRestoreDialog()
        }
    }

    private fun observeAuth() {
        interactor.authState
            .map { it.toUi() }
            .launch { ui ->
                updateState { current -> current.copy(backupAuth = ui) }
            }
    }

    private fun signIn() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.SigningIn) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applySignInOutcome(interactor.signIn())
        }
    }

    private fun handleAuthResult(resultIntent: android.content.Intent?) {
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applyCompleteSignInResult(interactor.completeSignIn(resultIntent))
        }
    }

    private fun signOut() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.SigningOut) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applyUnitResultAsIdle(interactor.signOut())
        }
    }

    private fun createBackup() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.CreatingBackup) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applyCreateBackupResult(interactor.createBackup())
        }
    }

    private fun requestRestore() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.FetchingBackups) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applyListLatestResult(interactor.listLatestBackup())
        }
    }

    private fun dismissRestoreDialog() {
        updateState { current -> current.copy(restoreConfirmation = null) }
    }

    private fun confirmRestore() {
        updateState { current ->
            current.copy(
                restoreConfirmation = null,
                backupOperation = BackupOperationUi.Restoring,
            )
        }
        launchDefault(
            onError = { emitUnknownErrorAndIdle() },
        ) {
            applyRestoreResult(interactor.restoreLatest())
        }
    }

    private fun applySignInOutcome(outcome: SignInOutcomeDomain) {
        when (outcome) {
            SignInOutcomeDomain.Success -> {
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
            }

            is SignInOutcomeDomain.NeedsResolution -> {
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.AuthResolutionRequested(outcome.intentSender))
            }

            is SignInOutcomeDomain.Failure -> {
                val errorUi = outcome.error.toUi()
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.ShowBackupError(errorUi))
            }
        }
    }

    private fun applyCompleteSignInResult(result: BackupResult<Unit>) {
        when (result) {
            is BackupResult.Success -> Unit // authState flow drives the UI update.
            is BackupResult.Failure -> {
                val errorUi = result.error.toUi()
                sendEvent(Event.ShowBackupError(errorUi))
            }
        }
    }

    private fun applyUnitResultAsIdle(result: BackupResult<Unit>) {
        val errorUi = (result as? BackupResult.Failure)?.let {
            it.error.toUi()
        }
        updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
        if (errorUi != null) sendEvent(Event.ShowBackupError(errorUi))
    }

    private fun applyCreateBackupResult(result: BackupResult<Unit>) {
        when (result) {
            is BackupResult.Success -> {
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.ShowBackupCreated)
            }

            is BackupResult.Failure -> {
                val errorUi = result.error.toUi()
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.ShowBackupError(errorUi))
            }
        }
    }

    private fun applyListLatestResult(result: BackupResult<BackupSummaryDomain?>) {
        when (result) {
            is BackupResult.Success -> {
                val summary = result.data
                if (summary == null) {
                    updateState { current ->
                        current.copy(backupOperation = BackupOperationUi.Idle)
                    }
                    sendEvent(Event.ShowBackupError(BackupErrorUi.NO_BACKUPS_FOUND))
                } else {
                    val confirmation: RestoreConfirmationUi = summary.toConfirmation(context)
                    updateState { current ->
                        current.copy(
                            backupOperation = BackupOperationUi.Idle,
                            restoreConfirmation = confirmation,
                        )
                    }
                }
            }

            is BackupResult.Failure -> {
                val errorUi = result.error.toUi()
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.ShowBackupError(errorUi))
            }
        }
    }

    private fun applyRestoreResult(result: BackupResult<Unit>) {
        when (result) {
            is BackupResult.Success -> sendEvent(Event.AppRestartRequested)
            is BackupResult.Failure -> {
                val errorUi = result.error.toUi()
                updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                sendEvent(Event.ShowBackupError(errorUi))
            }
        }
    }

    private fun emitUnknownErrorAndIdle() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
        sendEvent(Event.ShowBackupError(BackupErrorUi.UNKNOWN))
    }
}
