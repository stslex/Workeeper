// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Crossed before the first irreversible action. */
internal class PonrTracker {
    @Volatile
    var crossed = false
}

/** Context marker that routes preflight rollback into its current transaction. */
internal class ReplacementTransaction(
    var nextDbGeneration: Int,
) : AbstractCoroutineContextElement(Key) {

    @Volatile
    var candidate: RuntimeGeneration? = null

    @Volatile
    var rolledBack = false

    @Volatile
    var rollbackCause: BackupError? = null

    /** A candidate close failed, so the ladder must stop. */
    @Volatile
    var closeFailed = false

    /** Inline rollback invalidated the current candidate. */
    @Volatile
    var candidateInvalidated = false

    @Volatile
    var candidateDisposed = false

    companion object Key : CoroutineContext.Key<ReplacementTransaction>
}

/** One in-flight replacement submission's bookkeeping (single-flight per operation). */
internal class InFlightReplacement(
    val operation: ReplacementOperation,
    val outcome: CompletableDeferred<ReplacementOutcome>,
) {
    /** The runtime-owned staged restore source; deleted on every terminal outcome. */
    @Volatile
    var stagedSource: File? = null

    @Volatile
    var stagingFailure: Throwable? = null

    /** The rollback snapshot this attempt reserved inside the transaction (spec §8.5a). */
    @Volatile
    var reservation: File? = null
}

/** Marks a graph-only transition's preflight — a rollback inside it is rejected, not run. */
internal object GraphOnlyTransition : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> get() = Key

    object Key : CoroutineContext.Key<GraphOnlyTransition>
}

internal sealed interface AttemptResult {
    class Published(val generation: RuntimeGeneration) : AttemptResult

    /** The attempt failed but every partial resource was released cleanly — a retry is legal. */
    data object Retryable : AttemptResult

    /** A partial resource could not be released (close threw) — the ladder must go Fatal. */
    data object LadderFatal : AttemptResult
}

internal class OrphanCloseException(cause: Throwable) :
    IllegalStateException("orphaned candidate database failed to close", cause)

/** A shared-database candidate could not be fully unwound and must not be republished beside N. */
internal class PartialCandidateUnwindException(cause: Throwable) :
    IllegalStateException("partially built candidate could not be unwound", cause)

/** Stages a restore source before suspension, transferring ownership to the runtime. */
internal fun stageRestoreSource(source: File, stagingDirectory: File, sequence: Long): File {
    val staged = File(stagingDirectory, "staged_restore_$sequence.db")
    staged.delete()
    if (!source.renameTo(staged)) {
        // The copy fallback cleans its partial file: terminal cleanup sees only a completed stage.
        runCatching { source.copyTo(staged, overwrite = true) }.onFailure { error ->
            staged.delete()
            throw error
        }
        source.delete()
    }
    return staged
}

