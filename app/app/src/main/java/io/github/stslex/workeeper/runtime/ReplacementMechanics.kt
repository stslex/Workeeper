// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeDeps
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkLease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Crossed before the first irreversible action. */
internal class PonrTracker {
    @Volatile
    var crossed = false
}

/** The durable claim is the first point of no return, after reversible quiescence. */
internal suspend fun claimPreparedMutation(
    mutation: MutationPlan,
    tracker: PonrTracker,
): Throwable? {
    val restoreSourceRef = (mutation.source as? MutationSource.Restore)?.ref
    tracker.crossed = true
    return runCatching {
        mutation.effects.onBeforeMutation(mutation.undoRef, restoreSourceRef)
    }.exceptionOrNull()
}

internal suspend fun claimMutationAfterQuiesce(
    mutation: MutationPlan,
    tracker: PonrTracker,
    logger: Logger,
    publishFatal: (String) -> ReplacementOutcome,
): ReplacementOutcome? {
    val failure = claimPreparedMutation(mutation, tracker)
    if (failure == null) return null
    logger.e(failure, "pre-mutation journal claim failed after quiescence")
    return publishFatal("pre-mutation journal ownership is unprovable: $failure")
}

internal suspend fun prepareCompensationOwner(
    mutation: MutationPlan,
    rollbackRef: UndoRef,
    requestedRollback: Boolean,
): Result<RestoreOwnerId?> {
    if (requestedRollback) return Result.success(null)
    val owner = RestoreOwnerId(UUID.randomUUID().toString())
    return runCatching {
        mutation.effects.onBeforeCompensation(owner, rollbackRef)
        owner
    }
}

internal suspend fun commitRecoveryRollback(
    mutation: MutationPlan,
    compensationOwner: RestoreOwnerId?,
): CommitResult = if (compensationOwner == null) {
    commitMutation(mutation)
} else {
    runCatching { mutation.effects.onCompensationCommitted(compensationOwner) }.fold(
        onSuccess = { CommitResult.Durable },
        onFailure = { error ->
            CommitResult.NotDurable(
                BackupError.Io(
                    IOException("compensation commit bookkeeping failed: $error"),
                ),
            )
        },
    )
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

    /** A durable success transition forbids any later compensation of this restore. */
    @Volatile
    var restoreFinalized = false

    companion object Key : CoroutineContext.Key<ReplacementTransaction>
}

/** One serialized replacement submission and its exact owner's result. */
internal class InFlightReplacement(
    val operation: ReplacementOperation,
    val outcome: CompletableDeferred<ReplacementOutcome>,
)

/** Serializes staged submission ownership against persisted-state garbage collection. */
internal class ReplacementSubmissionGuard {

    private val mutex = Mutex()
    private var pending = 0

    suspend fun <T> begin(block: suspend () -> T): T = withContext(NonCancellable) {
        mutex.withLock {
            pending += 1
            runCatching { block() }.getOrElse { error ->
                pending -= 1
                throw error
            }
        }
    }

    suspend fun finish(sweep: suspend () -> Unit) {
        mutex.withLock {
            check(pending > 0) { "replacement submission accounting underflow" }
            pending -= 1
            if (pending == 0) sweep()
        }
    }
}

/** Caller cache ownership moves into the recovery root before cancellable submission. */
internal suspend fun stageRestoreSourceForSubmission(
    operation: ReplacementOperation,
    provider: () -> DatabaseSnapshotProvider,
): Throwable? {
    if (operation !is ReplacementOperation.RestoreFromSnapshot) return null
    return runCatching {
        val staged = provider().stageRestoreSource(
            source = File(operation.sourcePath),
            ref = operation.sourceRef,
        )
        when (staged) {
            is BackupResult.Success -> null
            is BackupResult.Failure -> IOException(
                "restore source ownership transfer failed: ${staged.error}",
            )
        }
    }.getOrElse { error -> error }
}

/** A UI/store coroutine may disappear during quiescence; the runtime host owns restart. */
internal fun restartAfterPonr(
    outcome: ReplacementOutcome,
    policy: RuntimeTransitionPolicy,
    logger: Logger,
): ReplacementOutcome = runCatching { policy.restartProcess() }.fold(
    onSuccess = { outcome },
    onFailure = { error ->
        logger.e(error, "process restart failed after database mutation")
        val restartError = BackupError.Io(
            IOException("process restart failed after database mutation: $error"),
        )
        ReplacementOutcome.Fatal(effectsError = restartError)
    },
)

