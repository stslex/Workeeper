// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toConfirmation
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
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
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    SignInOutcomeDomain.Success -> {
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                    }

                    is SignInOutcomeDomain.NeedsResolution -> {
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.AuthResolutionRequested(result.intentSender))
                    }

                    is SignInOutcomeDomain.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.signIn()
        }
    }

    private fun handleAuthResult(resultIntent: android.content.Intent?) {
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> {
                        logger.i { "Sign-in successful for account: ${result.data}" }
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.completeSignIn(resultIntent)
        }
    }

    private fun signOut() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.SigningOut) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                val errorUi = (result as? BackupResult.Failure)?.error?.toUi()
                updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                if (errorUi != null) sendEvent(Event.ShowBackupError(errorUi))
            },
        ) {
            interactor.signOut()
        }
    }

    private fun createBackup() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.CreatingBackup) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> {
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.ShowBackupCreated)
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateState { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.createBackup()
        }
    }

    private fun requestRestore() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.FetchingBackups) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> {
                        val summary = result.data
                        if (summary == null) {
                            updateStateImmediate { current ->
                                current.copy(backupOperation = BackupOperationUi.Idle)
                            }
                            sendEvent(Event.ShowBackupError(BackupErrorUi.NO_BACKUPS_FOUND))
                        } else {
                            val confirmation = summary.toConfirmation(context)
                            updateStateImmediate { current ->
                                current.copy(
                                    backupOperation = BackupOperationUi.Idle,
                                    restoreConfirmation = confirmation,
                                )
                            }
                        }
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.listLatestBackup()
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
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> sendEvent(Event.AppRestartRequested)
                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.restoreLatest()
        }
    }

    private suspend fun emitUnknownErrorAndIdle(e: Throwable) {
        logger.e(e, "Unknown error during backup operation")
        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
        sendEvent(Event.ShowBackupError(BackupErrorUi.UNKNOWN))
    }
}
