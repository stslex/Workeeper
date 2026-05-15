// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import androidx.room.Room
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
        if (database.isOpen) database.close()
        context.deleteDatabase(AppDatabase.NAME)
        // Clean up any snapshots written into the databases dir.
        val dbDir = context.getDatabasePath(AppDatabase.NAME).parentFile
        dbDir?.listFiles()?.forEach { it.delete() }
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
            .allowMainThreadQueries()
            .build()
        try {
            val names = restored.tagDao.observeAll().first().map { it.name }.toSet()
            assertEquals(setOf("Original"), names)
            assertNotNull(restored.openHelper.readableDatabase)
        } finally {
            restored.close()
        }
    }
}