/** GC derives ownership only from epoch-reconciled persisted state. */
internal suspend fun sweepPersistedRecoveryAssets(
    generation: RuntimeGeneration?,
    logger: Logger,
) {
    val graph = generation?.graph ?: return
    val state = runCatching { graph.restoreStateRepository.readProtocol() }.getOrNull()
    if (state is RestoreProtocolRead.Current) {
        runCatching { graph.databaseSnapshotProvider.sweepRecoveryFiles(state.state) }
            .onFailure { error -> logger.w { "recovery garbage sweep deferred: $error" } }
    }
}

/** True inside either transaction machine, where a fresh submission would deadlock the mutex. */
internal fun CoroutineContext.isInsideReplacementTransaction(): Boolean =
    this[ReplacementTransaction] != null || this[GraphOnlyTransition.Key] != null

/** Typed refusal for a restore submitted from inside a transaction — never a deadlock. */
internal suspend fun rejectNestedRestore(
    effects: DatabaseReplacementEffects,
    logger: Logger,
): DatabaseReplacementResult = runTerminalEffects(
    effects = effects,
    outcome = ReplacementOutcome.RejectedBeforeMutation(
        BackupError.Io(IOException("restore is unavailable inside a replacement transaction")),
    ),
    ponrCrossed = false,
    logger = logger,
).toSeamResult()

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

    /** Verified live DB cannot publish because mandatory restore-state finalization is not durable. */
    data object ProtocolFatal : AttemptResult

    /** Restore state is durably final; retry candidate arming without rolling the database back. */
    data object RestoreFinalizedRetryable : AttemptResult
}

internal class OrphanCloseException(cause: Throwable) :
    IllegalStateException("orphaned candidate database failed to close", cause)

/** A shared-database candidate could not be fully unwound and must not be republished beside N. */
internal class PartialCandidateUnwindException(cause: Throwable) :
    IllegalStateException("partially built candidate could not be unwound", cause)

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

/** Android ending: close and replace; the runtime host invokes restart after terminal effects. */
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
    val replaced = mutation.replaceLiveDatabase()
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

internal sealed interface MutationSource {
    data class Restore(val ref: RestoreSourceRef) : MutationSource
    data class Undo(val ref: UndoRef) : MutationSource
}

internal class MutationPlan(
    val provider: DatabaseSnapshotProvider,
    val source: MutationSource,
    val effects: DatabaseReplacementEffects,
    /** Restore's immutable pre-image; rollback's exact applied source. */
    val undoRef: UndoRef,
)

internal enum class RestoreFinalizationStatus {
    Unresolved,
    Finalized,
    Unprovable,
}

/** Owner-checks durable protocol truth after candidate preflight escaped. */
internal suspend fun MutationPlan.restoreFinalizationStatus(
    candidate: RuntimeGeneration,
): RestoreFinalizationStatus {
    val restore = source as? MutationSource.Restore ?: return RestoreFinalizationStatus.Unresolved
    val owner = restore.ref.owner
    if (effects.attemptId != owner || undoRef.owner != owner) {
        return RestoreFinalizationStatus.Unprovable
    }
    val protocol = runCatching {
        candidate.graph.restoreStateRepository.readProtocol()
    }.getOrElse {
        return RestoreFinalizationStatus.Unprovable
    }
    val state = (protocol as? RestoreProtocolRead.Current)?.state
        ?: return RestoreFinalizationStatus.Unprovable
    val terminal = state.terminalOutbox as? RestoreTerminal.RestoreSucceeded
    val active = state.activeUndo
    val finalizationMatches = state.attempt == null &&
        terminal?.owner == owner &&
        terminal.previousVersionAvailable == (active != null) &&
        (active == null || active.ref == undoRef)
    if (finalizationMatches) return RestoreFinalizationStatus.Finalized

    val attempt = state.attempt as? RestoreAttempt.Restore
    return if (attempt?.id == owner && attempt.phase == RestoreAttempt.Phase.Committed) {
        RestoreFinalizationStatus.Unresolved
    } else {
        RestoreFinalizationStatus.Unprovable
    }
}

/** Classifies a cleanly released candidate without allowing post-success compensation. */
internal suspend fun classifyReleasedCandidateFailure(
    transaction: ReplacementTransaction,
    mutation: MutationPlan,
    candidate: RuntimeGeneration,
    preflightFailed: Boolean,
): AttemptResult {
    if (!preflightFailed || transaction.rolledBack || transaction.candidateInvalidated) {
        return AttemptResult.Retryable
    }
    val status = if (transaction.restoreFinalized) {
        RestoreFinalizationStatus.Finalized
    } else {
        mutation.restoreFinalizationStatus(candidate)
    }
    return when (status) {
        RestoreFinalizationStatus.Unresolved -> AttemptResult.Retryable
        RestoreFinalizationStatus.Finalized -> {
            transaction.restoreFinalized = true
            AttemptResult.RestoreFinalizedRetryable
        }

        RestoreFinalizationStatus.Unprovable -> AttemptResult.ProtocolFatal
    }
}

