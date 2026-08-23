// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
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
 * Bind the `internal` store constructor to a fresh temp file per test, for the same reason as
 * `BackupPreferencesRepositoryImplTest`: the provider's memoization is static and process-lifetime,
 * so routing these tests through it would share one store across every test method. The provider
 * routing itself is pinned on device by `app/app` androidTest `AppScopeDataStoreSingletonTest`.
 *
 * The journal tests (Phase 5 R3) drive the public API for everything the runtime does, and write
 * RAW preference keys only where the scenario cannot be produced through the API at all — a
 * pre-R3 install's legacy `restore_in_progress` marker, and a corrupt/partial record. Those raw
 * key names are duplicated here on purpose: they are wire format, and a silent rename in the impl
 * must fail a test rather than silently strand a user mid-restore.
 */
internal class RestoreStateRepositoryImplTest {

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: RestoreStateRepositoryImpl

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile(PREFS_FILE_NAME, ".preferences_pb").also { it.delete() }
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        repo = RestoreStateRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `initial state has no attempt and no preserved backup`() = runTest {
        assertNull(repo.getAttempt())
        assertFalse(repo.observePreRestoreBackupAvailable().first())
        assertNull(repo.getPreRestoreOriginalDate())
    }

    @Test
    fun `beginAttempt persists identity, kind, context and rollback path atomically`() = runTest {
        val attempt = RestoreAttempt(
            id = "attempt-a",
            kind = RestoreAttempt.Kind.Restore,
            phase = RestoreAttempt.Phase.Prepared,
            context = RestoreInProgressContext(
                backupSchemaVersion = 6,
                backupCreatedAtEpochMs = 1_700_000_000_000L,
                backupAppVersion = "1.2.3",
                startedAtEpochMs = 1_710_000_000_000L,
            ),
            rollbackSnapshotPath = "/data/cache/rollback_attempt-a.db",
        )

        assertTrue(repo.beginAttempt(attempt))

        assertEquals(attempt, repo.getAttempt())
    }

    @Test
    fun `a Rollback attempt round-trips with a null context`() = runTest {
        val attempt = RestoreAttempt(
            id = "attempt-rollback",
            kind = RestoreAttempt.Kind.Rollback,
            phase = RestoreAttempt.Phase.Prepared,
            context = null,
            rollbackSnapshotPath = "/data/cache/pre_restore_backup.db",
        )

        assertTrue(repo.beginAttempt(attempt))

        assertEquals(attempt, repo.getAttempt())
    }

    @Test
    fun `a DIFFERENT unresolved attempt cannot claim the slot`() = runTest {
        val first = attempt(id = "attempt-a")
        val second = attempt(id = "attempt-b", schemaVersion = 9, appVersion = "9.9.9")

        assertTrue(repo.beginAttempt(first))
        assertFalse(repo.beginAttempt(second))

        // Nothing of B leaked into the slot: A still owns every field.
        assertEquals(first, repo.getAttempt())
    }

    @Test
    fun `re-claiming with the same id is idempotent`() = runTest {
        val claim = attempt(id = "attempt-a")

        assertTrue(repo.beginAttempt(claim))
        assertTrue(repo.beginAttempt(claim))

        assertEquals(claim, repo.getAttempt())
    }

    @Test
    fun `re-claiming with the same id and no rollback path clears the stale path`() = runTest {
        val withPath = attempt(id = "attempt-a", rollbackSnapshotPath = "/data/cache/stale.db")
        assertTrue(repo.beginAttempt(withPath))

        val withoutPath = withPath.copy(rollbackSnapshotPath = null)
        assertTrue(repo.beginAttempt(withoutPath))

        assertNull(repo.getAttempt()?.rollbackSnapshotPath)
    }

