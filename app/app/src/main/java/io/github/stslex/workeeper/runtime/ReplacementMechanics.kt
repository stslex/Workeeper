// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import java.io.File
import java.io.IOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The pure swap mechanics of the replacement transaction (Phase 5 R2, spec §8.4/§8.5) —
 * stateless pieces extracted from [AppRuntime]: they read and publish NO runtime state, only the
 * close verb, the provider's file mechanics, and the per-transaction bookkeeping the runtime
 * hands them. Everything that touches published state (phases, admission, Fatal) stays on the
 * runtime.
 */

/** Per-transaction point-of-no-return flag — set when the outgoing close begins. */
internal class PonrTracker {
    @Volatile
    var crossed = false
}

/**
 * The per-transaction CoroutineContext marker: installed around the candidate preflight so
 * the coordinator's rollback call is detected as the current transaction's rollback branch,
 * never a nested transaction.
 */
internal class ReplacementTransaction(
    var nextDbGeneration: Int,
) : AbstractCoroutineContextElement(Key) {

    @Volatile
    var candidate: RuntimeGeneration? = null

    @Volatile
    var rolledBack = false

    companion object Key : CoroutineContext.Key<ReplacementTransaction>
}

/**
 * The Android-production ending, byte-equivalent to the pre-split provider methods: close
 * (the generation is now terminal) + atomic file replacement, NO quiescing (process death is
 * the quiescence — the caller's restart flow follows), NO phase change (the app keeps
 * running on the loud-failing closed database until the restart lands, exactly as today).
 * Deliberately startable from an already-terminal generation: the undo IoFailure re-tap
 * re-runs the idempotent close + rename.
 */
internal suspend fun runRestartProcessSwap(
    closeDatabase: (AppDatabase) -> Unit,
    outgoing: RuntimeGeneration,
    provider: DatabaseSnapshotProvider,
    source: File,
    consumeSource: Boolean,
    tracker: PonrTracker,
): ReplacementOutcome {
    val closed = runCatching { closeDatabase(outgoing.database) }
    if (closed.isFailure) {
        // Never rename under a database whose close failed / state is unknown (finding 5).
        // Nothing on disk mutated → pre-mutation rejection; the (possibly degraded)
        // generation keeps serving loud until the user retries — close is idempotent.
        return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.Io(IOException("database close failed: ${closed.exceptionOrNull()}")),
        )
    }
    tracker.crossed = true
    val replaced = provider.replaceLiveDatabaseFile(source)
    if (replaced is BackupResult.Failure) {
        // Today's shipped post-close failure behavior: surface the error, no restart, no
        // rebuild — the closed database fails loud until the user acts. Phase-aware
        // (finding 4): callers must NOT clean recovery assets on this outcome.
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    if (consumeSource) provider.deletePreRestoreBackup()
    return ReplacementOutcome.Completed(generation = null)
}

/** The inline Scenario-1 rollback branch of the CURRENT transaction (see the seam method). */
internal suspend fun runInlineRollback(
    closeDatabase: (AppDatabase) -> Unit,
    transaction: ReplacementTransaction,
): DatabaseReplacementResult {
    val candidate = transaction.candidate
        ?: return DatabaseReplacementResult.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "inline rollback outside a candidate preflight"),
        )
    val provider = candidate.graph.databaseSnapshotProvider
    val rollbackSource = provider.getPreRestoreBackupFile()
        ?: return DatabaseReplacementResult.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
        )
    // The candidate's open-verification handle is the only open handle; close it (terminal)
    // before the file mechanics. A failed close → never rename (finding 5).
    val closed = runCatching { closeDatabase(candidate.database) }
    if (closed.isFailure) {
        return DatabaseReplacementResult.FailedAfterMutation(
            BackupError.Io(IOException("candidate close failed: ${closed.exceptionOrNull()}")),
        )
    }
    val replaced = provider.replaceLiveDatabaseFile(rollbackSource)
    if (replaced is BackupResult.Failure) {
        return DatabaseReplacementResult.FailedAfterMutation(replaced.error)
    }
    // Mark BEFORE consuming (finding 5).
    transaction.rolledBack = true
    provider.deletePreRestoreBackup()
    return DatabaseReplacementResult.Committed
}

internal fun ReplacementOutcome.toSeamResult(): DatabaseReplacementResult = when (this) {
    is ReplacementOutcome.Completed -> DatabaseReplacementResult.Committed
    is ReplacementOutcome.RejectedBeforeMutation ->
        DatabaseReplacementResult.RejectedBeforeMutation(error)

    is ReplacementOutcome.FailedAfterMutation ->
        DatabaseReplacementResult.FailedAfterMutation(error)

    ReplacementOutcome.Fatal -> DatabaseReplacementResult.FatalNoGeneration
}
