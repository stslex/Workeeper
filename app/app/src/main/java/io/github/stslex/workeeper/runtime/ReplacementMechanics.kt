// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The transaction-protocol mechanics of [AppRuntime] (Phase 5 R2, spec §8.4/§8.5) — the
 * admission gates, per-transaction bookkeeping, source staging, typed-effects execution, and
 * the pure swap endings. Everything that touches the runtime's PUBLISHED state (phases,
 * Fatal) stays on the runtime; these pieces read and publish none of it.
 */

/**
 * Per-transaction point-of-no-return flag. Crossed at the START of the FIRST irreversible
 * action — the outgoing teardown (ViewModelStore clear / lifetime cancel), the database close
 * INVOCATION, or the file mutation — never at its completion (spec §8.4): a step that began
 * and failed leaves unknown state, which is post-PONR by definition.
 */
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

    /** The primary failure that made [rolledBack] happen — carried into RecoveredByRollback. */
    @Volatile
    var rollbackCause: BackupError? = null

    /**
     * A candidate/orphan `close()` FAILED — the ladder must stop with Fatal and never attempt
     * another rename over a file an unknown-state handle may still hold (spec §8.4).
     */
    @Volatile
    var closeFailed = false

    /**
     * The CURRENT candidate's database was closed by the inline rollback — the candidate can
     * never be published even if its preflight reports Proceed; the ladder retries fresh.
     */
    @Volatile
    var candidateInvalidated = false

    companion object Key : CoroutineContext.Key<ReplacementTransaction>
}

/** One BuildingGeneration→Preflight→Publishing attempt's result (the ladder's step verdict). */
internal sealed interface AttemptResult {
    class Published(val generation: RuntimeGeneration) : AttemptResult

    /** The attempt failed but every partial resource was released cleanly — a retry is legal. */
    data object Retryable : AttemptResult

    /** A partial resource could not be released (close threw) — the ladder must go Fatal. */
    data object LadderFatal : AttemptResult
}

/** Thrown by generation construction when an ORPHANED candidate database failed to close. */
internal class OrphanCloseException(cause: Throwable) :
    IllegalStateException("orphaned candidate database failed to close", cause)

/**
 * Stages a restore source into a runtime-owned file (spec §8.5 source-ownership transfer).
 * Runs inside the NON-SUSPENDING submission call, so a caller cancellation can never strand a
 * half-transferred file: after this returns, the runtime owns the staged copy and deletes it
 * on every terminal outcome; the caller's own temp-file cleanup becomes a no-op.
 */
internal fun stageRestoreSource(source: File, stagingDirectory: File, sequence: Long): File {
    val staged = File(stagingDirectory, "staged_restore_$sequence.db")
    staged.delete()
    if (!source.renameTo(staged)) {
        // Cross-filesystem or exotic-provider fallback; copyTo throws if the source is gone.
        // A mid-copy failure must not orphan a partial staged file — the terminal cleanup only
        // tracks a SUCCESSFULLY staged copy, so this frame deletes its own debris.
        runCatching { source.copyTo(staged, overwrite = true) }.onFailure { error ->
            staged.delete()
            throw error
        }
        source.delete()
    }
    return staged
}

/**
 * Executes the caller's TERMINAL effect for [outcome] — at most one method, on the calling
 * (transaction) coroutine, under the transition mutex. A failing committed-effect is surfaced
 * on the outcome ([ReplacementOutcome.Completed.effectsError]), never swallowed into a clean
 * commit; failures of compensation effects are logged loudly (the outcome already carries the
 * primary error).
 *
 * **Phase-aware Fatal dispatch:** a transaction that resolves Fatal WITHOUT having crossed its
 * own point of no return (queued behind another transaction's Fatal; submitted onto an
 * already-Fatal runtime) performed NOTHING — dispatching `onFatal` would let a caller journal
 * a mutation that never happened (and later force a rollback of a committed restore), while
 * dispatching the rejection-compensation would let it delete the recovery assets the FATAL
 * transaction's next process needs. Neither is truthful, so no compensation runs; the caller
 * learns from the Fatal result alone.
 */
