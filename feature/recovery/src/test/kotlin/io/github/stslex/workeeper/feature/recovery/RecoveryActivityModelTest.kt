// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.feature.recovery.domain.RecoveryExportOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

internal class RecoveryActivityModelTest {

    private val checker = mockk<InterruptedRestoreChecker>()
    private val recoveryFiles = mockk<RestoreRecoveryFiles>()
    private val restoreStateRepository = mockk<RestoreStateRepository>()
    private val appReinitializer = mockk<AppReinitializer>(relaxed = true)

    @Test
    fun `InterruptedRestore construction does not read protocol or inspect SQLite`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null

        interruptedModel()

        coVerify(exactly = 0) { restoreStateRepository.readProtocol() }
        coVerify(exactly = 0) { checker.check() }
    }

    @Test
    fun `Continue checks first and abandons exact owner only after explicit confirmation`() =
        runTest {
            every { recoveryFiles.recoveryExportFile() } returns null
            coEvery { restoreStateRepository.readProtocol() } returns currentProtocol(OWNER)
            coEvery { checker.check() } returns
                InterruptedRestoreCheckResult.Healthy(CURRENT_SCHEMA)
            coEvery {
                restoreStateRepository.abandonInterruptedAttempt(OWNER)
            } returns true
            val model = interruptedModel()

            model.requestContinue()

            assertEquals(
                DialogState.ContinueConfirmation(OWNER, CURRENT_SCHEMA),
                model.state.value.dialogState,
            )
            coVerify(exactly = 0) {
                restoreStateRepository.abandonInterruptedAttempt(any())
            }
            verify(exactly = 0) { appReinitializer.reinitialize() }

            model.confirmContinue()

            assertEquals(DialogState.Hidden, model.state.value.dialogState)
            assertEquals(ContinueState.Restarting, model.state.value.continueState)
            coVerifyOrder {
                restoreStateRepository.abandonInterruptedAttempt(OWNER)
                appReinitializer.reinitialize()
            }
        }

    @Test
    fun `missing raw export does not prevent the two-step Continue check`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null
        coEvery { restoreStateRepository.readProtocol() } returns currentProtocol(OWNER)
        coEvery { checker.check() } returns
            InterruptedRestoreCheckResult.Healthy(CURRENT_SCHEMA)
        val model = interruptedModel()

        model.requestContinue()

        assertTrue(model.state.value.rawExportState is RawExportState.Unavailable)
        assertTrue(model.state.value.dialogState is DialogState.ContinueConfirmation)
    }

    @Test
    fun `integrity failure never reaches confirmation or abandonment`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null
        coEvery { restoreStateRepository.readProtocol() } returns currentProtocol(OWNER)
        coEvery { checker.check() } returns InterruptedRestoreCheckResult.Unhealthy(
            InterruptedRestoreCheckResult.Reason.IntegrityCheckFailed,
        )
        val model = interruptedModel()

        model.requestContinue()

        assertEquals(DialogState.Hidden, model.state.value.dialogState)
        assertEquals(
            ContinueState.Failed(ContinueState.FailureReason.IntegrityCheckFailed),
            model.state.value.continueState,
        )
        coVerify(exactly = 0) {
            restoreStateRepository.abandonInterruptedAttempt(any())
        }
        verify(exactly = 0) { appReinitializer.reinitialize() }
    }

    @Test
    fun `confirmation cannot abandon a replacement owner`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null
        coEvery { restoreStateRepository.readProtocol() } returnsMany listOf(
            currentProtocol(OWNER),
            currentProtocol(OTHER_OWNER),
        )
        coEvery { checker.check() } returns
            InterruptedRestoreCheckResult.Healthy(CURRENT_SCHEMA)
        val model = interruptedModel()

        model.requestContinue()
        model.confirmContinue()

        assertEquals(DialogState.Hidden, model.state.value.dialogState)
        assertEquals(
            ContinueState.Failed(ContinueState.FailureReason.OwnerChanged),
            model.state.value.continueState,
        )
        coVerify(exactly = 0) {
            restoreStateRepository.abandonInterruptedAttempt(any())
        }
        verify(exactly = 0) { appReinitializer.reinitialize() }
    }

    @Test
    fun `share-copy failure is visible in typed raw export state`() = runTest {
        val durable = File("durable_recovery.db")
        every { recoveryFiles.recoveryExportFile() } returns durable
        coEvery {
            recoveryFiles.createShareCopy(durable, any())
        } returns BackupResult.Failure(BackupError.Io(IOException("disk full")))
        val model = interruptedModel()

        assertEquals(RawExportPreparation.NotReady, model.prepareRawExport())
        assertEquals(
            RawExportState.Failed(RawExportState.FailureReason.ShareCopyFailed),
            model.state.value.rawExportState,
        )
    }

    @Test
    fun `durable export lookup failure is visible`() {
        every { recoveryFiles.recoveryExportFile() } throws IOException("root unavailable")

        val model = interruptedModel()

        assertEquals(
            RawExportState.Failed(RawExportState.FailureReason.AssetLookupFailed),
            model.state.value.rawExportState,
        )
    }

    @Test
    fun `startup export creation failure remains visible even when an old export exists`() =
        runTest {
            every { recoveryFiles.recoveryExportFile() } returns File("stale_recovery.db")
            val model = interruptedModel(RecoveryExportOutcome.Failed)

            assertEquals(
                RawExportState.Failed(
                    RawExportState.FailureReason.RecoveryExportCreationFailed,
                ),
                model.state.value.rawExportState,
            )
            assertEquals(RawExportPreparation.NotReady, model.prepareRawExport())
            coVerify(exactly = 0) { recoveryFiles.createShareCopy(any(), any()) }
        }

    @Test
    fun `StartupMigration never runs the Continue checker`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null
        val model = RecoveryActivityModel(
            scenario = RecoveryScenario.StartupMigration,
            allowContinue = true,
            checker = checker,
            recoveryFiles = recoveryFiles,
            restoreStateRepository = restoreStateRepository,
            appReinitializer = appReinitializer,
        )

        model.requestContinue()

        assertEquals(ContinueState.Hidden, model.state.value.continueState)
        coVerify(exactly = 0) { checker.check() }
        coVerify(exactly = 0) { restoreStateRepository.readProtocol() }
    }

    @Test
    fun `InterruptedRestore without preflight opt-in hides Continue and does no work`() = runTest {
        every { recoveryFiles.recoveryExportFile() } returns null
        val model = interruptedModel(allowContinue = false)

        model.requestContinue()
        model.confirmContinue()

        assertEquals(ContinueState.Hidden, model.state.value.continueState)
        assertEquals(DialogState.Hidden, model.state.value.dialogState)
        coVerify(exactly = 0) { checker.check() }
        coVerify(exactly = 0) { restoreStateRepository.readProtocol() }
        coVerify(exactly = 0) {
            restoreStateRepository.abandonInterruptedAttempt(any())
        }
        verify(exactly = 0) { appReinitializer.reinitialize() }
    }

    private fun interruptedModel(
        recoveryExportOutcome: RecoveryExportOutcome? = null,
        allowContinue: Boolean = true,
    ) = RecoveryActivityModel(
        scenario = RecoveryScenario.InterruptedRestore,
        allowContinue = allowContinue,
        checker = checker,
        recoveryFiles = recoveryFiles,
        restoreStateRepository = restoreStateRepository,
        appReinitializer = appReinitializer,
        recoveryExportOutcome = recoveryExportOutcome,
    )

    private fun currentProtocol(owner: RestoreOwnerId): RestoreProtocolRead.Current =
        RestoreProtocolRead.Current(
            RestoreProtocolState(
                installEpoch = InstallEpoch(EPOCH),
                attempt = RestoreAttempt.Restore(
                    id = owner,
                    phase = RestoreAttempt.Phase.Prepared,
                    context = null,
                    undoRef = null,
                    sourceRef = null,
                ),
                activeUndo = null,
                terminalOutbox = null,
            ),
        )

    private companion object {
        const val CURRENT_SCHEMA = 6
        val OWNER = LegacyRestoreOwners.InterruptedAttempt
        val OTHER_OWNER = RestoreOwnerId("10000000-0000-4000-8000-000000000002")
        val EPOCH = RestoreOwnerId("20000000-0000-4000-8000-000000000002")
    }
}