/**
 * Runs exactly one terminal effect on the transaction coroutine. Effect failures are surfaced on
 * the outcome. A pre-PONR Fatal dispatches no effect because this transaction did no mutation.
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
    is ReplacementOutcome.Completed -> outcome.withEffects(logger, "onCommitted") {
        effects.onCommitted()
    }

    is ReplacementOutcome.RejectedBeforeMutation ->
        outcome.withEffects(logger, "onRejectedBeforeMutation") {
            effects.onRejectedBeforeMutation(outcome.error)
        }

    is ReplacementOutcome.RecoveredByRollback ->
        outcome.withEffects(logger, "onRecoveredByRollback") {
            effects.onRecoveredByRollback(outcome.error)
        }

    is ReplacementOutcome.FailedAfterMutation ->
        outcome.withEffects(logger, "onFailedAfterMutation") {
            effects.onFailedAfterMutation(outcome.error)
        }

    is ReplacementOutcome.Fatal -> outcome.withEffects(logger, "onFatal") { effects.onFatal() }
}

private suspend fun ReplacementOutcome.withEffects(
    logger: Logger,
    label: String,
    block: suspend () -> Unit,
): ReplacementOutcome = runCatching { block() }.fold(
    onSuccess = { this },
    onFailure = { error ->
        logger.e(error, "$label effects failed — surfacing on the outcome, not swallowing")
        val effectsError = BackupError.Io(IOException("$label effects failed: $error"))
        when (this) {
            is ReplacementOutcome.Completed -> copy(effectsError = effectsError)
            is ReplacementOutcome.RejectedBeforeMutation -> copy(effectsError = effectsError)
            is ReplacementOutcome.RecoveredByRollback -> copy(effectsError = effectsError)
            is ReplacementOutcome.FailedAfterMutation -> copy(effectsError = effectsError)
            is ReplacementOutcome.Fatal -> copy(effectsError = effectsError)
        }
    },
)

/** Android restart-process ending: close, replace the file, then let the caller restart. */
internal suspend fun runRestartProcessSwap(
    closeDatabase: (AppDatabase) -> Unit,
    outgoing: RuntimeGeneration,
    mutation: MutationPlan,
    tracker: PonrTracker,
): ReplacementOutcome {
    val provider = mutation.provider
    tracker.crossed = true
    val closed = runCatching { closeDatabase(outgoing.database) }
    if (closed.isFailure) {
        return ReplacementOutcome.FailedAfterMutation(
            BackupError.Io(IOException("database close failed: ${closed.exceptionOrNull()}")),
        )
    }
    val replaced = provider.replaceLiveDatabaseFile(mutation.source)
    if (replaced is BackupResult.Failure) {
        // Preserve recovery assets after a post-close failure.
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    return when (val committed = commitMutation(mutation)) {
        CommitResult.Durable -> ReplacementOutcome.Completed(generation = null)

        // The mutation is not durable; keep Prepared state for conservative recovery.
        is CommitResult.NotDurable -> ReplacementOutcome.FailedAfterMutation(committed.error)
    }
}

/** Rollback consumes exactly its applied source; broader cleanup is explicit owner policy. */
internal sealed interface SourceConsumption {

    /** A restore: the swapped-in source is the staged snapshot; nothing is a rollback asset. */
    data object None : SourceConsumption

    /** The rollback applied the canonical `pre_restore_backup.db` undo slot — consume IT. */
    data object CanonicalSlot : SourceConsumption

    /** The rollback applied exactly [file] (a journal-named reservation) — consume only it. */
    data class ExactFile(val file: File) : SourceConsumption
}

internal class MutationPlan(
    val provider: DatabaseSnapshotProvider,
    val source: File,
    val consume: SourceConsumption,
    val effects: DatabaseReplacementEffects,
    val reservation: File?,
)

internal sealed interface OperationSourcePlan {
    class Proceed(val source: File, val consume: SourceConsumption) : OperationSourcePlan
    class Reject(val error: BackupError) : OperationSourcePlan
}

/** Resolves the explicit journal source or, only when absent, the canonical undo slot. */
internal fun selectRollbackOperationSource(
    explicitPath: String?,
    provider: DatabaseSnapshotProvider,
): OperationSourcePlan {
    if (explicitPath != null) {
        val explicit = File(explicitPath)
        if (!explicit.exists()) {
            return OperationSourcePlan.Reject(
                BackupError.CorruptedBackup(
                    reason = "journal-named rollback source is missing: $explicitPath",
                ),
            )
        }
        return OperationSourcePlan.Proceed(explicit, SourceConsumption.ExactFile(explicit))
    }
    val canonical = provider.getPreRestoreBackupFile()
        ?: return OperationSourcePlan.Reject(
            BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
        )
    return OperationSourcePlan.Proceed(canonical, SourceConsumption.CanonicalSlot)
}

internal sealed interface RecoverySourcePlan {
    class Apply(val source: File, val consume: SourceConsumption) : RecoverySourcePlan
    class Stop(val reason: String) : RecoverySourcePlan
}

/**
 * Resolves only the attempt-owned recovery source. Before a clean commit that is its reservation;
 * after one it is the canonical slot. An explicit missing source has no canonical fallback.
 */
internal fun selectRecoverySource(
    mutation: MutationPlan,
    cause: BackupError?,
    afterCleanCommit: Boolean,
): RecoverySourcePlan {
    val reservation = mutation.reservation
    if (reservation != null && !afterCleanCommit) {
        if (!reservation.exists()) {
            return RecoverySourcePlan.Stop(
                "replacement failed ($cause) and this attempt's reservation vanished",
            )
        }
        return RecoverySourcePlan.Apply(reservation, SourceConsumption.None)
    }
    if (reservation != null) {
        val canonical = mutation.provider.getPreRestoreBackupFile()
            ?: return RecoverySourcePlan.Stop(
                "post-commit recovery found no promoted undo slot ($cause)",
            )
        // Preflight owns canonical consumption after reclaiming the journal.
        return RecoverySourcePlan.Apply(canonical, SourceConsumption.None)
    }
    return when (mutation.consume) {
        is SourceConsumption.ExactFile -> RecoverySourcePlan.Stop(
            "the journal-named rollback source could not be applied ($cause) — " +
                "the canonical slot belongs to another attempt and is never substituted",
        )

        SourceConsumption.CanonicalSlot, SourceConsumption.None -> {
            val canonical = mutation.provider.getPreRestoreBackupFile()
                ?: return RecoverySourcePlan.Stop(
                    "replacement failed ($cause) and no pre-restore backup exists",
                )
            RecoverySourcePlan.Apply(canonical, SourceConsumption.CanonicalSlot)
        }
    }
}

/** `Durable` permits committed effects; `NotDurable` retains Prepared recovery state. */
internal sealed interface CommitResult {
    data object Durable : CommitResult
    data class NotDurable(val error: BackupError) : CommitResult
}

/**
 * Commits in crash-safe order: promote reservation, record `Committed`, remove reservation,
 * then consume the exact rollback source. A promotion or record failure remains `Prepared`.
 */
internal suspend fun commitMutation(mutation: MutationPlan): CommitResult {
    val provider = mutation.provider
    val promoted = mutation.reservation?.let { provider.promoteRollbackReservation(it) }
    if (promoted is BackupResult.Failure) {
        return CommitResult.NotDurable(
            BackupError.Io(
                IOException("rollback snapshot promotion failed: ${promoted.error}"),
            ),
        )
    }
    val recorded = runCatching { mutation.effects.onMutationCommitted() }
    if (recorded.isFailure) {
        return CommitResult.NotDurable(
            BackupError.Io(
                IOException("durable commit bookkeeping failed: ${recorded.exceptionOrNull()}"),
            ),
        )
    }
    // Remove the reservation only after `Committed` is durable.
    mutation.reservation?.let { reserved -> runCatching { reserved.delete() } }
    when (val consume = mutation.consume) {
        SourceConsumption.None -> Unit
        SourceConsumption.CanonicalSlot -> provider.deletePreRestoreBackup()
        is SourceConsumption.ExactFile -> runCatching { consume.file.delete() }
    }
    return CommitResult.Durable
}

/**
 * Executes preflight rollback inside the current transaction with the same journal and source
 * ownership rules as a top-level rollback.
 */
internal suspend fun runInlineRollback(
    transaction: ReplacementTransaction,
    effects: DatabaseReplacementEffects,
    sourcePath: String?,
    disposeCandidate: suspend (RuntimeGeneration) -> Boolean,
): ReplacementOutcome {
    val candidate = transaction.candidate
        ?: return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "inline rollback outside a candidate preflight"),
        )
    val provider = candidate.graph.databaseSnapshotProvider
    val plan = when (val selected = selectRollbackOperationSource(sourcePath, provider)) {
        is OperationSourcePlan.Reject ->
            return ReplacementOutcome.RejectedBeforeMutation(selected.error)

        is OperationSourcePlan.Proceed -> selected
    }
    // Claim the journal before teardown or file mutation.
    val prepared = runCatching { effects.onBeforeMutation("") }
    if (prepared.isFailure) {
        return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.Io(
                IOException("pre-mutation persistence failed: ${prepared.exceptionOrNull()}"),
            ),
        )
    }
    // Teardown order is store clear, lifetime join, database close, then file swap.
    transaction.candidateInvalidated = true
    val disposed = runCatching { disposeCandidate(candidate) }.getOrDefault(false)
    if (!disposed) {
        transaction.closeFailed = true
        return ReplacementOutcome.FailedAfterMutation(
            BackupError.Io(IOException("candidate teardown failed before the inline rollback")),
        )
    }
    transaction.candidateDisposed = true
    val replaced = provider.replaceLiveDatabaseFile(plan.source)
    if (replaced is BackupResult.Failure) {
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    // Mark before consuming so the outer result stays truthful.
    transaction.rolledBack = true
    transaction.rollbackCause = BackupError.Io(
        IOException("restore rolled back by the scenario-1 preflight"),
    )
    // Consume only after durable commit, and only the applied source.
    val committed = commitMutation(
        mutation = MutationPlan(
            provider = provider,
            source = plan.source,
            consume = plan.consume,
            effects = effects,
            reservation = null,
        ),
    )
    return when (committed) {
        CommitResult.Durable -> ReplacementOutcome.Completed(generation = null)

        // Preserve Prepared state and source; a non-durable swap is never Committed.
        is CommitResult.NotDurable -> ReplacementOutcome.FailedAfterMutation(committed.error)
    }
}

