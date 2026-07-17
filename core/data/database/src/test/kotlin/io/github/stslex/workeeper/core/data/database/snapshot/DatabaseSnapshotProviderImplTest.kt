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
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
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
                .databaseBuilder(context, AppDatabase::class.java, target.name)
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
                .databaseBuilder(context, AppDatabase::class.java, firstTarget.name)
                .allowMainThreadQueries()
                .build()
            assertEquals(
                setOf("First"),
                firstSnapshot.tagDao.observeAll().first().map { it.name }.toSet(),
            )
            firstSnapshot.close()

            val secondSnapshot = Room
                .databaseBuilder(context, AppDatabase::class.java, secondTarget.name)
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
    fun `restoreFromSnapshot with non-SQLite source returns CorruptedBackup`() = runTest {
        val dbDir = requireNotNull(context.getDatabasePath(AppDatabase.NAME).parentFile)
        val bogus = File(dbDir, "bogus.db")
        bogus.writeText("this is definitely not a sqlite file")

        val result = provider.restoreFromSnapshot(bogus)
        assertTrue(result is BackupResult.Failure)
        assertTrue(
            (result as BackupResult.Failure).error is BackupError.CorruptedBackup,
            "expected CorruptedBackup, got ${result.error}",
        )
    }

    @Test
    fun `restoreFromSnapshot returns BackupTooNew when source schema is newer`() = runTest {
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

        val result = provider.restoreFromSnapshot(source)
        assertTrue(result is BackupResult.Failure)
        val error = (result as BackupResult.Failure).error
        assertTrue(
            error is BackupError.BackupTooNew,
            "expected BackupTooNew, got $error",
        )
        assertEquals(futureVersion, (error as BackupError.BackupTooNew).backupSchemaVersion)
    }

    @Test
    fun `restoreFromSnapshot replaces live db with snapshot contents`() = runTest {
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

        val result = provider.restoreFromSnapshot(source)
        assertEquals(BackupResult.Success(Unit), result)

        // Rebuild Room — the previous handle is stale after restoreFromSnapshot.
        val restored = Room
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val names = restored.tagDao.observeAll().first().map { it.name }.toSet()
            assertEquals(setOf("Original"), names)
            // The successful DAO read above IS proof the restored DB opens (Room 3 removed
            // `openHelper`); a second independent read confirms the connection is live.
            assertNotNull(restored.tagDao.observeAll().first())
        } finally {
            restored.close()
        }
    }

    @Test
    fun `preserveCurrentDb writes a file in cacheDir that hasPreRestoreBackup detects`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreserveMe"))

            val result = provider.preserveCurrentDb()
            assertTrue(result is BackupResult.Success, "expected Success, got $result")
            val preservedFile = (result as BackupResult.Success).data

            assertTrue(preservedFile.exists(), "preserved file should exist on disk")
            assertEquals(context.cacheDir, preservedFile.parentFile)
            assertTrue(provider.hasPreRestoreBackup())
        }

    @Test
    fun `preserveCurrentDb produces a self-contained SQLite snapshot at the live schema`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "PreservedRow"))
            val result = provider.preserveCurrentDb()
            assertTrue(result is BackupResult.Success)
            val preserved = (result as BackupResult.Success).data

            // The preserved file must be a valid SQLite database at the same
            // schema version as the live db (WAL checkpointed, no missing pages).
            val peek = provider.peekSnapshotSchemaVersion(preserved)
            assertTrue(peek is BackupResult.Success, "peek should succeed on preserved file")
            assertEquals(provider.currentSchemaVersion(), (peek as BackupResult.Success).data)
        }

    @Test
    fun `rollbackToPreRestoreBackup with no preserved file returns CorruptedBackup`() = runTest {
        assertFalse(provider.hasPreRestoreBackup())
        val result = provider.rollbackToPreRestoreBackup()
        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.CorruptedBackup)
    }

    @Test
    fun `rollbackToPreRestoreBackup swaps live db with preserved contents and consumes file`() =
        runTest {
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "BeforeRestore"))
            assertTrue(provider.preserveCurrentDb() is BackupResult.Success)

            // Simulate a restore: mutate the live db so it differs from the preserved snapshot.
            database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "AfterRestore"))
            assertEquals(
                setOf("BeforeRestore", "AfterRestore"),
                database.tagDao.observeAll().first().map { it.name }.toSet(),
            )

            assertEquals(BackupResult.Success(Unit), provider.rollbackToPreRestoreBackup())

            // Preserved file consumed.
            assertFalse(provider.hasPreRestoreBackup())

            // Live db reverts to pre-restore state.
            val restored = Room
                .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
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
        assertTrue(provider.preserveCurrentDb() is BackupResult.Success)
        assertTrue(provider.hasPreRestoreBackup())

        provider.deletePreRestoreBackup()
        assertFalse(provider.hasPreRestoreBackup())
    }

    @Test
    fun `deletePreRestoreBackup is a no-op when no file exists`() = runTest {
        assertFalse(provider.hasPreRestoreBackup())
        // Just verify no exception.
        provider.deletePreRestoreBackup()
        assertFalse(provider.hasPreRestoreBackup())
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
            assertTrue(provider.hasPreMigrationBackup())
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
        assertFalse(provider.hasPreMigrationBackup())
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
        assertTrue(provider.hasPreMigrationBackup())

        provider.deletePreMigrationBackup()
        assertFalse(provider.hasPreMigrationBackup())

        // Idempotent — second call no-ops without exception.
        provider.deletePreMigrationBackup()
        assertFalse(provider.hasPreMigrationBackup())
    }

    @Test
    fun `pre_migration and pre_restore slots have independent lifecycles`() = runTest {
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = "Independence"))
        // Scenario 1: preserve pre-restore (must run while Room is open — uses
        // the live appDatabase to WAL-checkpoint).
        assertTrue(provider.preserveCurrentDb() is BackupResult.Success)
        // Scenario 2: preserve pre-migration via direct copy (close Room first).
        database.close()
        assertNotNull(provider.preserveDbBeforeMigration())

        assertTrue(provider.hasPreRestoreBackup())
        assertTrue(provider.hasPreMigrationBackup())

        // Deleting pre-migration does not affect pre-restore.
        provider.deletePreMigrationBackup()
        assertFalse(provider.hasPreMigrationBackup())
        assertTrue(provider.hasPreRestoreBackup())

        // Inverse direction: re-create pre-migration, delete pre-restore, both
        // remain independent.
        assertNotNull(provider.preserveDbBeforeMigration())
        provider.deletePreRestoreBackup()
        assertFalse(provider.hasPreRestoreBackup())
        assertTrue(provider.hasPreMigrationBackup())
    }
}