/** One candidate retry after success finalization; no branch may compensate the restored DB. */
internal suspend fun retryFinalizedRestoreGeneration(
    transaction: ReplacementTransaction,
    mutation: MutationPlan,
    requestedRollback: Boolean,
    runAttempt: suspend (ReplacementTransaction, MutationPlan) -> AttemptResult,
    publishFatal: (String) -> ReplacementOutcome,
): ReplacementOutcome = when (val retry = runAttempt(transaction, mutation)) {
    is AttemptResult.Published -> completedOrRecovered(
        transaction,
        retry.generation,
        requestedRollback,
    )

    AttemptResult.LadderFatal ->
        publishFatal("finalized restore candidate resources could not be released")

    AttemptResult.ProtocolFatal ->
        publishFatal("finalized restore protocol became unprovable during candidate retry")

    AttemptResult.RestoreFinalizedRetryable,
    AttemptResult.Retryable,
    -> publishFatal("finalized restore candidate arming failed twice — rollback forbidden")
}

/** Performs the single candidate retry licensed after a durable rollback. */
internal suspend fun retryAfterRollbackGeneration(
    transaction: ReplacementTransaction,
    mutation: MutationPlan,
    requestedRollback: Boolean,
    protocolFailure: String,
    generationFailure: String,
    runAttempt: suspend (ReplacementTransaction, MutationPlan) -> AttemptResult,
    publishFatal: (String) -> ReplacementOutcome,
): ReplacementOutcome = when (val retry = runAttempt(transaction, mutation)) {
    is AttemptResult.Published -> completedOrRecovered(
        transaction,
        retry.generation,
        requestedRollback,
    )

    AttemptResult.ProtocolFatal -> publishFatal(protocolFailure)
    else -> publishFatal(generationFailure)
}

/** Reopens an intact outgoing generation after reversible quiescence rejects. */
internal fun unwindQuiesce(
    outgoing: RuntimeGeneration,
    reason: String,
    logger: Logger,
    reopen: (Int) -> Unit,
    publishServing: (RuntimeGeneration) -> Unit,
): ReplacementOutcome {
    logger.w {
        "replacement aborted during quiesce: $reason — generation ${outgoing.id} keeps serving"
    }
    reopen(outgoing.id)
    publishServing(outgoing)
    return ReplacementOutcome.RejectedBeforeMutation(BackupError.Io(IOException(reason)))
}

internal suspend fun MutationPlan.replaceLiveDatabase(): BackupResult<Unit> = when (val value = source) {
    is MutationSource.Restore -> provider.replaceLiveDatabaseFromRestore(value.ref)
    is MutationSource.Undo -> provider.replaceLiveDatabaseFromUndo(value.ref)
}

internal sealed interface OperationSourcePlan {
    class Proceed(val source: MutationSource, val undoRef: UndoRef) : OperationSourcePlan
    class Reject(val error: BackupError) : OperationSourcePlan
}

/**
 * Resolves and validates the source a submitted operation applies, while the live database is
 * still open. A restore validates its staged snapshot; a rollback validates only its exact
 * journal-owned immutable undo.
 */
internal suspend fun selectOperationSource(
    operation: ReplacementOperation,
    provider: DatabaseSnapshotProvider,
): OperationSourcePlan = when (operation) {
    is ReplacementOperation.RestoreFromSnapshot ->
        selectRestoreOperationSource(operation, provider)

    is ReplacementOperation.RollbackFromUndo -> selectRollbackOperationSource(
        sourceRef = operation.sourceRef,
        provider = provider,
    )
}

private suspend fun selectRestoreOperationSource(
    operation: ReplacementOperation.RestoreFromSnapshot,
    provider: DatabaseSnapshotProvider,
): OperationSourcePlan {
    provider.getRestoreSourceFile(operation.sourceRef)
        ?: return OperationSourcePlan.Reject(
            BackupError.Io(IOException("staged restore source is missing")),
        )
    val validation = provider.validateRestoreSource(operation.sourceRef)
    if (validation is BackupResult.Failure) {
        return OperationSourcePlan.Reject(validation.error)
    }
    val capacity = provider.checkRestoreCapacity(operation.sourceRef)
    if (capacity is BackupResult.Failure) {
        return OperationSourcePlan.Reject(capacity.error)
    }
    val undoRef = UndoRef(operation.owner)
    return when (val undo = provider.createUndo(undoRef)) {
        is BackupResult.Failure -> OperationSourcePlan.Reject(undo.error)
        is BackupResult.Success -> OperationSourcePlan.Proceed(
            source = MutationSource.Restore(operation.sourceRef),
            undoRef = undoRef,
        )
    }
}

