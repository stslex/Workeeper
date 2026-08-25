// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModelStore
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.closeAppDatabase
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Process-owned runtime for generation publication and database replacement.
 *
 * Submitted work owns its effects after submission. The runtime serializes transitions, runs
 * `onBeforeMutation` before mutation, and delivers exactly one terminal effect. See the Phase-5
 * startup-processor spec for the complete protocol.
 */
internal class AppRuntime(
    private val applicationContext: Context,
    private val dbFactory: (Context) -> AppDatabase,
    private val imageStorageFactory: (Context) -> ImageStorage,
    private val graphFactory: GraphFactory,
    private val preflight: suspend (RuntimeGeneration) -> StartupOutcome,
    // Injectable so app:app remains Room-free and JVM tests can record close ordering.
    private val closeDatabase: (AppDatabase) -> Unit = ::closeAppDatabase,
    private val replacementPolicy: ReplacementPolicy = ReplacementPolicy.RestartProcess,
    private val policy: RuntimeTransitionPolicy = RuntimeTransitionPolicy(),
) : AppReinitializationHost,
    DatabaseReplacement {

    private val logger = Log.tag(APP_RUNTIME_TAG)

    private val hostScope = CoroutineScope(
        SupervisorJob() +
            policy.hostDispatcher +
            CoroutineExceptionHandler { _, error ->
                Log.tag(APP_RUNTIME_TAG).e(error, "runtime host coroutine failed unexpectedly")
            },
    )

    /** Serializes transitions; generation 1 is built outside this mutex. */
    private val transitionMutex = Mutex()

    /** Prevents a sweep from crossing a staged caller queued before its journal claim. */
    private val submissionGuard = ReplacementSubmissionGuard()

    private val buildLock = Any()

    private val uiGate = UiAdmissionGate(logger)

    private val workerGate = WorkerAdmissionGate()

    private val quiescer = GenerationQuiescer(
        uiGate = uiGate,
        workerGate = workerGate,
        policy = policy,
        closeDatabase = closeDatabase,
        logger = logger,
    )

    val imageStorage: ImageStorage by lazy { imageStorageFactory(applicationContext) }

    private class Published(val phase: RuntimePhase, val ui: AppUiPhase)

    private val publishedFlow = MutableStateFlow(
        Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning),
    )

    val phases: StateFlow<RuntimePhase> = DerivedStateFlow(publishedFlow) { it.phase }

    /** The app:common face — what `BaseApplication` exposes through `AppUiGenerationsHolder`. */
    val uiPhases: StateFlow<AppUiPhase> = DerivedStateFlow(publishedFlow) { it.ui }

    @Volatile
    private var currentOrNull: RuntimeGeneration? = null

    @Volatile
    private var isFatal = false

    @Volatile
    private var restartTerminal = false

    private val isTerminal: Boolean get() = isFatal || restartTerminal

    private fun publishServing(generation: RuntimeGeneration) {
        check(!isTerminal) { "a terminal runtime must never publish Serving again" }
        currentOrNull = generation
        publishedFlow.value = Published(
            phase = RuntimePhase.Serving(generation),
            ui = AppUiPhase.Generation(id = generation.id, viewModelStoreOwner = generation),
        )
    }

    private fun publishTransitioning() {
        check(!isTerminal) { "a terminal runtime must never publish Transitioning again" }
        publishedFlow.value = Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning)
    }

    internal fun publishFatal(reason: String): ReplacementOutcome {
        logger.e(IllegalStateException(reason), "replacement FATAL")
        isFatal = true
        // The host chooses how an in-process Fatal reaches recovery UI.
        publishedFlow.value = Published(RuntimePhase.Fatal, AppUiPhase.Transitioning)
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return ReplacementOutcome.Fatal()
    }

    // Generation 1 mints under buildLock; later generations mint under the mutex.
    private val nextGenerationId = AtomicInteger(APP_RUNTIME_FIRST_GENERATION_ID)

    /**
     * The one published generation; builds and publishes generation 1 on first read. Reads during
     * a transition answer with the outgoing generation; after [RuntimePhase.Fatal] this THROWS.
     */
    val currentGeneration: RuntimeGeneration
        get() {
            check(!isTerminal) { "runtime is terminal — no generation is serving; restart required" }
            return currentOrNull ?: synchronized(buildLock) {
                currentOrNull ?: buildGeneration(
                    database = dbFactory(applicationContext),
                    dbGeneration = APP_RUNTIME_FIRST_GENERATION_ID,
                    ownsDatabase = true,
                ).also { first ->
                    publishServing(first)
                }
            }
        }

    /**
     * Admission for a generation's UI region, requested during composition. Null means the
     * generation is retired: the region must render nothing and touch no dependency.
     */
    fun admitUiGeneration(id: Int): AppUiAdmissionToken? = uiGate.admit(id)

    /** Releases exactly one admitted region; idempotent and ABA-safe. */
    fun releaseUiGeneration(token: AppUiAdmissionToken) {
        (token as? UiAdmissionGate.Token)?.let(uiGate::release)
    }

    fun uiAttachmentCount(id: Int): Int = uiGate.admittedCount(id)

    /**
     * The serving generation's store, or null before generation 1 exists. `currentOrNull`, never
     * `currentGeneration`: reading this must never BUILD a generation, and a Fatal runtime's
     * store must still be releasable — it will never publish again.
     */
    val servingViewModelStore: ViewModelStore? get() = currentOrNull?.viewModelStore

    /** Terminally refuses DB-bound work admission for the rest of this process. */
    fun sealWorkerAdmission() = workerGate.seal()

    /**
     * First-operation worker admission: suspends while a transition holds admission closed, then
     * binds the lease to the current generation's deps. Throws when the runtime is Fatal, and
     * answers `null` once admission is sealed — the caller must touch no database.
     */
    suspend fun awaitBackupWorkLease(): BackupWorkLease? = workerGate.awaitLease {
        check(!isTerminal) { "runtime is terminal — no generation may admit new work" }
        currentGeneration.graph
    }

    override fun requestReinitialize() {
        val expected = currentOrNull
        hostScope.launch { reinitialize(expected) }
    }

    /** Submits a graph-only replacement; caller cancellation abandons only its await. */
    suspend fun reinitialize(expected: RuntimeGeneration? = null): ReinitializeOutcome {
        val result = CompletableDeferred<ReinitializeOutcome>()
        hostScope.launch {
            val tracker = PonrTracker()
            val outcome = runCatching {
                // Ensure generation 1 exists before taking the transition mutex (cold-start rule).
                currentGeneration
                transitionMutex.withLock {
                    val outgoing = currentOrNull
                    when {
                        // Fatal is terminal: a queued reinitialize performs NOTHING.
                        isTerminal || outgoing == null -> ReinitializeOutcome.Fatal

                        expected != null && outgoing.id != expected.id ->
                            ReinitializeOutcome.AlreadyReplaced(outgoing)

                        else -> runCatching { runGraphOnlyTransition(outgoing, tracker) }
                            .getOrElse { error -> resolveGraphOnlyEscape(error, outgoing, tracker) }
                    }
                }
            }.getOrElse { error ->
                logger.e(error, "reinitialize failed outside the transition body")
                when {
                    isTerminal -> ReinitializeOutcome.Fatal

                    // A failed release leaves an unknown-state handle or live job: Fatal.
                    error is OrphanCloseException || error is PartialCandidateUnwindException -> {
                        publishFatal("generation resources could not be released: $error")
                        ReinitializeOutcome.Fatal
                    }

                    else -> ReinitializeOutcome.Aborted("reinitialize failed: $error", currentOrNull)
                }
            }
            result.complete(outcome)
        }
        return result.await()
    }

    /** Escape resolution for the graph-only machine — mirrors [resolveTransactionEscape]. */
    private fun resolveGraphOnlyEscape(
        error: Throwable,
        outgoing: RuntimeGeneration,
        tracker: PonrTracker,
    ): ReinitializeOutcome {
        logger.e(error, "graph-only transition threw")
        if (isTerminal) return ReinitializeOutcome.Fatal
        return if (tracker.crossed) {
            // Teardown of N began — a partially-disposed generation is NEVER resurrected.
            publishFatal("graph-only transition escaped after teardown began: $error")
            ReinitializeOutcome.Fatal
        } else {
            abortToServing(outgoing, reason = "transition threw: $error")
        }
    }

    private suspend fun runGraphOnlyTransition(
        outgoing: RuntimeGeneration,
        tracker: PonrTracker,
    ): ReinitializeOutcome {
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason -> return abortToServing(outgoing, reason) }

        // Build and preflight against the shared database while the outgoing generation is intact.
        val candidate = runCatching {
            buildGeneration(
                database = outgoing.database,
                dbGeneration = outgoing.dbGeneration,
                ownsDatabase = false,
            )
        }.getOrElse { error ->
            // An unjoinable partial candidate may still use the shared database.
            if (error is PartialCandidateUnwindException) {
                publishFatal("graph-only candidate could not be unwound: ${error.cause}")
                return ReinitializeOutcome.Fatal
            }
            return abortToServing(outgoing, reason = "candidate construction failed: $error")
        }

        // Reject nested rollback here: Mutex is non-reentrant and no mutation has started.
        val preflightOutcome = runCatching {
            withContext(GraphOnlyTransition) { preflight(candidate) }
        }
        if (preflightOutcome.getOrNull() != StartupOutcome.Proceed) {
            // Republish only after candidate jobs have stopped using the shared database.
            if (!quiescer.tearDownCandidate(candidate, closeCandidateDatabase = false)) {
                publishFatal(
                    "candidate teardown failed after a rejected preflight — " +
                        "its jobs may still hold the shared database",
                )
                return ReinitializeOutcome.Fatal
            }
            return abortToServing(
                outgoing,
                reason = "candidate preflight failed: " +
                    (
                        preflightOutcome.exceptionOrNull()?.toString()
                            ?: "${preflightOutcome.getOrNull()}"
                        ),
            )
        }

        // PONR: fully tear down N before exposing N+1; failures are terminal.
        tracker.crossed = true
        quiescer.tearDown(outgoing)?.let { failure ->
            val candidateReleased =
                quiescer.tearDownCandidate(candidate, closeCandidateDatabase = false)
            val aggregated = if (candidateReleased) {
                failure
            } else {
                "$failure; the candidate's own teardown also failed"
            }
            publishFatal("graph-only outgoing teardown failed after PONR: $aggregated")
            return ReinitializeOutcome.Fatal
        }
        // Advance the epoch before publication so stale snackbar callbacks cannot reach N+1.
        policy.advanceSnackbarGeneration()
        publishServing(candidate)
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return ReinitializeOutcome.Published(candidate)
    }

    private fun abortToServing(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReinitializeOutcome {
        logger.w { "reinitialize aborted: $reason — generation ${outgoing.id} keeps serving" }
        quiescer.reopen(outgoing.id)
        publishServing(outgoing)
        return ReinitializeOutcome.Aborted(reason = reason, serving = outgoing)
    }

    override suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult {
        // Rollback has two inline escapes; restore has none by design, so a reentrant
        // submission would block forever on the non-reentrant transition mutex.
        if (coroutineContext.isInsideReplacementTransaction()) {
            return rejectNestedRestore(effects, logger)
        }
        return replace(
            ReplacementOperation.RestoreFromSnapshot(source, effects.attemptId),
            effects,
        ).toSeamResult()
    }

    override suspend fun rollbackFromUndo(
        sourceRef: UndoRef,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult {
        // A matching marker makes preflight rollback part of this transaction, never nested.
        coroutineContext[ReplacementTransaction]?.let { transaction ->
            val outcome = runInlineRollback(
                transaction = transaction,
                effects = effects,
                sourceRef = sourceRef,
                disposeCandidate = { candidate ->
                    quiescer.tearDownCandidate(candidate, closeCandidateDatabase = true)
                },
            )
            // The inline branch runs inside the outer transaction (mutex held, PONR crossed).
            return runTerminalEffects(effects, outcome, ponrCrossed = true, logger = logger)
                .toSeamResult()
        }
        // Graph-only preflight cannot roll back through the non-reentrant transition mutex.
        if (coroutineContext[GraphOnlyTransition.Key] != null) {
            val rejected = ReplacementOutcome.RejectedBeforeMutation(
                BackupError.Io(IOException("rollback unavailable inside a graph-only transition")),
            )
            return runTerminalEffects(effects, rejected, ponrCrossed = false, logger = logger)
                .toSeamResult()
        }
        return replace(
            ReplacementOperation.RollbackFromUndo(sourceRef, effects.attemptId),
            effects,
        ).toSeamResult()
    }

    /** Every caller owns one serialized transaction; no callbacks are coalesced across owners. */
    suspend fun replace(
        operation: ReplacementOperation,
        effects: DatabaseReplacementEffects,
    ): ReplacementOutcome {
        val inFlight = submissionGuard.begin {
            val stagingFailure = stageRestoreSourceForSubmission(operation) {
                currentGeneration.graph.databaseSnapshotProvider
            }
            // Submission happens before caller cancellation can delete its cache file.
            submitReplacement(operation, effects, stagingFailure)
        }
        return inFlight.outcome.await()
    }

    private fun submitReplacement(
        operation: ReplacementOperation,
        effects: DatabaseReplacementEffects,
        stagingFailure: Throwable?,
    ): InFlightReplacement {
        val inFlight = InFlightReplacement(operation, CompletableDeferred())
        hostScope.launch {
            val tracker = PonrTracker()
            val finalOutcome = stagingFailure?.let { stagingError ->
                transitionMutex.withLock {
                    val escaped = resolveTransactionEscape(stagingError, tracker)
                    runTerminalEffectsAndSealRestart(effects, escaped, tracker)
                }
            } ?: runCatching {
                currentGeneration
                transitionMutex.withLock {
                    val outcome = runCatching {
                        executeReplacement(
                            operation = operation,
                            effects = effects,
                            tracker = tracker,
                        )
                    }.getOrElse { error ->
                        if (error is CancellationException) {
                            // hostScope is never cancelled; an internal one is a bug.
                            logger.e(error, "replacement transaction cancelled internally")
                        }
                        resolveTransactionEscape(error, tracker)
                    }
                    runTerminalEffectsAndSealRestart(effects, outcome, tracker)
                }
            }.getOrElse { error ->
                // Escapes OUTSIDE the mutex (gen-1 build): nothing was mutated.
                val escaped = resolveTransactionEscape(error, tracker)
                transitionMutex.withLock {
                    runTerminalEffectsAndSealRestart(effects, escaped, tracker)
                }
            }
            submissionGuard.finish {
                sweepPersistedRecoveryAssets(currentOrNull, logger)
            }
            val restartWasRequired =
                replacementPolicy == ReplacementPolicy.RestartProcess && tracker.crossed
            val deliveredOutcome = if (restartWasRequired) {
                restartAfterPonr(finalOutcome, policy, logger)
            } else {
                finalOutcome
            }
            val restartFailed = deliveredOutcome is ReplacementOutcome.Fatal &&
                finalOutcome !is ReplacementOutcome.Fatal
            if (restartWasRequired && restartFailed && !isFatal) {
                publishFatal("process restart failed after point of no return")
            }
            inFlight.outcome.complete(deliveredOutcome)
        }
        return inFlight
    }

    /** Runs under [transitionMutex]; a restart-policy PONR is terminal before the lock opens. */
    private suspend fun runTerminalEffectsAndSealRestart(
        effects: DatabaseReplacementEffects,
        outcome: ReplacementOutcome,
        tracker: PonrTracker,
    ): ReplacementOutcome = runTerminalEffects(
        effects = effects,
        outcome = outcome,
        ponrCrossed = tracker.crossed,
        logger = logger,
    ).also {
        if (replacementPolicy == ReplacementPolicy.RestartProcess && tracker.crossed) {
            restartTerminal = true
        }
    }

    /** Resolves escapes to an intact outgoing generation before PONR, otherwise Fatal. */
    private fun resolveTransactionEscape(error: Throwable, tracker: PonrTracker): ReplacementOutcome {
        logger.e(error, "replacement transaction threw unexpectedly")
        if (isTerminal) return ReplacementOutcome.Fatal()
        // An unreleased candidate may still hold the live file, even before PONR.
        if (error is OrphanCloseException || error is PartialCandidateUnwindException) {
            return publishFatal("generation resources could not be released: $error")
        }
        return if (tracker.crossed) {
            publishFatal("transaction escaped after the point of no return: $error")
        } else {
            currentOrNull?.let { outgoing ->
                quiescer.reopen(outgoing.id)
                publishServing(outgoing)
            }
            ReplacementOutcome.RejectedBeforeMutation(
                BackupError.Io(IOException("replacement failed before mutation: $error")),
            )
        }
    }

    private suspend fun executeReplacement(
        operation: ReplacementOperation,
        effects: DatabaseReplacementEffects,
        tracker: PonrTracker,
    ): ReplacementOutcome {
        if (isTerminal) return ReplacementOutcome.Fatal()
        val outgoing = requireNotNull(currentOrNull) { "generation must exist before replacement" }
        val provider = outgoing.graph.databaseSnapshotProvider
        // Validate while the live database remains open.
        val plan = when (val selected = selectOperationSource(operation, provider)) {
            is OperationSourcePlan.Reject ->
                return ReplacementOutcome.RejectedBeforeMutation(selected.error)

            is OperationSourcePlan.Proceed -> selected
        }
        val requestedRollback = operation is ReplacementOperation.RollbackFromUndo
        val mutation = MutationPlan(provider, plan.source, effects, plan.undoRef)
        return when (replacementPolicy) {
            ReplacementPolicy.RestartProcess -> executeRestartProcessTransaction(
                outgoing = outgoing,
                mutation = mutation,
                tracker = tracker,
            )

            ReplacementPolicy.RebuildInProcess -> executeRebuildTransaction(
                outgoing = outgoing,
                mutation = mutation,
                tracker = tracker,
                requestedRollback = requestedRollback,
            )
        }
    }

    /** Keeps UI and DB-bound admission closed from pre-swap quiescence until process restart. */
    private suspend fun executeRestartProcessTransaction(
        outgoing: RuntimeGeneration,
        mutation: MutationPlan,
        tracker: PonrTracker,
    ): ReplacementOutcome {
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason ->
            return unwindQuiesce(outgoing, reason, logger, quiescer::reopen, ::publishServing)
        }
        claimMutationAfterQuiesce(mutation, tracker, logger, ::publishFatal)?.let { return it }
        return runRestartProcessSwap(
            closeDatabase = closeDatabase,
            outgoing = outgoing,
            mutation = mutation,
            tracker = tracker,
        )
    }

    /** Runs the in-process replacement machine; PONR failures publish a successor or Fatal. */
    private suspend fun executeRebuildTransaction(
        outgoing: RuntimeGeneration,
        mutation: MutationPlan,
        tracker: PonrTracker,
        requestedRollback: Boolean,
    ): ReplacementOutcome {
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason ->
            return unwindQuiesce(outgoing, reason, logger, quiescer::reopen, ::publishServing)
        }
        claimMutationAfterQuiesce(mutation, tracker, logger, ::publishFatal)?.let { return it }

        // PONR was crossed by the durable claim; the outgoing generation cannot be republished.
        quiescer.tearDown(outgoing)?.let { failure ->
            return publishFatal("outgoing teardown failed after PONR: $failure")
        }
        // A close failure leaves the handle unknown and is therefore Fatal.
        val closed = runCatching { closeDatabase(outgoing.database) }
        if (closed.isFailure) {
            return publishFatal("outgoing database close failed: ${closed.exceptionOrNull()}")
        }

        val transaction = ReplacementTransaction(nextDbGeneration = outgoing.dbGeneration + 1)
        val replaced = mutation.replaceLiveDatabase()
        if (replaced is BackupResult.Failure) {
            return recoverViaRollback(mutation, transaction, replaced.error, requestedRollback)
        }
        val committed = commitMutation(mutation)
        if (committed is CommitResult.NotDurable) {
            // A non-durable mutation is unprovable: retain assets and enter recovery.
            return recoverViaRollback(
                mutation = mutation,
                transaction = transaction,
                cause = committed.error,
                requestedRollback = requestedRollback,
            )
        }
        // The primary rollback may retry generation construction once.
        if (requestedRollback) transaction.rolledBack = true

        return when (val attempt = attemptGeneration(transaction, mutation)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released — ladder stopped")

            AttemptResult.ProtocolFatal ->
                publishFatal("candidate finalization is not durable — publication refused")

            AttemptResult.RestoreFinalizedRetryable ->
                retryFinalizedRestoreGeneration(
                    transaction,
                    mutation,
                    requestedRollback,
                    ::attemptGeneration,
                    ::publishFatal,
                )

            AttemptResult.Retryable ->
                if (transaction.rolledBack) {
                    retryAfterRollbackGeneration(
                        transaction,
                        mutation,
                        requestedRollback,
                        "candidate finalization is not durable — publication refused",
                        "post-rollback generation attempt failed",
                        ::attemptGeneration,
                        ::publishFatal,
                    )
                } else {
                    recoverViaRollback(
                        mutation = mutation,
                        transaction = transaction,
                        cause = null,
                        requestedRollback = requestedRollback,
                    )
                }
        }
    }

    /**
     * Runs one recovery rollback and one fresh-generation attempt. Requested rollbacks commit
     * through caller effects; compensating restore rollbacks do not.
     */
    private suspend fun recoverViaRollback(
        mutation: MutationPlan,
        transaction: ReplacementTransaction,
        cause: BackupError?,
        requestedRollback: Boolean,
    ): ReplacementOutcome {
        val provider = mutation.provider
        val rollbackRef = mutation.undoRef
        val validated = provider.validateUndo(rollbackRef)
        if (validated is BackupResult.Failure) {
            return publishFatal(
                "replacement failed ($cause) and owned undo is unusable (${validated.error})",
            )
        }
        val capacity = provider.checkRollbackCapacity(rollbackRef)
        if (capacity is BackupResult.Failure) {
            return publishFatal(
                "replacement failed ($cause) and rollback capacity is unavailable " +
                    "(${capacity.error})",
            )
        }

        val compensationOwner = prepareCompensationOwner(
            mutation = mutation,
            rollbackRef = rollbackRef,
            requestedRollback = requestedRollback,
        ).getOrElse { error ->
            return publishFatal("could not journal exact compensation owner before rollback: $error")
        }
        val rolledBack = provider.replaceLiveDatabaseFromUndo(rollbackRef)
        if (rolledBack is BackupResult.Failure) {
            return publishFatal("replacement failed ($cause) and rollback failed (${rolledBack.error})")
        }
        // Mark before consuming so a crash leaves a truthful journal.
        transaction.rolledBack = true
        transaction.rollbackCause = cause
            ?: BackupError.Io(IOException("restore could not complete; rolled back"))
        val committed = commitRecoveryRollback(mutation, compensationOwner)
        if (committed is CommitResult.NotDurable) {
            // Keep the Prepared journal and source; do not publish an unprovable rollback.
            return publishFatal(
                "the recovery rollback could not become durably provable " +
                    "(${committed.error}) — journal and assets preserved",
            )
        }
        return when (val attempt = attemptGeneration(transaction, mutation)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released after rollback")

            AttemptResult.ProtocolFatal ->
                publishFatal("rollback finalization is not durable — publication refused")

            AttemptResult.RestoreFinalizedRetryable -> publishFatal(
                "restore finalized during rollback recovery — compensation refused",
            )

            AttemptResult.Retryable ->
                // Inline recovery resolved its journal; one clean preflight retry is allowed.
                if (transaction.candidateInvalidated) {
                    retryAfterRollbackGeneration(
                        transaction,
                        mutation,
                        requestedRollback,
                        "rollback finalization is not durable — publication refused",
                        "post-rollback generation attempt failed (original cause: $cause)",
                        ::attemptGeneration,
                        ::publishFatal,
                    )
                } else {
                    publishFatal("post-rollback generation attempt failed (original cause: $cause)")
                }
        }
    }

    /** Builds, preflights, and publishes one candidate over the current live file. */
    private suspend fun attemptGeneration(
        transaction: ReplacementTransaction,
        mutation: MutationPlan,
    ): AttemptResult {
        val candidate = runCatching {
            buildGeneration(
                database = dbFactory(applicationContext),
                dbGeneration = transaction.nextDbGeneration++,
                ownsDatabase = true,
            )
        }.getOrElse { error ->
            logger.e(error, "candidate generation construction failed")
            return if (error is OrphanCloseException) AttemptResult.LadderFatal else AttemptResult.Retryable
        }
        transaction.candidate = candidate
        transaction.candidateInvalidated = false
        transaction.candidateDisposed = false
        val outcome = runCatching { withContext(transaction) { preflight(candidate) } }
        if (transaction.closeFailed) {
            // Failed inline teardown leaves the candidate handle unknown.
            return AttemptResult.LadderFatal
        }
        if (outcome.getOrNull() == StartupOutcome.FinalizationPending) {
            val released = quiescer.tearDownCandidate(candidate, closeCandidateDatabase = true)
            return if (released) AttemptResult.ProtocolFatal else AttemptResult.LadderFatal
        }
        val proceed = outcome.getOrNull() == StartupOutcome.Proceed && !transaction.candidateInvalidated
        if (!proceed) {
            logger.w { "candidate preflight did not proceed: $outcome" }
            // Inline rollback already disposed this candidate.
            val released = transaction.candidateDisposed ||
                quiescer.tearDownCandidate(candidate, closeCandidateDatabase = true)
            if (!released) return AttemptResult.LadderFatal
            return classifyReleasedCandidateFailure(
                transaction,
                mutation,
                candidate,
                outcome.isFailure,
            )
        }
        policy.advanceSnackbarGeneration()
        publishServing(candidate)
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return AttemptResult.Published(candidate)
    }

    /** Builds a staged generation and safely releases an orphan if graph creation fails. */
    private fun buildGeneration(
        database: AppDatabase,
        dbGeneration: Int,
        ownsDatabase: Boolean,
    ): RuntimeGeneration {
        val id = nextGenerationId.getAndIncrement()
        val lifetime = AppScopeLifetime()
        val graph = runCatching {
            graphFactory(applicationContext, database, imageStorage, lifetime, this)
        }.getOrElse { error ->
            // A partial graph may have jobs; join them before closing its owned database.
            throw releasePartialGeneration(
                lifetime = lifetime,
                database = database,
                ownsDatabase = ownsDatabase,
                cause = error,
                closeDatabase = closeDatabase,
                drainTimeoutMillis = policy.drainTimeoutMillis,
            )
        }
        return RuntimeGeneration(
            id = id,
            dbGeneration = dbGeneration,
            database = database,
            graph = graph,
            lifetime = lifetime,
            viewModelStore = ViewModelStore(),
        )
    }
}

