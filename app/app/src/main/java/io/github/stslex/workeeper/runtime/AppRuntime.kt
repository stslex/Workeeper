// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModelStore
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkLease
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.closeAppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The application-owned runtime host (Phase 5 R2, ownership model H1 —
 * `kmp-phase-5-startup-processor.md` §8.1). Owns everything the Metro graph cannot own about
 * itself: the process roots ([ImageStorage]; the database FACTORY — each DB generation is built
 * from it), the sequence of [RuntimeGeneration]s, worker admission, and the single-flight
 * transition machinery. `BaseApplication` holds one instance for the process and answers every
 * graph seam from [currentGeneration]; the androidTest harness builds its own over test factories.
 *
 * ## Transaction ownership (REQUEST_CHANGES finding 1)
 *
 * Every transition — replacement AND graph-only — is **submission-owned**: the caller's
 * coroutine only SUBMITS the operation and awaits a [CompletableDeferred]; the transaction body
 * runs on [hostScope], the process host's own never-cancelled scope. Caller cancellation
 * abandons the await and NOTHING else — critical because both real replacement initiators die
 * mid-transaction by design: the Settings restore Store's scope is disposed when the transaction
 * publishes `Transitioning`, and the undo initiator (`RestoreDialogChoiceObserver`) is a child
 * of the outgoing [AppScopeLifetime] that Quiescing cancels-and-joins (the cancel is what breaks
 * the await↔join cycle; the join then completes). Post-commit state/dialog effects therefore run
 * as caller-supplied [onCommitted] hooks ON THE TRANSACTION's coroutine — after the commit,
 * before the awaiters complete — never in a caller scope the transition itself kills. Hooks must
 * touch only process-lifetime state (DataStore); their failures are contained and logged, the
 * same containment contract as the dialog reactor's.
 *
 * Cancellation semantics, precisely: before the point of no return an internal failure/throw
 * unwinds to `Serving(outgoing)` and reports [ReplacementOutcome.RejectedBeforeMutation]; after
 * it, the failure ladder runs and ends in a published successor or the explicit terminal
 * [RuntimePhase.Fatal] — a transaction never strands `Transitioning` and is never cancelled
 * from outside.
 *
 * ## Closed admission (finding 2)
 *
 * DB-bound WorkManager work enters ONLY through [acquireBackupWorkLease]: deps and lease are
 * acquired atomically under [admissionLock], so no worker can capture outgoing-generation
 * dependencies after a transition closed admission. Quiescing closes admission, then awaits the
 * lease count reaching zero (a real join over previously admitted work — including workers
 * constructed but not yet RUNNING, the gap a WorkInfo snapshot cannot see); a timeout aborts
 * BEFORE close and reopens admission. Blocked acquirers resume against whatever generation is
 * published when admission reopens. UI Stores and per-entry jobs are covered by the awaited
 * UI-region disposal; graph-owned collectors/jobs by the bounded lifetime join — and per the
 * never-close-after-failed-join rule, a join timeout also ABORTS (degraded: reactors are already
 * cancelled; recorded in the outcome reason) instead of closing the database.
 *
 * ## UI gate (finding 3)
 *
 * UI acknowledgement is generation-id-bound and multi-attachment-safe: [onUiGenerationAttached]/
 * [onUiGenerationDisposed] keep a per-id attachment COUNT, and a transition awaits ITS outgoing
 * id's count reaching zero. A wrong or stale id only ever decrements its own key — it can never
 * release another generation's gate — and overlapping compositions (Activity recreation) must
 * all detach before the gate opens.
 *
 * ## Published state (finding 7)
 *
 * One immutable [Published] value backs BOTH faces ([phases] and [uiPhases]) — they can never
 * disagree. [RuntimePhase.Fatal] is a real state: [currentGeneration] and lease acquisition
 * throw (no closed generation is ever exposed through the holders), and no code path converts
 * Fatal back to Serving.
 *
 * Production Android never runs an in-process transition — restore/rollback/undo keep the
 * process-restart `AppReinitializer` and the [ReplacementPolicy.RestartProcess] ending (locked
 * invariant). The in-process callers are Android instrumentation and, in Phase 7, the iOS host
 * via [AppReinitializationHost].
 */
