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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The application-owned runtime host (Phase 5 R2, ownership model H1 —
 * `kmp-phase-5-startup-processor.md` §8.1). Owns everything the Metro graph cannot own about
 * itself: the process roots ([ImageStorage]; the database FACTORY — each DB generation is built
 * from it), the sequence of [RuntimeGeneration]s, both admission gates, and the single-flight
 * transition machinery. `BaseApplication` holds one instance for the process and answers every
 * graph seam from [currentGeneration]; the androidTest harness builds its own over test factories.
 *
 * ## ONE transaction protocol (round-2 REQUEST_CHANGES)
 *
 * Every transition is a submission-owned transaction with runtime-owned compensation:
 *
 *  - **Submission + source ownership.** Callers submit and await a [CompletableDeferred]; the
 *    body runs on [hostScope] (never cancelled). A restore's source file is STAGED into a
 *    runtime-owned copy inside the non-suspending submission frame — a cancelled caller's
 *    temp-file cleanup can never mutate a file the transaction still needs, and the runtime
 *    deletes the staged copy on every terminal outcome.
 *  - **Typed effects, exactly once.** All caller compensation is a [DatabaseReplacementEffects]
 *    object: `onBeforeMutation` runs inside the mutex before anything irreversible; exactly ONE
 *    terminal method runs per transaction (rejection / commit / recovered-by-rollback /
 *    failed-after-mutation / fatal), on the transaction's coroutine, for every outcome
 *    INCLUDING internal escapes. A failing commit-effect surfaces on
 *    [ReplacementOutcome.Completed.effectsError] — never a silently clean commit.
 *  - **PONR = the START of the first irreversible action** (outgoing teardown, close
 *    INVOCATION, file mutation). Before it, every failure unwinds to `Serving(outgoing)` with
 *    the generation fully intact. After it, the outgoing generation is NEVER republished: a
 *    throwing close is an unknown state and goes Fatal; the ladder ends in the requested
 *    commit, [ReplacementOutcome.RecoveredByRollback] (serving on PRE-operation data —
 *    restore-FAILURE semantics for callers), or the explicit terminal [RuntimePhase.Fatal].
 *  - **Result truth.** `Completed` means the REQUESTED operation committed. A restore that was
 *    rolled back (by the ladder or by the scenario-1 preflight's inline rollback) NEVER reports
 *    Completed, even when a generation was successfully published on the rolled-back data.
 *
 * ## Admission (closed barriers)
 *
 * DB-bound WorkManager work binds through [awaitBackupWorkLease] as the FIRST operation inside
 * `doWork` — the factory captures nothing, so a constructed-but-never-started worker holds no
 * lease. UI regions bind through the [UiAdmissionGate]: a transition RETIRES its outgoing id in
 * the same atomic step that observes the attachment count at zero, so a late attach can never
 * land after the zero observation — it is refused (aborts un-retire the id; commits leave it
 * retired forever). Queued snackbar models are generation-tagged at enqueue; a COMMITTED
 * handover advances the epoch (old callbacks are discarded at delivery, never executed inside
 * the successor), an aborted one preserves them.
 *
 * ## Fatal is terminal under concurrency
 *
 * Liveness is rechecked INSIDE [transitionMutex]: a transaction queued behind one that went
 * Fatal performs no validation, no close, no swap, and no publication — it completes with
 * Fatal. Every submitted deferred completes exactly once (internal [CancellationException]
 * included); nothing overwrites the Fatal publication; [currentGeneration] and lease
 * acquisition throw.
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

    /** Guards submission registration — atomic check-and-register (single-flight per op). */
    private val submissionLock = Any()

    private val uiGate = UiAdmissionGate(logger)

    private val workerGate = WorkerAdmissionGate()

    /** The quiescence/teardown half of every transition (spec §8.4) — see [GenerationQuiescer]. */
    private val quiescer = GenerationQuiescer(
        uiGate = uiGate,
        workerGate = workerGate,
        policy = policy,
        closeDatabase = closeDatabase,
        logger = logger,
    )

    val imageStorage: ImageStorage by lazy { imageStorageFactory(applicationContext) }

    // ------------------------------------------------------------------------------------------
    // Published state — ONE immutable value behind both faces.
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
        // Fatal is terminal: no transition window may ever overwrite it. Unreachable by
        // construction (liveness is rechecked under the mutex before any quiesce) — a throw
        // here resolves through the transaction's escape hatch to a Fatal outcome.
        check(!isFatal) { "a Fatal runtime must never publish Transitioning" }
        publishedFlow.value = Published(RuntimePhase.Transitioning, AppUiPhase.Transitioning)
    }

    private fun publishFatal(reason: String): ReplacementOutcome {
        logger.e(IllegalStateException(reason), "replacement FATAL")
        isFatal = true
        // The UI face stays the neutral interstitial; surfacing a recovery UI for a Fatal
        // in-process outcome is the calling HOST's wiring (Phase 7 / instrumentation).
        publishedFlow.value = Published(RuntimePhase.Fatal, AppUiPhase.Transitioning)
        // Wake any parked lease acquirer so it hits the fatal check and fails LOUD instead of
        // suspending a worker forever, and reopen the snackbar gate so the host is not left
        // silently refusing every routing.
        workerGate.reopen()
        policy.unfenceSnackbarResolves()
        return ReplacementOutcome.Fatal()
    }

    // Atomic because generation 1 mints under [buildLock] while transitions mint under the mutex.
    private val nextGenerationId = AtomicInteger(FIRST_GENERATION_ID)

    /**
     * The one published generation; builds and publishes generation 1 on first read. Reads
     * during a transition answer with the outgoing generation (alive and open for graph-only
     * transitions; terminal reads after a replacement's close fail loud — §7.1's measured pin).
     * After [RuntimePhase.Fatal] this THROWS: no closed generation is ever exposed.
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
    // Admission gate delegates (the gates themselves live in ReplacementMechanics.kt).
    // ------------------------------------------------------------------------------------------

    /**
     * Admission for a generation's UI region, requested during COMPOSITION before the region
     * resolves anything (spec §8.4 step 1). Null means the generation is retired: the region
     * must render nothing and touch no dependency.
     */
    fun admitUiGeneration(id: Int): AppUiAdmissionToken? = uiGate.admit(id)

    /** Releases exactly one admitted region; idempotent and ABA-safe. */
    fun releaseUiGeneration(token: AppUiAdmissionToken) {
        (token as? UiAdmissionGate.Token)?.let(uiGate::release)
    }

    /** Test-facing view of the UI admission gate (JVM + instrumentation assertions). */
    fun uiAttachmentCount(id: Int): Int = uiGate.admittedCount(id)

    /**
     * First-operation worker admission (spec §8.4): suspends while a transition holds admission
     * closed, then atomically binds the lease to the CURRENT generation's deps. Throws when the
     * runtime is Fatal — no work may ever bind to a closed generation.
     */
    suspend fun awaitBackupWorkLease(): BackupWorkLease = workerGate.awaitLease {
        check(!isFatal) { "runtime is Fatal — no generation may admit new work" }
        currentGeneration.graph
    }

    // ------------------------------------------------------------------------------------------
    // Graph-only transitions.
    // ------------------------------------------------------------------------------------------

    override fun requestReinitialize() {
        val expected = currentOrNull
        hostScope.launch { reinitialize(expected) }
    }

    /**
     * Graph-only generation replacement, submission-owned: the body runs on [hostScope]; a
     * cancelled caller abandons only its await; the deferred completes exactly once for EVERY
     * ending — published, aborted, coalesced, fatal, and internal escapes (a
     * [CancellationException] thrown by the candidate preflight included).
     */
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
                if (isFatal) {
                    ReinitializeOutcome.Fatal
                } else {
                    ReinitializeOutcome.Aborted("reinitialize failed: $error", currentOrNull)
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
        // New UI work sees no generation for the whole window (spec §8.4 step 1).
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason -> return abortToServing(outgoing, reason) }

        // ---- BuildingGeneration + Preflight while N is FULLY INTACT (still abortable): SAME
        // database object, fresh graph/lifetime/VM store; nothing of N has been torn down.
        val candidate = runCatching {
            buildGeneration(
                database = outgoing.database,
                dbGeneration = outgoing.dbGeneration,
                ownsDatabase = false,
            )
        }.getOrElse { error ->
            return abortToServing(outgoing, reason = "candidate construction failed: $error")
        }

        // A Scenario-1 rollback request from inside this preflight is REJECTED deterministically
        // (pre-mutation) instead of deadlocking on the non-reentrant transition mutex — the
        // coordinator keeps its persisted state intact and a later replacement transaction (or a
        // cold start) completes the rollback. A CancellationException from the preflight is an
        // internal escape like any other: the candidate is disposed and the transition aborts —
        // the caller's deferred ALWAYS resolves.
        val preflightOutcome = runCatching {
            withContext(GraphOnlyTransition) { preflight(candidate) }
        }
        if (preflightOutcome.getOrNull() != StartupOutcome.Proceed) {
            quiescer.tearDownCandidate(candidate, closeCandidateDatabase = false)
            return abortToServing(
                outgoing,
                reason = "candidate preflight failed: " +
                    (
                        preflightOutcome.exceptionOrNull()?.toString()
                            ?: "${preflightOutcome.getOrNull()}"
                        ),
            )
        }

        // ---- The committed safe boundary (PONR): N's teardown completes BEFORE N+1 is exposed
        // (spec §8.4). From here the transition never aborts back to N — a partially-disposed
        // generation is never resurrected. Teardown steps are total (non-throwing); a degraded
        // teardown is logged loudly and the healthy candidate still publishes (the SHARED
        // database was never closed).
        tracker.crossed = true
        quiescer.tearDown(outgoing)?.let { degraded ->
            logger.e(
                IllegalStateException(degraded),
                "graph-only teardown degraded post-PONR; publishing the candidate anyway",
            )
        }
        // The epoch advances BEFORE the successor is published (spec §8.4 step 3): otherwise
        // N+1's collector is live for a window in which generation N's queued models still pass
        // the delivery filter and run their callbacks against the successor.
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

    // ------------------------------------------------------------------------------------------
    // The database replacement transaction.
    // ------------------------------------------------------------------------------------------

    private val inFlightReplacements = HashMap<ReplacementOperation, InFlightReplacement>()

    override suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult =
        replace(ReplacementOperation.RestoreFromSnapshot(source), effects).toSeamResult()

    override suspend fun rollbackToPreRestoreBackup(
        sourcePath: String?,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult {
        // Re-entrancy (spec §8.4): a rollback issued from INSIDE this runtime's own candidate
        // preflight is the current transaction's rollback branch, executed inline — never a
        // nested transaction (the mutex is non-reentrant and the transaction coroutine holds
        // it). This coroutine IS the transaction, so the terminal effect runs right here.
        coroutineContext[ReplacementTransaction]?.let { transaction ->
            val outcome = runInlineRollback(closeDatabase, transaction, effects)
            // The inline branch runs inside the outer transaction (mutex held, PONR crossed).
            return runTerminalEffects(effects, outcome, ponrCrossed = true, logger = logger)
                .toSeamResult()
        }
        // A rollback during a GRAPH-ONLY transition's preflight is rejected pre-mutation
        // (deterministic; nothing on disk or in DataStore is touched) — deadlock-free by
        // construction, and the persisted Scenario-1 state stays intact for a cold start or a
        // later replacement transaction to complete.
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

    /**
     * Submission entry: registration is ATOMIC under [submissionLock] — two same-operation
     * requests arriving before either registers share one transaction and one outcome (the
     * first submitter's effects win; for the real callers a same-op race is the same semantic
     * intent, e.g. an undo re-tap). A different operation registers its own transaction, which
     * serializes behind the mutex and never receives another operation's result.
     */
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
        // Source-ownership transfer (mandate 1): staging runs in THIS non-suspending frame —
        // there is no cancellation point between registration and the transfer, so a cancelled
        // caller can never strand a half-owned file. From here the runtime owns the staged copy.
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
            // Escape hatches are deliberately Throwable-broad: every escape must resolve to a
            // published state and complete the awaiters exactly once. The terminal effect runs
            // UNDER the transition mutex on every path (seam contract): a successor transaction
            // entering the mutex must observe this one's compensation as already complete —
            // otherwise a pending terminal cleanup could erase the successor's crash-safety
            // marker or delete the shared preserved snapshot mid-swap.
            val finalOutcome = inFlight.stagingFailure?.let { stagingError ->
                val rejected = ReplacementOutcome.RejectedBeforeMutation(
                    BackupError.Io(IOException("restore source staging failed: $stagingError")),
                )
                transitionMutex.withLock {
                    runTerminalEffects(effects, rejected, tracker.crossed, logger)
                }
            } ?: runCatching {
                // Cold-start rule: generation 1 exists before the transition mutex is taken.
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
                            // hostScope is never cancelled; an internal CancellationException
                            // is a bug — resolve it like any escape, never strand awaiters.
                            logger.e(error, "replacement transaction cancelled internally")
                        }
                        resolveTransactionEscape(error, tracker)
                    }
                    runTerminalEffects(effects, outcome, tracker.crossed, logger)
                }
            }.getOrElse { error ->
                // Escapes OUTSIDE the mutex (gen-1 build): nothing was mutated; the mutex is
                // taken fresh so even this path's compensation cannot interleave a successor.
                val escaped = resolveTransactionEscape(error, tracker)
                transitionMutex.withLock {
                    runTerminalEffects(effects, escaped, tracker.crossed, logger)
                }
            }
            inFlight.stagedSource?.let { staged -> runCatching { staged.delete() } }
            // The reservation is this attempt's asset, but whenever the journal may still be
            // UNRESOLVED and naming it, it is the recovery source — not litter (R4 invariant 8).
            // That is every post-PONR-without-recovery outcome AND every outcome whose terminal
            // compensation failed: a rejection whose `resolveAttempt` threw leaves the journal
            // at `Prepared` pointing at this file exactly like a failed commit record does.
            // Only an outcome that is both terminal-clean and journal-resolving discards it.
            val keepReservation = finalOutcome is ReplacementOutcome.FailedAfterMutation ||
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

    /**
     * Deterministic resolution for an unexpected transaction escape. Fatal is terminal — an
     * escape on a Fatal runtime resolves to Fatal without touching published state. Before the
     * point of no return the outgoing generation is republished (it is intact — the abortable
     * quiesce touches nothing irreversible); after it, the state is unknown → the explicit
     * Fatal terminal, never a stranded `Transitioning`, never a resurrected generation.
     */
    private fun resolveTransactionEscape(error: Throwable, tracker: PonrTracker): ReplacementOutcome {
        logger.e(error, "replacement transaction threw unexpectedly")
        if (isFatal) return ReplacementOutcome.Fatal()
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
        // Fatal recheck INSIDE the mutex (spec §8.4): a transaction queued behind one that went
        // Fatal performs no validation, no close, no swap, no publication.
        if (isFatal) return ReplacementOutcome.Fatal()
        val outgoing = requireNotNull(currentOrNull) { "generation must exist before replacement" }
        val provider = outgoing.graph.databaseSnapshotProvider
        // Running-state validation — reads the LIVE database, so it precedes any quiescing.
        // Same checks, same order, same error taxonomy as the pre-split provider methods.
        val source: File
        val consume: SourceConsumption
        when (operation) {
            is ReplacementOperation.RestoreFromSnapshot -> {
                val staged = stagedSource ?: return ReplacementOutcome.RejectedBeforeMutation(
                    BackupError.Io(IOException("staged restore source is missing")),
                )
                val validation = provider.validateSnapshotForRestore(staged)
                if (validation is BackupResult.Failure) {
                    return ReplacementOutcome.RejectedBeforeMutation(validation.error)
                }
                source = staged
                consume = SourceConsumption.None
            }

            is ReplacementOperation.RollbackToPreRestoreBackup -> {
                val explicitPath = operation.sourcePath
                if (explicitPath != null) {
                    // A journal-named source is AUTHORITATIVE (R4 invariant 2): when it is
                    // missing, the canonical slot — which belongs to ANOTHER attempt — is never
                    // substituted for it. The typed rejection routes the recovering launch to
                    // terminal recovery instead of silently reverting onto older data.
                    val explicit = File(explicitPath)
                    if (!explicit.exists()) {
                        return ReplacementOutcome.RejectedBeforeMutation(
                            BackupError.CorruptedBackup(
                                reason = "journal-named rollback source is missing: $explicitPath",
                            ),
                        )
                    }
                    source = explicit
                    consume = SourceConsumption.ExactFile(explicit)
                } else {
                    // No explicit source: the canonical undo slot IS the requested source.
                    source = provider.getPreRestoreBackupFile()
                        ?: return ReplacementOutcome.RejectedBeforeMutation(
                            BackupError.CorruptedBackup(
                                reason = "no pre-restore backup to roll back to",
                            ),
                        )
                    consume = SourceConsumption.CanonicalSlot
                }
            }
        }
        // ROLLBACK-SLOT RESERVATION (spec §8.5a) — inside the transaction, after validation and
        // before anything irreversible, so the snapshot belongs to THIS serialized attempt: a
        // concurrent restore can neither observe a half-written slot nor overwrite this one, and
        // a rejection discards only this reservation while the previous undo slot survives.
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
        // Pre-mutation persistence: the attempt claims the durable journal slot (identity +
        // context + the reserved path) in ONE atomic write, under the mutex. A throw here — a
        // DIFFERENT unresolved attempt still owns the slot, or the write failed — rejects the
        // transaction before anything irreversible.
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

    /**
     * The full in-process machine (spec §8.4): abortable quiesce → PONR (teardown start) →
     * teardown → close → ReplacingFile → the bounded ladder. Every post-PONR failure ends in a
     * published successor ([completedOrRecovered]) or the explicit Fatal — the outgoing
     * generation is never republished once its teardown began, and a throwing close is Fatal.
     */
    private suspend fun executeRebuildTransaction(
        outgoing: RuntimeGeneration,
        mutation: MutationPlan,
        tracker: PonrTracker,
        requestedRollback: Boolean,
    ): ReplacementOutcome {
        val effects = mutation.effects
        publishTransitioning()
        quiescer.quiesce(outgoing)?.let { reason -> return unwindQuiesce(outgoing, reason) }

        // ---- PONR: the outgoing teardown BEGINS (spec §8.4). Never abortable from here. ----
        tracker.crossed = true
        quiescer.tearDown(outgoing)?.let { failure ->
            return publishFatal("outgoing teardown failed after PONR: $failure")
        }
        // Close INVOCATION is post-PONR by definition: a throw leaves the handle in an unknown
        // state — Fatal, never RejectedBeforeMutation, never a republished outgoing generation.
        val closed = runCatching { closeDatabase(outgoing.database) }
        if (closed.isFailure) {
            return publishFatal("outgoing database close failed: ${closed.exceptionOrNull()}")
        }

        // ---- ReplacingFile ----
        val transaction = ReplacementTransaction(nextDbGeneration = outgoing.dbGeneration + 1)
        val replaced = mutation.provider.replaceLiveDatabaseFile(mutation.source)
        if (replaced is BackupResult.Failure) {
            return recoverViaRollback(mutation, transaction, replaced.error, requestedRollback)
        }
        // The mutation committed: promote the reservation and record the durable commit BEFORE
        // consuming any rollback asset (spec §8.5a). A bookkeeping failure keeps every asset and
        // is carried onto the final outcome.
        val committed = commitMutation(mutation = mutation, generation = null)
        val commitEffectsError = (committed as? ReplacementOutcome.Completed)?.effectsError
        if (requestedRollback) {
            // The primary swap of the ROLLBACK operation IS the requested rollback: mark it so a
            // first-candidate failure takes the allowed follow-up attempt over the already
            // rolled-back file instead of Fatal.
            transaction.rolledBack = true
        }

        // ---- BuildingGeneration → Preflight → Publishing, with the bounded ladder ----
        return when (val attempt = attemptGeneration(transaction)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback, commitEffectsError)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released — ladder stopped")

            AttemptResult.Retryable ->
                if (transaction.rolledBack) {
                    when (val retry = attemptGeneration(transaction)) {
                        is AttemptResult.Published -> completedOrRecovered(
                            transaction,
                            retry.generation,
                            requestedRollback,
                            commitEffectsError,
                        )

                        else -> publishFatal("post-rollback generation attempt failed")
                    }
                } else {
                    recoverViaRollback(
                        mutation = mutation,
                        transaction = transaction,
                        cause = null,
                        requestedRollback = requestedRollback,
                        // A clean commit consumed the reservation BY PROTOCOL (promote → record
                        // → consume): the canonical slot is this attempt's own promoted image.
                        // A commit with failed bookkeeping KEPT the reservation — strict source.
                        afterCleanCommit = commitEffectsError == null,
                    )
                }
        }
    }

    /**
     * Result truth (mandate 3): `Completed` ONLY when the REQUESTED operation committed. A
     * restore whose data ended up rolled back — by the ladder or the preflight's inline
     * rollback — reports [ReplacementOutcome.RecoveredByRollback] even though a generation
     * published successfully: the serving data is the PRE-operation data.
     */
    private fun completedOrRecovered(
        transaction: ReplacementTransaction,
        generation: RuntimeGeneration,
        requestedRollback: Boolean,
        commitEffectsError: BackupError? = null,
    ): ReplacementOutcome = if (!requestedRollback && transaction.rolledBack) {
        ReplacementOutcome.RecoveredByRollback(
            error = transaction.rollbackCause
                ?: BackupError.Io(IOException("restore rolled back")),
            generation = generation,
            effectsError = commitEffectsError,
        )
    } else {
        ReplacementOutcome.Completed(generation, effectsError = commitEffectsError)
    }

    /**
     * Ladder branch: rollback file mechanics, then exactly one fresh-generation attempt.
     *
     * The rollback source is THIS attempt's own reservation when it has one — after a failed
     * live-file swap the reservation, not the canonical slot, holds the true pre-attempt
     * database; the canonical slot still holds the PREVIOUS restore's older snapshot, and
     * applying that would revert data the failed swap never touched. An applied CANONICAL slot
     * is consumed here; an applied RESERVATION is deliberately not — it stays the journal-named
     * recovery source until the caller's terminal effects resolve the attempt, after which the
     * submission frame discards it (a crash in between leaves the journal still pointing at a
     * file that exists).
     *
     * The caller's effects are deliberately NOT used for the commit bookkeeping: what committed
     * here is the ROLLBACK, not the requested operation, so recording the caller's attempt as
     * `Committed` would let the very next preflight read it as a successful restore and publish
     * `RestoreSuccess` for data that was rolled back. The caller learns the truth from the
     * `RecoveredByRollback` outcome and compensates in `onRecoveredByRollback`.
     */
    private suspend fun recoverViaRollback(
        mutation: MutationPlan,
        transaction: ReplacementTransaction,
        cause: BackupError?,
        requestedRollback: Boolean,
        afterCleanCommit: Boolean = false,
    ): ReplacementOutcome {
        val provider = mutation.provider
        // Source-owner identity (R4 invariant 2): the recovery source is the attempt's OWN
        // asset, never a substitute belonging to another attempt.
        //  - A restore attempt recovering BEFORE its commit sequence completed rolls back onto
        //    its reservation; if that vanished mid-transaction, the canonical slot — another
        //    attempt's OLDER snapshot — is never applied in its place: the ladder stops instead
        //    of silently reverting data this attempt never touched. The reservation is NOT
        //    consumed here: it stays the journal-named recovery source until the caller's
        //    terminal effects resolve the attempt (the submission frame then discards it; a
        //    crash first leaves the journal still pointing at it).
        //  - AFTER a clean commit ([afterCleanCommit]: promote → durable record → reservation
        //    consumed, in that order), the canonical slot provably holds THIS attempt's
        //    promoted pre-image — the same ordering proof the Committed cold-start rule rests
        //    on — so it is the attempt's own recovery source, not a substitute.
        //  - A canonical-slot rollback retries its own source once (the file is intact after a
        //    failed copy/rename; the failure may be transient).
        //  - An EXPLICIT-source rollback whose swap failed gets no substitute at all — applying
        //    the canonical here would be exactly the cross-owner substitution invariant 2 bans.
        val rollbackSource: File
        val rollbackConsume: SourceConsumption
        if (mutation.reservation != null && !afterCleanCommit) {
            if (!mutation.reservation.exists()) {
                return publishFatal(
                    "replacement failed ($cause) and this attempt's reservation vanished",
                )
            }
            rollbackSource = mutation.reservation
            rollbackConsume = SourceConsumption.None
        } else if (mutation.reservation != null) {
            rollbackSource = provider.getPreRestoreBackupFile()
                ?: return publishFatal(
                    "post-commit recovery found no promoted undo slot ($cause)",
                )
            rollbackConsume = SourceConsumption.CanonicalSlot
        } else {
            when (mutation.consume) {
                is SourceConsumption.ExactFile -> return publishFatal(
                    "the journal-named rollback source could not be applied ($cause) — " +
                        "the canonical slot belongs to another attempt and is never substituted",
                )

                SourceConsumption.CanonicalSlot, SourceConsumption.None -> {
                    rollbackSource = provider.getPreRestoreBackupFile()
                        ?: return publishFatal(
                            "replacement failed ($cause) and no pre-restore backup exists",
                        )
                    rollbackConsume = SourceConsumption.CanonicalSlot
                }
            }
        }
        val rolledBack = provider.replaceLiveDatabaseFile(rollbackSource)
        if (rolledBack is BackupResult.Failure) {
            return publishFatal("replacement failed ($cause) and rollback failed (${rolledBack.error})")
        }
        // Mark BEFORE consuming: a crash between the two leaves a truthful flag.
        transaction.rolledBack = true
        transaction.rollbackCause = cause
            ?: BackupError.Io(IOException("restore could not complete; rolled back"))
        // Consume ONLY the exact file that was applied (R4 invariant 3).
        commitMutation(
            mutation = MutationPlan(
                provider = provider,
                source = rollbackSource,
                consume = rollbackConsume,
                effects = DatabaseReplacementEffects.None,
                reservation = null,
            ),
            generation = null,
        )
        return when (val attempt = attemptGeneration(transaction)) {
            is AttemptResult.Published ->
                completedOrRecovered(transaction, attempt.generation, requestedRollback)

            AttemptResult.LadderFatal ->
                publishFatal("candidate resources could not be released after rollback")

            AttemptResult.Retryable ->
                publishFatal("post-rollback generation attempt failed (original cause: $cause)")
        }
    }

    /**
     * One BuildingGeneration → Preflight → Publishing attempt over the current live file.
     * Allocation is STAGED: a database built but orphaned by a later stage failure is closed
     * (jobs joined first), and a close that THROWS stops the ladder ([AttemptResult.LadderFatal])
     * — no partial candidate ever leaks an open handle over a file a later step would rename.
     */
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
        val outcome = runCatching { withContext(transaction) { preflight(candidate) } }
        if (transaction.closeFailed) {
            // The inline rollback's candidate close failed: unknown handle state — stop the
            // ladder without touching the candidate again, never rename again.
            return AttemptResult.LadderFatal
        }
        val proceed = outcome.getOrNull() == StartupOutcome.Proceed && !transaction.candidateInvalidated
        if (!proceed) {
            logger.w { "candidate preflight did not proceed: $outcome" }
            return if (quiescer.tearDownCandidate(candidate, closeCandidateDatabase = true)) {
                AttemptResult.Retryable
            } else {
                AttemptResult.LadderFatal
            }
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

    private class InFlightReplacement(
        val operation: ReplacementOperation,
        val outcome: CompletableDeferred<ReplacementOutcome>,
    ) {
        /** The runtime-owned staged restore source; deleted on every terminal outcome. */
        @Volatile
        var stagedSource: File? = null

        /** A staging failure recorded in the submission frame; resolved to a rejection. */
        @Volatile
        var stagingFailure: Throwable? = null

        /** The rollback snapshot this attempt reserved inside the transaction (spec §8.5a). */
        @Volatile
        var reservation: File? = null
    }

    /** Marks a graph-only transition's preflight — a rollback inside it is rejected, not run. */
    private object GraphOnlyTransition : CoroutineContext.Element {

        override val key: CoroutineContext.Key<*> get() = Key

        object Key : CoroutineContext.Key<GraphOnlyTransition>
    }

    /**
     * STAGED construction: the database enters first; a graphFactory failure closes it (when
     * this generation OWNS it — a graph-only candidate shares the outgoing database and must
     * never close it) and cancels the fresh lifetime before rethrowing. An orphan close that
     * ITSELF throws escalates to [OrphanCloseException] — the ladder must stop (Fatal).
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
            // A partially constructed graph may already have handed the lifetime to consumers
            // that started jobs, so the orphan database is closed only after those jobs are
            // JOINED (spec §8.4). Non-suspend by necessity here — generation 1 is built from the
            // `currentGeneration` getter — so the join runs on the caller's thread with the same
            // bounded budget the candidate paths use; a failure to join or close escalates to
            // [OrphanCloseException], which stops the ladder instead of renaming over a file an
            // unknown-state handle may still hold.
            throw releasePartialGeneration(lifetime, database, ownsDatabase, error)
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

    /**
     * Releases a generation whose graph construction failed, and returns the exception to throw:
     * the orphan database is closed only AFTER its lifetime is cancelled and JOINED, because a
     * partially constructed graph may already have handed that lifetime to consumers that
     * started jobs. A failed join or close escalates to [OrphanCloseException], which stops the
     * ladder rather than renaming over a file an unknown-state handle may still hold.
     */
    private fun releasePartialGeneration(
        lifetime: AppScopeLifetime,
        database: AppDatabase,
        ownsDatabase: Boolean,
        cause: Throwable,
    ): Throwable {
        val joined: Unit? = runCatching {
            runBlocking {
                withTimeoutOrNull(policy.drainTimeoutMillis) { lifetime.cancelAndJoin() }
            }
        }.getOrNull()
        if (!ownsDatabase) return cause
        if (joined == null) return OrphanCloseException(cause)
        return runCatching { closeDatabase(database) }
            .fold(onSuccess = { cause }, onFailure = { OrphanCloseException(it) })
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

/** Typed result of a graph-only reinitialization. */
internal sealed interface ReinitializeOutcome {

    /** The candidate passed preflight and is the published generation. */
    data class Published(val generation: RuntimeGeneration) : ReinitializeOutcome

    /**
     * A pre-PONR step failed; generation N keeps serving, intact. [serving] is null only for
     * the cold-start corner where no generation could be built at all.
     */
    data class Aborted(val reason: String, val serving: RuntimeGeneration?) : ReinitializeOutcome

    /** The caller's expected generation was already replaced; no second transition ran. */
    data class AlreadyReplaced(val serving: RuntimeGeneration) : ReinitializeOutcome

    /** The runtime is (or became) Fatal — the request performed nothing further. */
    data object Fatal : ReinitializeOutcome
}

/**
 * The injectable seams of one transition (grouped so [AppRuntime]'s surface stays a handful of
 * factories): the snackbar drain + epoch advance, the main-thread dispatcher for ViewModelStore
 * clears, the staging directory for restore-source ownership transfer, and the bounded-await
 * budgets. Production wiring lives in `BaseApplication`; tests substitute deterministic seams.
 */
internal data class RuntimeTransitionPolicy(
    /**
     * Atomically awaits "no snackbar routing in flight" AND closes admission for new ones
     * (spec §8.4 step 3). Every non-committing path must call [unfenceSnackbarResolves].
     */
    val fenceSnackbarResolves: suspend () -> Unit = {},
    val unfenceSnackbarResolves: () -> Unit = {},
    val pendingSnackbarCount: () -> Int = { 0 },
    /**
     * Called ONLY after a COMMITTED handover and BEFORE the successor is published — never on
     * abort (spec §8.4 step 3).
     */
    val advanceSnackbarGeneration: () -> Unit = {},
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    // The transition executor's dispatcher (the host scope every transaction runs on) —
    // virtual-time schedulable in the JVM suites, Dispatchers.Default in production.
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

/** Which replacement ending runs (spec §8.4). Android production is [RestartProcess], locked. */
internal enum class ReplacementPolicy { RestartProcess, RebuildInProcess }

/** The two file-swap operations, identity-compared for same-operation coalescing. */
internal sealed interface ReplacementOperation {

    /** Identity is the ORIGINAL source path — staging happens after coalescing. */
    data class RestoreFromSnapshot(val sourcePath: String) : ReplacementOperation {
        constructor(source: File) : this(source.absolutePath)
    }

    /**
     * [sourcePath] names the rollback snapshot explicitly — the durable journal's reservation
     * when a recovering launch knows which file holds the true pre-attempt database. `null`
     * uses the canonical undo slot. Part of the operation IDENTITY, so a recovery rollback and
     * a user undo of different files are different operations.
     */
    data class RollbackToPreRestoreBackup(
        val sourcePath: String? = null,
    ) : ReplacementOperation
}

/** Typed, phase-aware result of a replacement transaction (mandates 3 + 4). */
internal sealed interface ReplacementOutcome {

    /**
     * The REQUESTED operation committed. [generation] is the published in-process successor
     * under [ReplacementPolicy.RebuildInProcess]; `null` under [ReplacementPolicy
     * .RestartProcess] — the outgoing generation is terminal and the caller's process restart
     * follows, as today. [effectsError] is non-null when the caller's committed-effects failed
     * — surfaced, never swallowed into a clean commit.
     */
    data class Completed(
        val generation: RuntimeGeneration?,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /** Nothing irreversible happened — the outgoing generation keeps serving, fully intact. */
    data class RejectedBeforeMutation(
        val error: BackupError,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /**
     * The requested RESTORE failed after PONR and the bounded recovery rolled back: a successor
     * generation is SERVING on the PRE-operation data. Restore-FAILURE semantics for callers.
     */
    data class RecoveredByRollback(
        val error: BackupError,
        val generation: RuntimeGeneration,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /**
     * The point of no return was crossed and the transaction could not complete; no in-process
     * recovery ran (the RestartProcess ending). Recovery assets and markers are PRESERVED and
     * belong to the runtime/journal protocol — callers must never clean them on this outcome.
     */
    data class FailedAfterMutation(
        val error: BackupError,
        val effectsError: BackupError? = null,
    ) : ReplacementOutcome

    /** The runtime is terminal — no generation serving, nothing further performed. */
    data class Fatal(val effectsError: BackupError? = null) : ReplacementOutcome
}

/**
 * The generation graph constructor: `buildAppGraph`'s shape — every `create()` root threaded by
 * the runtime, including the runtime itself as the [DatabaseReplacement] bound instance.
 */
internal typealias GraphFactory =
    (Context, AppDatabase, ImageStorage, AppScopeLifetime, DatabaseReplacement) -> AppGraph