    @Test
    fun `beginAttempt rejects an attempt that is not Prepared`() = runTest {
        val committed = attempt(id = "attempt-a").copy(phase = RestoreAttempt.Phase.Committed)

        val failure = runCatching { repo.beginAttempt(committed) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(repo.getAttempt())
    }

    @Test
    fun `recordAttemptCommitted advances only the owning attempt`() = runTest {
        val owner = attempt(id = "attempt-a")
        assertTrue(repo.beginAttempt(owner))

        assertFalse(repo.recordAttemptCommitted("attempt-b"))
        assertEquals(RestoreAttempt.Phase.Prepared, repo.getAttempt()?.phase)

        assertTrue(repo.recordAttemptCommitted("attempt-a"))
        assertEquals(RestoreAttempt.Phase.Committed, repo.getAttempt()?.phase)
        // Advancing the phase leaves the rest of the record untouched.
        assertEquals(owner.copy(phase = RestoreAttempt.Phase.Committed), repo.getAttempt())
    }

    @Test
    fun `recordAttemptCommitted on a free slot returns false`() = runTest {
        assertFalse(repo.recordAttemptCommitted("attempt-a"))
        assertNull(repo.getAttempt())
    }

    @Test
    fun `resolveAttempt clears only for the owner`() = runTest {
        val owner = attempt(id = "attempt-a")
        assertTrue(repo.beginAttempt(owner))

        assertFalse(repo.resolveAttempt("attempt-b"))
        assertEquals(owner, repo.getAttempt())

        assertTrue(repo.resolveAttempt("attempt-a"))
        assertNull(repo.getAttempt())
    }

    @Test
    fun `legacy restore_in_progress migrates to a Prepared attempt`() = runTest {
        val context = RestoreInProgressContext(
            backupSchemaVersion = 4,
            backupCreatedAtEpochMs = 1_600_000_000_000L,
            backupAppVersion = "0.9.1",
            startedAtEpochMs = 1_610_000_000_000L,
        )
        writeLegacyInProgress(context)

        // A pre-R3 marker carries no phase, so its outcome is unknown: it must read as Prepared
        // (recovery), never as a success verdict.
        assertEquals(
            RestoreAttempt(
                id = LEGACY_ATTEMPT_ID,
                kind = RestoreAttempt.Kind.Restore,
                phase = RestoreAttempt.Phase.Prepared,
                context = context,
                rollbackSnapshotPath = null,
            ),
            repo.getAttempt(),
        )

        // The legacy marker owns the slot: a new attempt cannot claim it while unresolved.
        assertFalse(repo.beginAttempt(attempt(id = "attempt-new")))
        assertEquals(LEGACY_ATTEMPT_ID, repo.getAttempt()?.id)

        // ACTUAL behavior pinned: a legacy marker has no id, so the FIRST resolver clears it —
        // an arbitrary attempt id succeeds, not just the synthetic LEGACY_ATTEMPT_ID. Without
        // that, the migration state would outlive every attempt.
        assertTrue(repo.resolveAttempt("some-unrelated-attempt-id"))
        assertNull(repo.getAttempt())
    }

    @Test
    fun `resolveAttempt clears the legacy mutation-interrupted flag`() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] = true
            prefs[KEY_LEGACY_MUTATION_INTERRUPTED] = true
        }

        assertTrue(repo.resolveAttempt(LEGACY_ATTEMPT_ID))

        val prefs = dataStore.data.first()
        assertNull(prefs[KEY_LEGACY_RESTORE_IN_PROGRESS])
        assertNull(prefs[KEY_LEGACY_MUTATION_INTERRUPTED])
        assertNull(repo.getAttempt())
    }

    @Test
    fun `an unparsable phase reads as Prepared`() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_ATTEMPT_ID] = "attempt-a"
            prefs[KEY_ATTEMPT_KIND] = RestoreAttempt.Kind.Restore.name
            prefs[KEY_ATTEMPT_PHASE] = "Committed_but_not_really"
        }

        assertEquals(RestoreAttempt.Phase.Prepared, repo.getAttempt()?.phase)
    }

    @Test
    fun `an unparsable kind reads as Restore`() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_ATTEMPT_ID] = "attempt-a"
            prefs[KEY_ATTEMPT_KIND] = "Teleport"
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Committed.name
        }

        assertEquals(RestoreAttempt.Kind.Restore, repo.getAttempt()?.kind)
    }

    @Test
    fun `an attempt whose context is partially written reads back with a null context`() = runTest {
        // Defensive: the impl writes the four context fields in one edit, so a partial record is
        // only reachable through a torn/legacy file. Any missing field collapses to a null
        // context rather than a half-populated one.
        dataStore.edit { prefs ->
            prefs[KEY_ATTEMPT_ID] = "attempt-a"
            prefs[KEY_ATTEMPT_KIND] = RestoreAttempt.Kind.Restore.name
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Prepared.name
            prefs[KEY_BACKUP_SCHEMA_VERSION] = 6
            prefs[KEY_BACKUP_CREATED_AT] = 1L
        }

        val attempt = repo.getAttempt()
        assertEquals("attempt-a", attempt?.id)
        assertNull(attempt?.context)
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
    fun `pre-restore availability and the attempt journal are independent`() = runTest {
        // Claiming or resolving an attempt must not touch pre_restore_backup_available,
        // or vice versa.
        val owner = attempt(id = "attempt-a")
        repo.markPreRestoreBackupAvailable(1_700_000_000_000L)
        assertTrue(repo.beginAttempt(owner))

        assertTrue(repo.observePreRestoreBackupAvailable().first())
        assertEquals(owner, repo.getAttempt())

        assertTrue(repo.resolveAttempt("attempt-a"))
        assertNull(repo.getAttempt())
        // Pre-restore availability unaffected by resolving the attempt.
        assertTrue(repo.observePreRestoreBackupAvailable().first())
        assertEquals(1_700_000_000_000L, repo.getPreRestoreOriginalDate())
    }

    private suspend fun writeLegacyInProgress(context: RestoreInProgressContext) {
        dataStore.edit { prefs ->
            prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] = true
            prefs[KEY_BACKUP_SCHEMA_VERSION] = context.backupSchemaVersion
            prefs[KEY_BACKUP_CREATED_AT] = context.backupCreatedAtEpochMs
            prefs[KEY_BACKUP_APP_VERSION] = context.backupAppVersion
            prefs[KEY_RESTORE_STARTED_AT] = context.startedAtEpochMs
        }
    }

    private fun attempt(
        id: String,
        kind: RestoreAttempt.Kind = RestoreAttempt.Kind.Restore,
        schemaVersion: Int = 6,
        appVersion: String = "1.2.3",
        rollbackSnapshotPath: String? = "/data/cache/rollback_$id.db",
    ): RestoreAttempt = RestoreAttempt(
        id = id,
        kind = kind,
        phase = RestoreAttempt.Phase.Prepared,
        context = RestoreInProgressContext(
            backupSchemaVersion = schemaVersion,
            backupCreatedAtEpochMs = 1_700_000_000_000L,
            backupAppVersion = appVersion,
            startedAtEpochMs = 1_710_000_000_000L,
        ),
        rollbackSnapshotPath = rollbackSnapshotPath,
    )

    private companion object {
        const val PREFS_FILE_NAME = "restore_state_prefs"

        /** Mirrors `RestoreStateRepositoryImpl.LEGACY_ATTEMPT_ID` (private there). */
        const val LEGACY_ATTEMPT_ID = "legacy-restore-in-progress"

        // Raw wire-format keys, duplicated from the impl's private companion on purpose.
        val KEY_ATTEMPT_ID = stringPreferencesKey("restore_attempt_id")
        val KEY_ATTEMPT_KIND = stringPreferencesKey("restore_attempt_kind")
        val KEY_ATTEMPT_PHASE = stringPreferencesKey("restore_attempt_phase")

        val KEY_BACKUP_SCHEMA_VERSION =
            intPreferencesKey("restore_in_progress_backup_schema_version")
        val KEY_BACKUP_CREATED_AT =
            longPreferencesKey("restore_in_progress_backup_created_at_epoch_ms")
        val KEY_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_in_progress_backup_app_version")
        val KEY_RESTORE_STARTED_AT =
            longPreferencesKey("restore_in_progress_started_at_epoch_ms")

        val KEY_LEGACY_RESTORE_IN_PROGRESS = booleanPreferencesKey("restore_in_progress")
        val KEY_LEGACY_MUTATION_INTERRUPTED =
            booleanPreferencesKey("restore_mutation_interrupted")
    }
}