internal suspend fun runTerminalEffects(
    effects: DatabaseReplacementEffects,
    outcome: ReplacementOutcome,
    ponrCrossed: Boolean,
    logger: Logger,
): ReplacementOutcome = if (outcome is ReplacementOutcome.Fatal && !ponrCrossed) {
    logger.w {
        "transaction performed nothing on a Fatal runtime — no compensation effect dispatched"
    }
    outcome
} else when (outcome) {
    is ReplacementOutcome.Completed ->
        runCatching { effects.onCommitted() }.fold(
            onSuccess = { outcome },
            onFailure = { error ->
                logger.e(error, "onCommitted effects failed — surfacing, not swallowing")
                outcome.copy(
                    effectsError = BackupError.Io(IOException("committed effects failed: $error")),
                )
            },
        )

    is ReplacementOutcome.RejectedBeforeMutation -> outcome.also {
        runCatching { effects.onRejectedBeforeMutation(outcome.error) }
            .onFailure { logger.e(it, "onRejectedBeforeMutation effects failed") }
    }

    is ReplacementOutcome.RecoveredByRollback -> outcome.also {
        runCatching { effects.onRecoveredByRollback(outcome.error) }
            .onFailure { logger.e(it, "onRecoveredByRollback effects failed") }
    }

    is ReplacementOutcome.FailedAfterMutation -> outcome.also {
        runCatching { effects.onFailedAfterMutation(outcome.error) }
            .onFailure { logger.e(it, "onFailedAfterMutation effects failed") }
    }

    ReplacementOutcome.Fatal -> outcome.also {
        runCatching { effects.onFatal() }
            .onFailure { logger.e(it, "onFatal effects failed") }
    }
}

/**
 * The Android-production ending, byte-equivalent to the pre-split provider methods: close
 * (the generation is now terminal) + atomic file replacement, NO quiescing (process death is
 * the quiescence — the caller's restart flow follows), NO phase change (the app keeps
 * running on the loud-failing closed database until the restart lands, exactly as today).
 * Deliberately startable from an already-terminal generation: a failure re-tap re-runs the
 * idempotent close + rename.
 *
 * PONR = the close INVOCATION (spec §8.4): [tracker] is crossed BEFORE the call, and a close
 * throw is a post-PONR unknown state — [ReplacementOutcome.FailedAfterMutation], never
 * RejectedBeforeMutation; every recovery asset stays preserved for the journal protocol.
 */
internal suspend fun runRestartProcessSwap(
    closeDatabase: (AppDatabase) -> Unit,
    outgoing: RuntimeGeneration,
    provider: DatabaseSnapshotProvider,
    source: File,
    consumeSource: Boolean,
    tracker: PonrTracker,
): ReplacementOutcome {
    tracker.crossed = true
    val closed = runCatching { closeDatabase(outgoing.database) }
    if (closed.isFailure) {
        return ReplacementOutcome.FailedAfterMutation(
            BackupError.Io(IOException("database close failed: ${closed.exceptionOrNull()}")),
        )
    }
    val replaced = provider.replaceLiveDatabaseFile(source)
    if (replaced is BackupResult.Failure) {
        // Today's shipped post-close failure behavior: surface the error, no restart, no
        // rebuild — the closed database fails loud until the user acts. Assets preserved;
        // the caller's onFailedAfterMutation effects journal the interrupted mutation.
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    if (consumeSource) provider.deletePreRestoreBackup()
    return ReplacementOutcome.Completed(generation = null)
}

/** The inline Scenario-1 rollback branch of the CURRENT transaction (see the seam method). */
internal suspend fun runInlineRollback(
    closeDatabase: (AppDatabase) -> Unit,
    transaction: ReplacementTransaction,
): ReplacementOutcome {
    val candidate = transaction.candidate
        ?: return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "inline rollback outside a candidate preflight"),
        )
    val provider = candidate.graph.databaseSnapshotProvider
    val rollbackSource = provider.getPreRestoreBackupFile()
        ?: return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
        )
    // The candidate's open-verification handle is the only open handle; close it (terminal)
    // before the file mechanics. Closing invalidates the candidate for publication either way;
    // a failed candidate close leaves an unknown-state handle over the live file — the LADDER
    // MUST STOP (Fatal), and no further rename may run (spec §8.4).
    transaction.candidateInvalidated = true
    val closed = runCatching { closeDatabase(candidate.database) }
    if (closed.isFailure) {
        transaction.closeFailed = true
        return ReplacementOutcome.FailedAfterMutation(
            BackupError.Io(IOException("candidate close failed: ${closed.exceptionOrNull()}")),
        )
    }
    val replaced = provider.replaceLiveDatabaseFile(rollbackSource)
    if (replaced is BackupResult.Failure) {
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    // Mark BEFORE consuming, and record the cause for the outer transaction's outcome.
    transaction.rolledBack = true
    transaction.rollbackCause = BackupError.Io(
        IOException("restore rolled back by the scenario-1 preflight"),
    )
    provider.deletePreRestoreBackup()
    return ReplacementOutcome.Completed(generation = null)
}

/**
 * The UI attachment admission gate (spec §8.4 Quiescing step 1). Counts attachments per
 * generation id; a transition RETIRES its outgoing id in the SAME atomic compare-and-set that
 * observes the count at zero, so no late attach can land between the zero observation and
 * anything irreversible: an attach against a retired id is refused (and logged loudly — the
 * region cannot exist, its phase is no longer published). An ABORTED transition un-retires the
 * id before republishing; a COMMITTED one leaves it retired forever.
 */
