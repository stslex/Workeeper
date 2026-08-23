// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.closeAppDatabase
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class DatabaseSnapshotProviderImplTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var provider: DatabaseSnapshotProviderImpl

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Wipe any leftover db from previous test runs in this Robolectric sandbox.
        context.deleteDatabase(AppDatabase.NAME)
        database = Room
            .databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .allowMainThreadQueries()
            .build()
        provider = DatabaseSnapshotProviderImpl(
            appDatabase = database,
            context = context,
            dispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        // Room 3 removed the public `isOpen`; close() is idempotent on a closed/never-opened DB.
        database.close()
        context.deleteDatabase(AppDatabase.NAME)
        // Clean up any snapshots written into the databases dir.
        val dbDir = context.getDatabasePath(AppDatabase.NAME).parentFile
        dbDir?.listFiles()?.forEach { it.delete() }
        // Clean preserved snapshots written into cacheDir.
        context.cacheDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `liveDatabaseFile resolves to the app database path`() {
        assertEquals(
            context.getDatabasePath(AppDatabase.NAME).absolutePath,
            provider.liveDatabaseFile().absolutePath,
        )
    }

    @Test
    fun `captureSnapshot truncates WAL sidecar and snapshot opens with persisted data`() =
        runTest {
            // Pre-insert so the WAL has unsynced bytes — without this the test
            // would still pass even if wal_checkpoint were broken (false-positive).
            database.tagDao.insertAll(
                listOf(
                    TagEntity(uuid = Uuid.random(), name = "Push"),
                    TagEntity(uuid = Uuid.random(), name = "Pull"),
                    TagEntity(uuid = Uuid.random(), name = "Legs"),
                ),
            )

            val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
            val walFile = File(dbDir, "${AppDatabase.NAME}-wal")
            assertTrue(walFile.exists(), "WAL sidecar must exist after DAO write")
            assertTrue(
                walFile.length() > 0L,
                "WAL must contain unsynced bytes pre-capture; was ${walFile.length()}",
            )

            val target = File(dbDir, "snapshot_target.db")
            target.delete()

            val result = provider.captureSnapshot(target)
            assertEquals(BackupResult.Success(Unit), result)

            assertEquals(
                0L,
                walFile.length(),
                "WAL sidecar must be truncated by wal_checkpoint(TRUNCATE)",
            )
            assertTrue(target.exists(), "Snapshot file must exist post-capture")
            assertTrue(target.length() > 0L, "Snapshot file must be non-empty")

            database.close()
            val snapshotDb = Room
                .databaseBuilder<AppDatabase>(context, target.name)
                .allowMainThreadQueries()
                .build()
            val tagsFromSnapshot = snapshotDb.tagDao.observeAll().first()
            assertEquals(
                setOf("Push", "Pull", "Legs"),
                tagsFromSnapshot.map { it.name }.toSet(),
            )
            snapshotDb.close()
        }

    @Test
    fun `second captureSnapshot reflects only entities present at second capture`() =
        runTest {
            val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "First"))

            val firstTarget = File(dbDir, "snapshot_first.db")
            assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(firstTarget))

            database.tagDao.insertAll(
                listOf(
                    TagEntity(uuid = Uuid.random(), name = "Second"),
                    TagEntity(uuid = Uuid.random(), name = "Third"),
                ),
            )

            val secondTarget = File(dbDir, "snapshot_second.db")
            assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(secondTarget))

            database.close()

            val firstSnapshot = Room
                .databaseBuilder<AppDatabase>(context, firstTarget.name)
                .allowMainThreadQueries()
                .build()
            assertEquals(
                setOf("First"),
                firstSnapshot.tagDao.observeAll().first().map { it.name }.toSet(),
            )
            firstSnapshot.close()

            val secondSnapshot = Room
                .databaseBuilder<AppDatabase>(context, secondTarget.name)
                .allowMainThreadQueries()
                .build()
            assertEquals(
                setOf("First", "Second", "Third"),
                secondSnapshot.tagDao.observeAll().first().map { it.name }.toSet(),
            )
            secondSnapshot.close()
        }

    @Test
    fun `peekSnapshotSchemaVersion matches currentSchemaVersion for fresh capture`() = runTest {
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        val target = File(dbDir, "snapshot_peek.db")

        // Touch the writable database so Room writes user_version, then capture.
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "Anything"))
        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(target))

        val current = provider.currentSchemaVersion()
        val peeked = provider.peekSnapshotSchemaVersion(target)
        assertTrue(peeked is BackupResult.Success, "peek must succeed, was $peeked")
        assertEquals(current, (peeked as BackupResult.Success).data)
    }

    @Test
    fun `validateSnapshotForRestore with non-SQLite source returns CorruptedBackup`() = runTest {
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        val bogus = File(dbDir, "bogus.db")
        bogus.writeText("this is definitely not a sqlite file")

        val result = provider.validateSnapshotForRestore(bogus)
        assertTrue(result is BackupResult.Failure)
        assertTrue(
            (result as BackupResult.Failure).error is BackupError.CorruptedBackup,
            "expected CorruptedBackup, got ${result.error}",
        )
    }

    @Test
    fun `validateSnapshotForRestore returns BackupTooNew when source schema is newer`() = runTest {
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        // Capture a valid snapshot first.
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "anything"))
        val source = File(dbDir, "snapshot_future.db")
        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(source))

        // Doctor the snapshot's user_version PRAGMA to a future version.
        val futureVersion = provider.currentSchemaVersion() + 100
        android.database.sqlite.SQLiteDatabase
            .openDatabase(source.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
            .use { it.version = futureVersion }

        val result = provider.validateSnapshotForRestore(source)
        assertTrue(result is BackupResult.Failure)
        val error = (result as BackupResult.Failure).error
        assertTrue(
            error is BackupError.BackupTooNew,
            "expected BackupTooNew, got $error",
        )
        assertEquals(futureVersion, (error as BackupError.BackupTooNew).backupSchemaVersion)
    }

    @Test
    fun `restore transaction sequence replaces live db with snapshot contents`() = runTest {
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "Original"))
        val source = File(dbDir, "snapshot_restore.db")
        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(source))

        // Mutate live DB after capture so we can verify the restore reverts it.
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "Drift"))
        assertEquals(
            setOf("Original", "Drift"),
            database.tagDao.observeAll().first().map { it.name }.toSet(),
        )

        // The runtime-owned transaction sequence (Phase 5 R2, spec §8.5): validate through the
        // still-open db, close (terminal), then the pure file mechanics.
        assertEquals(BackupResult.Success(Unit), provider.validateSnapshotForRestore(source))
        closeAppDatabase(database)
        val result = provider.replaceLiveDatabaseFile(source)
        assertEquals(BackupResult.Success(Unit), result)

        // Rebuild Room — the previous handle is terminal after the close.
        val restored = Room
            .databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val names = restored.tagDao.observeAll().first().map { it.name }.toSet()
            assertEquals(setOf("Original"), names)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `a reserved snapshot promoted onto the undo slot is detected by getPreRestoreBackupFile`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreserveMe"))

            val result = stageCanonicalSnapshot(provider)
            assertTrue(result is BackupResult.Success, "expected Success, got $result")
            val preservedFile = (result as BackupResult.Success).data

            assertTrue(preservedFile.exists(), "preserved file should exist on disk")
            assertEquals(context.cacheDir, preservedFile.parentFile)
            assertTrue(provider.getPreRestoreBackupFile() != null)
        }

    @Test
    fun `a reserved snapshot is a self-contained SQLite copy at the live schema`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreservedRow"))
            val result = stageCanonicalSnapshot(provider)
            assertTrue(result is BackupResult.Success)
            val preserved = (result as BackupResult.Success).data

            // The preserved file must be a valid SQLite database at the same
            // schema version as the live db (WAL checkpointed, no missing pages).
            val peek = provider.peekSnapshotSchemaVersion(preserved)
            assertTrue(peek is BackupResult.Success, "peek should succeed on preserved file")
            assertEquals(provider.currentSchemaVersion(), (peek as BackupResult.Success).data)
        }

    @Test
    fun `promotion COPIES - the journal-named reservation survives with its exact bytes`() =
        runTest {
            // R4 blocker A, real files, distinct sentinels. The reservation (A) is the file the
            // still-`Prepared` journal names; the canonical slot (B) belongs to an OLDER
            // attempt. The old move-based promotion destroyed A at its FIRST step, so a process
            // death anywhere before the durable `Committed` record left the journal pointing at
            // a missing file — and recovery silently fell back to B. Copy-based promotion keeps
            // A intact across EVERY crash point of the promotion: at any interruption the next
            // launch still recovers from A, never B.
            val reservationA = File(context.cacheDir, "rollback_reservation_r4.db")
                .apply { writeText("SENTINEL-A-TRUE-PRE-ATTEMPT") }
            File(context.cacheDir, "pre_restore_backup.db").writeText("SENTINEL-B-OLDER")

            val promoted = provider.promoteRollbackReservation(reservationA)

            assertEquals(BackupResult.Success(Unit), promoted)
            assertTrue(
                reservationA.exists(),
                "the reservation must SURVIVE the promotion — the journal still names it " +
                    "until the runtime durably records Committed",
            )
            assertEquals("SENTINEL-A-TRUE-PRE-ATTEMPT", reservationA.readText())
            assertEquals(
                "SENTINEL-A-TRUE-PRE-ATTEMPT",
                File(context.cacheDir, "pre_restore_backup.db").readText(),
                "the canonical slot now holds this attempt's pre-image",
            )
            assertFalse(
                File(context.cacheDir, "pre_restore_backup.db.promoting").exists(),
                "no staging residue is left behind",
            )
        }

    @Test
    fun `a stale promoting file from a crashed promotion is discarded, never promoted`() =
        runTest {
            // Deterministic handling of `.promoting` debris (R4): a crashed promotion may leave
            // a partial staging file. It must never redirect anything — the next promotion
            // deletes it before staging its own copy, recovery never reads it (the canonical
            // lookup is an exact-name match), and the promoted content comes from the
            // reservation actually passed in.
            File(context.cacheDir, "pre_restore_backup.db.promoting")
                .writeText("GARBAGE-FROM-A-CRASHED-PROMOTION")
            val reservation = File(context.cacheDir, "rollback_reservation_fresh.db")
                .apply { writeText("FRESH-RESERVATION") }

            val promoted = provider.promoteRollbackReservation(reservation)

            assertEquals(BackupResult.Success(Unit), promoted)
            assertEquals(
                "FRESH-RESERVATION",
                File(context.cacheDir, "pre_restore_backup.db").readText(),
                "the canonical content comes from the reservation, never from stale staging",
            )
            assertFalse(File(context.cacheDir, "pre_restore_backup.db.promoting").exists())
            assertEquals(
                null,
                provider.getPreRestoreBackupFile()?.name?.takeIf { it.endsWith(".promoting") },
                "the canonical lookup can never resolve to a staging file",
            )
        }

    @Test
    fun `getPreRestoreBackupFile returns null when no file was preserved`() = runTest {
        // The CorruptedBackup mapping for this case moved to the runtime transaction, which
        // resolves the source through this accessor before touching anything.
        assertFalse(provider.getPreRestoreBackupFile() != null)
        assertEquals(null, provider.getPreRestoreBackupFile())
    }

    @Test
    fun `rollback transaction sequence swaps live db with preserved contents and consumes file`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "BeforeRestore"))
            assertTrue(stageCanonicalSnapshot(provider) is BackupResult.Success)

            // Simulate a restore: mutate the live db so it differs from the preserved snapshot.
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "AfterRestore"))
            assertEquals(
                setOf("BeforeRestore", "AfterRestore"),
                database.tagDao.observeAll().first().map { it.name }.toSet(),
            )

            // The runtime-owned rollback sequence: resolve source, close (terminal), replace,
            // consume — same net effect as the pre-split copy+rename+delete.
            val rollbackSource = requireNotNull(provider.getPreRestoreBackupFile())
            closeAppDatabase(database)
            assertEquals(BackupResult.Success(Unit), provider.replaceLiveDatabaseFile(rollbackSource))
            provider.deletePreRestoreBackup()

            // Preserved file consumed.
            assertFalse(provider.getPreRestoreBackupFile() != null)

            // Live db reverts to pre-restore state.
            val restored = Room
                .databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
                .allowMainThreadQueries()
                .build()
            try {
                assertEquals(
                    setOf("BeforeRestore"),
                    restored.tagDao.observeAll().first().map { it.name }.toSet(),
                )
            } finally {
                restored.close()
            }
        }

    @Test
    fun `deletePreRestoreBackup removes the file when present`() = runTest {
        assertTrue(stageCanonicalSnapshot(provider) is BackupResult.Success)
        assertTrue(provider.getPreRestoreBackupFile() != null)

        provider.deletePreRestoreBackup()
        assertFalse(provider.getPreRestoreBackupFile() != null)
    }

    @Test
    fun `deletePreRestoreBackup is a no-op when no file exists`() = runTest {
        assertFalse(provider.getPreRestoreBackupFile() != null)
        // Just verify no exception.
        provider.deletePreRestoreBackup()
        assertFalse(provider.getPreRestoreBackupFile() != null)
    }

    @Test
    fun `preserveDbBeforeMigration copies the live db file into cacheDir without Room`() =
        runTest {
            // Seed a row via Room so the live .db file has content on disk.
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreMigration"))
            // Close Room so the file is not open exclusively (mirrors Scenario 2
            // pre-flight: Room has not yet been opened on this launch).
            database.close()

            val preserved = provider.preserveDbBeforeMigration()
            assertNotNull(preserved)
            assertTrue(preserved!!.exists())
            assertEquals(context.cacheDir, preserved.parentFile)
            assertNotNull(provider.getPreMigrationBackupFile())
            // The preserved file is a valid SQLite database (peek opens it
            // standalone without Room).
            val peek = provider.peekSnapshotSchemaVersion(preserved)
            assertTrue(peek is BackupResult.Success, "preserved file must be valid SQLite")
        }

    @Test
    fun `preserveDbBeforeMigration returns null when no live db file exists`() = runTest {
        database.close()
        context.deleteDatabase(AppDatabase.NAME)
        assertEquals(null, provider.preserveDbBeforeMigration())
        assertNull(provider.getPreMigrationBackupFile())
    }

    @Test
    fun `preserveDbBeforeMigration runs wal_checkpoint via direct SQLite`() = runTest {
        // Seed a row and confirm the WAL sidecar carries unsynced bytes before
        // the snapshot runs — pre-condition for the checkpoint path to do work.
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreCheckpoint"))
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        val walFile = File(dbDir, "${AppDatabase.NAME}-wal")
        assertTrue(walFile.exists(), "WAL sidecar must exist after DAO write")
        assertTrue(
            walFile.length() > 0L,
            "WAL must contain unsynced bytes pre-snapshot; was ${walFile.length()}",
        )
        // Close Room — the checkpoint inside preserveDbBeforeMigration must work
        // through a direct SQLite open, not through the (closed) Room helper.
        database.close()

        val preserved = provider.preserveDbBeforeMigration()
        assertNotNull(preserved)

        // The row must round-trip through the snapshot. Read via direct SQLite —
        // Room.databaseBuilder resolves paths against the databases dir, not the
        // cacheDir location where the snapshot lives.
        val snapshotDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            preserved!!.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        val names = snapshotDb.rawQuery("SELECT name FROM tag_table", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        snapshotDb.close()
        assertEquals(setOf("PreCheckpoint"), names)
    }

    @Test
    fun `getPreMigrationBackupFile returns the file when present and null when absent`() =
        runTest {
            assertEquals(null, provider.getPreMigrationBackupFile())

            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "ForExport"))
            database.close()
            assertNotNull(provider.preserveDbBeforeMigration())

            val file = provider.getPreMigrationBackupFile()
            assertNotNull(file)
            assertTrue(file!!.exists())
        }

    @Test
    fun `deletePreMigrationBackup removes the file and is idempotent`() = runTest {
        // Force the .db file onto disk via an insert before closing Room.
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "ToDelete"))
        database.close()
        assertNotNull(provider.preserveDbBeforeMigration())
        assertNotNull(provider.getPreMigrationBackupFile())

        provider.deletePreMigrationBackup()
        assertNull(provider.getPreMigrationBackupFile())

        // Idempotent — second call no-ops without exception.
        provider.deletePreMigrationBackup()
        assertNull(provider.getPreMigrationBackupFile())
    }

    @Test
    fun `pre_migration and pre_restore slots have independent lifecycles`() = runTest {
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "Independence"))
        // Scenario 1: preserve pre-restore (must run while Room is open — uses
        // the live appDatabase to WAL-checkpoint).
        assertTrue(stageCanonicalSnapshot(provider) is BackupResult.Success)
        // Scenario 2: preserve pre-migration via direct copy (close Room first).
        database.close()
        assertNotNull(provider.preserveDbBeforeMigration())

        assertTrue(provider.getPreRestoreBackupFile() != null)
        assertNotNull(provider.getPreMigrationBackupFile())

        // Deleting pre-migration does not affect pre-restore.
        provider.deletePreMigrationBackup()
        assertNull(provider.getPreMigrationBackupFile())
        assertTrue(provider.getPreRestoreBackupFile() != null)

        // Inverse direction: re-create pre-migration, delete pre-restore, both
        // remain independent.
        assertNotNull(provider.preserveDbBeforeMigration())
        provider.deletePreRestoreBackup()
        assertFalse(provider.getPreRestoreBackupFile() != null)
        assertNotNull(provider.getPreMigrationBackupFile())
    }

    /**
     * Stages the canonical undo slot the way the runtime does since R3: reserve a per-attempt
     * snapshot, then promote it (spec §8.5a). Replaces the removed `preserveCurrentDb()`, whose
     * single-canonical-path copy is exactly what let two concurrent restores collide.
     */
    private suspend fun stageCanonicalSnapshot(
        provider: DatabaseSnapshotProvider,
    ): BackupResult<File> {
        val reserved = provider.reserveRollbackSnapshot("test-attempt")
        if (reserved is BackupResult.Failure) return reserved
        val file = (reserved as BackupResult.Success).data
        return when (val promoted = provider.promoteRollbackReservation(file)) {
            is BackupResult.Success -> {
                // The runtime's step 4 (R4): the retained reservation is discarded only after
                // the durable Committed record — mirrored here so the helper leaves the same
                // end state the real transaction does.
                file.delete()
                BackupResult.Success(requireNotNull(provider.getPreRestoreBackupFile()))
            }

            is BackupResult.Failure -> promoted
        }
    }
}
