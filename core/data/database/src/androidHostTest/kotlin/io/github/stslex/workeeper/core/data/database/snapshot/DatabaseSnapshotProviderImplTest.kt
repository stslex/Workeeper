// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import androidx.room3.Room
import androidx.room3.deferredTransaction
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.closeAppDatabase
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.io.IOException
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class DatabaseSnapshotProviderImplTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var recoveryFiles: RestoreRecoveryFilesImpl
    private lateinit var capacity: FakeStorageCapacity
    private lateinit var provider: DatabaseSnapshotProviderImpl

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.NAME)
        recoveryRoot().deleteRecursively()
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        database = createDatabase()
        recoveryFiles = RestoreRecoveryFilesImpl(context, UnconfinedTestDispatcher())
        capacity = FakeStorageCapacity()
        provider = createProvider(recoveryFiles, capacity)
    }

    @AfterEach
    fun teardown() {
        database.close()
        context.deleteDatabase(AppDatabase.NAME)
        recoveryRoot().deleteRecursively()
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        context.getDatabasePath(AppDatabase.NAME).parentFile
            ?.listFiles()
            .orEmpty()
            .filter { it.name != AppDatabase.NAME }
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun `captureSnapshot checkpoints WAL and opens with persisted Workeeper data`() = runTest {
        insertTag("captured")
        val wal = File(requireNotNull(provider.liveDatabaseFile().parentFile), "${AppDatabase.NAME}-wal")
        assertTrue(wal.length() > 0L, "fixture must exercise the WAL checkpoint")
        val target = File(context.cacheDir, "backup.db")

        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(target))

        assertEquals(0L, wal.length())
        val snapshot = android.database.sqlite.SQLiteDatabase.openDatabase(
            target.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        snapshot.use {
            assertEquals(
                1,
                it.rawQuery("SELECT COUNT(*) FROM tag_table", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                },
            )
        }
    }

    @Test
    fun `createUndo publishes an exact immutable file below noBackup`() = runTest {
        insertTag("before")
        val ref = undoRef(1)

        val created = assertFileSuccess(provider.createUndo(ref))

        assertEquals("undo_${ref.owner}.db", created.name)
        assertEquals(recoveryRoot().canonicalFile, created.parentFile!!.canonicalFile)
        assertEquals(created.canonicalFile, provider.getUndoFile(ref)!!.canonicalFile)
        assertEquals(BackupResult.Success(Unit), provider.validateUndo(ref))
    }

    @Test
    fun `createUndo rejects a busy checkpoint without publishing an immutable undo`() = runTest {
        val ref = undoRef(12)
        withBusyWalReader("undo") {
            val result = provider.createUndo(ref)

            assertCheckpointFailure(result)
            assertNull(provider.getUndoFile(ref))
            assertTrue(
                recoveryRoot().listFiles().orEmpty().none { ref.owner.toString() in it.name },
                "a failed checkpoint must not publish either a final or partial undo",
            )
        }
    }

    @Test
    fun `missing exact refs are corruption and never select another owner`() = runTest {
        val present = undoRef(2)
        val missing = undoRef(3)
        insertTag("before")
        assertFileSuccess(provider.createUndo(present))

        val result = provider.validateUndo(missing)

        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.CorruptedBackup)
        assertNull(provider.getUndoFile(missing))
        assertNotNull(provider.getUndoFile(present))
    }

    @Test
    fun `staged restore validates and exact replacement installs its bytes`() = runTest {
        insertTag("snapshot")
        val ref = stageCurrentSnapshot(4)
        insertTag("drift")
        assertEquals(BackupResult.Success(Unit), provider.validateRestoreSource(ref))

        closeAppDatabase(database)
        assertEquals(BackupResult.Success(Unit), provider.replaceLiveDatabaseFromRestore(ref))

        val restored = createDatabase()
        try {
            val names = restored.tagDao.observeAll().first().map { it.name }.toSet()
            assertEquals(setOf("snapshot"), names)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `restore capacity accepts exactly the conservative required bytes`() = runTest {
        insertTag("capacity")
        val ref = stageCurrentSnapshot(5)
        val liveSize = provider.liveDatabaseFile().length()
        val stagedSize = provider.getRestoreSourceFile(ref)!!.length()
        capacity.available = Math.addExact(
            Math.addExact(liveSize, stagedSize),
            DatabaseSnapshotProviderImpl.CAPACITY_MARGIN_BYTES,
        )

        assertEquals(BackupResult.Success(Unit), provider.checkRestoreCapacity(ref))
        assertEquals(1, capacity.queryCount)
    }

    @Test
    fun `restore capacity rejects one byte short without creating undo or stopping Room`() =
        runTest {
            insertTag("still-serving")
            val ref = stageCurrentSnapshot(6)
            val liveSize = provider.liveDatabaseFile().length()
            val stagedSize = provider.getRestoreSourceFile(ref)!!.length()
            val required = Math.addExact(
                Math.addExact(liveSize, stagedSize),
                DatabaseSnapshotProviderImpl.CAPACITY_MARGIN_BYTES,
            )
            capacity.available = required - 1L
            val undoRef = undoRef(6)

            val result = provider.checkRestoreCapacity(ref)

            assertTrue(
                result is BackupResult.Failure &&
                    result.error == BackupError.InsufficientLocalStorage(required, required - 1L),
                "expected one-byte-short rejection, got $result",
            )
            assertNull(provider.getUndoFile(undoRef))
            assertEquals(
                setOf("still-serving"),
                database.tagDao.observeAll().first().map { it.name }.toSet(),
            )
            assertNotNull(provider.getRestoreSourceFile(ref))
        }

    @Test
    fun `capacity arithmetic overflow rejects conservatively`() = runTest {
        insertTag("overflow")
        val ref = stageCurrentSnapshot(7)
        capacity.available = Long.MAX_VALUE
        capacity.sizeOverride = { Long.MAX_VALUE }

        val result = provider.checkRestoreCapacity(ref)

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupError.InsufficientLocalStorage(Long.MAX_VALUE, Long.MAX_VALUE),
            (result as BackupResult.Failure).error,
        )
    }

    @Test
    fun `capacity query exception is typed and leaves serving generation untouched`() = runTest {
        insertTag("query-failure")
        val ref = stageCurrentSnapshot(8)
        val cause = IOException("capacity unavailable")
        capacity.queryFailure = cause

        val result = provider.checkRestoreCapacity(ref)

        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupError.StorageCapacityUnavailable(cause), (result as BackupResult.Failure).error)
        assertEquals(
            setOf("query-failure"),
            database.tagDao.observeAll().first().map { it.name }.toSet(),
        )
    }

    @Test
    fun `rollback capacity uses source tmp plus margin and accepts equality`() = runTest {
        insertTag("rollback")
        val ref = undoRef(9)
        val undo = assertFileSuccess(provider.createUndo(ref))
        capacity.available = Math.addExact(
            undo.length(),
            DatabaseSnapshotProviderImpl.CAPACITY_MARGIN_BYTES,
        )

        assertEquals(BackupResult.Success(Unit), provider.checkRollbackCapacity(ref))
    }

    @Test
    fun `sufficient admission followed by ENOSPC undo write fails without live mutation`() =
        runTest {
            insertTag("survives-enospc")
            val sourceRef = stageCurrentSnapshot(10)
            capacity.available = Long.MAX_VALUE
            assertEquals(BackupResult.Success(Unit), provider.checkRestoreCapacity(sourceRef))
            val cause = IOException("ENOSPC")
            val failedStore = object : RestoreRecoveryFileStore by recoveryFiles {
                override suspend fun publishUndo(
                    source: File,
                    ref: UndoRef,
                ): BackupResult<File> = BackupResult.Failure(BackupError.Io(cause))
            }
            val failingProvider = createProvider(failedStore, capacity)
            val undoRef = undoRef(10)

            val result = failingProvider.createUndo(undoRef)

            assertEquals(BackupResult.Failure(BackupError.Io(cause)), result)
            assertNull(failingProvider.getUndoFile(undoRef))
            assertEquals(
                setOf("survives-enospc"),
                database.tagDao.observeAll().first().map { it.name }.toSet(),
            )
        }

    @Test
    fun `legacy C validates migrates exactly and is consumed only explicitly`() = runTest {
        insertTag("legacy")
        val legacy = File(context.cacheDir, "legacy-build.db")
        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(legacy))
        legacy.renameTo(File(context.cacheDir, "pre_restore_backup.db"))
        val ref = undoRef(11)

        assertEquals(BackupResult.Success(Unit), provider.validateLegacyUndo())
        val migrated = assertFileSuccess(provider.migrateLegacyUndo(ref))

        assertTrue(File(context.cacheDir, "pre_restore_backup.db").exists())
        assertEquals(BackupResult.Success(Unit), provider.validateUndo(ref))
        assertEquals(migrated, provider.getUndoFile(ref))
        assertTrue(provider.deleteLegacyPreRestore())
    }

    @Test
    fun `pre-migration export is durable and reports missing live file as typed failure`() =
        runTest {
            insertTag("export")
            database.close()

            val exported = assertFileSuccess(provider.preserveDbBeforeMigration())

            assertEquals(recoveryRoot().canonicalFile, exported.parentFile!!.canonicalFile)
            assertEquals(exported, provider.getRecoveryExportFile())
            assertTrue(context.deleteDatabase(AppDatabase.NAME))
            val missing = provider.preserveDbBeforeMigration()
            assertTrue(
                missing is BackupResult.Failure &&
                    missing.error is BackupError.CorruptedBackup,
            )
        }

    @Test
    fun `pre-migration export rejects a busy checkpoint without publishing an export`() =
        runTest {
            withBusyWalReader("export") {
                val result = provider.preserveDbBeforeMigration()

                assertCheckpointFailure(result)
                assertNull(provider.getRecoveryExportFile())
                assertFalse(
                    recoveryRoot().exists(),
                    "a failed checkpoint must not create an authoritative recovery root",
                )
            }
        }

    @Test
    fun `header-only live inspection accepts current Workeeper data without SQLite sidecars`() =
        runTest {
            insertTag("inspect")
            database.close()
            val parent = requireNotNull(provider.liveDatabaseFile().parentFile)
            val sidecarsBefore = parent.listFiles().orEmpty()
                .filter { it.name.startsWith(AppDatabase.NAME) && it.name != AppDatabase.NAME }
                .map { it.name to it.length() }

            val inspected = provider.inspectLiveDatabaseWithoutRoom()

            assertEquals(
                BackupResult.Success(APP_DATABASE_VERSION),
                inspected,
            )
            assertEquals(
                sidecarsBefore,
                parent.listFiles().orEmpty()
                    .filter { it.name.startsWith(AppDatabase.NAME) && it.name != AppDatabase.NAME }
                    .map { it.name to it.length() },
                "header inspection must not open framework SQLite or create sidecars",
            )
        }

    private suspend fun stageCurrentSnapshot(suffix: Int): RestoreSourceRef {
        val caller = File(context.cacheDir, "download-$suffix.db")
        assertEquals(BackupResult.Success(Unit), provider.captureSnapshot(caller))
        val ref = RestoreSourceRef(owner(suffix))
        assertFileSuccess(provider.stageRestoreSource(caller, ref))
        assertFalse(caller.exists(), "cache caller is consumed only after durable publish")
        return ref
    }

    private suspend fun insertTag(name: String) {
        database.tagDao.insert(TagEntity(uuid = Uuid.random(), name = name))
    }

    private suspend fun <T> withBusyWalReader(
        suffix: String,
        block: suspend () -> T,
    ): T = coroutineScope {
        insertTag("before-$suffix-reader")
        val readerReady = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()
        val reader = launch {
            database.useReaderConnection { connection ->
                connection.deferredTransaction {
                    usePrepared("SELECT COUNT(*) FROM tag_table") { statement ->
                        assertTrue(statement.step())
                        assertTrue(statement.getLong(0) > 0L)
                    }
                    readerReady.complete(Unit)
                    releaseReader.await()
                }
            }
        }
        readerReady.await()
        insertTag("after-$suffix-reader")
        try {
            block()
        } finally {
            releaseReader.complete(Unit)
            reader.join()
        }
    }

    private fun assertCheckpointFailure(result: BackupResult<*>) {
        assertTrue(result is BackupResult.Failure, "expected checkpoint failure, got $result")
        val error = (result as BackupResult.Failure).error
        assertTrue(error is BackupError.Io, "expected typed IO failure, got $error")
        assertTrue(
            (error as BackupError.Io).cause.message.orEmpty().contains("checkpoint"),
            "checkpoint failure must remain visible to recovery UI/logging",
        )
    }

    private fun createDatabase(): AppDatabase = Room
        .databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
        .setDriver(AndroidSQLiteDriver())
        .allowMainThreadQueries()
        .build()

    private fun createProvider(
        store: RestoreRecoveryFileStore,
        storage: RestoreStorageCapacity,
    ): DatabaseSnapshotProviderImpl = DatabaseSnapshotProviderImpl(
        appDatabase = database,
        context = context,
        recoveryFiles = store,
        storageCapacity = storage,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun recoveryRoot(): File = File(context.noBackupFilesDir, "restore-recovery")

    private fun undoRef(suffix: Int): UndoRef = UndoRef(owner(suffix))

    private fun owner(suffix: Int): RestoreOwnerId = RestoreOwnerId(
        "10000000-0000-4000-8000-${suffix.toString().padStart(12, '0')}",
    )

    private fun assertFileSuccess(result: BackupResult<File>): File {
        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        return (result as BackupResult.Success).data
    }

    private class FakeStorageCapacity : RestoreStorageCapacity {
        var available: Long = Long.MAX_VALUE
        var queryFailure: Throwable? = null
        var sizeOverride: ((File) -> Long)? = null
        var queryCount: Int = 0

        override fun getAllocatableBytes(path: File): Long {
            queryCount += 1
            queryFailure?.let { throw it }
            return available
        }

        override fun sizeBytes(file: File): Long = sizeOverride?.invoke(file) ?: file.length()
    }
}
