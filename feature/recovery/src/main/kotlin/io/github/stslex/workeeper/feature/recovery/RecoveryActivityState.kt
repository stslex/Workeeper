// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.feature.recovery.domain.RecoveryExportOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Stable
internal data class RecoveryUiState(
    val rawExportState: RawExportState,
    val diagnosticsState: DiagnosticsState = DiagnosticsState.Ready,
    val continueState: ContinueState,
    val dialogState: DialogState = DialogState.Hidden,
)

@Stable
internal sealed interface RawExportState {

    @Stable
    data object Available : RawExportState

    @Stable
    data class Unavailable(val reason: UnavailableReason) : RawExportState

    @Stable
    data class Failed(val reason: FailureReason) : RawExportState

    enum class UnavailableReason { DurableExportMissing }

    enum class FailureReason {
        RecoveryExportCreationFailed,
        AssetLookupFailed,
        ShareCopyFailed,
        ShareIntentFailed,
    }
}

@Stable
internal sealed interface DiagnosticsState {

    @Stable
    data object Ready : DiagnosticsState

    @Stable
    data object Failed : DiagnosticsState
}

@Stable
internal sealed interface ContinueState {

    @Stable
    data object Hidden : ContinueState

    @Stable
    data object Ready : ContinueState

    @Stable
    data object Checking : ContinueState

    @Stable
    data class Failed(val reason: FailureReason) : ContinueState

    @Stable
    data object Restarting : ContinueState

    enum class FailureReason {
        NoOwnedInterruptedRestore,
        LiveDatabaseMissing,
        IntegrityCheckFailed,
        UnsupportedSchema,
        CheckFailed,
        OwnerChanged,
        AbandonFailed,
    }
}

@Stable
internal sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    @Stable
    data class ContinueConfirmation(
        val owner: RestoreOwnerId,
        val userVersion: Int,
    ) : DialogState
}

internal sealed interface RawExportPreparation {

    data class Ready(val file: File) : RawExportPreparation

    data object NotReady : RawExportPreparation
}