internal suspend fun selectRollbackOperationSource(
    sourceRef: UndoRef,
    provider: DatabaseSnapshotProvider,
): OperationSourcePlan {
    provider.getUndoFile(sourceRef)
        ?: return OperationSourcePlan.Reject(
            BackupError.CorruptedBackup(reason = "owned undo source is missing: $sourceRef"),
        )
    return when (val validation = provider.validateUndo(sourceRef)) {
        is BackupResult.Failure -> OperationSourcePlan.Reject(validation.error)
        is BackupResult.Success -> when (val capacity = provider.checkRollbackCapacity(sourceRef)) {
            is BackupResult.Failure -> OperationSourcePlan.Reject(capacity.error)
            is BackupResult.Success -> OperationSourcePlan.Proceed(
                source = MutationSource.Undo(sourceRef),
                undoRef = sourceRef,
            )
        }
    }
}

/** `Durable` permits committed effects; `NotDurable` retains Prepared recovery state. */
internal sealed interface CommitResult {
    data object Durable : CommitResult
    data class NotDurable(val error: BackupError) : CommitResult
}

/**
 * Records the already-installed immutable source as committed. Pointer transition and source
 * deletion belong to verified preflight finalization, not this mutation step.
 */
internal suspend fun commitMutation(mutation: MutationPlan): CommitResult {
    val recorded = runCatching { mutation.effects.onMutationCommitted() }
    if (recorded.isFailure) {
        return CommitResult.NotDurable(
            BackupError.Io(
                IOException("durable commit bookkeeping failed: ${recorded.exceptionOrNull()}"),
            ),
        )
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
    sourceRef: UndoRef,
    disposeCandidate: suspend (RuntimeGeneration) -> Boolean,
): ReplacementOutcome {
    val candidate = transaction.candidate
        ?: return ReplacementOutcome.RejectedBeforeMutation(
            BackupError.CorruptedBackup(reason = "inline rollback outside a candidate preflight"),
        )
    val provider = candidate.graph.databaseSnapshotProvider
    val plan = when (val selected = selectRollbackOperationSource(sourceRef, provider)) {
        is OperationSourcePlan.Reject ->
            return ReplacementOutcome.RejectedBeforeMutation(selected.error)

        is OperationSourcePlan.Proceed -> selected
    }
    // Claim the journal before teardown or file mutation.
    val prepared = runCatching { effects.onBeforeMutation(sourceRef, null) }
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
    val mutation = MutationPlan(provider, plan.source, effects, plan.undoRef)
    val replaced = mutation.replaceLiveDatabase()
    if (replaced is BackupResult.Failure) {
        return ReplacementOutcome.FailedAfterMutation(replaced.error)
    }
    // Mark before consuming so the outer result stays truthful.
    transaction.rolledBack = true
    transaction.rollbackCause = BackupError.Io(
        IOException("restore rolled back by the scenario-1 preflight"),
    )
    val committed = commitMutation(mutation)
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

    /**
     * Terminal refusal, distinct from [close]'s reversible transition barrier: this process
     * declared its database provenance unprovable and nothing can prove it again. See spec §8.5b.
     */
    @Volatile
    private var sealed = false

    /** Null once [seal]ed: the caller must touch no database and record no bookkeeping. */
    suspend fun awaitLease(deps: () -> BackupWorkerDeps): BackupWorkLease? {
        while (true) {
            synchronized(lock) {
                if (sealed) return null
                if (!closed.value) {
                    val bound = deps()
                    activeLeases.update { it + 1 }
                    return LeaseImpl(bound)
                }
            }
            closed.first { isClosed -> !isClosed }
        }
    }

    /** Wear callbacks share the exact closed/drained counter with backup work. */
    suspend fun awaitWearLease(deps: () -> WearBridgeDeps): WearBridgeWorkLease? {
        while (true) {
            synchronized(lock) {
                if (sealed) return null
                if (!closed.value) {
                    val bound = deps()
                    activeLeases.update { it + 1 }
                    return WearLeaseImpl(bound)
                }
            }
            closed.first { isClosed -> !isClosed }
        }
    }

    /**
     * Driven only from the cold-start recovery verdict, which runs inside `Application.onCreate`
     * before any component callback — so no acquirer can be parked when it fires.
     */
    fun seal() = synchronized(lock) { sealed = true }

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

    private inner class WearLeaseImpl(override val deps: WearBridgeDeps) : WearBridgeWorkLease {
        private val released = AtomicBoolean(false)
        override fun release() {
            if (released.compareAndSet(false, true)) {
                activeLeases.update { count -> (count - 1).coerceAtLeast(0) }
            }
        }
    }
}

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