/** UI admission gate that atomically observes zero attachments and retires a generation. */
internal class UiAdmissionGate(private val logger: Logger) {

    /** Token identity makes late release ABA-safe. */
    class Token internal constructor(
        internal val generationId: Int,
        internal val serial: Long,
    ) : AppUiAdmissionToken

    private data class State(
        val live: Map<Int, Set<Long>> = emptyMap(),
        val retired: Set<Int> = emptySet(),
    )

    private val state = MutableStateFlow(State())
    private val nextSerial = AtomicLong(0)

    /** Requests composition-time admission; a retired generation receives no token. */
    fun admit(generationId: Int): Token? {
        val serial = nextSerial.incrementAndGet()
        // Read the verdict from the winning CAS state, not a retried update lambda.
        val after = state.updateAndGet { s ->
            if (generationId in s.retired) {
                s
            } else {
                s.copy(live = s.live + (generationId to (s.live[generationId].orEmpty() + serial)))
            }
        }
        val granted = serial in after.live[generationId].orEmpty()
        if (!granted) {
            logger.w {
                "ui admission REFUSED for retired generation $generationId — the region renders nothing"
            }
            return null
        }
        return Token(generationId, serial)
    }

    /** Releases exactly this token. Idempotent, and a no-op for a token that never counted. */
    fun release(token: Token) {
        state.update { s ->
            val serials = s.live[token.generationId] ?: return@update s
            if (token.serial !in serials) return@update s
            val remaining = serials - token.serial
            s.copy(
                live = if (remaining.isEmpty()) {
                    s.live - token.generationId
                } else {
                    s.live + (token.generationId to remaining)
                },
            )
        }
    }

