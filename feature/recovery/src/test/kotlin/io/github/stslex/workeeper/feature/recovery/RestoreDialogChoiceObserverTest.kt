// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.Context
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.UndoRestoreOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [RestoreDialogChoiceObserver] on a scheduler-sharing test dispatcher.
 * GUARD: [RestoreRecoveryCoordinator] stays a relaxed mock — the real `restartApp` exits the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RestoreDialogChoiceObserverTest {

    private val androidContext = mockk<Context>(relaxed = true)
    private val observer = mockk<AppDialogObserver>(relaxed = true)
    private val coordinator = mockk<RestoreRecoveryCoordinator>(relaxed = true)
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
    private val diagnosticsExporter = mockk<RecoveryDiagnosticsExporter>(relaxed = true)

    private fun TestScope.createObserver(): RestoreDialogChoiceObserver = RestoreDialogChoiceObserver(
        context = androidContext,
        observer = observer,
        coordinator = coordinator,
        restoreStateRepository = restoreStateRepository,
        appDialogPublisher = appDialogPublisher,
        diagnosticsExporter = diagnosticsExporter,
        lifetime = AppScopeLifetime(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun stubChoice(dialog: AppDialog, action: AppDialogUserAction) {
        every { observer.observeUserActions() } returns flowOf(AppDialogUserChoice(dialog, action))
    }

    @Test
    fun `RequestUndo with no preserved date neither publishes nor acknowledges`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 0L, previousVersionAvailable = true)
        stubChoice(dialog, AppDialogUserAction.RequestUndo)
        coEvery { restoreStateRepository.getPreRestoreOriginalDate() } returns null

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        coVerify(exactly = 0) { observer.acknowledgeReaction(any()) }
    }

    @Test
    fun `RequestUndo with preserved date publishes confirmation then acknowledges success`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 0L, previousVersionAvailable = true)
        stubChoice(dialog, AppDialogUserAction.RequestUndo)
        coEvery { restoreStateRepository.getPreRestoreOriginalDate() } returns 123L

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            appDialogPublisher.publish(
                AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 123L),
            )
        }
        coVerify(exactly = 1) { observer.acknowledgeReaction(dialog) }
    }

    @Test
    fun `RestoreSuccess Acknowledge acknowledges and publishes nothing`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 0L, previousVersionAvailable = true)
        stubChoice(dialog, AppDialogUserAction.Acknowledge)

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { observer.acknowledgeReaction(dialog) }
        coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
    }

    @Test
    fun `ConfirmUndo Succeeded acknowledges through the onCommitted hook and restarts`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 123L)
        stubChoice(dialog, AppDialogUserAction.ConfirmUndo)
        // Invoking the hook proves the acknowledge is passed as the transaction's onCommitted.
        coEvery { coordinator.performUndoRestore(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
            UndoRestoreOutcome.Succeeded
        }

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { coordinator.performUndoRestore(any()) }
        coVerify(exactly = 1) { observer.acknowledgeReaction(dialog) }
        verify(exactly = 1) { coordinator.restartApp() }
    }

    @Test
    fun `ConfirmUndo IoFailure keeps dialog visible and does not restart`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 123L)
        stubChoice(dialog, AppDialogUserAction.ConfirmUndo)
        // Non-commit: the seam never runs the hook, so the observer must not acknowledge.
        coEvery { coordinator.performUndoRestore(any()) } returns UndoRestoreOutcome.IoFailure

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { coordinator.performUndoRestore(any()) }
        coVerify(exactly = 0) { observer.acknowledgeReaction(any()) }
        verify(exactly = 0) { coordinator.restartApp() }
    }

    @Test
    fun `ConfirmUndo FileMissing acknowledges directly but does not restart`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 123L)
        stubChoice(dialog, AppDialogUserAction.ConfirmUndo)
        coEvery { coordinator.performUndoRestore(any()) } returns UndoRestoreOutcome.FileMissing

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { observer.acknowledgeReaction(dialog) }
        verify(exactly = 0) { coordinator.restartApp() }
    }

    @Test
    fun `Cancel acknowledges without performing undo`() = runTest {
        val dialog = AppDialog.UndoRestoreConfirmation(originalDataDateEpochMs = 123L)
        stubChoice(dialog, AppDialogUserAction.Cancel)

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { observer.acknowledgeReaction(dialog) }
        coVerify(exactly = 0) { coordinator.performUndoRestore(any()) }
    }

    @Test
    fun `UndoRestoreSuccess Acknowledge acknowledges the data object`() = runTest {
        stubChoice(AppDialog.UndoRestoreSuccess, AppDialogUserAction.Acknowledge)

        createObserver()
        advanceUntilIdle()

        coVerify(exactly = 1) { observer.acknowledgeReaction(AppDialog.UndoRestoreSuccess) }
    }
}