internal class UiAdmissionGate(private val logger: Logger) {

    private data class State(
        val counts: Map<Int, Int> = emptyMap(),
        val retired: Set<Int> = emptySet(),
    )

    private val state = MutableStateFlow(State())

    fun attach(id: Int) {
        val after = state.updateAndGet { s ->
            if (id in s.retired) s
            else s.copy(counts = s.counts + (id to (s.counts[id] ?: 0) + 1))
        }
        if (id in after.retired) {
            // Refused: retirement only ever happens at count zero, so a retired id has no
            // outstanding attachments — this attach is a stale frame's and must not pass.
            logger.e(
                IllegalStateException("ui attach for RETIRED generation $id refused"),
                "a stale frame attached a retired generation — refused by the admission gate",
            )
        }
    }

    fun dispose(id: Int) {
        state.update { s ->
            if (id in s.retired) s
            else when (val remaining = (s.counts[id] ?: 0) - 1) {
                in Int.MIN_VALUE..0 -> s.copy(counts = s.counts - id)
                else -> s.copy(counts = s.counts + (id to remaining))
            }
        }
    }

    /**
     * Awaits the id's attachment count reaching zero and CLOSES admission for it in one atomic
     * step (the retire CAS). Returns false on timeout — the id stays un-retired.
     */
    suspend fun awaitRetired(id: Int, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            var retired = false
            while (!retired) {
                state.first { s -> (s.counts[id] ?: 0) == 0 || id in s.retired }
                val after = state.updateAndGet { s ->
                    if ((s.counts[id] ?: 0) == 0 && id !in s.retired) {
                        s.copy(counts = s.counts - id, retired = s.retired + id)
                    } else {
                        s
                    }
                }
                retired = id in after.retired
            }
        } != null

    /** Reopens attachment admission for an ABORTED transition's outgoing id. */
    fun reopen(id: Int) {
        state.update { s -> s.copy(retired = s.retired - id) }
    }

    /** Test/diagnostic view: the current attachment count for [id]. */
    fun attachmentCount(id: Int): Int = state.value.counts[id] ?: 0
}

/**
 * The DB-bound background-work admission gate (spec §8.4 Quiescing step 2): leases are
 * acquired atomically with the generation deps at the work's FIRST operation, a transition
 * closes admission and awaits the outstanding count, and blocked acquirers suspend through
 * the bounded transition window.
 */
internal class WorkerAdmissionGate {

    private val lock = Any()
    private val closed = MutableStateFlow(false)
    private val activeLeases = MutableStateFlow(0)

    /**
     * Suspends while admission is closed, then atomically (under the gate lock) increments the
     * lease count and captures the current generation's deps via [deps] — which must be
     * fail-fast on a Fatal runtime so no work ever binds to a closed generation.
     */
    suspend fun awaitLease(deps: () -> BackupWorkerDeps): BackupWorkLease {
        while (true) {
            synchronized(lock) {
                if (!closed.value) {
                    val bound = deps()
                    activeLeases.update { it + 1 }
                    return LeaseImpl(bound)
                }
            }
            closed.first { isClosed -> !isClosed }
        }
    }

    fun close() = synchronized(lock) { closed.value = true }

    fun reopen() = synchronized(lock) { closed.value = false }

    suspend fun awaitDrained(timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) { activeLeases.first { it == 0 } } != null

    private inner class LeaseImpl(override val deps: BackupWorkerDeps) : BackupWorkLease {
        private val released = AtomicBoolean(false)
        override fun release() {
            if (released.compareAndSet(false, true)) {
                activeLeases.update { count -> (count - 1).coerceAtLeast(0) }
            }
        }
    }
}

/** Monotonic staged-source sequence — process-scoped, feeds [stageRestoreSource] file names. */
internal val stagedSourceSequence = AtomicLong(0)

/** Maps the runtime's transaction outcome onto the caller seam's result type. */
internal fun ReplacementOutcome.toSeamResult(): DatabaseReplacementResult = when (this) {
    is ReplacementOutcome.Completed -> DatabaseReplacementResult.Committed(effectsError)

    is ReplacementOutcome.RejectedBeforeMutation ->
        DatabaseReplacementResult.RejectedBeforeMutation(error)

    is ReplacementOutcome.RecoveredByRollback ->
        DatabaseReplacementResult.RecoveredByRollback(error)

    is ReplacementOutcome.FailedAfterMutation ->
        DatabaseReplacementResult.FailedAfterMutation(error)

    ReplacementOutcome.Fatal -> DatabaseReplacementResult.FatalNoGeneration
}