internal const val APP_RUNTIME_TAG = "AppRuntime"
internal const val APP_RUNTIME_FIRST_GENERATION_ID = 1

/** Result of a graph-only reinitialization. */
internal sealed interface ReinitializeOutcome {

    data class Published(val generation: RuntimeGeneration) : ReinitializeOutcome

    /** A pre-PONR failure left [serving] intact. */
    data class Aborted(val reason: String, val serving: RuntimeGeneration?) : ReinitializeOutcome

    data class AlreadyReplaced(val serving: RuntimeGeneration) : ReinitializeOutcome

    data object Fatal : ReinitializeOutcome
}

/** Injectable transition seams for production wiring and deterministic tests. */
internal data class RuntimeTransitionPolicy(
    /** Atomically drains active snackbar routing and closes new admission. */
    val fenceSnackbarResolves: suspend () -> Unit = {},
    val unfenceSnackbarResolves: () -> Unit = {},
    val pendingSnackbarCount: () -> Int = { 0 },
    /** Advances the snackbar epoch before a committed successor is published. */
    val advanceSnackbarGeneration: () -> Unit = {},
    /** Invoked by the host after every restart-policy PONR outcome; production never returns. */
    val restartProcess: () -> Unit = {},
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    // Host dispatcher is virtual-time schedulable in JVM tests.
    val hostDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val uiDisposalTimeoutMillis: Long = DEFAULT_UI_DISPOSAL_TIMEOUT_MILLIS,
    val drainTimeoutMillis: Long = DEFAULT_DRAIN_TIMEOUT_MILLIS,
) {
    private companion object {
        const val DEFAULT_UI_DISPOSAL_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_DRAIN_TIMEOUT_MILLIS = 10_000L
    }
}