internal class AppRuntime(
    private val applicationContext: Context,
    private val dbFactory: (Context) -> AppDatabase,
    private val imageStorageFactory: (Context) -> ImageStorage,
    private val graphFactory: GraphFactory,
    private val preflight: suspend (RuntimeGeneration) -> StartupOutcome,
    // The terminal close verb, injectable for the JVM suites (app/app is Room-free by design —
    // the default is the database module's helper, same seam philosophy as dbFactory).
    private val closeDatabase: (AppDatabase) -> Unit = ::closeAppDatabase,
    private val replacementPolicy: ReplacementPolicy = ReplacementPolicy.RestartProcess,
    private val policy: RuntimeTransitionPolicy = RuntimeTransitionPolicy(),
) : AppReinitializationHost,
    DatabaseReplacement {

    private val logger = Log.tag(TAG)

    /**
     * The runtime's own scope — the ONE deliberately process-lifetime scope in the app: the
     * runtime IS the process host, the owner every other lifetime hangs off, so there is nothing
     * above it to own this. Every transition RUNS here (submission ownership, class KDoc); the
     * handler is a last-resort recorder — transaction bodies complete their deferreds through
     * their own catch-all, so an escape here is a bug, logged rather than crashing the process.
     */
    private val hostScope = CoroutineScope(
        SupervisorJob() +
            policy.hostDispatcher +
            CoroutineExceptionHandler { _, error ->
                Log.tag(TAG).e(error, "runtime host coroutine failed unexpectedly")
            },
    )

    /** Serializes every generation transition. NEVER held while building/publishing gen 1. */
    private val transitionMutex = Mutex()

    private val buildLock = Any()

    /** Guards submission registration (finding 6b) — atomic check-and-register. */
    private val submissionLock = Any()

    /** Guards lease admission (finding 2) — deps + lease count move atomically. */
    private val admissionLock = Any()

    val imageStorage: ImageStorage by lazy { imageStorageFactory(applicationContext) }

    // ------------------------------------------------------------------------------------------
    // Published state — ONE immutable value behind both faces (finding 7).
    // ------------------------------------------------------------------------------------------

    private class Published(val phase: RuntimePhase, val ui: AppUiPhase)

    private val publishedFlow = MutableStateFlow(
        Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning),
    )

    /** The published phase stream (runtime-internal face). */
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
        publishedFlow.value = Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning)
    }

    private fun publishFatal(reason: String): ReplacementOutcome {
        logger.e(IllegalStateException(reason), "replacement FATAL")
        isFatal = true
        // The UI face stays the neutral interstitial; surfacing a recovery UI for a Fatal
        // in-process outcome is the calling HOST's wiring (Phase 7 / instrumentation).
        publishedFlow.value = Published(RuntimePhase.Fatal, AppUiPhase.Transitioning)
        // Wake any parked lease acquirer so it hits the fatal check and fails LOUD instead of
        // parking a WorkManager thread forever.
        reopenAdmission()
        return ReplacementOutcome.Fatal
    }

    // Atomic because generation 1 mints under [buildLock] while transitions mint under the mutex.
    private val nextGenerationId = AtomicInteger(FIRST_GENERATION_ID)

    /**
     * The one published generation; builds and publishes generation 1 on first read. Reads
     * during a transition answer with the outgoing generation (alive and open for graph-only
     * transitions; terminal reads after a replacement's close fail loud — §7.1's measured pin).
     * After [RuntimePhase.Fatal] this THROWS: no closed generation is ever exposed (finding 5).
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

    // ------------------------------------------------------------------------------------------
    // UI attachment gate — id-bound, multi-attachment safe (finding 3).
    // ------------------------------------------------------------------------------------------

    private val uiAttachments = MutableStateFlow<Map<Int, Int>>(emptyMap())

    fun onUiGenerationAttached(id: Int) {
        uiAttachments.update { counts -> counts + (id to (counts[id] ?: 0) + 1) }
    }

    fun onUiGenerationDisposed(id: Int) {
        uiAttachments.update { counts ->
            when (val remaining = (counts[id] ?: 0) - 1) {
                in Int.MIN_VALUE..0 -> counts - id
                else -> counts + (id to remaining)
            }
        }
    }

    /** Suspends until NO composition holds the given generation's region. */
    private suspend fun awaitUiDetached(generationId: Int) {
        uiAttachments.first { counts -> (counts[generationId] ?: 0) == 0 }
    }

    // ------------------------------------------------------------------------------------------
    // Worker admission — closed barrier with leases (finding 2).
    // ------------------------------------------------------------------------------------------

    private val admissionClosed = MutableStateFlow(false)
    private val activeWorkerLeases = MutableStateFlow(0)

    /**
     * Atomically admits one DB-bound worker: waits while admission is closed (bounded only by
     * the in-flight transition), then — under [admissionLock], so a concurrent close cannot
     * interleave — increments the lease count and captures the CURRENT generation's deps. The
     * lease must be [BackupWorkLease.release]d when the worker's run ends; Quiescing awaits the
     * count reaching zero. Throws when the runtime is Fatal: a worker must never receive a
     * closed generation's dependencies.
     *
     * Blocking by design: WorkManager's `createWorker` is synchronous and runs on its serial
     * task-executor thread; parking it for the bounded transition window binds the worker
     * coherently to exactly one generation instead of tearing it across two.
     */
    fun acquireBackupWorkLease(): BackupWorkLease {
        while (true) {
            synchronized(admissionLock) {
                check(!isFatal) { "runtime is Fatal — no generation may admit new work" }
                if (!admissionClosed.value) {
                    val generation = currentGeneration
                    activeWorkerLeases.update { it + 1 }
                    return LeaseImpl(generation.graph)
                }
            }
            runBlocking { admissionClosed.first { closed -> !closed } }
        }
    }

    private inner class LeaseImpl(override val deps: BackupWorkerDeps) : BackupWorkLease {
        private val released = AtomicBoolean(false)
        override fun release() {
            if (released.compareAndSet(false, true)) {
                activeWorkerLeases.update { count -> (count - 1).coerceAtLeast(0) }
            }
        }
    }

    private fun closeAdmission() = synchronized(admissionLock) { admissionClosed.value = true }

    private fun reopenAdmission() = synchronized(admissionLock) { admissionClosed.value = false }

    private suspend fun awaitLeasesReleased(): Boolean =
        withTimeoutOrNull(policy.drainTimeoutMillis) {
            activeWorkerLeases.first { count -> count == 0 }
        } != null

    // ------------------------------------------------------------------------------------------
    // Graph-only transitions (submission-owned; finding 7 fixes).
    // ------------------------------------------------------------------------------------------

    override fun requestReinitialize() {
        val expected = currentOrNull
        hostScope.launch { reinitialize(expected) }
    }

    /**
     * Graph-only generation replacement, submission-owned: the body runs on [hostScope]; a
     * cancelled caller abandons only its await. [expected] coalesces stale requests.
     */
    suspend fun reinitialize(expected: RuntimeGeneration? = null): ReinitializeOutcome {
        val result = CompletableDeferred<ReinitializeOutcome>()
        hostScope.launch {
            // Ensure generation 1 exists before taking the transition mutex (cold-start rule).
            currentGeneration
            val outcome = transitionMutex.withLock {
                val outgoing = requireNotNull(currentOrNull) { "generation 1 must exist here" }
                if (expected != null && outgoing.id != expected.id) {
                    ReinitializeOutcome.AlreadyReplaced(outgoing)
                } else {
                    runCatching { runGraphOnlyTransition(outgoing) }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        // Deterministic unwind (finding 7): a construction/preflight escape must
                        // never strand Transitioning; the outgoing generation is untouched
                        // (its VM store and lifetime are only touched after publish).
                        logger.e(error, "graph-only transition threw; unwinding to Serving")
                        abortToServing(outgoing, reason = "transition threw: $error")
                    }
                }
            }
            result.complete(outcome)
        }
        return result.await()
    }

    private suspend fun runGraphOnlyTransition(outgoing: RuntimeGeneration): ReinitializeOutcome {
        // ---- Quiescing (relaxed graph-only order; every step abortable back to `outgoing`,
        // which stays INTACT: its ViewModelStore and lifetime are untouched until after publish.
        publishTransitioning()
        val uiDetached = withTimeoutOrNull(policy.uiDisposalTimeoutMillis) {
            awaitUiDetached(outgoing.id)
        }
        if (uiDetached == null) {
            return abortToServing(outgoing, reason = "ui region did not dispose in time")
        }
        closeAdmission()
        if (!awaitLeasesReleased()) {
            return abortToServing(outgoing, reason = "worker lease drain timed out")
        }
        val resolvesIdle = withTimeoutOrNull(policy.drainTimeoutMillis) {
            policy.drainSnackbarResolves()
        }
        if (resolvesIdle == null) {
            return abortToServing(outgoing, reason = "snackbar resolve drain timed out")
        }
        policy.pendingSnackbarCount().takeIf { it > 0 }?.let { queued ->
            // Recorded, never silently dropped: queued models carry the outgoing generation's
            // closures; executing one later follows ED11's interruption semantics (spec §8.4).
            logger.w { "$queued queued snackbar model(s) will cross the generation boundary" }
        }

        // ---- BuildingGeneration: SAME database object, fresh graph/lifetime/VM store. Staged:
        // a graphFactory failure cancels the fresh lifetime and leaves the SHARED database open.
        val candidate = runCatching {
            buildGeneration(
                database = outgoing.database,
                dbGeneration = outgoing.dbGeneration,
                ownsDatabase = false,
            )
        }.getOrElse { error ->
            return abortToServing(outgoing, reason = "candidate construction failed: $error")
        }

        // ---- Preflight, under the graph-only marker: a Scenario-1 rollback request from inside
        // this preflight is REJECTED deterministically (pre-mutation) instead of deadlocking on
        // the non-reentrant transition mutex — the coordinator keeps its persisted state intact
        // and the transition aborts; a subsequent REPLACEMENT transaction completes the rollback.
        val preflightOutcome = runCatching {
            withContext(GraphOnlyTransition) { preflight(candidate) }
        }
        val proceed = preflightOutcome.getOrNull() == StartupOutcome.Proceed
        if (!proceed) {
            disposeFailedCandidate(candidate, closeCandidateDatabase = false)
            return abortToServing(
                outgoing,
                reason = "candidate preflight failed: " +
                    (preflightOutcome.exceptionOrNull()?.toString() ?: "${preflightOutcome.getOrNull()}"),
            )
        }

        // ---- Publishing: atomic handover, THEN deterministic disposal of the outgoing
        // generation's UI ownership and lifetime (VM store clear lives HERE, not in quiesce —
        // an aborted transition re-enters the same store with its ViewModels intact).
        publishServing(candidate)
        reopenAdmission()
        withContext(policy.mainDispatcher) { outgoing.viewModelStore.clear() }
        withTimeoutOrNull(policy.drainTimeoutMillis) { outgoing.lifetime.cancelAndJoin() }
            ?: logger.w { "outgoing generation ${outgoing.id} lifetime join timed out (cancel signalled)" }
        return ReinitializeOutcome.Published(candidate)
    }

    private fun abortToServing(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReinitializeOutcome {
        logger.w { "reinitialize aborted: $reason — generation ${outgoing.id} keeps serving" }
        reopenAdmission()
        publishServing(outgoing)
        return ReinitializeOutcome.Aborted(reason = reason, serving = outgoing)
    }

    private suspend fun disposeFailedCandidate(
        candidate: RuntimeGeneration,
        closeCandidateDatabase: Boolean = true,
    ) {
        // close() is idempotent — safe even when the inline rollback already closed it. A
        // graph-only candidate SHARES the outgoing database and must never close it.
        if (closeCandidateDatabase) runCatching { closeDatabase(candidate.database) }
        withContext(policy.mainDispatcher) { candidate.viewModelStore.clear() }
        candidate.lifetime.cancelAndJoin()
    }

    // ------------------------------------------------------------------------------------------
    // The database replacement transaction — submission-owned (findings 1, 4, 5, 6).
    // ------------------------------------------------------------------------------------------

    private val inFlightReplacements = HashMap<ReplacementOperation, InFlightReplacement>()

    override suspend fun restoreFromSnapshot(
        source: File,
        beforeMutation: suspend () -> Unit,
    ): DatabaseReplacementResult = replace(
        operation = ReplacementOperation.RestoreFromSnapshot(source),
        hooks = ReplacementHooks(beforeMutation = beforeMutation),
    ).toSeamResult()

    override suspend fun rollbackToPreRestoreBackup(
        onCommitted: suspend () -> Unit,
    ): DatabaseReplacementResult {
        // Re-entrancy (spec §8.4): a rollback issued from INSIDE this runtime's own candidate
        // preflight is the current transaction's rollback branch, executed inline — never a
        // nested transaction (the mutex is non-reentrant and the transaction coroutine holds it).
        coroutineContext[ReplacementTransaction]?.let { transaction ->
            return runInlineRollback(closeDatabase, transaction).also { result ->
                if (result is DatabaseReplacementResult.Committed) {
                    runCommittedHook(onCommitted)
                }
            }
        }
        // A rollback during a GRAPH-ONLY transition's preflight is rejected pre-mutation
        // (deterministic; nothing on disk or in DataStore is touched) — deadlock-free by
        // construction, and the persisted Scenario-1 state stays intact for a cold start or a
        // later replacement transaction to complete.
        if (coroutineContext[GraphOnlyTransition.Key] != null) {
            return DatabaseReplacementResult.RejectedBeforeMutation(
                BackupError.Io(IOException("rollback unavailable inside a graph-only transition")),
            )
        }
        return replace(
            operation = ReplacementOperation.RollbackToPreRestoreBackup,
            hooks = ReplacementHooks(onCommitted = onCommitted),
        ).toSeamResult()
    }

    /**
     * Submission entry (findings 1 + 6b): registration is ATOMIC under [submissionLock] — two
     * same-operation requests arriving before either registers share one transaction and one
     * outcome (the first submitter's hooks win; for the real callers a same-op race is the same
     * semantic intent, e.g. an undo re-tap). A different operation registers its own transaction,
     * which serializes behind the mutex and never receives another operation's result. The
     * transaction body runs on [hostScope]; callers only await.
     */
    suspend fun replace(
        operation: ReplacementOperation,
        hooks: ReplacementHooks = ReplacementHooks(),
    ): ReplacementOutcome = submitReplacement(operation, hooks).outcome.await()

    private fun submitReplacement(
        operation: ReplacementOperation,
        hooks: ReplacementHooks,
    ): InFlightReplacement {
        val inFlight = synchronized(submissionLock) {
            inFlightReplacements[operation]?.let { return it }
            InFlightReplacement(operation, CompletableDeferred()).also {
                inFlightReplacements[operation] = it
            }
        }
        hostScope.launch {
            val tracker = PonrTracker()
            // Escape hatches are deliberately Throwable-broad (finding 1): every escape must
            // resolve to a published state and complete the awaiters.
            val outcome = runCatching {
                // Cold-start rule: generation 1 exists before the transition mutex is taken.
                currentGeneration
                transitionMutex.withLock {
                    runCatching { executeReplacement(operation, hooks, tracker) }
                        .getOrElse { error ->
                            if (error is CancellationException) {
                                // hostScope is never cancelled; an internal CancellationException
                                // is a bug — resolve it like any escape, never strand awaiters.
                                logger.e(error, "replacement transaction cancelled internally")
                            }
                            resolveTransactionEscape(error, tracker)
                        }
                }
            }.getOrElse { error ->
                // Escapes OUTSIDE the mutex (gen-1 build): nothing was mutated.
                resolveTransactionEscape(error, tracker)
            }
            synchronized(submissionLock) { inFlightReplacements.remove(operation) }
            inFlight.outcome.complete(outcome)
        }
        return inFlight
    }

    /**
     * Deterministic resolution for an unexpected transaction escape (finding 1): before the
     * point of no return the outgoing generation is republished (it is intact — quiesce touches
     * nothing irreversible before PONR); after it, the state is unknown → the explicit Fatal
     * terminal, never a stranded `Transitioning` and never a resurrected closed generation.
     */
    private fun resolveTransactionEscape(error: Throwable, tracker: PonrTracker): ReplacementOutcome {
        logger.e(error, "replacement transaction threw unexpectedly")
        val outgoing = currentOrNull
        return if (tracker.crossed) {
            publishFatal("transaction escaped after the point of no return: $error")
        } else {
            reopenAdmission()
            if (outgoing != null && !isFatal) publishServing(outgoing)
            ReplacementOutcome.RejectedBeforeMutation(
                BackupError.Io(IOException("replacement failed before mutation: $error")),
            )
        }
    }

    private suspend fun executeReplacement(
        operation: ReplacementOperation,
        hooks: ReplacementHooks,
        tracker: PonrTracker,
    ): ReplacementOutcome {
        val outgoing = requireNotNull(currentOrNull) { "generation must exist before replacement" }
        val provider = outgoing.graph.databaseSnapshotProvider
        // Running-state validation — reads the LIVE database, so it precedes any quiescing/close.
        // Same checks, same order, same error taxonomy as the pre-split provider methods.
        val source: File = when (operation) {
            is ReplacementOperation.RestoreFromSnapshot -> {
                val validation = provider.validateSnapshotForRestore(operation.source)
                if (validation is BackupResult.Failure) {
                    return ReplacementOutcome.RejectedBeforeMutation(validation.error)
                }
                operation.source
            }

            ReplacementOperation.RollbackToPreRestoreBackup ->
                provider.getPreRestoreBackupFile() ?: return ReplacementOutcome.RejectedBeforeMutation(
                    BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
                )
        }
        // Pre-mutation persistence (e.g. restore's `restore_in_progress` marker) runs INSIDE the
        // transaction, under the mutex — no other transition's preflight can interleave between
        // the marker write and the swap (the spurious-Scenario-1 window is closed by ordering).
        runCatching { hooks.beforeMutation() }.onFailure { error ->
            return ReplacementOutcome.RejectedBeforeMutation(
                BackupError.Io(IOException("pre-mutation persistence failed: $error")),
            )
        }
        val consumeSource = operation is ReplacementOperation.RollbackToPreRestoreBackup
        val outcome = when (replacementPolicy) {
            ReplacementPolicy.RestartProcess ->
                runRestartProcessSwap(closeDatabase, outgoing, provider, source, consumeSource, tracker)

            ReplacementPolicy.RebuildInProcess ->
                executeRebuildTransaction(outgoing, provider, source, consumeSource, tracker)
        }
        if (outcome is ReplacementOutcome.Completed) {
            runCommittedHook(hooks.onCommitted)
        }
        return outcome
    }

    /** Post-commit effects run on the TRANSACTION's coroutine; failures are contained (KDoc). */
    private suspend fun runCommittedHook(onCommitted: suspend () -> Unit) {
        runCatching { onCommitted() }
            .onFailure { error -> logger.e(error, "post-commit hook failed (contained)") }
    }

    /**
     * The full in-process machine: Running → Quiescing (STRICT: every fallible step precedes the
     * point of no return; ANY quiesce failure aborts without closing) → close → ReplacingFile →
     * BuildingGeneration → Preflight → Publishing, with the locked post-close failure ladder.
     */
    @Suppress("ReturnCount", "LongMethod")
    private suspend fun executeRebuildTransaction(
        outgoing: RuntimeGeneration,
        provider: DatabaseSnapshotProvider,
        source: File,
        consumeSource: Boolean,
        tracker: PonrTracker,
    ): ReplacementOutcome {
        // ---- Quiescing: the outgoing generation stays INTACT through every abortable step ----
        publishTransitioning()
        val uiDetached = withTimeoutOrNull(policy.uiDisposalTimeoutMillis) {
            awaitUiDetached(outgoing.id)
        }
        if (uiDetached == null) {
            return unwindQuiesce(outgoing, "ui region did not dispose in time")
        }
        closeAdmission()
        if (!awaitLeasesReleased()) {
            return unwindQuiesce(outgoing, "worker lease drain timed out")
        }
        withTimeoutOrNull(policy.drainTimeoutMillis) { policy.drainSnackbarResolves() }
            ?: return unwindQuiesce(outgoing, "snackbar resolve drain timed out")
        policy.pendingSnackbarCount().takeIf { it > 0 }?.let { queued ->
            logger.w { "$queued queued snackbar model(s) will cross the replacement boundary" }
        }
        // The lifetime join ends the graph-owned collectors/jobs. Cancel is required to end the
        // infinite collectors, so a join timeout leaves the generation DEGRADED (reactors dead)
        // — but per the never-close-after-failed-join rule it still ABORTS: the database is not
        // closed, the file is not touched, and the degradation is recorded in the reason.
        val lifetimeJoined = withTimeoutOrNull(policy.drainTimeoutMillis) {
            outgoing.lifetime.cancelAndJoin()
        }
        if (lifetimeJoined == null) {
            return unwindQuiesce(
                outgoing,
                "lifetime join timed out; aborted WITHOUT closing (generation degraded: " +
                    "its reactors were cancelled — a later replacement can supersede it)",
            )
        }

        // ---- Point of no return: close. Generation N is TERMINAL from here — never republished.
        val closed = runCatching { closeDatabase(outgoing.database) }
        if (closed.isFailure) {
            // Close failed → database state unknown → never rename (finding 5). Nothing on disk
            // mutated; unwind. The generation may be degraded (lifetime already ended) — loud,
            // recorded, retryable.
            return unwindQuiesce(
                outgoing,
                "database close failed (${closed.exceptionOrNull()}); aborted without renaming",
            )
        }
        tracker.crossed = true

        // ---- ReplacingFile ----
        val transaction = ReplacementTransaction(nextDbGeneration = outgoing.dbGeneration + 1)
        val replaced = provider.replaceLiveDatabaseFile(source)
        if (replaced is BackupResult.Failure) {
            // Post-close failure ladder branch (b): rollback + one fresh generation attempt.
            return recoverViaRollback(provider, transaction, replaced.error)
        }
        if (consumeSource) {
            // The primary swap of the ROLLBACK operation IS a rollback: mark it as such BEFORE
            // consuming the source (findings 5 + 6a) so a first-candidate failure takes the
            // allowed follow-up attempt over the already-rolled-back file instead of Fatal.
            transaction.rolledBack = true
            provider.deletePreRestoreBackup()
        }

        // ---- BuildingGeneration → Preflight → Publishing, with the bounded ladder ----
        attemptGeneration(transaction)?.let { return ReplacementOutcome.Completed(it) }
        return if (transaction.rolledBack) {
            attemptGeneration(transaction)?.let { ReplacementOutcome.Completed(it) }
                ?: publishFatal("post-rollback generation attempt failed")
        } else {
            recoverViaRollback(provider, transaction, cause = null)
        }
    }

    /** Ladder branch (b): rollback file mechanics, then exactly one fresh-generation attempt. */
    private suspend fun recoverViaRollback(
        provider: DatabaseSnapshotProvider,
        transaction: ReplacementTransaction,
        cause: BackupError?,
    ): ReplacementOutcome {
        val rollbackSource = provider.getPreRestoreBackupFile()
            ?: return publishFatal("replacement failed ($cause) and no pre-restore backup exists")
        val rolledBack = provider.replaceLiveDatabaseFile(rollbackSource)
        if (rolledBack is BackupResult.Failure) {
            return publishFatal("replacement failed ($cause) and rollback failed (${rolledBack.error})")
        }
        // Mark BEFORE consuming (finding 5): a crash between the two leaves a truthful flag.
        transaction.rolledBack = true
        provider.deletePreRestoreBackup()
        return attemptGeneration(transaction)
            ?.let { ReplacementOutcome.Completed(it) }
            ?: publishFatal("post-rollback generation attempt failed (original cause: $cause)")
    }

    /**
     * One BuildingGeneration → Preflight → Publishing attempt over the current live file.
     * Allocation is STAGED (finding 5): a database built but orphaned by a later stage failure
     * is closed, and a fresh lifetime is cancelled — no partial candidate leaks an open Room
     * handle over a file a later ladder step may replace.
     */
    private suspend fun attemptGeneration(
        transaction: ReplacementTransaction,
    ): RuntimeGeneration? {
        val candidate = runCatching {
            buildGeneration(
                database = dbFactory(applicationContext),
                dbGeneration = transaction.nextDbGeneration++,
                ownsDatabase = true,
            )
        }.getOrElse { error ->
            logger.e(error, "candidate generation construction failed")
            return null
        }
        transaction.candidate = candidate
        val outcome = runCatching { withContext(transaction) { preflight(candidate) } }
            .getOrElse { error ->
                logger.e(error, "candidate preflight threw")
                disposeFailedCandidate(candidate)
                return null
            }
        if (outcome != StartupOutcome.Proceed) {
            logger.w { "candidate preflight returned $outcome" }
            disposeFailedCandidate(candidate)
            return null
        }
        publishServing(candidate)
        reopenAdmission()
        return candidate
    }

    private fun unwindQuiesce(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReplacementOutcome {
        logger.w { "replacement aborted during quiesce: $reason — generation ${outgoing.id} keeps serving" }
        reopenAdmission()
        publishServing(outgoing)
        return ReplacementOutcome.RejectedBeforeMutation(BackupError.Io(IOException(reason)))
    }

    private class InFlightReplacement(
        val operation: ReplacementOperation,
        val outcome: CompletableDeferred<ReplacementOutcome>,
    )

    /** Marks a graph-only transition's preflight — a rollback inside it is rejected, not run. */
    private object GraphOnlyTransition : CoroutineContext.Element {

        override val key: CoroutineContext.Key<*> get() = Key

        object Key : CoroutineContext.Key<GraphOnlyTransition>
    }

    /**
     * STAGED construction (finding 5): the database enters first; a graphFactory failure closes
     * it (when this generation OWNS it — a graph-only candidate shares the outgoing database and
     * must never close it) and cancels the fresh lifetime before rethrowing.
     */
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
            if (ownsDatabase) runCatching { closeDatabase(database) }
            lifetime.cancel()
            throw error
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

    /** A read-only [StateFlow] view derived field-access-cheaply from one source of truth. */
    private class DerivedStateFlow<T, R>(
        private val source: StateFlow<T>,
        private val transform: (T) -> R,
    ) : StateFlow<R> {

        override val value: R get() = transform(source.value)

        override val replayCache: List<R> get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            source.collect { collector.emit(transform(it)) }
        }
    }

    private companion object {
        const val TAG = "AppRuntime"
        const val FIRST_GENERATION_ID = 1
    }
}

