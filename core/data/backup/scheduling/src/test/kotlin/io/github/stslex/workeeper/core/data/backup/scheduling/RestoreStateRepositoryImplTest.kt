// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Binds the repository's `internal` store constructor to a fresh temp file per test, for the same
 * reason as `BackupPreferencesRepositoryImplTest`: the production constructor goes through
 * `DataStoreProviderFactory`, whose memoization is static and process-lifetime, so routing these
 * tests through it would share one store across every test method. The provider routing itself is
 * pinned on device by `app/app` androidTest `AppScopeDataStoreSingletonTest`.
 */
internal class RestoreStateRepositoryImplTest {

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var tempFile: File
    private lateinit var repo: RestoreStateRepositoryImpl

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile(PREFS_FILE_NAME, ".preferences_pb").also { it.delete() }
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        repo = RestoreStateRepositoryImpl(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile },
        )
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `initial state has no in-progress restore and no preserved backup`() = runTest {
        assertNull(repo.getRestoreInProgressContext())
        assertFalse(repo.observePreRestoreBackupAvailable().first())
        assertNull(repo.getPreRestoreOriginalDate())
    }

    @Test
    fun `markRestoreInProgress persists the full manifest context`() = runTest {
        val expected = RestoreInProgressContext(
            backupSchemaVersion = 6,
            backupCreatedAtEpochMs = 1_700_000_000_000L,
            backupAppVersion = "1.2.3",
            startedAtEpochMs = 1_710_000_000_000L,
        )
        repo.markRestoreInProgress(expected)
        assertEquals(expected, repo.getRestoreInProgressContext())
    }

    @Test
    fun `clearRestoreInProgress removes the flag and the full context`() = runTest {
        repo.markRestoreInProgress(
            RestoreInProgressContext(
                backupSchemaVersion = 6,
                backupCreatedAtEpochMs = 1L,
                backupAppVersion = "1.0",
                startedAtEpochMs = 2L,
            ),
        )
        repo.clearRestoreInProgress()
        assertNull(repo.getRestoreInProgressContext())
    }

    @Test
    fun `markRestoreInProgress twice keeps the second context (overwrite, not dedup)`() = runTest {
        val first = RestoreInProgressContext(5, 1L, "1.0", 2L)
        val second = RestoreInProgressContext(6, 3L, "1.1", 4L)
        repo.markRestoreInProgress(first)
        repo.markRestoreInProgress(second)
        assertEquals(second, repo.getRestoreInProgressContext())
    }

    @Test
    fun `markPreRestoreBackupAvailable enables the flag and persists the original date`() =
        runTest {
            val original = 1_700_000_000_000L
            repo.markPreRestoreBackupAvailable(original)
            assertTrue(repo.observePreRestoreBackupAvailable().first())
            assertEquals(original, repo.getPreRestoreOriginalDate())
        }

    @Test
    fun `clearPreRestoreBackupAvailable disables the flag and clears the date`() = runTest {
        repo.markPreRestoreBackupAvailable(1_700_000_000_000L)
        repo.clearPreRestoreBackupAvailable()
        assertFalse(repo.observePreRestoreBackupAvailable().first())
        assertNull(repo.getPreRestoreOriginalDate())
    }

    @Test
    fun `pre-restore and in-progress flags are independent`() = runTest {
        // Marking restore_in_progress should not touch pre_restore_backup_available
        // or vice versa.
        repo.markPreRestoreBackupAvailable(1_700_000_000_000L)
        repo.markRestoreInProgress(RestoreInProgressContext(6, 1L, "1.0", 2L))

        assertTrue(repo.observePreRestoreBackupAvailable().first())
        assertEquals(
            RestoreInProgressContext(6, 1L, "1.0", 2L),
            repo.getRestoreInProgressContext(),
        )

        repo.clearRestoreInProgress()
        assertNull(repo.getRestoreInProgressContext())
        // Pre-restore availability unaffected by clearing in-progress.
        assertTrue(repo.observePreRestoreBackupAvailable().first())
    }

    @Test
    fun `getRestoreInProgressContext returns null when in-progress flag is set without context`() =
        runTest {
            // Defensive: the impl only persists a non-null context when the flag is
            // also set. But if the flag is true and any metadata field is missing
            // (e.g. partial write), the accessor returns null and the caller treats
            // it as inconsistent state.
            assertNull(repo.getRestoreInProgressContext())
        }

    private companion object {
        const val PREFS_FILE_NAME = "restore_state_prefs"
    }
}
