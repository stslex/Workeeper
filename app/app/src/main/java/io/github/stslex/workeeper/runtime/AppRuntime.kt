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

    private val logger = Log.tag(TAG)

    private val hostScope = CoroutineScope(
        SupervisorJob() +
            policy.hostDispatcher +
            CoroutineExceptionHandler { _, error ->
                Log.tag(TAG).e(error, "runtime host coroutine failed unexpectedly")
            },
    )

    /** Serializes transitions; generation 1 is built outside this mutex. */
    private val transitionMutex = Mutex()

    private val buildLock = Any()

    private val submissionLock = Any()

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

    private fun publishServing(generation: RuntimeGeneration) {
        check(!isFatal) { "a Fatal runtime must never publish Serving again" }
        currentOrNull = generation
        publishedFlow.value = Published(
            phase = RuntimePhase.Serving(generation),
            ui = AppUiPhase.Generation(id = generation.id, viewModelStoreOwner = generation),
        )
    }

    private fun publishTransitioning() {
        check(!isFatal) { "a Fatal runtime must never publish Transitioning" }
        publishedFlow.value = Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning)
    }

    private fun publishFatal(reason: String): ReplacementOutcome {
        logger.e(IllegalStateException(reason), "replacement FATAL")
        isFatal = true
        // The host chooses how an in-process Fatal reaches recovery UI.
        publishedFlow.value = Published(RuntimePhase.Fatal, AppUiPhase.Transitioning)
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return ReplacementOutcome.Fatal()
    }

    // Generation 1 mints under buildLock; later generations mint under the mutex.
    private val nextGenerationId = AtomicInteger(FIRST_GENERATION_ID)

    /**
     * The one published generation; builds and publishes generation 1 on first read. Reads during
     * a transition answer with the outgoing generation; after [RuntimePhase.Fatal] this THROWS.
     */
    val currentGeneration: RuntimeGeneration
        get() {
            check(!isFatal) { "runtime is Fatal — no generation is serving; recovery required" }
            return currentOrNull ?: synchronized(buildLock) {
                currentOrNull ?: buildGeneration(
                    database = dbFactory(applicationContext),
                    dbGeneration = FIRST_GENERATION_ID,
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
        check(!isFatal) { "runtime is Fatal — no generation may admit new work" }
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
                        isFatal || outgoing == null -> ReinitializeOutcome.Fatal

                        expected != null && outgoing.id != expected.id ->
                            ReinitializeOutcome.AlreadyReplaced(outgoing)

                        else -> runCatching { runGraphOnlyTransition(outgoing, tracker) }
                            .getOrElse { error -> resolveGraphOnlyEscape(error, outgoing, tracker) }
                    }
                }
            }.getOrElse { error ->
                logger.e(error, "reinitialize failed outside the transition body")
                when {
                    isFatal -> ReinitializeOutcome.Fatal

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
        if (isFatal) return ReinitializeOutcome.Fatal
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

    private val inFlightReplacements = HashMap<ReplacementOperation, InFlightReplacement>()

    override suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult {
        // Rollback has two inline escapes; restore has none by design, so a reentrant
        // submission would block forever on the non-reentrant transition mutex.
        if (coroutineContext.isInsideReplacementTransaction()) {
            return rejectNestedRestore(effects, logger)
        }
        return replace(ReplacementOperation.RestoreFromSnapshot(source), effects).toSeamResult()
    }

    override suspend fun rollbackToPreRestoreBackup(
        sourcePath: String?,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult {
        // A matching marker makes preflight rollback part of this transaction, never nested.
        coroutineContext[ReplacementTransaction]?.let { transaction ->
            val outcome = runInlineRollback(
                transaction = transaction,
                effects = effects,
                sourcePath = sourcePath,
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
            ReplacementOperation.RollbackToPreRestoreBackup(sourcePath),
            effects,
        ).toSeamResult()
    }

    /** Same operations coalesce atomically; different operations serialize independently. */
    suspend fun replace(
        operation: ReplacementOperation,
        effects: DatabaseReplacementEffects = DatabaseReplacementEffects.None,
    ): ReplacementOutcome = submitReplacement(operation, effects).outcome.await()

    private fun submitReplacement(
        operation: ReplacementOperation,
        effects: DatabaseReplacementEffects,
    ): InFlightReplacement {
        val inFlight = synchronized(submissionLock) {
            inFlightReplacements[operation]?.let { return it }
            InFlightReplacement(operation, CompletableDeferred()).also {
                inFlightReplacements[operation] = it
            }
        }
        // Stage before suspension so the runtime owns the source despite caller cancellation.
        if (operation is ReplacementOperation.RestoreFromSnapshot) {
            runCatching {
                stageRestoreSource(
                    source = File(operation.sourcePath),
                    stagingDirectory = policy.stagingDirectory(applicationContext),
                    sequence = stagedSourceSequence.incrementAndGet(),
                )
            }.fold(
                onSuccess = { staged -> inFlight.stagedSource = staged },
                onFailure = { error -> inFlight.stagingFailure = error },
            )
        }
        hostScope.launch {
            val tracker = PonrTracker()
            // Terminal effects remain under the mutex so a successor cannot interleave them.
            val finalOutcome = inFlight.stagingFailure?.let { stagingError ->
                val rejected = ReplacementOutcome.RejectedBeforeMutation(
                    BackupError.Io(IOException("restore source staging failed: $stagingError")),
                )
                transitionMutex.withLock {
                    runTerminalEffects(effects, rejected, tracker.crossed, logger)
                }
            } ?: runCatching {
                currentGeneration
                transitionMutex.withLock {
                    val outcome = runCatching {
                        executeReplacement(
                            operation = operation,
                            effects = effects,
                            tracker = tracker,
                            stagedSource = inFlight.stagedSource,
                            reservationSlot = { inFlight.reservation = it },
                        )
                    }.getOrElse { error ->
                        if (error is CancellationException) {
                            // hostScope is never cancelled; an internal one is a bug.
                            logger.e(error, "replacement transaction cancelled internally")
                        }
                        resolveTransactionEscape(error, tracker)
                    }
                    runTerminalEffects(effects, outcome, tracker.crossed, logger)
                }
            }.getOrElse { error ->
                // Escapes OUTSIDE the mutex (gen-1 build): nothing was mutated.
                val escaped = resolveTransactionEscape(error, tracker)
                transitionMutex.withLock {
                    runTerminalEffects(effects, escaped, tracker.crossed, logger)
                }
            }
            inFlight.stagedSource?.let { staged -> runCatching { staged.delete() } }
            // Keep a journal-named reservation until a clean terminal outcome resolves it. A
            // Completed transaction's reservation belongs to the commit protocol, which deleted it
            // when the undo slot landed and deliberately kept it when the install failed.
            val keepReservation = finalOutcome is ReplacementOutcome.Completed ||
                finalOutcome is ReplacementOutcome.FailedAfterMutation ||
                finalOutcome is ReplacementOutcome.Fatal ||
                finalOutcome.effectsError() != null
            if (!keepReservation) {
                inFlight.reservation?.let { reserved -> runCatching { reserved.delete() } }
            }
            synchronized(submissionLock) { inFlightReplacements.remove(operation) }
            inFlight.outcome.complete(finalOutcome)
        }
        return inFlight
    }

    /** Resolves escapes to an intact outgoing generation before PONR, otherwise Fatal. */
    private fun resolveTransactionEscape(error: Throwable, tracker: PonrTracker): ReplacementOutcome {
        logger.e(error, "replacement transaction threw unexpectedly")
        if (isFatal) return ReplacementOutcome.Fatal()
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
        stagedSource: File?,
        reservationSlot: (File) -> Unit,
    ): ReplacementOutcome {
        if (isFatal) return ReplacementOutcome.Fatal()
        val outgoing = requireNotNull(currentOrNull) { "generation must exist before replacement" }
        val provider = outgoing.graph.databaseSnapshotProvider
        // Validate while the live database remains open.
        val plan = when (val selected = selectOperationSource(operation, provider, stagedSource)) {
            is OperationSourcePlan.Reject ->
                return ReplacementOutcome.RejectedBeforeMutation(selected.error)

            is OperationSourcePlan.Proceed -> selected
        }
        val source = plan.source
        val consume = plan.consume
        // Reserve after validation and before PONR; each attempt owns only its own snapshot.
        var reservation: File? = null
        if (operation is ReplacementOperation.RestoreFromSnapshot) {
            when (val reserved = provider.reserveRollbackSnapshot(effects.attemptId)) {
                is BackupResult.Success -> {
                    reservation = reserved.data
                    reservationSlot(reserved.data)
                }

                is BackupResult.Failure -> return ReplacementOutcome.RejectedBeforeMutation(
                    reserved.error,
                )
            }
        }
        // Claim the journal atomically before mutation.
        runCatching { effects.onBeforeMutation(reservation?.absolutePath.orEmpty()) }
            .onFailure { error ->
                return ReplacementOutcome.RejectedBeforeMutation(
                    BackupError.Io(IOException("pre-mutation persistence failed: $error")),
                )
            }
        val requestedRollback = operation is ReplacementOperation.RollbackToPreRestoreBackup
        return when (replacementPolicy) {
            ReplacementPolicy.RestartProcess -> runRestartProcessSwap(
                closeDatabase = closeDatabase,
                outgoing = outgoing,
                mutation = MutationPlan(provider, source, consume, effects, reservation),
                tracker = tracker,
            )

            ReplacementPolicy.RebuildInProcess -> executeRebuildTransaction(
                outgoing = outgoing,
                mutation = MutationPlan(provider, source, consume, effects, reservation),
                tracker = tracker,
                requestedRollback = requestedRollback,
            )
        }
    }

    /** Runs the in-process replacement machine; PONR failures publish a successor or Fatal. */
    private suspend fun executeRebuildTransaction(
        outgoing: RuntimeGeneration,
        mutation: MutationPlan,
        tracker: PonrTracker,
        requestedRollback: Boolean,
    ): ReplacementOutcome {
        val effects = mutation.effects
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason -> return unwindQuiesce(outgoing, reason) }

        // PONR: teardown has begun and the outgoing generation cannot be republished.
        tracker.crossed = true
        quiescer.tearDown(outgoing)?.let { failure ->
            return publishFatal("outgoing teardown failed after PONR: $failure")
        }
        // A close failure leaves the handle unknown and is therefore Fatal.
        val closed = runCatching { closeDatabase(outgoing.database) }
        if (closed.isFailure) {
            return publishFatal("outgoing database close failed: ${closed.exceptionOrNull()}")
        }

        val transaction = ReplacementTransaction(nextDbGeneration = outgoing.dbGeneration + 1)
        val replaced = mutation.provider.replaceLiveDatabaseFile(mutation.source)
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
                afterCleanCommit = false,
            )
        }
        if (requestedRollback) {
            // The primary rollback may retry generation construction once.
            transaction.rolledBack = true
        }

        return when (val attempt = attemptGeneration(transaction)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released — ladder stopped")

            AttemptResult.Retryable ->
                if (transaction.rolledBack) {
                    when (val retry = attemptGeneration(transaction)) {
                        is AttemptResult.Published -> completedOrRecovered(
                            transaction,
                            retry.generation,
                            requestedRollback,
                        )

                        else -> publishFatal("post-rollback generation attempt failed")
                    }
                } else {
                    recoverViaRollback(
                        mutation = mutation,
                        transaction = transaction,
                        cause = null,
                        requestedRollback = requestedRollback,
                        // A durable commit makes the canonical slot this attempt's source.
                        afterCleanCommit = true,
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
        afterCleanCommit: Boolean = false,
    ): ReplacementOutcome {
        val provider = mutation.provider
        val rollbackSource: File
        val rollbackConsume: SourceConsumption
        when (val plan = selectRecoverySource(mutation, cause, afterCleanCommit)) {
            is RecoverySourcePlan.Stop -> return publishFatal(plan.reason)
            is RecoverySourcePlan.Apply -> {
                rollbackSource = plan.source
                rollbackConsume = plan.consume
            }
        }
        if (afterCleanCommit) {
            // Re-journal the exact source this recovery applies: a null path would let the next
            // launch substitute a canonical that may still be the PREVIOUS attempt's image.
            val rejournalled = rollbackSource.takeIf { it == mutation.reservation }
            val unCommitted = runCatching {
                mutation.effects.onBeforeMutation(rejournalled?.absolutePath.orEmpty())
            }
            if (unCommitted.isFailure) {
                return publishFatal(
                    "could not durably un-commit before the recovery rollback: " +
                        "${unCommitted.exceptionOrNull()}",
                )
            }
        }
        val rolledBack = provider.replaceLiveDatabaseFile(rollbackSource)
        if (rolledBack is BackupResult.Failure) {
            return publishFatal("replacement failed ($cause) and rollback failed (${rolledBack.error})")
        }
        // Mark before consuming so a crash leaves a truthful journal.
        transaction.rolledBack = true
        transaction.rollbackCause = cause
            ?: BackupError.Io(IOException("restore could not complete; rolled back"))
        // Consume exactly the applied source after its durable commit.
        val commitEffects = if (requestedRollback) mutation.effects else DatabaseReplacementEffects.None
        val committed = commitMutation(
            mutation = MutationPlan(
                provider = provider,
                source = rollbackSource,
                consume = rollbackConsume,
                effects = commitEffects,
                reservation = null,
            ),
        )
        if (committed is CommitResult.NotDurable) {
            // Keep the Prepared journal and source; do not publish an unprovable rollback.
            return publishFatal(
                "the recovery rollback could not become durably provable " +
                    "(${committed.error}) — journal and assets preserved",
            )
        }
        return when (val attempt = attemptGeneration(transaction)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released after rollback")

            AttemptResult.Retryable ->
                // Inline recovery resolved its journal; one clean preflight retry is allowed.
                if (transaction.candidateInvalidated) {
                    when (val retry = attemptGeneration(transaction)) {
                        is AttemptResult.Published -> completedOrRecovered(
                            transaction,
                            retry.generation,
                            requestedRollback,
                        )

                        else -> publishFatal(
                            "post-rollback generation attempt failed (original cause: $cause)",
                        )
                    }
                } else {
                    publishFatal("post-rollback generation attempt failed (original cause: $cause)")
                }
        }
    }

    /** Builds, preflights, and publishes one candidate over the current live file. */
    private suspend fun attemptGeneration(transaction: ReplacementTransaction): AttemptResult {
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
        val proceed = outcome.getOrNull() == StartupOutcome.Proceed && !transaction.candidateInvalidated
        if (!proceed) {
            logger.w { "candidate preflight did not proceed: $outcome" }
            // Inline rollback already disposed this candidate.
            val released = transaction.candidateDisposed ||
                quiescer.tearDownCandidate(candidate, closeCandidateDatabase = true)
            return if (released) AttemptResult.Retryable else AttemptResult.LadderFatal
        }
        policy.advanceSnackbarGeneration()
        publishServing(candidate)
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return AttemptResult.Published(candidate)
    }

    private fun unwindQuiesce(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReplacementOutcome {
        logger.w { "replacement aborted during quiesce: $reason — generation ${outgoing.id} keeps serving" }
        quiescer.reopen(outgoing.id)
        publishServing(outgoing)
        return ReplacementOutcome.RejectedBeforeMutation(BackupError.Io(IOException(reason)))
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

    private companion object {
        const val TAG = "AppRuntime"
        const val FIRST_GENERATION_ID = 1
    }
}

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
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    // Host dispatcher is virtual-time schedulable in JVM tests.
    val hostDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Where staged restore sources live — runtime-owned files, deleted on every outcome. */
    val stagingDirectory: (Context) -> File = { context -> context.cacheDir },
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

/** File-swap operations, identity-compared for same-operation coalescing. */
internal sealed interface ReplacementOperation {

    /** Identity uses the original source path; staging follows coalescing. */
    data class RestoreFromSnapshot(val sourcePath: String) : ReplacementOperation {
        constructor(source: File) : this(source.absolutePath)
    }

    /** `null` [sourcePath] selects the canonical undo slot; otherwise it is exact and owned. */
    data class RollbackToPreRestoreBackup(
        val sourcePath: String? = null,
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
