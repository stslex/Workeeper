// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupDateMapper
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupPreferencesUiMapper
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupPreferencesUiMapper.toDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toConfirmation
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class BackupClickHandler @Inject constructor(
    private val interactor: BackupInteractor,
    private val preferencesRepository: BackupPreferencesRepository,
    private val autoBackupController: AutoBackupController,
    private val restoreStateRepository: RestoreStateRepository,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val appDialogPublisher: AppDialogPublisher,
    @ApplicationContext private val context: Context,
    store: SettingsHandlerStore,
) : Handler<Action.Backup>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Backup) {
        when (action) {
            Action.Backup.ObserveAuth -> observeAuth()
            Action.Backup.ObservePreferences -> observePreferences()
            Action.Backup.ObserveRestoreState -> observeRestoreState()
            Action.Backup.RequestRevertLastRestore -> requestRevertLastRestore()
            Action.Backup.SignIn -> signIn()
            is Action.Backup.HandleAuthResult -> handleAuthResult(action.resultIntent)
            Action.Backup.RequestSignOut -> requestSignOut()
            Action.Backup.DismissSignOutConfirmation -> dismissSignOutConfirmation()
            Action.Backup.ConfirmSignOut -> confirmSignOut()
            Action.Backup.CreateBackup -> createBackup()
            Action.Backup.RequestRestore -> requestRestore()
            Action.Backup.ConfirmRestore -> confirmRestore()
            Action.Backup.DismissRestoreDialog -> dismissRestoreDialog()
            Action.Backup.LoadBackupList -> loadBackupList()
            Action.Backup.OpenFrequencyPicker -> openFrequencyPicker()
            Action.Backup.DismissFrequencyPicker -> dismissFrequencyPicker()
            is Action.Backup.SaveFrequency -> saveFrequency(
                action.schedule,
                action.allowOnMobileData,
            )

            is Action.Backup.UpdateFrequencyPickerSelection -> updateFrequencyPickerSelection(
                action.schedule,
                action.allowOnMobileData,
            )

            is Action.Backup.ToggleAiExport -> toggleAiExport(action.enabled)
        }
    }

    /**
     * Visibility for the "Revert last restore" row needs **both** the
     * `pre_restore_backup_available` DataStore flag (intent: undo slot was set
     * by the last successful Restore) and the actual `cache/pre_restore_backup.db`
     * file. The file lives in `cacheDir`, which Android can reclaim under
     * storage pressure — the flag would survive while the file is gone, and a
     * row tap would land in a silent-failure path inside the coordinator.
     *
     * Combining both signals here hides the row immediately when the file is
     * evicted, and self-heals the DataStore flag to keep the two sources in
     * sync. Defence-in-depth still lives in
     * `RestoreRecoveryCoordinator.performUndoRestore` for the race window
     * between observation and tap.
     */
    private fun observeRestoreState() {
        restoreStateRepository.observePreRestoreBackupAvailable()
            .launch { flagged ->
                val fileExists = snapshotProvider.hasPreRestoreBackup()
                if (flagged && !fileExists) {
                    restoreStateRepository.clearPreRestoreBackupAvailable()
                }
                updateState { current ->
                    current.copy(canRevertLastRestore = flagged && fileExists)
                }
            }
    }

    private fun requestRevertLastRestore() {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to publish UndoRestoreConfirmation") },
            onSuccess = { },
        ) {
            val originalDate = restoreStateRepository.getPreRestoreOriginalDate()
                ?: return@launchDefault
            appDialogPublisher.publish(
                AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = originalDate),
            )
        }
    }

    private fun observeAuth() {
        interactor.authState
            .map { it.toUi() }
            .launch { ui ->
                val previousAuth = state.value.backupAuth
                updateState { current -> current.copy(backupAuth = ui) }
                when {
                    ui is BackupAuthUi.Authenticated &&
                        previousAuth !is BackupAuthUi.Authenticated -> {
                        loadBackupList()
                        bootstrapOrRehydrate()
                    }

                    ui is BackupAuthUi.NotAuthenticated -> {
                        updateState { current ->
                            current.copy(backupInfo = null, backupPreferences = null)
                        }
                    }

                    else -> Unit
                }
            }
    }

    private fun observePreferences() {
        combine(
            preferencesRepository.observe(),
            autoBackupController.observePeriodicStatus(),
            interactor.driveFileGranted,
        ) { prefs, infos, driveFileGranted ->
            BackupPreferencesUiMapper.toUi(
                prefs = prefs,
                periodicInfos = infos,
                now = System.currentTimeMillis(),
                driveFileGranted = driveFileGranted,
            )
        }
            .launch { ui ->
                updateState { current -> current.copy(backupPreferences = ui) }
            }
    }

    private suspend fun bootstrapOrRehydrate() {
        val prefs = preferencesRepository.observe().first()
        if (!prefs.autoBackupBootstrapped) {
            preferencesRepository.setSchedule(BackupSchedule.Daily)
            preferencesRepository.setAllowOnMobileData(false)
            preferencesRepository.setAutoBackupBootstrapped(true)
            autoBackupController.schedulePeriodic(BackupPreferences.DEFAULT)
            autoBackupController.enqueueOneTime()
            sendEvent(Event.ShowAutoBackupEnabledSnackbarRequested)
        } else if (prefs.schedule != BackupSchedule.ManualOnly) {
            autoBackupController.schedulePeriodic(prefs)
        }
        if (prefs.lastError == BackupErrorCode.AuthRevoked) {
            preferencesRepository.setLastError(null)
        }
    }

    private fun signIn() {
        updateState { current -> current.copy(backupOperation = BackupOperationUi.SigningIn) }
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                when (result) {
                    SignInOutcomeDomain.Success -> {
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        launchDefault { bootstrapOrRehydrate() }
                    }

                    is SignInOutcomeDomain.NeedsResolution -> {
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        sendEvent(Event.AuthResolutionRequested(result.intentSender))
                    }

                    SignInOutcomeDomain.PartialGrant -> {
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        sendEvent(Event.ShowBackupError(BackupErrorUi.MISSING_REQUIRED_SCOPE))
                    }

                    is SignInOutcomeDomain.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
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
            onError = { e -> emitUnknownErrorAndIdle(e) },
            onSuccess = { result ->
                // The in-flight operation is the single source of truth for whether this
                // resolution was driving an AI-export grant (vs a plain sign-in) — no
                // cross-coroutine var. Read it before resetting to Idle.
                val wasAiExportGrant =
                    state.value.backupOperation == BackupOperationUi.TogglingAiExport
                updateStateImmediate { current ->
                    current.copy(backupOperation = BackupOperationUi.Idle)
                }
                when (result) {
                    is BackupResult.Success -> {
                        logger.i { "Sign-in successful for account: ${result.data}" }
                        if (wasAiExportGrant) reconcileAiExportGrant()
                        launchDefault { bootstrapOrRehydrate() }
                    }

                    is BackupResult.Failure -> {
                        // A cancelled/failed resolution that was driving an AI-export grant
                        // surfaces the access-needed snackbar, not a generic backup error.
                        if (wasAiExportGrant) {
                            sendEvent(Event.ShowAiExportAccessNeeded)
                        } else {
                            sendEvent(Event.ShowBackupError(result.error.toUi()))
                        }
                    }
                }
            },
        ) {
            interactor.completeSignIn(resultIntent)
        }
    }

    private fun toggleAiExport(enabled: Boolean) {
        if (!enabled) {
            // Withdraw consent: stop future exports (flag off so a racing worker won't re-upload),
            // then best-effort delete the already-exported plaintext snapshots from visible Drive.
            launchDefault {
                preferencesRepository.setAiExportEnabled(false)
                interactor.deleteAiExportSnapshots()
            }
            return
        }
        // Ignore a re-entrant enable while a grant is already in flight (the switch is also
        // disabled in the UI while TogglingAiExport) — prevents launching a second
        // requestDriveFileAccess() and a second resolution intent.
        if (state.value.backupOperation.isInProgress) return
        // Mark in flight and keep it set THROUGH the resolution round-trip so handleAuthResult can
        // tell this resolution apart from a plain sign-in. Pessimistic: never persist enabled=true
        // before the drive.file grant is confirmed. (`updateState` — non-suspend entry point, as
        // in `signIn`.)
        updateState { current ->
            current.copy(backupOperation = BackupOperationUi.TogglingAiExport)
        }
        launchDefault(
            onError = { e ->
                logger.e(e, "Failed to request drive.file for AI export")
                updateStateImmediate { current ->
                    current.copy(backupOperation = BackupOperationUi.Idle)
                }
                sendEvent(Event.ShowAiExportAccessNeeded)
            },
            onSuccess = { outcome ->
                when (outcome) {
                    SignInOutcomeDomain.Success -> {
                        preferencesRepository.setAiExportEnabled(true)
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                    }

                    // Keep TogglingAiExport set; handleAuthResult resolves the grant and resets it.
                    is SignInOutcomeDomain.NeedsResolution ->
                        sendEvent(Event.AuthResolutionRequested(outcome.intentSender))

                    SignInOutcomeDomain.PartialGrant,
                    is SignInOutcomeDomain.Failure,
                    -> {
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        sendEvent(Event.ShowAiExportAccessNeeded)
                    }
                }
            },
        ) {
            interactor.requestDriveFileAccess()
        }
    }

    /**
     * Post-resolution reconciliation (called from [handleAuthResult] success only when the
     * in-flight op was [BackupOperationUi.TogglingAiExport]). Persist the toggle ON only when
     * `drive.file` is now actually granted; otherwise the user declined the scope — surface the
     * access-needed snackbar and leave the toggle off.
     */
    private suspend fun reconcileAiExportGrant() {
        if (interactor.isDriveFileGranted()) {
            preferencesRepository.setAiExportEnabled(true)
        } else {
            sendEvent(Event.ShowAiExportAccessNeeded)
        }
    }

    private fun requestSignOut() {
        updateState { current -> current.copy(dialogState = DialogState.SignOutConfirmation) }
    }

    private fun dismissSignOutConfirmation() {
        updateState { current -> current.copy(dialogState = DialogState.Hidden) }
    }

    private fun confirmSignOut() {
        updateState { current ->
            current.copy(
                dialogState = DialogState.Hidden,
                backupOperation = BackupOperationUi.SigningOut,
            )
        }
        launchDefault(
            onError = { emitUnknownErrorAndIdle(it) },
            onSuccess = { result ->
                val errorUi = (result as? BackupResult.Failure)?.error?.toUi()
                updateStateImmediate { current ->
                    current.copy(backupOperation = BackupOperationUi.Idle)
                }
                if (errorUi != null) sendEvent(Event.ShowBackupError(errorUi))
            },
        ) {
            autoBackupController.cancelPeriodic()
            // Delete the visible-Drive snapshots BEFORE signOut revokes drive.file — once revoked,
            // drive.file can no longer see the app's own files, stranding them permanently.
            interactor.deleteAiExportSnapshots()
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
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        sendEvent(Event.ShowBackupCreated)
                        loadBackupList()
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateState { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
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
                                    dialogState = confirmation,
                                )
                            }
                        }
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.listLatestBackup()
        }
    }

    private fun dismissRestoreDialog() {
        updateState { current -> current.copy(dialogState = DialogState.Hidden) }
    }

    private fun confirmRestore() {
        updateState { current ->
            current.copy(
                dialogState = DialogState.Hidden,
                backupOperation = BackupOperationUi.Restoring,
                restoreProgress = RestoreProgressUi.Restoring,
            )
        }
        launchDefault(
            onError = { e ->
                logger.e(e, "Unknown error during restore operation")
                updateStateImmediate { current ->
                    current.copy(
                        backupOperation = BackupOperationUi.Idle,
                        restoreProgress = RestoreProgressUi.Idle,
                    )
                }
                sendEvent(Event.ShowBackupError(BackupErrorUi.UNKNOWN))
            },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> {
                        // Reset backupOperation alongside restoreProgress so the
                        // UI isn't locked in the Restoring state if scheduleAppRestart
                        // is aborted or delayed — e.g. process held in background,
                        // navigation pushes a different screen, debugger pauses.
                        updateStateImmediate { current ->
                            current.copy(
                                backupOperation = BackupOperationUi.Idle,
                                restoreProgress = RestoreProgressUi.Completed,
                            )
                        }
                        scheduleAppRestart()
                    }

                    is BackupResult.Failure -> {
                        val errorUi = result.error.toUi()
                        updateStateImmediate { current ->
                            current.copy(
                                backupOperation = BackupOperationUi.Idle,
                                restoreProgress = RestoreProgressUi.Idle,
                            )
                        }
                        sendEvent(Event.ShowBackupError(errorUi))
                    }
                }
            },
        ) {
            interactor.restoreLatest()
        }
    }

    private fun scheduleAppRestart() {
        flowOf(Unit).launch {
            delay(RESTART_DELAY_MS)
            consume(Action.Navigation.RestartApp)
        }
    }

    private fun loadBackupList() {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to load backup list") },
            onSuccess = { result ->
                when (result) {
                    is BackupResult.Success -> {
                        val info = BackupDateMapper.toInfo(result.data, context)
                        updateStateImmediate { current -> current.copy(backupInfo = info) }
                    }

                    is BackupResult.Failure -> {
                        logger.w { "Failed to load backup list: ${result.error}" }
                    }
                }
            },
        ) {
            interactor.listBackups()
        }
    }

    private fun openFrequencyPicker() {
        val current = state.value.backupPreferences
        val schedule = current?.schedule ?: BackupScheduleUi.WEEKLY
        val allowOnMobile = current?.allowOnMobileData ?: false
        updateState { state ->
            state.copy(
                dialogState = DialogState.FrequencyPicker(
                    selectedSchedule = schedule,
                    allowOnMobileData = allowOnMobile,
                ),
            )
        }
    }

    private fun dismissFrequencyPicker() {
        updateState { current -> current.copy(dialogState = DialogState.Hidden) }
    }

    private fun updateFrequencyPickerSelection(
        schedule: BackupScheduleUi,
        allowOnMobileData: Boolean,
    ) {
        updateState { current ->
            val existing = current.dialogState as? DialogState.FrequencyPicker
                ?: return@updateState current
            current.copy(
                dialogState = existing.copy(
                    selectedSchedule = schedule,
                    allowOnMobileData = allowOnMobileData,
                ),
            )
        }
    }

    private fun saveFrequency(
        schedule: BackupScheduleUi,
        allowOnMobileData: Boolean,
    ) {
        updateState { current -> current.copy(dialogState = DialogState.Hidden) }
        val domainSchedule = schedule.toDomain()
        launchDefault(
            onError = { e -> logger.e(e, "Failed to save frequency") },
            onSuccess = { },
        ) {
            // Read the current preferences before overlaying the two settings the
            // user just edited. `BackupPreferences.DEFAULT.copy(...)` would have
            // silently passed sentinel values for the other fields
            // (lastAttempt/lastSuccess/lastError/autoBackupBootstrapped) into
            // schedulePeriodic. It only consumes schedule + allowOnMobileData
            // today, but the snapshot it receives should reflect persisted state
            // either way — otherwise future readers of the snapshot field will
            // hit the same hazard.
            val current = preferencesRepository.observe().first()
            val updated = current.copy(
                schedule = domainSchedule,
                allowOnMobileData = allowOnMobileData,
            )
            preferencesRepository.setSchedule(domainSchedule)
            preferencesRepository.setAllowOnMobileData(allowOnMobileData)
            if (domainSchedule == BackupSchedule.ManualOnly) {
                autoBackupController.cancelPeriodic()
            } else {
                autoBackupController.schedulePeriodic(updated)
            }
        }
    }

    private suspend fun emitUnknownErrorAndIdle(e: Throwable) {
        logger.e(e, "Unknown error during backup operation")
        updateStateImmediate { current -> current.copy(backupOperation = BackupOperationUi.Idle) }
        sendEvent(Event.ShowBackupError(BackupErrorUi.UNKNOWN))
    }

    private companion object {
        const val RESTART_DELAY_MS = 2_000L
    }
}
