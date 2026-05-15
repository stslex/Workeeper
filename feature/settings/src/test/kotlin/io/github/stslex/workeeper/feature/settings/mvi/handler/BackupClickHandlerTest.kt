// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.res.Resources
import android.text.format.DateUtils
import android.text.format.Formatter
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupWorkInfo
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupErrorUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupOperationUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
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
    private val preferencesFlow = MutableStateFlow(BackupPreferences.DEFAULT)
    private val periodicStatusFlow = MutableStateFlow<List<AutoBackupWorkInfo>>(emptyList())
    private val interactor = mockk<BackupInteractor>(relaxed = true).apply {
        every { authState } returns authFlow
    }
    private val preferencesRepository = mockk<BackupPreferencesRepository>(relaxed = true).apply {
        every { observe() } returns preferencesFlow
        coEvery { setSchedule(any()) } coAnswers {
            preferencesFlow.value = preferencesFlow.value.copy(schedule = firstArg())
        }
        coEvery { setAllowOnMobileData(any()) } coAnswers {
            preferencesFlow.value = preferencesFlow.value.copy(allowOnMobileData = firstArg())
        }
        coEvery { setAutoBackupBootstrapped(any()) } coAnswers {
            preferencesFlow.value = preferencesFlow.value.copy(autoBackupBootstrapped = firstArg())
        }
        coEvery { setLastError(any()) } coAnswers {
            preferencesFlow.value = preferencesFlow.value.copy(lastError = firstArg())
        }
    }
    private val autoBackupController = mockk<AutoBackupController>(relaxed = true).apply {
        every { observePeriodicStatus() } returns periodicStatusFlow
        every { observeOneTimeStatus() } returns MutableStateFlow(emptyList())
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
    private val restoreStateRepository = mockk<RestoreStateRepository>(relaxed = true)
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>(relaxed = true).apply {
        every { hasPreRestoreBackup() } returns true
    }
    private val appDialogPublisher = mockk<AppDialogPublisher>(relaxed = true)
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
        handler = BackupClickHandler(
            interactor = interactor,
            preferencesRepository = preferencesRepository,
            autoBackupController = autoBackupController,
            restoreStateRepository = restoreStateRepository,
            snapshotProvider = snapshotProvider,
            appDialogPublisher = appDialogPublisher,
            context = context,
            store = store,
        )
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
    fun `SignIn Success flips operation to Idle and rehydrates auto-backup`() =
        runTest(testDispatcher) {
            // Pin steady-state so the rehydrate branch (not bootstrap) is exercised; the
            // bootstrap-from-SignIn scenario is covered by its own test below.
            preferencesFlow.value = preferencesFlow.value.copy(
                autoBackupBootstrapped = true,
                schedule = BackupSchedule.Weekly,
            )
            coEvery { interactor.signIn() } returns SignInOutcomeDomain.Success

            handler.invoke(Action.Backup.SignIn)

            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            coVerify { autoBackupController.schedulePeriodic(any()) }
            assertTrue(
                store.events.none { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "steady-state rehydrate must not re-emit the bootstrap snackbar",
            )
        }

    @Test
    fun `SignIn Success when not bootstrapped triggers bootstrap (snackbar + one-time)`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(autoBackupBootstrapped = false)
            coEvery { interactor.signIn() } returns SignInOutcomeDomain.Success

            handler.invoke(Action.Backup.SignIn)

            coVerify { preferencesRepository.setAutoBackupBootstrapped(true) }
            coVerify { autoBackupController.schedulePeriodic(any()) }
            coVerify { autoBackupController.enqueueOneTime() }
            assertTrue(
                store.events.any { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "first-sign-in bootstrap snackbar must fire from SignIn path even when " +
                    "observeAuth does not see an auth-state transition",
            )
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
    fun `SignIn PartialGrant emits MISSING_REQUIRED_SCOPE and stays Idle`() =
        runTest(testDispatcher) {
            coEvery { interactor.signIn() } returns SignInOutcomeDomain.PartialGrant

            handler.invoke(Action.Backup.SignIn)

            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            assertEquals(
                BackupAuthUi.NotAuthenticated,
                store.stateFlow.value.backupAuth,
                "partial grant must not flip backupAuth into Authenticated",
            )
            val event = store.events.single()
            assertTrue(event is Event.ShowBackupError)
            assertEquals(
                BackupErrorUi.MISSING_REQUIRED_SCOPE,
                (event as Event.ShowBackupError).error,
            )
        }

    @Test
    fun `HandleAuthResult Failure(MissingRequiredScope) emits MISSING_REQUIRED_SCOPE`() =
        runTest(testDispatcher) {
            val intent = mockk<Intent>(relaxed = true)
            coEvery { interactor.completeSignIn(intent) } returns BackupResult.Failure(
                BackupError.MissingRequiredScope,
            )

            handler.invoke(Action.Backup.HandleAuthResult(intent))

            val event = store.events.single()
            assertTrue(event is Event.ShowBackupError)
            assertEquals(
                BackupErrorUi.MISSING_REQUIRED_SCOPE,
                (event as Event.ShowBackupError).error,
            )
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
    fun `HandleAuthResult Success flips to Idle and rehydrates auto-backup`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(
                autoBackupBootstrapped = true,
                schedule = BackupSchedule.Weekly,
            )
            val intent = mockk<Intent>(relaxed = true)
            val expectedAccount = AccountDomain(email = "a@b.com", displayName = "A")
            coEvery { interactor.completeSignIn(intent) } returns BackupResult.Success(expectedAccount)

            handler.invoke(Action.Backup.HandleAuthResult(intent))

            assertEquals(BackupOperationUi.Idle, store.stateFlow.value.backupOperation)
            coVerify { autoBackupController.schedulePeriodic(any()) }
            assertTrue(
                store.events.none { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "steady-state rehydrate must not re-emit the bootstrap snackbar",
            )
        }

    @Test
    fun `HandleAuthResult Success when not bootstrapped triggers bootstrap`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(autoBackupBootstrapped = false)
            val intent = mockk<Intent>(relaxed = true)
            coEvery { interactor.completeSignIn(intent) } returns BackupResult.Success(
                AccountDomain("first@example.com", "First"),
            )

            handler.invoke(Action.Backup.HandleAuthResult(intent))

            coVerify { preferencesRepository.setAutoBackupBootstrapped(true) }
            coVerify { autoBackupController.schedulePeriodic(any()) }
            coVerify { autoBackupController.enqueueOneTime() }
            assertTrue(
                store.events.any { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "first-sign-in via auth-resolution path must trigger bootstrap snackbar",
            )
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
    fun `ConfirmRestore Success sets Completed then consumes RestartApp after delay`() =
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
            assertEquals(
                BackupOperationUi.Idle,
                store.stateFlow.value.backupOperation,
                "backupOperation must reset to Idle alongside restoreProgress so the UI " +
                    "is not locked in the Restoring state if the restart is aborted",
            )
            assertTrue(
                store.consumedActions.isEmpty(),
                "RestartApp should not be consumed before delay completes",
            )

            advanceTimeBy(2_001L)
            runCurrent()

            assertEquals(Action.Navigation.RestartApp, store.consumedActions.single())
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

    @Test
    fun `OpenFrequencyPicker sets dialogState to FrequencyPicker carrying current prefs`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(
                schedule = BackupSchedule.Daily,
                allowOnMobileData = true,
            )
            handler.invoke(Action.Backup.ObservePreferences)

            handler.invoke(Action.Backup.OpenFrequencyPicker)

            val dialog = store.stateFlow.value.dialogState
            assertTrue(dialog is DialogState.FrequencyPicker)
            assertEquals(BackupScheduleUi.DAILY, (dialog as DialogState.FrequencyPicker).selectedSchedule)
            assertTrue(dialog.allowOnMobileData)
        }

    @Test
    fun `DismissFrequencyPicker flips dialogState to Hidden`() = runTest(testDispatcher) {
        store.stateFlow.value = store.stateFlow.value.copy(
            dialogState = DialogState.FrequencyPicker(
                selectedSchedule = BackupScheduleUi.WEEKLY,
                allowOnMobileData = false,
            ),
        )

        handler.invoke(Action.Backup.DismissFrequencyPicker)

        assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
    }

    @Test
    fun `SaveFrequency Daily false updates repo and schedules periodic`() =
        runTest(testDispatcher) {
            handler.invoke(
                Action.Backup.SaveFrequency(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = false,
                ),
            )

            coVerify { preferencesRepository.setSchedule(BackupSchedule.Daily) }
            coVerify { preferencesRepository.setAllowOnMobileData(false) }
            coVerify {
                autoBackupController.schedulePeriodic(
                    match { it.schedule == BackupSchedule.Daily && !it.allowOnMobileData },
                )
            }
            coVerify(exactly = 0) { autoBackupController.cancelPeriodic() }
            assertEquals(DialogState.Hidden, store.stateFlow.value.dialogState)
        }

    @Test
    fun `SaveFrequency preserves persisted non-sentinel fields in scheduled snapshot`() =
        runTest(testDispatcher) {
            preferencesFlow.value = BackupPreferences.DEFAULT.copy(
                schedule = BackupSchedule.Weekly,
                allowOnMobileData = false,
                autoBackupBootstrapped = true,
                lastAttemptAtEpochMs = 1_700_000_000_000L,
                lastSuccessAtEpochMs = 1_700_000_000_000L,
                lastError = BackupErrorCode.NetworkUnavailable,
            )

            handler.invoke(
                Action.Backup.SaveFrequency(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = true,
                ),
            )

            // The snapshot handed to schedulePeriodic must carry the persisted
            // non-sentinel fields; DEFAULT-based construction would have zeroed
            // them out.
            coVerify {
                autoBackupController.schedulePeriodic(
                    match { snapshot ->
                        snapshot.schedule == BackupSchedule.Daily &&
                            snapshot.allowOnMobileData &&
                            snapshot.autoBackupBootstrapped &&
                            snapshot.lastAttemptAtEpochMs == 1_700_000_000_000L &&
                            snapshot.lastSuccessAtEpochMs == 1_700_000_000_000L &&
                            snapshot.lastError == BackupErrorCode.NetworkUnavailable
                    },
                )
            }
        }

    @Test
    fun `SaveFrequency ManualOnly cancels periodic and does not schedule`() =
        runTest(testDispatcher) {
            handler.invoke(
                Action.Backup.SaveFrequency(
                    schedule = BackupScheduleUi.MANUAL_ONLY,
                    allowOnMobileData = false,
                ),
            )

            coVerify { autoBackupController.cancelPeriodic() }
            coVerify(exactly = 0) { autoBackupController.schedulePeriodic(any()) }
        }

    @Test
    fun `UpdateFrequencyPickerSelection updates dialog selected fields without committing`() =
        runTest(testDispatcher) {
            store.stateFlow.value = store.stateFlow.value.copy(
                dialogState = DialogState.FrequencyPicker(
                    selectedSchedule = BackupScheduleUi.WEEKLY,
                    allowOnMobileData = false,
                ),
            )

            handler.invoke(
                Action.Backup.UpdateFrequencyPickerSelection(
                    schedule = BackupScheduleUi.DAILY,
                    allowOnMobileData = true,
                ),
            )

            val dialog = store.stateFlow.value.dialogState as DialogState.FrequencyPicker
            assertEquals(BackupScheduleUi.DAILY, dialog.selectedSchedule)
            assertTrue(dialog.allowOnMobileData)
            coVerify(exactly = 0) { preferencesRepository.setSchedule(any()) }
            coVerify(exactly = 0) { autoBackupController.schedulePeriodic(any()) }
        }

    @Test
    fun `first-sign-in bootstrap sets defaults schedules periodic enqueues one-time emits snackbar`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(autoBackupBootstrapped = false)

            handler.invoke(Action.Backup.ObserveAuth)
            authFlow.value = BackupAuthDomain.Authenticated(
                AccountDomain("first@example.com", "First"),
            )

            coVerify { preferencesRepository.setSchedule(BackupSchedule.Weekly) }
            coVerify { preferencesRepository.setAllowOnMobileData(false) }
            coVerify { preferencesRepository.setAutoBackupBootstrapped(true) }
            coVerify { autoBackupController.schedulePeriodic(any()) }
            coVerify { autoBackupController.enqueueOneTime() }
            assertTrue(
                store.events.any { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "expected ShowAutoBackupEnabledSnackbarRequested in $${store.events}",
            )
        }

    @Test
    fun `re-sign-in after bootstrap does NOT emit snackbar but does rehydrate periodic`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(
                autoBackupBootstrapped = true,
                schedule = BackupSchedule.Daily,
            )

            handler.invoke(Action.Backup.ObserveAuth)
            authFlow.value = BackupAuthDomain.Authenticated(
                AccountDomain("repeat@example.com", "Repeat"),
            )

            coVerify(exactly = 0) { preferencesRepository.setAutoBackupBootstrapped(true) }
            coVerify { autoBackupController.schedulePeriodic(any()) }
            coVerify(exactly = 0) { autoBackupController.enqueueOneTime() }
            assertTrue(
                store.events.none { it is Event.ShowAutoBackupEnabledSnackbarRequested },
                "snackbar should NOT be re-emitted after bootstrap",
            )
        }

    @Test
    fun `re-sign-in clears AuthRevoked lastError`() = runTest(testDispatcher) {
        preferencesFlow.value = preferencesFlow.value.copy(
            autoBackupBootstrapped = true,
            schedule = BackupSchedule.Weekly,
            lastError = BackupErrorCode.AuthRevoked,
        )

        handler.invoke(Action.Backup.ObserveAuth)
        authFlow.value = BackupAuthDomain.Authenticated(
            AccountDomain("rehydrate@example.com", "Rehydrate"),
        )

        coVerify { preferencesRepository.setLastError(null) }
    }

    @Test
    fun `ObservePreferences populates state backupPreferences with mapped data`() =
        runTest(testDispatcher) {
            preferencesFlow.value = preferencesFlow.value.copy(
                schedule = BackupSchedule.Weekly,
                allowOnMobileData = true,
                lastError = BackupErrorCode.AuthRevoked,
            )

            handler.invoke(Action.Backup.ObservePreferences)

            val ui = store.stateFlow.value.backupPreferences
            assertNotNull(ui)
            assertEquals(BackupScheduleUi.WEEKLY, ui!!.schedule)
            assertTrue(ui.allowOnMobileData)
            assertTrue(ui.isAuthPaused)
        }

    @Test
    fun `ConfirmSignOut cancels periodic before signing out`() = runTest(testDispatcher) {
        coEvery { interactor.signOut() } returns BackupResult.Success(Unit)

        handler.invoke(Action.Backup.ConfirmSignOut)

        coVerify { autoBackupController.cancelPeriodic() }
        coVerify { interactor.signOut() }
    }

    @Test
    fun `ObserveRestoreState pipes preserved-backup availability into canRevertLastRestore`() =
        runTest(testDispatcher) {
            val availabilityFlow = MutableStateFlow(false)
            every {
                restoreStateRepository.observePreRestoreBackupAvailable()
            } returns availabilityFlow
            every { snapshotProvider.hasPreRestoreBackup() } returns true

            handler.invoke(Action.Backup.ObserveRestoreState)
            runCurrent()
            assertEquals(false, store.stateFlow.value.canRevertLastRestore)

            availabilityFlow.value = true
            runCurrent()
            assertEquals(true, store.stateFlow.value.canRevertLastRestore)

            availabilityFlow.value = false
            runCurrent()
            assertEquals(false, store.stateFlow.value.canRevertLastRestore)
        }

    @Test
    fun `ObserveRestoreState hides row and clears flag when cache evicted preserved file`() =
        runTest(testDispatcher) {
            val availabilityFlow = MutableStateFlow(true)
            every {
                restoreStateRepository.observePreRestoreBackupAvailable()
            } returns availabilityFlow
            // DataStore flag says available, but the file is gone (cache eviction).
            every { snapshotProvider.hasPreRestoreBackup() } returns false

            handler.invoke(Action.Backup.ObserveRestoreState)
            runCurrent()

            assertEquals(false, store.stateFlow.value.canRevertLastRestore)
            coVerify(exactly = 1) { restoreStateRepository.clearPreRestoreBackupAvailable() }
        }

    @Test
    fun `RequestRevertLastRestore publishes UndoRestoreConfirmation with persisted date`() =
        runTest(testDispatcher) {
            coEvery { restoreStateRepository.getPreRestoreOriginalDate() } returns 1_700_000_000_000L

            handler.invoke(Action.Backup.RequestRevertLastRestore)
            runCurrent()

            coVerify(exactly = 1) {
                appDialogPublisher.publish(
                    io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
                        .UndoRestoreConfirmation(originalDataDateEpochMs = 1_700_000_000_000L),
                )
            }
        }

    @Test
    fun `RequestRevertLastRestore is a no-op when no original date is persisted`() =
        runTest(testDispatcher) {
            coEvery { restoreStateRepository.getPreRestoreOriginalDate() } returns null

            handler.invoke(Action.Backup.RequestRevertLastRestore)
            runCurrent()

            coVerify(exactly = 0) { appDialogPublisher.publish(any()) }
        }
}