/** Caller-supplied transaction hooks, executed on the transaction's own coroutine (class KDoc). */
internal class ReplacementHooks(
    val beforeMutation: suspend () -> Unit = {},
    val onCommitted: suspend () -> Unit = {},
)

/** Typed result of a graph-only reinitialization. */
internal sealed interface ReinitializeOutcome {

    /** The candidate passed preflight and is the published generation. */
    data class Published(val generation: RuntimeGeneration) : ReinitializeOutcome

    /** A quiesce step or the candidate preflight failed; generation N keeps serving, intact. */
    data class Aborted(val reason: String, val serving: RuntimeGeneration) : ReinitializeOutcome

    /** The caller's expected generation was already replaced; no second transition ran. */
    data class AlreadyReplaced(val serving: RuntimeGeneration) : ReinitializeOutcome
}

/**
 * The injectable seams of one transition (grouped so [AppRuntime]'s surface stays a handful of
 * factories): the snackbar drain, the main-thread dispatcher for ViewModelStore clears, and the
 * bounded-await budgets. Worker draining is NOT here — it is the runtime's own lease admission.
 * Production wiring lives in `BaseApplication`; tests substitute deterministic drains and
 * virtual-time budgets.
 */
internal data class RuntimeTransitionPolicy(
    val drainSnackbarResolves: suspend () -> Unit = {},
    val pendingSnackbarCount: () -> Int = { 0 },
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    // The transition executor's dispatcher (the host scope every transaction runs on) —
    // virtual-time schedulable in the JVM suites, Dispatchers.Default in production.
    val hostDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val uiDisposalTimeoutMillis: Long = DEFAULT_UI_DISPOSAL_TIMEOUT_MILLIS,
    val drainTimeoutMillis: Long = DEFAULT_DRAIN_TIMEOUT_MILLIS,
) {
    private companion object {
        const val DEFAULT_UI_DISPOSAL_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_DRAIN_TIMEOUT_MILLIS = 10_000L
    }
}

