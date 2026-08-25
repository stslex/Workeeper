// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import java.io.File

/** Runtime-owned live-database replacement transaction. Every operation has one unique owner. */
interface DatabaseReplacement {

    /** Ownership transfers into no-backup storage before the first suspension. */
    suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult

    /** Applies exactly [sourceRef]; positional or arbitrary-path rollback is not representable. */
    suspend fun rollbackFromUndo(
        sourceRef: UndoRef,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult
}

/** Caller bookkeeping invoked under runtime serialization. No production no-op owner exists. */
interface DatabaseReplacementEffects {

    val attemptId: RestoreOwnerId

    /** After reversible quiescence, Restore receives undo/source; rollback receives its exact ref. */
    suspend fun onBeforeMutation(
        undoRef: UndoRef,
        restoreSourceRef: RestoreSourceRef?,
    )

    /** Records durable commit bookkeeping before any best-effort file deletion. */
    suspend fun onMutationCommitted()

    /** Re-journals an in-process restore compensation under its own exact rollback owner. */
    suspend fun onBeforeCompensation(
        rollbackOwner: RestoreOwnerId,
        appliedRef: UndoRef,
    )

    /** Records the exact compensation rollback as committed. */
    suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId)

    suspend fun onRejectedBeforeMutation(error: BackupError) {}

    /** Terminal: the requested operation committed and mandatory state is durable. */
    suspend fun onCommitted() {}

    suspend fun onRecoveredByRollback(error: BackupError) {}

    suspend fun onFailedAfterMutation(error: BackupError) {}

    suspend fun onFatal() {}
}

/** Phase-aware replacement outcome. */
sealed interface DatabaseReplacementResult {

    val effectsError: BackupError?

    data class Committed(override val effectsError: BackupError? = null) : DatabaseReplacementResult

    data class RejectedBeforeMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    data class RecoveredByRollback(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    data class FailedAfterMutation(
        val error: BackupError,
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult

    data class FatalNoGeneration(
        override val effectsError: BackupError? = null,
    ) : DatabaseReplacementResult
}