/** Retained Activity state. No SQLite work occurs until [requestContinue] is called. */
internal class RecoveryActivityModel(
    scenario: RecoveryScenario,
    allowContinue: Boolean,
    private val checker: InterruptedRestoreChecker,
    private val recoveryFiles: RestoreRecoveryFiles,
    private val restoreStateRepository: RestoreStateRepository,
    private val appReinitializer: AppReinitializer,
    private val recoveryExportOutcome: RecoveryExportOutcome? = null,
) : ViewModel() {

    private val canContinue =
        scenario == RecoveryScenario.InterruptedRestore && allowContinue
    private val transitionMutex = Mutex()
    private val exportMutex = Mutex()
    private val mutableState = MutableStateFlow(
        RecoveryUiState(
            rawExportState = initialRawExportState(),
            continueState = if (canContinue) {
                ContinueState.Ready
            } else {
                ContinueState.Hidden
            },
        ),
    )

    val state: StateFlow<RecoveryUiState> = mutableState.asStateFlow()

    suspend fun prepareRawExport(): RawExportPreparation = exportMutex.withLock {
        if (mutableState.value.rawExportState == RawExportState.Failed(
                RawExportState.FailureReason.RecoveryExportCreationFailed,
            )
        ) {
            return@withLock RawExportPreparation.NotReady
        }
        val source = try {
            recoveryFiles.recoveryExportFile()
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                rawExportState = RawExportState.Failed(
                    RawExportState.FailureReason.AssetLookupFailed,
                ),
            )
            return@withLock RawExportPreparation.NotReady
        }
        if (source == null) {
            mutableState.value = mutableState.value.copy(
                rawExportState = RawExportState.Unavailable(
                    RawExportState.UnavailableReason.DurableExportMissing,
                ),
            )
            return@withLock RawExportPreparation.NotReady
        }
        val copy = try {
            recoveryFiles.createShareCopy(source, RAW_EXPORT_SHARE_NAME)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        if (copy !is BackupResult.Success) {
            mutableState.value = mutableState.value.copy(
                rawExportState = RawExportState.Failed(
                    RawExportState.FailureReason.ShareCopyFailed,
                ),
            )
            return@withLock RawExportPreparation.NotReady
        }
        mutableState.value = mutableState.value.copy(rawExportState = RawExportState.Available)
        RawExportPreparation.Ready(copy.data)
    }

    fun markRawExportShareFailed() {
        mutableState.value = mutableState.value.copy(
            rawExportState = RawExportState.Failed(
                RawExportState.FailureReason.ShareIntentFailed,
            ),
        )
    }

    fun markDiagnosticsReady() {
        mutableState.value = mutableState.value.copy(diagnosticsState = DiagnosticsState.Ready)
    }

    fun markDiagnosticsFailed() {
        mutableState.value = mutableState.value.copy(diagnosticsState = DiagnosticsState.Failed)
    }

    /** First explicit step: validate the exact owned legacy interruption, then show confirmation. */
    suspend fun requestContinue() = transitionMutex.withLock {
        if (!canContinue) return@withLock
        mutableState.value = mutableState.value.copy(
            continueState = ContinueState.Checking,
            dialogState = DialogState.Hidden,
        )
        try {
            val attempt = readEligibleAttempt()
            if (attempt == null) {
                failContinue(ContinueState.FailureReason.NoOwnedInterruptedRestore)
                return@withLock
            }
            when (val checked = checker.check()) {
                is InterruptedRestoreCheckResult.Healthy -> {
                    mutableState.value = mutableState.value.copy(
                        continueState = ContinueState.Ready,
                        dialogState = DialogState.ContinueConfirmation(
                            owner = attempt.id,
                            userVersion = checked.userVersion,
                        ),
                    )
                }

                is InterruptedRestoreCheckResult.Unhealthy -> {
                    failContinue(checked.reason.toContinueFailure())
                }
            }
        } catch (cancellation: CancellationException) {
            mutableState.value = mutableState.value.copy(continueState = ContinueState.Ready)
            throw cancellation
        } catch (_: Exception) {
            failContinue(ContinueState.FailureReason.CheckFailed)
        }
    }

    fun dismissContinueConfirmation() {
        mutableState.value = mutableState.value.copy(dialogState = DialogState.Hidden)
    }

    /** Second explicit step: re-check owner, abandon atomically, then restart as the last action. */
    suspend fun confirmContinue() = transitionMutex.withLock {
        if (!canContinue) return@withLock
        val confirmation = mutableState.value.dialogState as? DialogState.ContinueConfirmation
            ?: return@withLock
        withContext(NonCancellable) {
            val current = runCatching { readEligibleAttempt() }.getOrNull()
            if (current?.id != confirmation.owner) {
                failContinue(ContinueState.FailureReason.OwnerChanged)
                return@withContext
            }
            val abandoned = runCatching {
                restoreStateRepository.abandonInterruptedAttempt(confirmation.owner)
            }.getOrDefault(false)
            if (!abandoned) {
                failContinue(ContinueState.FailureReason.AbandonFailed)
                return@withContext
            }
            mutableState.value = mutableState.value.copy(
                continueState = ContinueState.Restarting,
                dialogState = DialogState.Hidden,
            )
            appReinitializer.reinitialize()
        }
    }

    /** Only the synthetic same-install missing-ref legacy attempt has a safe acceptance escape. */
    private suspend fun readEligibleAttempt(): RestoreAttempt.Restore? {
        val protocol = restoreStateRepository.readProtocol() as? RestoreProtocolRead.Current
            ?: return null
        val attempt = protocol.state.attempt as? RestoreAttempt.Restore ?: return null
        return attempt.takeIf {
            it.id == LegacyRestoreOwners.InterruptedAttempt &&
                it.phase == RestoreAttempt.Phase.Prepared &&
                it.undoRef == null &&
                it.sourceRef == null
        }
    }

    private fun failContinue(reason: ContinueState.FailureReason) {
        mutableState.value = mutableState.value.copy(
            continueState = ContinueState.Failed(reason),
            dialogState = DialogState.Hidden,
        )
    }

    private fun initialRawExportState(): RawExportState = when (recoveryExportOutcome) {
        RecoveryExportOutcome.Failed -> RawExportState.Failed(
            RawExportState.FailureReason.RecoveryExportCreationFailed,
        )

        RecoveryExportOutcome.Available,
        null,
        -> try {
            if (recoveryFiles.recoveryExportFile() != null) {
                RawExportState.Available
            } else {
                RawExportState.Unavailable(RawExportState.UnavailableReason.DurableExportMissing)
            }
        } catch (_: Exception) {
            RawExportState.Failed(RawExportState.FailureReason.AssetLookupFailed)
        }
    }

    private fun InterruptedRestoreCheckResult.Reason.toContinueFailure(): ContinueState.FailureReason =
        when (this) {
            InterruptedRestoreCheckResult.Reason.LiveDatabaseMissing ->
                ContinueState.FailureReason.LiveDatabaseMissing

            InterruptedRestoreCheckResult.Reason.IntegrityCheckFailed ->
                ContinueState.FailureReason.IntegrityCheckFailed

            InterruptedRestoreCheckResult.Reason.UnsupportedSchema ->
                ContinueState.FailureReason.UnsupportedSchema

            InterruptedRestoreCheckResult.Reason.CheckFailed ->
                ContinueState.FailureReason.CheckFailed
        }

    private companion object {
        const val RAW_EXPORT_SHARE_NAME = "workeeper_recovery_export.db"
    }
}
