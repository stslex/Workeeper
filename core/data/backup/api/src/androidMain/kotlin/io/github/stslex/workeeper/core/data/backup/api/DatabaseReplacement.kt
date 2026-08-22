// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File

/**
 * The live-database replacement transaction, owned by the RUNTIME (Phase 5 R2 —
 * `kmp-phase-5-startup-processor.md` §8.5). Replaces the direct
 * `DatabaseSnapshotProvider.restoreFromSnapshot` / `rollbackToPreRestoreBackup` calls: the
 * snapshot provider no longer closes or swaps the published database independently — closing a
 * Room 3 database is TERMINAL for the object (§7.1, measured on device), so the only party that
 * may do it is the one that owns the generation being ended and can mint its successor.
 *
 * Callers keep their exact semantics and error taxonomy ([BackupResult]): the settings restore
 * flow and the recovery coordinator call these and then proceed exactly as before — on Android
 * production the implementation runs the `RestartProcess` policy (validate → close → atomic file
 * replacement, no quiescing, the caller's process restart follows), byte-equivalent to the old
 * provider methods. The `RebuildInProcess` policy (Android instrumentation now, the Phase 7 iOS
 * host later) runs the full state machine behind the same signatures.
 *
 * androidMain deliberately (the signature is `java.io.File`; precedent: [BackupStorage]).
 * Implemented by the application runtime host and handed into the app graph as a `create()`
 * bound-instance root — the graph cannot own the thing that replaces it.
 */
interface DatabaseReplacement {

    /**
     * Validate [source] (SQLite magic, schema-version gates — same checks, same order, same
     * [BackupResult] failures as the pre-split provider method), then replace the live database
     * file with it. [source] is NOT consumed — the caller deletes its temp file, as before.
     */
    suspend fun restoreFromSnapshot(source: File): BackupResult<Unit>

    /**
     * Replace the live database file from the preserved `pre_restore_backup.db`, consuming it on
     * success — the Scenario-1 auto-rollback and the undo flow, unchanged semantics.
     */
    suspend fun rollbackToPreRestoreBackup(): BackupResult<Unit>
}
