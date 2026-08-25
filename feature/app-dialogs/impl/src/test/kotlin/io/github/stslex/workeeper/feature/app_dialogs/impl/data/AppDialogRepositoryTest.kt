// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class AppDialogRepositoryTest {

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var tempFile: File
    private lateinit var repository: AppDialogRepository

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile("app_dialogs_", ".preferences_pb").also { it.delete() }
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        repository = AppDialogRepository(dataStore) { EPOCH_A }
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `currentDialog is null when no flag is set`() = runTest {
        assertNull(repository.currentDialog.first())
    }

    @Test
    fun `publish RestoreSuccess surfaces the variant with payload`() = runTest {
        val expected = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_700_000_000_000L,
            previousVersionAvailable = true,
        )
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `publish RestoreFailure surfaces the variant with reason`() = runTest {
        val expected = AppDialog.RestoreFailure(reason = BackupErrorCode.MissingMigrationPath)
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreConfirmation surfaces with date`() = runTest {
        val expected = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 1_650_000_000_000L,
        )
        repository.publish(expected)
        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `missing confirmation owner does not block publishing a valid owner`() = runTest {
        dataStore.edit { prefs ->
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] = true
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS] = 1L
        }
        val expected = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 2L,
        )

        repository.publish(expected)

        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `invalid confirmation owner does not block publishing a valid owner`() = runTest {
        dataStore.edit { prefs ->
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] = true
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS] = 1L
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER] = "invalid-owner"
        }
        val expected = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 2L,
        )

        repository.publish(expected)

        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `different confirmation owner does not block publishing the current owner`() = runTest {
        repository.publish(
            AppDialog.UndoRestoreConfirmation(
                undoRef = OTHER_UNDO_REF,
                originalDataDateEpochMs = 1L,
            ),
        )
        val expected = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 2L,
        )

        repository.publish(expected)

        assertEquals(expected, repository.currentDialog.first())
    }

    @Test
    fun `old owner dismiss preserves the newer confirmation`() = runTest {
        val oldDialog = AppDialog.UndoRestoreConfirmation(
            undoRef = OTHER_UNDO_REF,
            originalDataDateEpochMs = 1L,
        )
        val currentDialog = AppDialog.UndoRestoreConfirmation(
            undoRef = TEST_UNDO_REF,
            originalDataDateEpochMs = 2L,
        )
        repository.publish(oldDialog)
        repository.publish(currentDialog)

        repository.dismiss(oldDialog)

        assertEquals(currentDialog, repository.currentDialog.first())
    }

    @Test
    fun `publish UndoRestoreSuccess surfaces the data variant`() = runTest {
        val dialog = AppDialog.UndoRestoreSuccess()
        repository.publish(dialog)
        assertEquals(dialog, repository.currentDialog.first())
    }

    @Test
    fun `new terminal owner replaces identical payload and stale dismiss preserves it`() = runTest {
        val stale = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 100L,
            previousVersionAvailable = true,
            terminalOwner = TERMINAL_OWNER_A,
        )
        val current = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 100L,
            previousVersionAvailable = true,
            terminalOwner = TERMINAL_OWNER_B,
        )

        repository.publish(stale)
        repository.publish(current)

        assertEquals(current, repository.currentDialog.first())

        repository.dismiss(stale)

        assertEquals(current, repository.currentDialog.first())
    }

    @Test
    fun `new owned terminal payload replaces a stale pending payload`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        repository.publish(first)
        repository.publish(second)
        assertEquals(second, repository.currentDialog.first())

        repository.dismiss(first)

        assertEquals(second, repository.currentDialog.first())
    }

    @Test
    fun `foreign install epoch clears transferred restore dialogs before resolution`() = runTest {
        repository.publish(
            AppDialog.UndoRestoreConfirmation(
                undoRef = TEST_UNDO_REF,
                originalDataDateEpochMs = 123L,
            ),
        )
        val installB = AppDialogRepository(dataStore) { EPOCH_B }

        assertNull(installB.currentDialog.first())

        val prefs = dataStore.data.first()
        assertEquals(EPOCH_B.toString(), prefs[AppDialogKeys.RESTORE_DIALOG_INSTALL_EPOCH])
        assertNull(prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION])
        assertNull(prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER])
    }

    @Test
    fun `dismiss clears only the named variant's flags`() = runTest {
        val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
        // Both pending: dismiss(failure) must not clear success's flags.
        repository.publish(success)
        repository.publish(failure)
        repository.dismiss(failure)
        assertEquals(success, repository.currentDialog.first())
    }

    @Test
    fun `dismiss the only pending variant emits null`() = runTest {
        val dialog = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = false)
        repository.publish(dialog)
        repository.dismiss(dialog)
        assertNull(repository.currentDialog.first())
    }

    @Test
    fun `dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish`() =
        runTest {
            val failure = AppDialog.RestoreFailure(reason = BackupErrorCode.Unknown)
            val success = AppDialog.RestoreSuccess(restoredAtEpochMs = 1L, previousVersionAvailable = false)
            repository.publish(failure)
            repository.publish(success)
            // Failure still wins priority; the success payload is persisted underneath.
            repository.dismiss(failure)
            assertEquals(success, repository.currentDialog.first())
        }

    @Test
    fun `publish then dismiss then publish same variant succeeds (flag was cleared)`() = runTest {
        val first = AppDialog.RestoreSuccess(restoredAtEpochMs = 100L, previousVersionAvailable = true)
        val second = AppDialog.RestoreSuccess(restoredAtEpochMs = 200L, previousVersionAvailable = false)
        repository.publish(first)
        repository.dismiss(first)
        repository.publish(second)
        // Cleared between the two publishes — second now wins.
        assertEquals(second, repository.currentDialog.first())
    }

    // No process-restart test — DataStore is singleton-per-file; see documentation/testing.md.

    private companion object {
        val EPOCH_A = InstallEpoch(
            RestoreOwnerId("00000000-0000-4000-8000-000000000101"),
        )
        val EPOCH_B = InstallEpoch(
            RestoreOwnerId("00000000-0000-4000-8000-000000000102"),
        )
        val TEST_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000011"),
        )
        val OTHER_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000012"),
        )
        val TERMINAL_OWNER_A = RestoreOwnerId("00000000-0000-4000-8000-000000000021")
        val TERMINAL_OWNER_B = RestoreOwnerId("00000000-0000-4000-8000-000000000022")
    }
}
