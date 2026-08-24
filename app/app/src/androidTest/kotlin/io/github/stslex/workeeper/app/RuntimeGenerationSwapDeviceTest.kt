// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.runtime.AppRuntime
import io.github.stslex.workeeper.runtime.ReplacementOperation
import io.github.stslex.workeeper.runtime.ReplacementOutcome
import io.github.stslex.workeeper.runtime.ReplacementPolicy
import io.github.stslex.workeeper.runtime.RuntimeGeneration
import io.github.stslex.workeeper.runtime.StartupProcessor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `RebuildInProcess` replacement transaction over the production database factory, real graph
 * and real preflight. No UI here — the live-disposal half is [AppRuntimeUiHandshakeDeviceTest].
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class RuntimeGenerationSwapDeviceTest {

    private lateinit var context: Context
    private lateinit var runtime: AppRuntime

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wipeFiles()
        runtime = AppRuntime(
            applicationContext = context,
            dbFactory = ::buildAppDatabase,
            imageStorageFactory = { FakeImageStorage() },
            graphFactory = ::buildAppGraph,
            preflight = { generation ->
                StartupProcessor(isLowRamDevice = { false }).preflightAndArm(
                    graph = generation.graph,
                    appDatabase = generation.database,
                    lifetime = generation.lifetime,
                )
            },
            replacementPolicy = ReplacementPolicy.RebuildInProcess,
        )
    }

    @After
    fun tearDown() {
        runBlocking { runtime.currentGeneration.lifetime.cancelAndJoin() }
        runCatching { runtime.currentGeneration.database.close() }
        wipeFiles()
    }

    @Test
    fun greenGate_restoreThenRollback_coherentGenerationHandover() = runBlocking {
        // Cycle 1: restore.
        val genOne = runtime.currentGeneration
        val daoOne = genOne.database.tagDao
        daoOne.insert(TagEntity(name = SNAPSHOT_SENTINEL))
        assertSuccess(
            "captureSnapshot",
            genOne.graph.databaseSnapshotProvider.captureSnapshot(snapshotFile()),
        )
        daoOne.insert(TagEntity(name = LIVE_ONLY_SENTINEL))
        val inodeBeforeRestore = liveDbInode()

        val restore = runtime.replace(ReplacementOperation.RestoreFromSnapshot(snapshotFile()))
        val genTwo = (restore as? ReplacementOutcome.Completed)?.generation
            ?: fail_("restore transaction must complete; got $restore")

        assertNotEquals("restore must install a new inode", inodeBeforeRestore, liveDbInode())
        assertOldGenerationFailsLoud(genOne)
        assertFalse("old lifetime must have ended", genOne.lifetime.isActive)
        assertNotSame(genOne.database, genTwo.database)
        val namesAfterRestore = genTwo.database.tagDao.searchByPrefix(SENTINEL_PREFIX).map { it.name }
        assertTrue(
            "new generation must serve the snapshot sentinel; got $namesAfterRestore",
            namesAfterRestore.contains(SNAPSHOT_SENTINEL),
        )
        assertFalse(
            "new generation must NOT serve the live-only sentinel; got $namesAfterRestore",
            namesAfterRestore.contains(LIVE_ONLY_SENTINEL),
        )
        val schemaViaNewGraph = genTwo.graph.databaseSnapshotProvider.currentSchemaVersion()
        assertTrue("new graph's provider must read through the new db", schemaViaNewGraph > 0)

        // Cycle 2: rollback.
        val daoTwo = genTwo.database.tagDao
        val reserved = assertSuccess(
            "reserveRollbackSnapshot",
            genTwo.graph.databaseSnapshotProvider.reserveRollbackSnapshot("gate"),
        )
        assertSuccess(
            "stagePromotedRollback",
            genTwo.graph.databaseSnapshotProvider.stagePromotedRollback(reserved, "gate"),
        )
        assertSuccess(
            "completePromotedRollback",
            genTwo.graph.databaseSnapshotProvider.completePromotedRollback(reserved, "gate"),
        )
        daoTwo.insert(TagEntity(name = POST_PRESERVE_SENTINEL))
        val inodeBeforeRollback = liveDbInode()

        val rollback = runtime.replace(ReplacementOperation.RollbackToPreRestoreBackup())
        val genThree = (rollback as? ReplacementOutcome.Completed)?.generation
            ?: fail_("rollback transaction must complete; got $rollback")

        assertNotEquals("rollback must install a new inode", inodeBeforeRollback, liveDbInode())
        assertOldGenerationFailsLoud(genTwo)
        val namesAfterRollback =
            genThree.database.tagDao.searchByPrefix(SENTINEL_PREFIX).map { it.name }
        assertTrue(
            "rolled-back generation must serve the preserved sentinel; got $namesAfterRollback",
            namesAfterRollback.contains(SNAPSHOT_SENTINEL),
        )
        assertFalse(
            "rolled-back generation must NOT serve the post-preserve write; got $namesAfterRollback",
            namesAfterRollback.contains(POST_PRESERVE_SENTINEL),
        )
        assertEquals(
            "the preserved slot must be consumed",
            null,
            genThree.graph.databaseSnapshotProvider.getPreRestoreBackupFile(),
        )
        assertTrue(genThree.id > genTwo.id && genTwo.id > genOne.id)
        assertTrue(genThree.dbGeneration > genTwo.dbGeneration)
    }

    /** The terminal generation throws loud pool-closed; it never serves stale rows. */
    private suspend fun assertOldGenerationFailsLoud(old: RuntimeGeneration) {
        val result = runCatching { old.database.tagDao.searchByPrefix(SENTINEL_PREFIX) }
        val error = result.exceptionOrNull()
        if (error == null) {
            fail(
                "terminal generation ${old.id} served data instead of failing loud: " +
                    "${result.getOrNull()?.map { it.name }} — the §7.1 characterization changed",
            )
        }
        assertTrue(
            "terminal generation must fail with the pool-closed error; got $error",
            error?.message?.contains("Connection pool is closed") == true,
        )
    }

    private fun <T> assertSuccess(label: String, result: BackupResult<T>): T {
        assertTrue("$label must succeed; got $result", result is BackupResult.Success)
        return (result as BackupResult.Success).data
    }

    private fun fail_(message: String): Nothing {
        fail(message)
        error("unreachable")
    }

    private fun liveDbInode(): Long =
        Os.stat(context.getDatabasePath(AppDatabase.NAME).absolutePath).st_ino

    private fun snapshotFile(): File = File(context.cacheDir, "generation_gate_snapshot.db")

    private fun wipeFiles() {
        context.deleteDatabase(AppDatabase.NAME)
        File(context.cacheDir, "pre_restore_backup.db").delete()
        // A journal left over by another test makes the preflight recover instead of proceeding.
        context.deleteSharedPreferences("restore_state_prefs")
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".promoting") }
            .forEach { it.delete() }
        snapshotFile().delete()
    }

    private companion object {
        const val SENTINEL_PREFIX = "gen-gate-"
        const val SNAPSHOT_SENTINEL = "${SENTINEL_PREFIX}a-snapshot"
        const val LIVE_ONLY_SENTINEL = "${SENTINEL_PREFIX}b-live-only"
        const val POST_PRESERVE_SENTINEL = "${SENTINEL_PREFIX}c-post-preserve"
    }
}
