// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import java.io.File

/**
 * Runtime-owned live-database replacement transaction. Restore source ownership transfers at
 * submission; [DatabaseReplacementEffects.None] is valid, so do not assume caller cleanup.
 */
interface DatabaseReplacement {

    /** Validates and stages [source], then replaces the live database through the runtime. */
    suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects = DatabaseReplacementEffects.None,
    ): DatabaseReplacementResult

    /** Replaces the live database from the selected rollback source. */
    suspend fun rollbackToPreRestoreBackup(
        /** `null` selects the canonical undo slot; otherwise the exact journal source is used. */
        sourcePath: String? = null,
        effects: DatabaseReplacementEffects = DatabaseReplacementEffects.None,
    ): DatabaseReplacementResult
}

/**
 * Caller effects for one transaction. The runtime invokes `onBeforeMutation` and exactly one
 * terminal method under serialization. Effects are idempotent and may be [None].
 */
interface DatabaseReplacementEffects {

    /** Caller-defined attempt identity for any durable bookkeeping. */
    val attemptId: String

    /** Pre-mutation preparation; [rollbackSnapshotPath] identifies the reserved snapshot. */
    suspend fun onBeforeMutation(rollbackSnapshotPath: String) {}

    /** Records durable commit bookkeeping before rollback-source consumption. */
    suspend fun onMutationCommitted() {}

    suspend fun onRejectedBeforeMutation(error: BackupError) {}

    /** Terminal: the REQUESTED operation committed. */
    suspend fun onCommitted() {}

    /** Terminal recovery onto pre-operation data; availability must use actual canonical state. */
    suspend fun onRecoveredByRollback(error: BackupError) {}

    /** Terminal post-PONR failure; preserve recovery assets. */
    suspend fun onFailedAfterMutation(error: BackupError) {}

    /** Terminal runtime failure; preserve assets. */
    suspend fun onFatal() {}

    object None : DatabaseReplacementEffects {
        override val attemptId: String = "no-effects"
    }
}

/** Phase-aware replacement outcome. */
sealed interface DatabaseReplacementResult {

    /** Non-null when the matching caller effect failed. */
    val effectsError: BackupError?

    /** Requested operation committed; non-null [effectsError] means bookkeeping still failed. */
    data class Committed(override val effectsError: BackupError? = null) : DatabaseReplacementResult

    /** No irreversible mutation occurred. */
    data class RejectedBeforeMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /** Restore rolled back to pre-operation data; callers must report restore failure. */
    data class RecoveredByRollback(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /** Post-PONR failure with preserved runtime-owned recovery assets. */
    data class FailedAfterMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    /** No generation is serving; the runtime is terminal and recovery must be surfaced. */
    data class FatalNoGeneration(
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult
}