    /** Atomically waits for zero admissions and retires the id; timeout leaves it admittable. */
    suspend fun awaitRetired(id: Int, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            var retired = false
            while (!retired) {
                state.first { s -> s.live[id].isNullOrEmpty() || id in s.retired }
                val after = state.updateAndGet { s ->
                    if (s.live[id].isNullOrEmpty() && id !in s.retired) {
                        s.copy(live = s.live - id, retired = s.retired + id)
                    } else {
                        s
                    }
                }
                retired = id in after.retired
            }
        } != null

    /** Reopens admission for an ABORTED transition's outgoing id. */
    fun reopen(id: Int) {
        state.update { s -> s.copy(retired = s.retired - id) }
    }

    fun admittedCount(id: Int): Int = state.value.live[id].orEmpty().size
}

/** DB-bound work admission; a lease binds dependencies atomically at first operation. */
internal class WorkerAdmissionGate {

    private val lock = Any()
    private val closed = MutableStateFlow(false)
    private val activeLeases = MutableStateFlow(0)

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

internal fun ReplacementOutcome.effectsError(): BackupError? = when (this) {
    is ReplacementOutcome.Completed -> effectsError
    is ReplacementOutcome.RejectedBeforeMutation -> effectsError
    is ReplacementOutcome.RecoveredByRollback -> effectsError
    is ReplacementOutcome.FailedAfterMutation -> effectsError
    is ReplacementOutcome.Fatal -> effectsError
}

internal fun ReplacementOutcome.toSeamResult(): DatabaseReplacementResult = when (this) {
    is ReplacementOutcome.Completed -> DatabaseReplacementResult.Committed(effectsError)

    is ReplacementOutcome.RejectedBeforeMutation ->
        DatabaseReplacementResult.RejectedBeforeMutation(error, effectsError)

    is ReplacementOutcome.RecoveredByRollback ->
        DatabaseReplacementResult.RecoveredByRollback(error, effectsError)

    is ReplacementOutcome.FailedAfterMutation ->
        DatabaseReplacementResult.FailedAfterMutation(error, effectsError)

    is ReplacementOutcome.Fatal -> DatabaseReplacementResult.FatalNoGeneration(effectsError)
}

/**
 * Releases a failed graph candidate: join its lifetime before closing an owned database. Failed
 * cleanup is terminal because candidate work may still hold the live file.
 */
internal fun releasePartialGeneration(
    lifetime: AppScopeLifetime,
    database: AppDatabase,
    ownsDatabase: Boolean,
    cause: Throwable,
    closeDatabase: (AppDatabase) -> Unit,
    drainTimeoutMillis: Long,
): Throwable {
    val joined: Unit? = runCatching {
        runBlocking {
            withTimeoutOrNull(drainTimeoutMillis) { lifetime.cancelAndJoin() }
        }
    }.getOrNull()
    if (!ownsDatabase) {
        return if (joined == null) PartialCandidateUnwindException(cause) else cause
    }
    if (joined == null) return OrphanCloseException(cause)
    return runCatching { closeDatabase(database) }
        .fold(onSuccess = { cause }, onFailure = { OrphanCloseException(it) })
}

/** `Completed` means the requested operation committed; restored pre-operation data is recovery. */
internal fun completedOrRecovered(
    transaction: ReplacementTransaction,
    generation: RuntimeGeneration,
    requestedRollback: Boolean,
): ReplacementOutcome = if (!requestedRollback && transaction.rolledBack) {
    ReplacementOutcome.RecoveredByRollback(
        error = transaction.rollbackCause
            ?: BackupError.Io(IOException("restore rolled back")),
        generation = generation,
    )
} else {
    ReplacementOutcome.Completed(generation)
}
