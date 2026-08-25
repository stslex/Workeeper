// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class RestoreRecoveryFilesImplTest {

    private lateinit var context: Context
    private lateinit var files: RestoreRecoveryFilesImpl

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recoveryRoot().deleteRecursively()
        File(context.cacheDir, RECOVERY_SHARE_DIR).deleteRecursively()
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        files = newStore(context)
    }

    @AfterEach
    fun teardown() {
        recoveryRoot().deleteRecursively()
        File(context.cacheDir, RECOVERY_SHARE_DIR).deleteRecursively()
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    @Test
    fun `installation epoch is stable across process-like reconstruction`() = runTest {
        val first = files.installEpoch()
        val second = newStore(context).installEpoch()

        assertEquals(first, second)
        assertEquals(
            context.noBackupFilesDir.canonicalFile,
            requireNotNull(recoveryRoot().parentFile).canonicalFile,
        )
        assertNotEquals(context.cacheDir.canonicalFile, recoveryRoot().canonicalFile)
    }

    @Test
    fun `installation epoch returns only after file and final directory are synced`() = runTest {
        assertTrue(recoveryRoot().mkdirs())
        val epochFile = File(recoveryRoot(), "install_epoch")
        var fileSynced = false
        var directorySyncCount = 0
        val durableStore = newStore(
            context,
            object : SnapshotFileDurability {
                override fun syncFile(file: File) {
                    assertTrue(file.name.endsWith(".creating"))
                    assertFalse(epochFile.exists())
                    fileSynced = true
                }

                override fun syncDirectory(directory: File) {
                    assertTrue(fileSynced)
                    assertEquals(recoveryRoot().canonicalFile, directory.canonicalFile)
                    directorySyncCount += 1
                    if (directorySyncCount == 1) {
                        assertFalse(epochFile.exists())
                    } else {
                        assertTrue(epochFile.isFile)
                    }
                }
            },
        )

        val epoch = durableStore.installEpoch()

        assertEquals(epoch.toString(), epochFile.readText())
        assertEquals(2, directorySyncCount)
    }

    @Test
    fun `immutable publication returns only after complete file and final directory are synced`() =
        runTest {
            assertTrue(recoveryRoot().mkdirs())
            val ref = UndoRef(owner(15))
            val target = File(recoveryRoot(), "undo_${ref.owner}.db")
            val source = File(context.filesDir, "durability-source.db").apply {
                writeText("DURABLE")
            }
            var fileSynced = false
            var directorySyncCount = 0
            val durableStore = newStore(
                context,
                object : SnapshotFileDurability {
                    override fun syncFile(file: File) {
                        assertEquals("DURABLE", file.readText())
                        assertFalse(target.exists())
                        fileSynced = true
                    }

                    override fun syncDirectory(directory: File) {
                        assertTrue(fileSynced)
                        assertEquals(recoveryRoot().canonicalFile, directory.canonicalFile)
                        directorySyncCount += 1
                        if (directorySyncCount == 1) {
                            assertFalse(target.exists())
                        } else {
                            assertEquals("DURABLE", target.readText())
                        }
                    }
                },
            )

            val published = assertSuccess(durableStore.publishUndo(source, ref))

            assertEquals(target.canonicalFile, published.canonicalFile)
            assertEquals(2, directorySyncCount)
        }

    @Test
    fun `immutable directory sync failure cannot report publication success`() = runTest {
        assertTrue(recoveryRoot().mkdirs())
        val ref = UndoRef(owner(16))
        val source = File(context.filesDir, "durability-failure-source.db").apply {
            writeText("PUBLISHED-BUT-NOT-SYNCED")
        }
        var directorySyncCount = 0
        val failingStore = newStore(
            context,
            object : SnapshotFileDurability {
                override fun syncFile(file: File) = Unit

                override fun syncDirectory(directory: File) {
                    directorySyncCount += 1
                    if (directorySyncCount == 2) throw IOException("directory fsync failed")
                }
            },
        )

        val result = failingStore.publishUndo(source, ref)

        assertTrue(result is BackupResult.Failure)
        assertEquals(2, directorySyncCount)
        assertEquals("PUBLISHED-BUT-NOT-SYNCED", files.undoFile(ref)?.readText())
        assertTrue(recoveryRoot().listFiles().orEmpty().none { it.name.endsWith(".creating") })
    }

    @Test
    fun `cache deletion cannot remove authoritative recovery assets`() = runTest {
        val undoRef = UndoRef(owner(1))
        val sourceRef = RestoreSourceRef(owner(2))
        val undoSource = File(context.filesDir, "undo-source.db").apply { writeText("UNDO") }
        val callerDownload = File(context.cacheDir, "restore-download.db").apply {
            writeText("RESTORE")
        }
        val exportSource = File(context.filesDir, "export-source.db").apply { writeText("EXPORT") }

        assertSuccess(files.publishUndo(undoSource, undoRef))
        assertSuccess(files.publishRestoreSource(callerDownload, sourceRef))
        assertSuccess(files.publishRecoveryExport(exportSource))
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }

        assertEquals("UNDO", requireNotNull(files.undoFile(undoRef)).readText())
        assertEquals("RESTORE", requireNotNull(files.restoreSourceFile(sourceRef)).readText())
        assertEquals("EXPORT", requireNotNull(files.recoveryExportFile()).readText())
        assertTrue(files.undoFile(undoRef)!!.canonicalPath.startsWith(recoveryRoot().canonicalPath))
    }

    @Test
    fun `immutable undo owner collision fails and never overwrites the existing file`() = runTest {
        val ref = UndoRef(owner(3))
        val first = File(context.filesDir, "first-undo.db").apply { writeText("FIRST") }
        val second = File(context.filesDir, "second-undo.db").apply { writeText("SECOND") }

        val firstPublished = assertSuccess(files.publishUndo(first, ref))
        val collision = files.publishUndo(second, ref)

        assertTrue(collision is BackupResult.Failure)
        assertEquals("FIRST", firstPublished.readText())
        assertFalse(File(recoveryRoot(), "${firstPublished.name}.creating").exists())
    }

    @Test
    fun `stale publication lock file is safely reacquired after process reconstruction`() = runTest {
        assertTrue(recoveryRoot().mkdirs())
        val lockFile = File(recoveryRoot(), ".publication.lock").apply {
            writeText("stale inode; no process holds its kernel lock")
        }
        val ref = UndoRef(owner(17))
        val source = File(context.filesDir, "stale-lock-undo.db").apply { writeText("UNDO") }

        val published = assertSuccess(newStore(context).publishUndo(source, ref))

        assertEquals("UNDO", published.readText())
        assertTrue(lockFile.isFile)
    }

    @Test
    fun `concurrent immutable undo publication has one winner that collisions cannot overwrite`() =
        runTest {
            val ref = UndoRef(owner(14))
            val target = File(recoveryRoot(), "undo_${ref.owner}.db")
            val payloads = List(CONCURRENT_WRITERS) { index -> "UNDO-$index" }

            val winner = assertConcurrentImmutablePublication(target, payloads)

            assertEquals(winner, requireNotNull(files.undoFile(ref)).readText())
        }

    @Test
    fun `concurrent install epoch publication has one winner that collisions cannot overwrite`() =
        runTest {
            val target = File(recoveryRoot(), "install_epoch")
            val payloads = List(CONCURRENT_WRITERS) { index -> owner(100 + index).toString() }

            val winner = assertConcurrentImmutablePublication(target, payloads)

            assertEquals(InstallEpoch(RestoreOwnerId(winner)), files.installEpoch())
        }

    @Test
    fun `immutable symlink target is rejected without touching its destination`() = runTest {
        files.installEpoch()
        val ref = UndoRef(owner(12))
        val outside = File(context.filesDir, "symlink-destination.db").apply {
            writeText("OUTSIDE")
        }
        val target = File(recoveryRoot(), "undo_${ref.owner}.db")
        Files.createSymbolicLink(target.toPath(), outside.toPath())
        val source = File(context.filesDir, "symlink-source.db").apply { writeText("NEW") }

        val result = files.publishUndo(source, ref)

        assertTrue(result is BackupResult.Failure)
        assertEquals("OUTSIDE", outside.readText())
        assertNull(files.undoFile(ref), "NOFOLLOW resolution must reject the symlink")
        Files.deleteIfExists(target.toPath())
        outside.delete()
    }

    @Test
    fun `symlink recovery and share directories are rejected without escaping their roots`() =
        runTest {
            val root = recoveryRoot()
            val outsideRoot = File(context.filesDir, "outside-recovery-root").apply { mkdirs() }
            Files.createSymbolicLink(root.toPath(), outsideRoot.toPath())
            val source = File(context.filesDir, "root-source.db").apply { writeText("DATA") }

            try {
                val rootResult = files.publishUndo(source, UndoRef(owner(13)))
                assertTrue(rootResult is BackupResult.Failure)
                assertTrue(outsideRoot.listFiles().orEmpty().isEmpty())
            } finally {
                Files.deleteIfExists(root.toPath())
                outsideRoot.deleteRecursively()
            }

            val durable = assertSuccess(files.publishRecoveryExport(source))
            val shareRoot = File(context.cacheDir, RECOVERY_SHARE_DIR)
            val outsideShare = File(context.filesDir, "outside-recovery-share").apply { mkdirs() }
            Files.createSymbolicLink(shareRoot.toPath(), outsideShare.toPath())
            try {
                val shareResult = files.createShareCopy(durable, "share.db")
                assertTrue(shareResult is BackupResult.Failure)
                assertTrue(outsideShare.listFiles().orEmpty().isEmpty())
            } finally {
                Files.deleteIfExists(shareRoot.toPath())
                outsideShare.deleteRecursively()
            }
        }

    @Test
    fun `restore source ownership transfer publishes final before consuming caller cache`() =
        runTest {
            val ref = RestoreSourceRef(owner(4))
            val caller = File(context.cacheDir, "caller-temp.db").apply { writeText("PAYLOAD") }

            val durable = assertSuccess(files.publishRestoreSource(caller, ref))

            assertFalse(caller.exists())
            assertEquals("PAYLOAD", durable.readText())
            assertEquals("staged_restore_${ref.owner}.db", durable.name)
            assertFalse(File(recoveryRoot(), "${durable.name}.creating").exists())
        }

    @Test
    fun `legacy C migration is immutable and does not consume C before state is durable`() =
        runTest {
            val legacy = File(context.cacheDir, "pre_restore_backup.db").apply {
                writeText("LEGACY")
            }
            val ref = UndoRef(owner(5))

            val migrated = assertSuccess(files.migrateLegacyUndo(ref))

            assertEquals("LEGACY", migrated.readText())
            assertTrue(legacy.exists(), "protocol state must land before released C is consumed")
            assertTrue(files.deleteLegacyPreRestore())
            assertFalse(legacy.exists())
        }

    @Test
    fun `legacy migration replay re-syncs a final left by failed directory publication`() =
        runTest {
            assertTrue(recoveryRoot().mkdirs())
            val legacy = File(context.cacheDir, "pre_restore_backup.db").apply {
                writeText("LEGACY-REPLAY")
            }
            val ref = UndoRef(owner(17))
            var directorySyncCount = 0
            val failedPublish = newStore(
                context,
                object : SnapshotFileDurability {
                    override fun syncFile(file: File) = Unit

                    override fun syncDirectory(directory: File) {
                        directorySyncCount += 1
                        if (directorySyncCount == 2) throw IOException("directory fsync failed")
                    }
                },
            )
            assertTrue(failedPublish.migrateLegacyUndo(ref) is BackupResult.Failure)
            assertEquals("LEGACY-REPLAY", failedPublish.undoFile(ref)?.readText())

            var targetSynced = false
            var rootSynced = false
            val reconstructed = newStore(
                context,
                object : SnapshotFileDurability {
                    override fun syncFile(file: File) {
                        assertEquals(failedPublish.undoFile(ref)?.canonicalFile, file.canonicalFile)
                        targetSynced = true
                    }

                    override fun syncDirectory(directory: File) {
                        assertTrue(targetSynced)
                        assertEquals(recoveryRoot().canonicalFile, directory.canonicalFile)
                        rootSynced = true
                    }
                },
            )

            val replayed = assertSuccess(reconstructed.migrateLegacyUndo(ref))

            assertEquals("LEGACY-REPLAY", replayed.readText())
            assertTrue(rootSynced)
            assertTrue(legacy.exists(), "protocol state still owns legacy C deletion")
        }

    @Test
    fun `recovery export is durable and sharing creates only a narrow cache copy`() = runTest {
        val source = File(context.filesDir, "raw-export.db").apply { writeText("RAW") }
        val durable = assertSuccess(files.publishRecoveryExport(source))

        val shared = assertSuccess(files.createShareCopy(durable, "workeeper_recovery.db"))

        assertEquals(recoveryRoot().canonicalFile, durable.parentFile!!.canonicalFile)
        assertEquals(
            File(context.cacheDir, RECOVERY_SHARE_DIR).canonicalFile,
            shared.parentFile!!.canonicalFile,
        )
        assertEquals("RAW", shared.readText())
        val invalid = files.createShareCopy(durable, "../outside.db")
        assertTrue(invalid is BackupResult.Failure)
    }

    @Test
    fun `owner-aware sweep preserves attempts pointer and pending terminal assets`() = runTest {
        val attemptOwner = owner(6)
        val activeOwner = owner(7)
        val terminalOwner = owner(8)
        val orphanOwner = owner(9)
        publishOwnedUndo(attemptOwner)
        publishOwnedUndo(activeOwner)
        publishOwnedUndo(terminalOwner)
        publishOwnedUndo(orphanOwner)
        publishOwnedSource(attemptOwner)
        publishOwnedSource(orphanOwner)
        File(recoveryRoot(), "undo_$attemptOwner.db.creating").writeText("protected partial")
        File(recoveryRoot(), "undo_$orphanOwner.db.creating").writeText("orphan partial")
        File(recoveryRoot(), "notes.txt").writeText("not protocol owned")
        val epoch = files.installEpoch()
        val state = RestoreProtocolState(
            installEpoch = epoch,
            attempt = RestoreAttempt.Restore(
                id = attemptOwner,
                phase = RestoreAttempt.Phase.Prepared,
                context = null,
                undoRef = UndoRef(attemptOwner),
                sourceRef = RestoreSourceRef(attemptOwner),
            ),
            activeUndo = ActiveUndo(UndoRef(activeOwner), originalDataDateEpochMs = 1L),
            terminalOutbox = RestoreTerminal.RestoreSucceeded(
                owner = terminalOwner,
                restoredAtEpochMs = 2L,
                previousVersionAvailable = true,
            ),
        )

        val report = files.sweep(state)

        assertNotNull(files.undoFile(UndoRef(attemptOwner)))
        assertNotNull(files.restoreSourceFile(RestoreSourceRef(attemptOwner)))
        assertNotNull(files.undoFile(UndoRef(activeOwner)))
        assertNotNull(files.undoFile(UndoRef(terminalOwner)))
        assertTrue(File(recoveryRoot(), "undo_$attemptOwner.db.creating").exists())
        assertNull(files.undoFile(UndoRef(orphanOwner)))
        assertNull(files.restoreSourceFile(RestoreSourceRef(orphanOwner)))
        assertFalse(File(recoveryRoot(), "undo_$orphanOwner.db.creating").exists())
        assertTrue(File(recoveryRoot(), "notes.txt").exists())
        assertTrue(File(recoveryRoot(), ".publication.lock").isFile)
        assertTrue(report.retryNames.isEmpty())
    }

    @Test
    fun `sweep reports failed deletion as retryable garbage`() = runTest {
        val orphan = owner(10)
        val undeletable = File(recoveryRoot(), "undo_$orphan.db")
        assertTrue(undeletable.mkdirs())
        File(undeletable, "child").writeText("keeps directory non-empty")
        val state = RestoreProtocolState(
            installEpoch = files.installEpoch(),
            attempt = null,
            activeUndo = null,
            terminalOutbox = null,
        )

        val report = files.sweep(state)

        assertEquals(listOf(undeletable.name), report.retryNames)
        assertTrue(undeletable.exists())

        assertTrue(File(undeletable, "child").delete())
        val retried = files.sweep(state)

        assertEquals(listOf(undeletable.name), retried.deletedNames)
        assertTrue(retried.retryNames.isEmpty())
        assertFalse(undeletable.exists())
    }

    @Test
    fun `unwritable recovery root returns typed IO failure`() = runTest {
        val blocker = File(context.cacheDir, "no-backup-blocker").apply { writeText("file") }
        val blockedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = blocker
        }
        val blocked = newStore(blockedContext)
        val source = File(context.filesDir, "blocked-source.db").apply { writeText("DATA") }

        val result = blocked.publishUndo(source, UndoRef(owner(11)))

        assertTrue(
            result is BackupResult.Failure && result.error is BackupError.Io,
            "root failure must reject as typed IO, got $result",
        )
    }

    private suspend fun publishOwnedUndo(owner: RestoreOwnerId) {
        val source = File(context.filesDir, "undo-source-$owner").apply { writeText(owner.value) }
        assertSuccess(files.publishUndo(source, UndoRef(owner)))
    }

    private suspend fun publishOwnedSource(owner: RestoreOwnerId) {
        val source = File(context.filesDir, "restore-source-$owner").apply { writeText(owner.value) }
        assertSuccess(files.publishRestoreSource(source, RestoreSourceRef(owner)))
    }

    private suspend fun assertConcurrentImmutablePublication(
        target: File,
        payloads: List<String>,
    ): String {
        assertTrue(requireNotNull(target.parentFile).mkdirs())
        val initial = raceImmutablePublications(target, payloads, "initial")
        val winnerIndex = initial.single { (_, outcome) -> outcome.isSuccess }.first
        val winner = payloads[winnerIndex]
        assertEquals(winner, target.readText())

        val collisionPayloads = payloads.map { payload -> "COLLISION-$payload" }
        val collisions = raceImmutablePublications(target, collisionPayloads, "collision")
        assertTrue(collisions.all { (_, outcome) -> outcome.isFailure })
        assertEquals(winner, target.readText())
        return winner
    }

    private suspend fun raceImmutablePublications(
        target: File,
        payloads: List<String>,
        round: String,
    ): List<Pair<Int, Result<Unit>>> = coroutineScope {
        val gate = CyclicBarrier(payloads.size)
        payloads.mapIndexed { index, payload ->
            val partial = File(requireNotNull(target.parentFile), "${target.name}.$round.$index.creating")
                .apply { writeText(payload) }
            async(Dispatchers.IO) {
                gate.await()
                index to runCatching { publishImmutableNoReplace(partial, target) }
            }
        }.awaitAll()
    }

    private fun recoveryRoot(): File = File(context.noBackupFilesDir, "restore-recovery")

    private fun newStore(
        targetContext: Context,
        durability: SnapshotFileDurability = PlatformSnapshotFileDurability,
    ): RestoreRecoveryFilesImpl =
        RestoreRecoveryFilesImpl(
            context = targetContext,
            dispatcher = UnconfinedTestDispatcher(),
            durability = durability,
        )

    private fun owner(suffix: Int): RestoreOwnerId = RestoreOwnerId(
        "00000000-0000-4000-8000-${suffix.toString().padStart(12, '0')}",
    )

    private fun assertSuccess(result: BackupResult<File>): File {
        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        return (result as BackupResult.Success).data
    }

    private companion object {
        const val CONCURRENT_WRITERS = 8
        const val RECOVERY_SHARE_DIR = "recovery_share"
    }
}