/** Which replacement ending runs (spec §8.4). Android production is [RestartProcess], locked. */
internal enum class ReplacementPolicy { RestartProcess, RebuildInProcess }

/** The two file-swap operations, identity-compared for same-operation coalescing. */
internal sealed interface ReplacementOperation {

    data class RestoreFromSnapshot(val source: File) : ReplacementOperation

    data object RollbackToPreRestoreBackup : ReplacementOperation
}

/** Typed, PHASE-AWARE result of a replacement transaction (finding 4). */
internal sealed interface ReplacementOutcome {

    /**
     * The transaction committed. [generation] is the published in-process successor under
     * [ReplacementPolicy.RebuildInProcess]; `null` under [ReplacementPolicy.RestartProcess] —
     * the outgoing generation is terminal and the caller's process restart follows, as today.
     */
    data class Completed(val generation: RuntimeGeneration?) : ReplacementOutcome

    /** Nothing was mutated — the outgoing generation keeps serving; pre-swap cleanup is safe. */
    data class RejectedBeforeMutation(val error: BackupError) : ReplacementOutcome

    /**
     * The point of no return was crossed (close and/or file mutation). Recovery assets and
     * markers belong to the RUNTIME's ladder and the persisted-state protocol from here —
     * callers must never clean them on this outcome.
     */
    data class FailedAfterMutation(val error: BackupError) : ReplacementOutcome

    /** Both construction and rollback recovery failed after close — no generation serving. */
    data object Fatal : ReplacementOutcome
}

/**
 * The generation graph constructor: `buildAppGraph`'s shape — every `create()` root threaded by
 * the runtime, including the runtime itself as the [DatabaseReplacement] bound instance.
 */
internal typealias GraphFactory =
    (Context, AppDatabase, ImageStorage, AppScopeLifetime, DatabaseReplacement) -> AppGraph