/** Replacement ending; Android production uses [RestartProcess]. */
internal enum class ReplacementPolicy { RestartProcess, RebuildInProcess }

/** File-swap operations; each caller receives an independently owned transaction. */
internal sealed interface ReplacementOperation {

    data class RestoreFromSnapshot(
        val sourcePath: String,
        val owner: RestoreOwnerId,
    ) : ReplacementOperation {
        val sourceRef: RestoreSourceRef = RestoreSourceRef(owner)

        constructor(source: File, owner: RestoreOwnerId) : this(source.absolutePath, owner)
    }

    data class RollbackFromUndo(
        val sourceRef: UndoRef,
        val owner: RestoreOwnerId,
    ) : ReplacementOperation
}

/** Phase-aware result of a replacement transaction. */
internal sealed interface ReplacementOutcome {

    /** Requested operation committed; [effectsError] reports a post-commit effect failure. */
    data class Completed(
        val generation: RuntimeGeneration?,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /** No irreversible mutation occurred; the outgoing generation stays intact. */
    data class RejectedBeforeMutation(
        val error: BackupError,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /** Requested restore rolled back after PONR; callers must use restore-failure semantics. */
    data class RecoveredByRollback(
        val error: BackupError,
        val generation: RuntimeGeneration,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /** Post-PONR failure under restart policy; runtime-owned recovery assets are preserved. */
    data class FailedAfterMutation(
        val error: BackupError,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    data class Fatal(val effectsError: BackupError? = null) : ReplacementOutcome
}

/** Runtime-wired generation graph constructor. */
internal typealias GraphFactory =
    (Context, AppDatabase, ImageStorage, AppScopeLifetime, DatabaseReplacement) -> AppGraph
