// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.runtime.AppRuntime
import io.github.stslex.workeeper.runtime.ReplacementOperation
import io.github.stslex.workeeper.runtime.ReplacementOutcome
import io.github.stslex.workeeper.runtime.ReplacementPolicy
import io.github.stslex.workeeper.runtime.RuntimeGeneration
import io.github.stslex.workeeper.runtime.launchStartupProcessor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
                launchStartupProcessor(context, isLowRamDevice = { false }).preflightAndArm(
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

        val restoreEffects = PersistedProtocolEffects.forRestore(
            owner = RESTORE_OWNER,
            repository = genOne.graph.restoreStateRepository,
            context = RESTORE_CONTEXT,
        )
        val restore = runtime.replace(
            operation = ReplacementOperation.RestoreFromSnapshot(snapshotFile(), RESTORE_OWNER),
            effects = restoreEffects,
        )
        val completedRestore = restore as? ReplacementOutcome.Completed
            ?: fail_("restore transaction must complete; got $restore")
        val genTwo = completedRestore.generation
            ?: fail_("in-process restore must publish a generation")
        assertNull("restore protocol effects must be durable", completedRestore.effectsError)

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
        val stateAfterRestore = protocolState(genTwo)
        val activeUndo = stateAfterRestore.activeUndo
        assertEquals(UndoRef(RESTORE_OWNER), activeUndo?.ref)
        assertNull("verified restore must resolve its attempt", stateAfterRestore.attempt)

        // Cycle 2: rollback.
        val daoTwo = genTwo.database.tagDao
        daoTwo.insert(TagEntity(name = POST_PRESERVE_SENTINEL))
        val inodeBeforeRollback = liveDbInode()

        val appliedRef = requireNotNull(activeUndo).ref
        val rollbackEffects = PersistedProtocolEffects.forUserUndo(
            owner = ROLLBACK_OWNER,
            repository = genTwo.graph.restoreStateRepository,
            sourceRef = appliedRef,
        )
        val rollback = runtime.replace(
            operation = ReplacementOperation.RollbackFromUndo(appliedRef, ROLLBACK_OWNER),
            effects = rollbackEffects,
        )
        val completedRollback = rollback as? ReplacementOutcome.Completed
            ?: fail_("rollback transaction must complete; got $rollback")
        val genThree = completedRollback.generation
            ?: fail_("in-process rollback must publish a generation")
        assertNull("rollback protocol effects must be durable", completedRollback.effectsError)

        assertNotEquals("rollback must install a new inode", inodeBeforeRollback, liveDbInode())
        assertOldGenerationFailsLoud(genTwo)
        val namesAfterRollback =
            genThree.database.tagDao.searchByPrefix(SENTINEL_PREFIX).map { it.name }
        assertTrue(
            "rolled-back generation must serve the preserved sentinel; got $namesAfterRollback",
            namesAfterRollback.contains(SNAPSHOT_SENTINEL),
        )
        assertTrue(
            "exact N must restore the pre-restore live-only row; got $namesAfterRollback",
            namesAfterRollback.contains(LIVE_ONLY_SENTINEL),
        )
        assertFalse(
            "rolled-back generation must NOT serve the post-preserve write; got $namesAfterRollback",
            namesAfterRollback.contains(POST_PRESERVE_SENTINEL),
        )
        val stateAfterRollback = protocolState(genThree)
        assertNull("user undo must resolve its attempt", stateAfterRollback.attempt)
        assertNull("user undo must clear only its matching active ref", stateAfterRollback.activeUndo)
        assertEquals(
            "the exact immutable undo must be consumed",
            null,
            genThree.graph.databaseSnapshotProvider.getUndoFile(appliedRef),
        )
        assertTrue(genThree.id > genTwo.id && genTwo.id > genOne.id)
        assertTrue(genThree.dbGeneration > genTwo.dbGeneration)
    }

    private suspend fun protocolState(generation: RuntimeGeneration): RestoreProtocolState {
        val read = generation.graph.restoreStateRepository.readProtocol()
        assertTrue("expected reconciled current protocol, got $read", read is RestoreProtocolRead.Current)
        return (read as RestoreProtocolRead.Current).state
    }

    private class PersistedProtocolEffects private constructor(
        override val attemptId: RestoreOwnerId,
        private val repository: RestoreStateRepository,
        private val kind: MutationKind,
        private val restoreContext: RestoreInProgressContext?,
        private val userUndoRef: UndoRef?,
    ) : DatabaseReplacementEffects {

        private var compensationOwner: RestoreOwnerId? = null

        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) {
            val attempt = when (kind) {
                MutationKind.Restore -> {
                    check(undoRef == UndoRef(attemptId))
                    check(restoreSourceRef == RestoreSourceRef(attemptId))
                    RestoreAttempt.Restore(
                        id = attemptId,
                        phase = RestoreAttempt.Phase.Prepared,
                        context = requireNotNull(restoreContext),
                        undoRef = undoRef,
                        sourceRef = restoreSourceRef,
                    )
                }

                MutationKind.UserUndo -> {
                    val exactRef = requireNotNull(userUndoRef)
                    check(undoRef == exactRef)
                    check(restoreSourceRef == null)
                    RestoreAttempt.Rollback(
                        id = attemptId,
                        phase = RestoreAttempt.Phase.Prepared,
                        sourceRef = exactRef,
                        origin = RestoreAttempt.RollbackOrigin.UserUndo,
                    )
                }
            }
            check(repository.beginAttempt(attempt))
        }

        override suspend fun onMutationCommitted() {
            check(repository.recordAttemptCommitted(attemptId))
        }

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) {
            check(kind == MutationKind.Restore)
            check(appliedRef == UndoRef(attemptId))
            val rollback = RestoreAttempt.Rollback(
                id = rollbackOwner,
                phase = RestoreAttempt.Phase.Prepared,
                sourceRef = appliedRef,
                origin = RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
            )
            check(repository.beginCompensation(attemptId, rollback))
            compensationOwner = rollbackOwner
        }

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) {
            check(compensationOwner == rollbackOwner)
            check(repository.recordAttemptCommitted(rollbackOwner))
        }

        private enum class MutationKind { Restore, UserUndo }

        companion object {
            fun forRestore(
                owner: RestoreOwnerId,
                repository: RestoreStateRepository,
                context: RestoreInProgressContext,
            ): PersistedProtocolEffects = PersistedProtocolEffects(
                attemptId = owner,
                repository = repository,
                kind = MutationKind.Restore,
                restoreContext = context,
                userUndoRef = null,
            )

            fun forUserUndo(
                owner: RestoreOwnerId,
                repository: RestoreStateRepository,
                sourceRef: UndoRef,
            ): PersistedProtocolEffects = PersistedProtocolEffects(
                attemptId = owner,
                repository = repository,
                kind = MutationKind.UserUndo,
                restoreContext = null,
                userUndoRef = sourceRef,
            )
        }
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
        File(context.noBackupFilesDir, "restore-recovery").deleteRecursively()
        File(context.cacheDir, "recovery_share").deleteRecursively()
        snapshotFile().delete()
    }

    private companion object {
        const val SENTINEL_PREFIX = "gen-gate-"
        const val SNAPSHOT_SENTINEL = "${SENTINEL_PREFIX}a-snapshot"
        const val LIVE_ONLY_SENTINEL = "${SENTINEL_PREFIX}b-live-only"
        const val POST_PRESERVE_SENTINEL = "${SENTINEL_PREFIX}c-post-preserve"
        val RESTORE_OWNER = RestoreOwnerId("40000000-0000-4000-8000-000000000001")
        val ROLLBACK_OWNER = RestoreOwnerId("40000000-0000-4000-8000-000000000002")
        val RESTORE_CONTEXT = RestoreInProgressContext(
            backupSchemaVersion = APP_DATABASE_VERSION,
            backupCreatedAtEpochMs = 1_700_000_000_000L,
            backupAppVersion = "device-gate",
            startedAtEpochMs = 1_700_000_100_000L,
        )
    }
}
