// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.res.Resources
import android.text.format.DateUtils
import android.text.format.Formatter
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

internal class BackupClickHandlerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val authFlow = MutableStateFlow<BackupAuthDomain>(BackupAuthDomain.NotAuthenticated)
    private val interactor = mockk<BackupInteractor>(relaxed = true).apply {
        every { authState } returns authFlow
    }
    private val resources = mockk<Resources>(relaxed = true).apply {
        every {
            getQuantityString(R.plurals.feature_settings_backup_info_count, any(), any())
        } returns "N backups stored"
    }
    private val context = mockk<Context>(relaxed = true).apply {
        every { resources } returns this@BackupClickHandlerTest.resources
        every { getString(R.string.feature_settings_backup_info_last_backup_never) } returns "Never"
        every { getString(R.string.feature_settings_backup_info_count_zero) } returns "No backups yet"
        every { getString(R.string.feature_settings_backup_info_last_backup_format, any()) } returns "Last backup: X"
    }
    private lateinit var store: FakeSettingsHandlerStore
    private lateinit var handler: BackupClickHandler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Formatter::class)
        every { Formatter.formatShortFileSize(any(), any()) } returns "1.0 KB"
        mockkStatic(DateUtils::class)
        every { DateUtils.getRelativeTimeSpanString(any(), any(), any(), any()) } returns "2 hours ago"
        coEvery { interactor.listBackups() } returns BackupResult.Success(emptyList())
        store = FakeSettingsHandlerStore(testDispatcher)
        handler = BackupClickHandler(interactor, context, store)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Formatter::class)
        unmockkStatic(DateUtils::class)
        store.dispose()
        Dispatchers.resetMain()
    }

    @Test
    fun `ObserveAuth pushes auth state into State backupAuth`() = runTest(testDispatcher) {
        handler.invoke(Action.Backup.ObserveAuth)

        authFlow.value = BackupAuthDomain.Authenticated(
            AccountDomain(email = "a@b.com", displayName = "Alice"),
        )

        assertEquals(
            BackupAuthUi.Authenticated(email = "a@b.com", displayName = "Alice"),
            store.stateFlow.value.backupAuth,
        )
    }

    @Test
    fun `ObserveAuth transition to Authenticated triggers LoadBackupList`() = runTest(testDispatcher) {
        coEvery { interactor.listBackups() } returns BackupResult.Success(
            listOf(
                BackupSummaryDomain(
                    createdAtEpochMs = 1_700_000_000_000L,
                    sizeBytes = 1024L,
                    appVersion = "1.2.3",
                    schemaVersion = 5,
                ),
            ),
        )

        handler.invoke(Action.Backup.ObserveAuth)
        authFlow.value = BackupAuthDomain.Authenticated(AccountDomain("a@b.com", "Alice"))

        val info = store.stateFlow.value.backupInfo
        assertNotNull(info)
        coVerify(exactly = 1) { interactor.listBackups() }
    }

    @Test
    fun `ObserveAuth transition to NotAuthenticated clears backupInfo`() = runTest(testDispatcher) {
        handler.invoke(Action.Backup.ObserveAuth)
        authFlow.value = BackupAuthDomain.Authenticated(AccountDomain("a@b.com", "Alice"))

        authFlow.value = BackupAuthDomain.NotAuthenticated

        assertNull(store.stateFlow.value.backupInfo)
    }

    @Test
    fun `SignIn Success flips operation to Idle and emits no events`() = runTest(testDispatcher) {
        coEvery { interactor.signIn() } returns SignInOutcomeDomain.Success
        handler.invoke(Action.Backup.SignIn)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        assertTrue(store.events.isEmpty(), "Success path emits no events")
    }

    @Test
    fun `SignIn NeedsResolution emits AuthResolutionRequested with same intentSender`() =
        runTest(testDispatcher) {
            val sender = mockk<IntentSender>(relaxed = true)
            coEvery { interactor.signIn() } returns SignInOutcomeDomain.NeedsResolution(sender)

            handler.invoke(Action.Backup.SignIn)

            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            val event = store.events.single()
            assertTrue(event is Event.AuthResolutionRequested)
            assertSame(sender, (event as Event.AuthResolutionRequested).intentSender)
        }

    @Test
    fun `SignIn Failure emits ShowBackupError with mapped enum`() = runTest(testDispatcher) {
        coEvery { interactor.signIn() } returns SignInOutcomeDomain.Failure(
            BackupError.NetworkUnavailable,
        )
        handler.invoke(Action.Backup.SignIn)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        val event = store.events.single()
        assertTrue(event is Event.ShowBackupError)
        assertEquals(BackupErrorUi.NETWORK_UNAVAILABLE, (event as Event.ShowBackupError).error)
    }

    @Test
    fun `HandleAuthResult Failure emits ShowBackupError`() = runTest(testDispatcher) {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { interactor.completeSignIn(intent) } returns BackupResult.Failure(
            BackupError.AuthRevoked,
        )
        handler.invoke(Action.Backup.HandleAuthResult(intent))
        val event = store.events.single()
        assertTrue(event is Event.ShowBackupError)
        assertEquals(BackupErrorUi.AUTH_REVOKED, (event as Event.ShowBackupError).error)
    }

    @Test
    fun `HandleAuthResult Success emits no events (auth flow drives state)`() =
        runTest(testDispatcher) {
            val intent = mockk<Intent>(relaxed = true)
            val expectedAccount = AccountDomain(email = "a@b.com", displayName = "A")
            coEvery { interactor.completeSignIn(intent) } returns BackupResult.Success(expectedAccount)
            handler.invoke(Action.Backup.HandleAuthResult(intent))
            assertTrue(store.events.isEmpty())
        }

    @Test
    fun `RequestSignOut sets dialogState to SignOutConfirmation`() = runTest(testDispatcher) {
        handler.invoke(Action.Backup.RequestSignOut)
        assertEquals(DialogState.SignOutConfirmation, store.stateFlow.value.dialogState)
        coVerify(exactly = 0) { interactor.signOut() }
    }

    @Test
    fun `DismissSignOutConfirmation flips dialogState to Hidden`() = runTest(testDispatcher) {
        store.stateFlow.value = store.stateFlow.value.copy(
            dialogState = DialogState.SignOutConfirmation,
        )

        handler.invoke(Action.Backup.DismissSignOutConfirmation)

        assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
        coVerify(exactly = 0) { interactor.signOut() }
    }

    @Test
    fun `ConfirmSignOut hides dialog and calls interactor signOut`() = runTest(testDispatcher) {
        coEvery { interactor.signOut() } returns BackupResult.Success(Unit)
        store.stateFlow.value = store.stateFlow.value.copy(
            dialogState = DialogState.SignOutConfirmation,
        )

        handler.invoke(Action.Backup.ConfirmSignOut)

        assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        coVerify(exactly = 1) { interactor.signOut() }
    }

    @Test
    fun `RequestSignOut while RestoreConfirmation is shown replaces dialog`() =
        runTest(testDispatcher) {
            store.stateFlow.value = store.stateFlow.value.copy(
                dialogState = DialogState.RestoreConfirmation(
                    createdAtFormatted = "x",
                    sizeFormatted = "y",
                ),
            )

            handler.invoke(Action.Backup.RequestSignOut)

            assertEquals(DialogState.SignOutConfirmation, store.stateFlow.value.dialogState)
        }

    @Test
    fun `ConfirmSignOut Failure emits ShowBackupError and resets to Idle`() =
        runTest(testDispatcher) {
            coEvery { interactor.signOut() } returns BackupResult.Failure(
                BackupError.Unknown(RuntimeException("x")),
            )
            handler.invoke(Action.Backup.ConfirmSignOut)
            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            val event = store.events.single()
            assertEquals(BackupErrorUi.UNKNOWN, (event as Event.ShowBackupError).error)
        }

    @Test
    fun `CreateBackup Success emits ShowBackupCreated and resets to Idle`() =
        runTest(testDispatcher) {
            coEvery { interactor.createBackup() } returns BackupResult.Success(Unit)
            handler.invoke(Action.Backup.CreateBackup)
            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            assertEquals(Event.ShowBackupCreated, store.events.single())
        }

    @Test
    fun `CreateBackup Failure emits ShowBackupError`() = runTest(testDispatcher) {
        coEvery { interactor.createBackup() } returns BackupResult.Failure(
            BackupError.StorageQuotaExceeded,
        )
        handler.invoke(Action.Backup.CreateBackup)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        val event = store.events.single()
        assertEquals(
            BackupErrorUi.STORAGE_QUOTA_EXCEEDED,
            (event as Event.ShowBackupError).error,
        )
    }

    @Test
    fun `RequestRestore Success null emits NO_BACKUPS_FOUND`() = runTest(testDispatcher) {
        coEvery { interactor.listLatestBackup() } returns BackupResult.Success(null)
        handler.invoke(Action.Backup.RequestRestore)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
        assertEquals(
            BackupErrorUi.NO_BACKUPS_FOUND,
            (store.events.single() as Event.ShowBackupError).error,
        )
    }

    @Test
    fun `RequestRestore Success with summary sets dialogState to RestoreConfirmation`() =
        runTest(testDispatcher) {
            coEvery { interactor.listLatestBackup() } returns BackupResult.Success(
                BackupSummaryDomain(
                    createdAtEpochMs = 1_700_000_000_000L,
                    sizeBytes = 1024L,
                    appVersion = "1.2.3",
                    schemaVersion = 5,
                ),
            )

            handler.invoke(Action.Backup.RequestRestore)

            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            val dialog = store.stateFlow.value.dialogState as? DialogState.RestoreConfirmation
            assertNotNull(dialog)
            assertEquals("1.0 KB", dialog!!.sizeFormatted)
            assertTrue(dialog.createdAtFormatted.isNotEmpty())
            assertTrue(store.events.isEmpty())
        }

    @Test
    fun `RequestRestore Failure emits ShowBackupError`() = runTest(testDispatcher) {
        coEvery { interactor.listLatestBackup() } returns BackupResult.Failure(
            BackupError.NetworkUnavailable,
        )
        handler.invoke(Action.Backup.RequestRestore)
        assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
        assertEquals(
            BackupErrorUi.NETWORK_UNAVAILABLE,
            (store.events.single() as Event.ShowBackupError).error,
        )
    }

    @Test
    fun `DismissRestoreDialog flips dialogState to Hidden`() = runTest(testDispatcher) {
        store.stateFlow.value = store.stateFlow.value.copy(
            dialogState = DialogState.RestoreConfirmation(
                createdAtFormatted = "x",
                sizeFormatted = "y",
            ),
        )
        handler.invoke(Action.Backup.DismissRestoreDialog)
        assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
    }

    @Test
    fun `ConfirmRestore Success sets Completed then emits AppRestartRequested after delay`() =
        runTest(testDispatcher) {
            coEvery { interactor.restoreLatest() } returns BackupResult.Success(Unit)
            store.stateFlow.value = store.stateFlow.value.copy(
                dialogState = DialogState.RestoreConfirmation(
                    createdAtFormatted = "x",
                    sizeFormatted = "y",
                ),
            )

            handler.invoke(Action.Backup.ConfirmRestore)

            assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
            assertEquals(RestoreProgressUi.Completed, store.stateFlow.value.restoreProgress)
            assertTrue(
                store.events.isEmpty(),
                "AppRestartRequested should not be emitted before delay completes",
            )

            advanceTimeBy(2_001L)
            runCurrent()

            assertEquals(Event.AppRestartRequested, store.events.single())
        }

    @Test
    fun `ConfirmRestore Failure resets restoreProgress to Idle and emits ShowBackupError`() =
        runTest(testDispatcher) {
            coEvery { interactor.restoreLatest() } returns BackupResult.Failure(
                BackupError.Io(IOException("disk")),
            )
            handler.invoke(Action.Backup.ConfirmRestore)
            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            assertEquals(RestoreProgressUi.Idle, store.stateFlow.value.restoreProgress)
            assertEquals(
                BackupErrorUi.IO_ERROR,
                (store.events.single() as Event.ShowBackupError).error,
            )
        }

    @Test
    fun `LoadBackupList Success populates backupInfo`() = runTest(testDispatcher) {
        coEvery { interactor.listBackups() } returns BackupResult.Success(
            listOf(
                BackupSummaryDomain(
                    createdAtEpochMs = 1_700_000_000_000L,
                    sizeBytes = 1024L,
                    appVersion = "1.2.3",
                    schemaVersion = 5,
                ),
                BackupSummaryDomain(
                    createdAtEpochMs = 1_600_000_000_000L,
                    sizeBytes = 512L,
                    appVersion = "1.2.0",
                    schemaVersion = 5,
                ),
            ),
        )

        handler.invoke(Action.Backup.LoadBackupList)

        val info = store.stateFlow.value.backupInfo
        assertNotNull(info)
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun `LoadBackupList Failure leaves backupInfo as-is and emits no error event`() =
        runTest(testDispatcher) {
            coEvery { interactor.listBackups() } returns BackupResult.Failure(
                BackupError.NetworkUnavailable,
            )

            handler.invoke(Action.Backup.LoadBackupList)

            assertNull(store.stateFlow.value.backupInfo)
            assertTrue(store.events.isEmpty(), "LoadBackupList failure must stay silent")
        }

    @Test
    fun `interactor signIn invoked exactly once per SignIn`() = runTest(testDispatcher) {
        coEvery { interactor.signIn() } returns SignInOutcomeDomain.Success
        handler.invoke(Action.Backup.SignIn)
        coVerify(exactly = 1) { interactor.signIn() }
    }
}
