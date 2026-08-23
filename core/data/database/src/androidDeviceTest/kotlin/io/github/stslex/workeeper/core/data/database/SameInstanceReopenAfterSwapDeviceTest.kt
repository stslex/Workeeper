// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProviderImpl
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PHASE 5 ROOM ENTRY GATE — real device, production driver, production swap code. Answers the
 * question `kmp-migration-assessment.md` §"restart-free actual" specified for Room 2.8.4 but never
 * ran, now against the shipped Room 3 driver mode: after the PRODUCTION atomic database-file
 * replacement (restore or rollback), can the **same `AppDatabase` object** and a **DAO captured
 * before the close** serve the swapped file's data?
 *
 * ## MEASURED ANSWER (Pixel 6 emulator, API 34, arm64-v8a, Room 3.0.0, 2026-08-22): NO — RED.
 *
 * Both production replacement paths succeed on disk (proven by the fresh-handle disk-truth read
 * and the inode change), and the subsequent read through the retained DAO throws
 * `android.database.SQLException: Error code: 21, message: Connection pool is closed`
 * (`ConnectionPoolImpl.useConnection` → `RoomConnectionManager.useConnection` →
 * `RoomDatabase.useConnection` → `performSuspending`). Room 3's `RoomDatabase.close()` is
 * terminal for the object: `closeBarrier.close()` is a one-way CAS, the connection manager is
 * assigned once, and a closed pool throws `SQLITE_MISUSE` forever — there is no reopen path.
 * The Room 2.8.4-era claim that "captured DAOs follow the reopen for free"
 * (kmp-migration-assessment.md:546) does NOT hold on Room 3.
 *
 * What this test therefore PINS (and must keep pinning):
 *  1. the production swap really replaces the file (disk truth: snapshot sentinel present,
 *     live-only sentinel absent, inode changed) — a bypassed swap goes red here (known-negative
 *     of the gate protocol, run and reverted, documentation/feature-specs/
 *     kmp-phase-5-startup-processor.md §7);
 *  2. a stale captured handle fails **LOUD** — it never silently serves the pre-swap rows. The
 *     silent-stale-inode outcome is the corruption class the assessment warned about; measured,
 *     it does not occur: the pool-closed throw is deterministic;
 *  3. the tripwire: **if [GateOutcome.ReopenServedSwappedData] is ever observed, this test fails
 *     with a "gate flipped GREEN" message** — that means a Room upgrade made same-object reopen
 *     real, and the Phase 5 restore-flow descope (restore/rollback stay process-restart) should
 *     be revisited. A green flip must be a loud, deliberate discovery, not a silent one.
 *
 * Anti-vacuity (same discipline as [AtomicRollbackDeviceTest]): the pre-swap read through the
 * retained DAO must see both sentinels (proves the handle worked before the swap), every
 * production call is asserted `Success`, and disk truth is asserted before the gate read — so a
 * gate-read failure can only ever mean "the same object cannot serve the swapped file", never
 * "the swap silently did nothing".
 *
 * ⚠️ MUST STAY androidDeviceTest + FILE-BACKED + BundledSQLiteDriver. Robolectric is not an
 * admissible oracle for file-handle/connection-pool semantics (see AtomicRollbackDeviceTest's
 * header), and an in-memory DB has no file to swap.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class SameInstanceReopenAfterSwapDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    /** Captured BEFORE any close — the gate reads through this exact instance. */
    private lateinit var retainedDao: TagDao
    private lateinit var provider: DatabaseSnapshotProviderImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wipeFiles()
        // Production shape: file-backed at the production name (the swap code resolves
        // getDatabasePath(AppDatabase.NAME) internally), production driver. Migrations are
        // irrelevant here — the file is created fresh at the current schema version.
        database = Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .setDriver(BundledSQLiteDriver())
            .build()
        retainedDao = database.tagDao
        provider = DatabaseSnapshotProviderImpl(
            appDatabase = database,
            context = context,
            dispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        wipeFiles()
    }

    @Test
    fun gate_restoreSwap_retainedHandlesFailLoud_neverStale() = runBlocking {
        retainedDao.insert(TagEntity(name = SNAPSHOT_SENTINEL))
        assertSuccess("captureSnapshot", provider.captureSnapshot(snapshotFile()))
        retainedDao.insert(TagEntity(name = LIVE_ONLY_SENTINEL))
        assertEquals(
            "pre-swap: the retained DAO must see both sentinels (handle-works precondition)",
            listOf(SNAPSHOT_SENTINEL, LIVE_ONLY_SENTINEL),
            readSentinelsViaRetainedDao(),
        )
        val inodeBeforeSwap = liveDbInode()

        // The production RestartProcess transaction sequence (spec §8.5): validate through the
        // still-open db → close (terminal; runtime-owned) → pure file mechanics.
        assertSuccess("validateSnapshotForRestore", provider.validateSnapshotForRestore(snapshotFile()))
        closeAppDatabase(database)
        assertSuccess("replaceLiveDatabaseFile", provider.replaceLiveDatabaseFile(snapshotFile()))

        assertSwapRealOnDisk(inodeBeforeSwap)
        assertGateOutcome()
    }

    @Test
    fun gate_rollbackSwap_retainedHandlesFailLoud_neverStale() = runBlocking {
        retainedDao.insert(TagEntity(name = SNAPSHOT_SENTINEL))
        // Stage the production undo slot (cache/pre_restore_backup.db) through the production
        // preserve path — the same file rollbackToPreRestoreBackup consumes.
        // R3: the canonical undo slot is staged by reserve+promote (the runtime's own sequence,
        // spec §8.5a) — identical bytes at the identical path as the removed preserveCurrentDb().
        val reserved = assertSuccess("reserveRollbackSnapshot", provider.reserveRollbackSnapshot("gate"))
        assertSuccess("promoteRollbackReservation", provider.promoteRollbackReservation(reserved))
        retainedDao.insert(TagEntity(name = LIVE_ONLY_SENTINEL))
        assertEquals(
            "pre-swap: the retained DAO must see both sentinels (handle-works precondition)",
            listOf(SNAPSHOT_SENTINEL, LIVE_ONLY_SENTINEL),
            readSentinelsViaRetainedDao(),
        )
        val inodeBeforeSwap = liveDbInode()

        // The production rollback sequence: resolve source → close (terminal) → replace → consume.
        val rollbackSource = requireNotNull(provider.getPreRestoreBackupFile()) {
            "reserve+promote must have staged the pre-restore slot"
        }
        closeAppDatabase(database)
        assertSuccess("replaceLiveDatabaseFile", provider.replaceLiveDatabaseFile(rollbackSource))
        provider.deletePreRestoreBackup()

        assertSwapRealOnDisk(inodeBeforeSwap)
        assertGateOutcome()
    }

    /** One-shot read of this test's sentinels through the DAO captured before the swap. */
    private suspend fun readSentinelsViaRetainedDao(): List<String> =
        retainedDao.searchByPrefix(SENTINEL_PREFIX).map { it.name }

    /**
     * Disk truth through a FRESH framework-SQLite handle on the live path, plus file identity:
     * the atomic rename must have installed a NEW inode. Red here means the production swap
     * itself failed; the gate classification below is then not reached, so a gate red can only
     * ever mean "the same object cannot serve the swapped file".
     */
    private fun assertSwapRealOnDisk(inodeBeforeSwap: Long) {
        val inodeAfterSwap = liveDbInode()
        assertNotEquals(
            "FILE IDENTITY: the atomic rename must install a new inode " +
                "(before=$inodeBeforeSwap, after=$inodeAfterSwap)",
            inodeBeforeSwap,
            inodeAfterSwap,
        )
        val onDisk = SQLiteDatabase.openDatabase(
            context.getDatabasePath(AppDatabase.NAME).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery(
                "SELECT name FROM tag_table WHERE name LIKE '$SENTINEL_PREFIX%' ORDER BY name",
                null,
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
        assertTrue(
            "DISK TRUTH: the swapped file must contain the snapshot sentinel; got $onDisk",
            onDisk.contains(SNAPSHOT_SENTINEL),
        )
        assertFalse(
            "DISK TRUTH: the swapped file must not contain the live-only sentinel; got $onDisk",
            onDisk.contains(LIVE_ONLY_SENTINEL),
        )
    }

    /** The measured outcome of reading through the retained handles after the swap. */
    private sealed interface GateOutcome {
        /** Room served the swapped file through the retained object — the gate would be GREEN. */
        data class ReopenServedSwappedData(val names: List<String>) : GateOutcome

        /** The corruption class: the retained object silently served pre-swap rows. */
        data class SilentlyServedStaleData(val names: List<String>) : GateOutcome

        /** Today's measured truth: a loud, deterministic pool-closed failure. */
        data class FailedLoud(val error: Throwable) : GateOutcome
    }

    /**
     * THE GATE, classified. Exactly one outcome is currently legal: [GateOutcome.FailedLoud] with
     * Room 3's pool-closed `SQLException`. The other two outcomes fail with messages explaining
     * what a flip means — see the class KDoc.
     */
    private suspend fun assertGateOutcome() {
        val outcome = runCatching { readSentinelsViaRetainedDao() }.fold(
            onSuccess = { names ->
                when {
                    names.contains(LIVE_ONLY_SENTINEL) -> GateOutcome.SilentlyServedStaleData(names)
                    names.contains(SNAPSHOT_SENTINEL) -> GateOutcome.ReopenServedSwappedData(names)
                    else -> GateOutcome.SilentlyServedStaleData(names)
                }
            },
            onFailure = { GateOutcome.FailedLoud(it) },
        )
        when (outcome) {
            is GateOutcome.FailedLoud -> assertTrue(
                "MEASURED PIN: the retained-handle failure must be Room 3's loud pool-closed " +
                    "SQLException; got ${outcome.error}",
                outcome.error is SQLException &&
                    outcome.error.message?.contains("Connection pool is closed") == true,
            )

            is GateOutcome.ReopenServedSwappedData -> fail(
                "GATE FLIPPED GREEN: the same AppDatabase/DAO served the swapped file " +
                    "(${outcome.names}). Same-object reopen after close() now works — revisit the " +
                    "Phase 5 restore-flow descope (kmp-phase-5-startup-processor.md §7) before " +
                    "changing this test.",
            )

            is GateOutcome.SilentlyServedStaleData -> fail(
                "CORRUPTION CLASS: the retained handles silently served pre-swap/absent data " +
                    "(${outcome.names}) instead of failing loud — the stale-inode outcome " +
                    "kmp-migration-assessment.md:553 warned about. This must never ship.",
            )
        }
    }

    private fun <T> assertSuccess(label: String, result: BackupResult<T>): T {
        assertTrue("$label must succeed; got $result", result is BackupResult.Success)
        return (result as BackupResult.Success).data
    }

    private fun liveDbInode(): Long =
        Os.stat(context.getDatabasePath(AppDatabase.NAME).absolutePath).st_ino

    private fun snapshotFile(): File = File(context.cacheDir, "reopen_gate_snapshot.db")

    private fun wipeFiles() {
        context.deleteDatabase(AppDatabase.NAME)
        File(context.cacheDir, "pre_restore_backup.db").delete()
        snapshotFile().delete()
    }

    private companion object {
        // Alphabetical order pins the pre-swap read: "…a-snapshot" < "…b-live-only".
        const val SENTINEL_PREFIX = "reopen-gate-"
        const val SNAPSHOT_SENTINEL = "${SENTINEL_PREFIX}a-snapshot"
        const val LIVE_ONLY_SENTINEL = "${SENTINEL_PREFIX}b-live-only"
    }
}
