// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import android.content.IntentSender
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupDateMapper
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupPreferencesUiMapper
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupPreferencesUiMapper.toDomain
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toConfirmation
import io.github.stslex.workeeper.feature.settings.mvi.mapper.BackupUiMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(SettingsScope::class)
internal class BackupClickHandler @Inject constructor(
    private val interactor: BackupInteractor,
    private val preferencesRepository: BackupPreferencesRepository,
    private val autoBackupController: AutoBackupController,
    private val restoreStateRepository: RestoreStateRepository,
    private val appDialogPublisher: AppDialogPublisher,
    private val context: Context,
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

    private fun observeRestoreState() {
        restoreStateRepository.observeActiveUndo()
            .launch { activeUndo ->
                updateState { current ->
                    current.copy(canRevertLastRestore = activeUndo != null)
                }
            }
    }

    private fun requestRevertLastRestore() {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to publish UndoRestoreConfirmation") },
            onSuccess = { },
        ) {
            val activeUndo = restoreStateRepository.observeActiveUndo().first()
                ?: return@launchDefault
            appDialogPublisher.publish(
                AppDialog.UndoRestoreConfirmation(
                    undoRef = activeUndo.ref,
                    originalDataDateEpochMs = activeUndo.originalDataDateEpochMs,
                ),
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
                            current.copy(backupInfo = BackupInfoUi.Unknown, backupPreferences = null)
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
                        // Downcast the opaque resolution to the Android launch handle at the
                        // mvi edge; the domain never unpacks .platform.
                        sendEvent(
                            Event.AuthResolutionRequested(result.resolution.platform as IntentSender),
                        )
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
                // The in-flight operation tells an AI-export grant apart from a plain
                // sign-in; read it before resetting to Idle.
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
                        // An AI-export grant surfaces the access-needed snackbar instead.
                        if (wasAiExportGrant) {
                            sendEvent(Event.ShowAiExportAccessNeeded)
                        } else {
                            sendEvent(Event.ShowBackupError(result.error.toUi()))
                        }
                    }
                }
            },
        ) {
            // Wrap the ActivityResult Intent into the neutral handle before re-entering domain.
            interactor.completeSignIn(AuthResolutionOutcome(resultIntent))
        }
    }

    private fun toggleAiExport(enabled: Boolean) {
        if (!enabled) {
            // Withdraw consent: flag off first so a racing worker won't re-upload, then
            // best-effort delete the exported plaintext snapshots. See the drive-ai-export spec.
            launchDefault {
                preferencesRepository.setAiExportEnabled(false)
                interactor.deleteAiExportSnapshots()
            }
            return
        }
        // GUARD: gate only the enable direction — a withdrawal above must never be swallowed.
        if (state.value.backupOperation.isInProgress) return
        // Kept set through the resolution round-trip so handleAuthResult can identify it; never
        // persist enabled=true before the drive.file grant is confirmed.
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
                    is SignInOutcomeDomain.Success -> {
                        preferencesRepository.setAiExportEnabled(true)
                        updateStateImmediate { current ->
                            current.copy(backupOperation = BackupOperationUi.Idle)
                        }
                    }

                    // Keep TogglingAiExport set; handleAuthResult resolves the grant and resets it.
                    is SignInOutcomeDomain.NeedsResolution -> sendEvent(
                        Event.AuthResolutionRequested(outcome.resolution.platform as IntentSender),
                    )

                    is SignInOutcomeDomain.PartialGrant,
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
     * Post-resolution reconciliation: persist the toggle ON only once `drive.file` is granted.
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
            // GUARD: delete the snapshots before signOut revokes drive.file, or they strand.
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
                        updateStateImmediate { current ->
                            current.copy(
                                backupOperation = BackupOperationUi.Idle,
                                restoreProgress = RestoreProgressUi.Completed,
                            )
                        }
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
            // GUARD: overlay onto persisted preferences — DEFAULT.copy(...) would hand
            // schedulePeriodic sentinel values for every field the user did not edit.
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
}
