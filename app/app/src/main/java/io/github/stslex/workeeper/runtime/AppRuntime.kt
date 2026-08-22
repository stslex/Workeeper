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
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.closeAppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The application-owned runtime host (Phase 5 R2, ownership model H1 —
 * `kmp-phase-5-startup-processor.md` §8.1). Owns everything the Metro graph cannot own about
 * itself: the process roots ([ImageStorage]; the database FACTORY — each DB generation is built
 * from it), the sequence of [RuntimeGeneration]s, and the single-flight transition machinery.
 * `BaseApplication` holds one instance for the process and answers every graph seam from
 * [currentGeneration]; the androidTest harness builds its own over test factories.
 *
 * ## Lifecycle model
 *
 * - **Generation 1** is built lazily on the first [currentGeneration] read — in production that
 *   is `BaseApplication.onCreate`'s preflight, preserving the cold-build ordering (`Room
 *   .databaseBuilder(...).build()` opens no SQLite file, so `RecoveryActivity`'s Room-free
 *   bootstrap safety holds). Per the cold-start mutex rule (spec §8.3 / review v2 condition 6)
 *   the build-and-publish completes atomically under its own monitor and holds NO suspending
 *   lock, so a cold-start Scenario-1 rollback can never deadlock against the transition mutex.
 * - **Graph-only reinitialization** ([reinitialize]) replaces graph + lifetime + ViewModel/
 *   navigation ownership and hands the SAME open [AppDatabase] into the next generation
 *   (R2's "graph-only lifecycle work reuses the current database instance"). Because the
 *   database never closes here, the quiesce order is the RELAXED one: the candidate is built
 *   and pre-flighted while the outgoing generation's reactors are still alive (overlap is
 *   harmless — each generation's observer collects its OWN graph's choice bus), so an abort at
 *   ANY point republishes a fully-serving generation N; the outgoing lifetime is
 *   cancelled-and-joined only AFTER successful publication. The STRICT order (lifetime join
 *   BEFORE close) belongs to the file-swap replacement transaction, where the closed database
 *   is the hazard (spec §8.4).
 * - **Publication** is one atomic [RuntimePhase] value write; readers observe generation N or
 *   N+1, never a mixture. During [RuntimePhase.Transitioning], [currentGeneration] keeps
 *   answering with the outgoing generation (alive and open for graph-only transitions) so
 *   non-UI seam readers never observe a gap.
 *
 * Production Android never calls [reinitialize] — restore/rollback/undo keep the process-restart
 * `AppReinitializer` (locked invariant). The callers are Android instrumentation and, in Phase 7,
 * the iOS host via [AppReinitializationHost].
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
     * above it to own this. Used only for fire-and-forget [requestReinitialize] dispatch.
     */
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Serializes every generation transition. NEVER held while building/publishing gen 1. */
    private val transitionMutex = Mutex()

    private val buildLock = Any()

    val imageStorage: ImageStorage by lazy { imageStorageFactory(applicationContext) }

    private val phasesFlow = MutableStateFlow<RuntimePhase>(RuntimePhase.Transitioning)

    /** The published phase stream (runtime-internal face). */
    val phases: StateFlow<RuntimePhase> = phasesFlow.asStateFlow()

    private val uiPhasesFlow = MutableStateFlow<AppUiPhase>(AppUiPhase.Transitioning)

    /**
     * The app:common face of [phases] — what `BaseApplication` exposes through
     * `AppUiGenerationsHolder`. Written in lockstep with [phasesFlow] by [publishPhase] (single
     * writer discipline; both writes are plain volatile stores, and the UI only ever acts on the
     * ui value, so cross-flow ordering cannot produce a mixed read).
     */
    val uiPhases: StateFlow<AppUiPhase> = uiPhasesFlow.asStateFlow()

    private fun publishPhase(phase: RuntimePhase) {
        phasesFlow.value = phase
        uiPhasesFlow.value = when (phase) {
            is RuntimePhase.Serving -> AppUiPhase.Generation(
                id = phase.generation.id,
                viewModelStoreOwner = phase.generation,
            )

            RuntimePhase.Transitioning -> AppUiPhase.Transitioning
        }
    }

    @Volatile
    private var currentOrNull: RuntimeGeneration? = null

    // Atomic because generation 1 mints under [buildLock] while transitions mint under the mutex.
    private val nextGenerationId = AtomicInteger(FIRST_GENERATION_ID)

    /** The generation id whose UI region is currently attached, if any. */
    @Volatile
    private var attachedUiGenerationId: Int? = null

    @Volatile
    private var uiDisposalSignal: CompletableDeferred<Unit>? = null

    /**
     * The one published generation; builds and publishes generation 1 on first read. Reads
     * during a transition answer with the outgoing generation (see class KDoc).
     */
    val currentGeneration: RuntimeGeneration
        get() = currentOrNull ?: synchronized(buildLock) {
            currentOrNull ?: buildGeneration(
                database = dbFactory(applicationContext),
                dbGeneration = FIRST_GENERATION_ID,
            ).also { first ->
                currentOrNull = first
                publishPhase(RuntimePhase.Serving(first))
            }
        }

    /** UI attach/dispose signals — called from `App()`'s generation region (spec §8.7). */
    fun onUiGenerationAttached(id: Int) {
        attachedUiGenerationId = id
    }

    fun onUiGenerationDisposed(id: Int) {
        if (attachedUiGenerationId == id) attachedUiGenerationId = null
        uiDisposalSignal?.takeIf { !it.isCompleted }?.complete(Unit)
    }

    override fun requestReinitialize() {
        val expected = currentOrNull
        hostScope.launch { reinitialize(expected) }
    }

    /**
     * Graph-only generation replacement. [expected] coalesces stale requests: when the published
     * generation already moved past it, the call returns [ReinitializeOutcome.AlreadyReplaced]
     * without running a second transition.
     */
    suspend fun reinitialize(expected: RuntimeGeneration? = null): ReinitializeOutcome {
        // Ensure generation 1 exists before taking the transition mutex (cold-start mutex rule).
        currentGeneration
        return transitionMutex.withLock {
            val outgoing = requireNotNull(currentOrNull) { "generation 1 must exist here" }
            if (expected != null && outgoing.id != expected.id) {
                return@withLock ReinitializeOutcome.AlreadyReplaced(outgoing)
            }
            runGraphOnlyTransition(outgoing)
        }
    }

    private suspend fun runGraphOnlyTransition(outgoing: RuntimeGeneration): ReinitializeOutcome {
        // ---- Quiescing (relaxed graph-only order; every step abortable back to `outgoing`) ----
        val uiDisposed = CompletableDeferred<Unit>()
        uiDisposalSignal = uiDisposed
        publishPhase(RuntimePhase.Transitioning)
        if (attachedUiGenerationId == outgoing.id) {
            val disposed = withTimeoutOrNull(policy.uiDisposalTimeoutMillis) { uiDisposed.await() }
            if (disposed == null) {
                return abortToServing(outgoing, reason = "ui region did not dispose in time")
            }
        }
        val workersIdle = withTimeoutOrNull(policy.drainTimeoutMillis) { policy.drainWorkers() }
        if (workersIdle == null) {
            return abortToServing(outgoing, reason = "worker drain timed out")
        }
        val resolvesIdle = withTimeoutOrNull(policy.drainTimeoutMillis) { policy.drainSnackbarResolves() }
        if (resolvesIdle == null) {
            return abortToServing(outgoing, reason = "snackbar resolve drain timed out")
        }
        policy.pendingSnackbarCount().takeIf { it > 0 }?.let { queued ->
            // Recorded, never silently dropped: queued models carry the outgoing generation's
            // closures; executing one later follows ED11's interruption semantics (spec §8.4).
            logger.w { "$queued queued snackbar model(s) will cross the generation boundary" }
        }
        withContext(policy.mainDispatcher) { outgoing.viewModelStore.clear() }

        // ---- BuildingGeneration: SAME database object, fresh graph/lifetime/VM store ----
        val candidate = buildGeneration(
            database = outgoing.database,
            dbGeneration = outgoing.dbGeneration,
        )

        // ---- Preflight: arms the candidate's reactors; outgoing reactors are still alive
        // (overlap harmless by bus identity), so a failure here can still abort to `outgoing`.
        val preflightOutcome = runCatching { preflight(candidate) }
        val proceed = preflightOutcome.getOrNull() == StartupOutcome.Proceed
        if (!proceed) {
            disposeCandidate(candidate)
            return abortToServing(
                outgoing,
                reason = "candidate preflight failed: " +
                    (preflightOutcome.exceptionOrNull()?.toString() ?: "${preflightOutcome.getOrNull()}"),
            )
        }

        // ---- Publishing: atomic handover, THEN deterministic disposal of the outgoing lifetime.
        currentOrNull = candidate
        publishPhase(RuntimePhase.Serving(candidate))
        withTimeoutOrNull(policy.drainTimeoutMillis) { outgoing.lifetime.cancelAndJoin() }
            ?: logger.w { "outgoing generation ${outgoing.id} lifetime join timed out (cancel signalled)" }
        return ReinitializeOutcome.Published(candidate)
    }

    private suspend fun abortToServing(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReinitializeOutcome {
        logger.w { "reinitialize aborted: $reason — generation ${outgoing.id} keeps serving" }
        publishPhase(RuntimePhase.Serving(outgoing))
        return ReinitializeOutcome.Aborted(reason = reason, serving = outgoing)
    }

    private suspend fun disposeCandidate(candidate: RuntimeGeneration) {
        withContext(policy.mainDispatcher) { candidate.viewModelStore.clear() }
        candidate.lifetime.cancelAndJoin()
    }

    // ------------------------------------------------------------------------------------------
    // The database replacement transaction (Phase 5 R2, spec §8.4/§8.5) — runtime-owned.
    // ------------------------------------------------------------------------------------------

    @Volatile
    private var inFlightReplacement: InFlightReplacement? = null

    override suspend fun restoreFromSnapshot(source: File): BackupResult<Unit> =
        replace(ReplacementOperation.RestoreFromSnapshot(source)).toSeamResult()

    override suspend fun rollbackToPreRestoreBackup(): BackupResult<Unit> {
        // Re-entrancy (spec §8.4, review v2 condition 4): a rollback issued from INSIDE this
        // runtime's own candidate preflight (the coordinator's Scenario-1 failure path) is the
        // current transaction's rollback branch, executed inline against the CANDIDATE — never a
        // nested transaction (the mutex is non-reentrant and this coroutine holds it).
        coroutineContext[ReplacementTransaction]?.let { transaction ->
            return performInlineRollback(transaction)
        }
        return replace(ReplacementOperation.RollbackToPreRestoreBackup).toSeamResult()
    }

    /**
     * Single-flight entry: concurrent requests for the SAME operation coalesce onto the
     * in-flight transaction's result; a DIFFERENT operation queues behind it on the mutex and
     * runs its own transaction — it never receives the other operation's result (review v2
     * condition 3).
     */
    suspend fun replace(operation: ReplacementOperation): ReplacementOutcome {
        inFlightReplacement?.takeIf { it.operation == operation }?.let { return it.outcome.await() }
        // Cold-start rule: generation 1 exists before the transition mutex is taken.
        currentGeneration
        return transitionMutex.withLock {
            val inFlight = InFlightReplacement(operation, CompletableDeferred())
            inFlightReplacement = inFlight
            try {
                runCatching { executeReplacement(operation) }
                    .getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        logger.e(error, "replacement transaction threw unexpectedly")
                        ReplacementOutcome.Fatal
                    }
                    .also(inFlight.outcome::complete)
            } finally {
                if (!inFlight.outcome.isCompleted) inFlight.outcome.complete(ReplacementOutcome.Fatal)
                inFlightReplacement = null
            }
        }
    }

    private suspend fun executeReplacement(operation: ReplacementOperation): ReplacementOutcome {
        val outgoing = requireNotNull(currentOrNull) { "generation must exist before replacement" }
        val provider = outgoing.graph.databaseSnapshotProvider
        // Running-state validation — reads the LIVE database, so it precedes any quiescing/close.
        // Same checks, same order, same error taxonomy as the pre-split provider methods.
        val source: File = when (operation) {
            is ReplacementOperation.RestoreFromSnapshot -> {
                val validation = provider.validateSnapshotForRestore(operation.source)
                if (validation is BackupResult.Failure) {
                    return ReplacementOutcome.Failed(validation.error)
                }
                operation.source
            }

            ReplacementOperation.RollbackToPreRestoreBackup ->
                provider.getPreRestoreBackupFile() ?: return ReplacementOutcome.Failed(
                    BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
                )
        }
        val consumeSource = operation is ReplacementOperation.RollbackToPreRestoreBackup
        return when (replacementPolicy) {
            ReplacementPolicy.RestartProcess ->
                executeRestartProcessSwap(outgoing, provider, source, consumeSource)

            ReplacementPolicy.RebuildInProcess ->
                executeRebuildTransaction(outgoing, provider, source, consumeSource)
        }
    }

    /**
     * The Android-production ending, byte-equivalent to the pre-split provider methods: close
     * (the generation is now terminal) + atomic file replacement, NO quiescing (process death is
     * the quiescence — the caller's restart flow follows), NO phase change (the app keeps
     * running on the loud-failing closed database until the restart lands, exactly as today —
     * review v2 condition 5a). Deliberately startable from an already-terminal generation:
     * the undo IoFailure re-tap re-runs the idempotent close + rename (condition 5b).
     */
    private suspend fun executeRestartProcessSwap(
        outgoing: RuntimeGeneration,
        provider: DatabaseSnapshotProvider,
        source: File,
        consumeSource: Boolean,
    ): ReplacementOutcome {
        closeDatabase(outgoing.database)
        val replaced = provider.replaceLiveDatabaseFile(source)
        if (replaced is BackupResult.Failure) {
            // Today's shipped post-close failure behavior: surface the error, no restart, no
            // rebuild — the closed database fails loud until the user acts.
            return ReplacementOutcome.Failed(replaced.error)
        }
        if (consumeSource) provider.deletePreRestoreBackup()
        return ReplacementOutcome.Completed(generation = null)
    }

    /**
     * The full in-process machine: Running → Quiescing (STRICT order — every fallible step
     * precedes the irreversible ones) → ReplacingFile → BuildingGeneration → Preflight →
     * Publishing, with the locked post-close failure ladder (spec §8.4).
     */
    @Suppress("ReturnCount")
    private suspend fun executeRebuildTransaction(
        outgoing: RuntimeGeneration,
        provider: DatabaseSnapshotProvider,
        source: File,
        consumeSource: Boolean,
    ): ReplacementOutcome {
        // ---- Quiescing (strict): abortable steps first; generation N keeps serving on abort ----
        val uiDisposed = CompletableDeferred<Unit>()
        uiDisposalSignal = uiDisposed
        publishPhase(RuntimePhase.Transitioning)
        if (attachedUiGenerationId == outgoing.id) {
            withTimeoutOrNull(policy.uiDisposalTimeoutMillis) { uiDisposed.await() }
                ?: return unwindQuiesce(outgoing, "ui region did not dispose in time")
        }
        withTimeoutOrNull(policy.drainTimeoutMillis) { policy.drainWorkers() }
            ?: return unwindQuiesce(outgoing, "worker drain timed out")
        withTimeoutOrNull(policy.drainTimeoutMillis) { policy.drainSnackbarResolves() }
            ?: return unwindQuiesce(outgoing, "snackbar resolve drain timed out")
        policy.pendingSnackbarCount().takeIf { it > 0 }?.let { queued ->
            logger.w { "$queued queued snackbar model(s) will cross the replacement boundary" }
        }
        withContext(policy.mainDispatcher) { outgoing.viewModelStore.clear() }
        // Last quiesce step; cannot fail (a bounded-join timeout is recorded and proceeds —
        // stragglers are cancelled and any post-close DB touch fails loud, §7.1's measured pin).
        withTimeoutOrNull(policy.drainTimeoutMillis) { outgoing.lifetime.cancelAndJoin() }
            ?: logger.w { "outgoing generation ${outgoing.id} lifetime join timed out (cancel signalled)" }

        // ---- Point of no return: generation N is TERMINAL from here — never republished ----
        // close() does not meaningfully throw; a throw would leave the pool state unknown, and
        // the transaction proceeds regardless — the atomic rename cannot be corrupted by it.
        runCatching { closeDatabase(outgoing.database) }
            .onFailure { logger.e(it, "close threw during replacement; proceeding") }

        // ---- ReplacingFile ----
        val transaction = ReplacementTransaction(nextDbGeneration = outgoing.dbGeneration + 1)
        val replaced = provider.replaceLiveDatabaseFile(source)
        if (replaced is BackupResult.Failure) {
            // Post-close failure ladder branch (b): rollback + one fresh generation attempt.
            return recoverViaRollback(provider, transaction, replaced.error)
        }
        if (consumeSource) provider.deletePreRestoreBackup()

        // ---- BuildingGeneration → Preflight → Publishing, with the bounded ladder ----
        attemptGeneration(transaction)?.let { return ReplacementOutcome.Completed(it) }
        // Ladder: if the failed attempt already rolled the file back (inline Scenario-1 branch),
        // one more attempt over the rolled-back file; otherwise roll back explicitly first.
        return if (transaction.rolledBack) {
            attemptGeneration(transaction)?.let { ReplacementOutcome.Completed(it) }
                ?: fatal("post-rollback generation attempt failed")
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
            ?: return fatal("replacement failed ($cause) and no pre-restore backup exists")
        val rolledBack = provider.replaceLiveDatabaseFile(rollbackSource)
        if (rolledBack is BackupResult.Failure) {
            return fatal("replacement failed ($cause) and rollback failed (${rolledBack.error})")
        }
        provider.deletePreRestoreBackup()
        transaction.rolledBack = true
        return attemptGeneration(transaction)
            ?.let { ReplacementOutcome.Completed(it) }
            ?: fatal("post-rollback generation attempt failed (original cause: $cause)")
    }

    /**
     * One BuildingGeneration → Preflight → Publishing attempt over the current live file.
     * Returns the published generation, or `null` after disposing the failed candidate. The
     * candidate preflight runs under the [ReplacementTransaction] context marker so a
     * coordinator-issued rollback lands in [performInlineRollback] (and flips
     * [ReplacementTransaction.rolledBack] for the caller's ladder).
     */
    private suspend fun attemptGeneration(
        transaction: ReplacementTransaction,
    ): RuntimeGeneration? {
        val candidate = runCatching {
            buildGeneration(
                database = dbFactory(applicationContext),
                dbGeneration = transaction.nextDbGeneration++,
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
        currentOrNull = candidate
        publishPhase(RuntimePhase.Serving(candidate))
        return candidate
    }

    /** The inline Scenario-1 rollback branch of the CURRENT transaction (see the seam method). */
    private suspend fun performInlineRollback(
        transaction: ReplacementTransaction,
    ): BackupResult<Unit> {
        val candidate = transaction.candidate
            ?: return BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "inline rollback outside a candidate preflight"),
            )
        val provider = candidate.graph.databaseSnapshotProvider
        val rollbackSource = provider.getPreRestoreBackupFile()
            ?: return BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "no pre-restore backup to roll back to"),
            )
        // The candidate's open-verification handle is the only open handle; close it (terminal)
        // before the file mechanics — the same close-then-replace shape as every other branch.
        closeDatabase(candidate.database)
        val replaced = provider.replaceLiveDatabaseFile(rollbackSource)
        if (replaced is BackupResult.Failure) return replaced
        provider.deletePreRestoreBackup()
        transaction.rolledBack = true
        return BackupResult.Success(Unit)
    }

    private suspend fun unwindQuiesce(
        outgoing: RuntimeGeneration,
        reason: String,
    ): ReplacementOutcome {
        logger.w { "replacement aborted during quiesce: $reason — generation ${outgoing.id} keeps serving" }
        publishPhase(RuntimePhase.Serving(outgoing))
        return ReplacementOutcome.Failed(BackupError.Io(IOException(reason)))
    }

    private suspend fun disposeFailedCandidate(candidate: RuntimeGeneration) {
        // close() is idempotent — safe even when the inline rollback already closed it.
        runCatching { closeDatabase(candidate.database) }
        withContext(policy.mainDispatcher) { candidate.viewModelStore.clear() }
        candidate.lifetime.cancelAndJoin()
    }

    private fun fatal(reason: String): ReplacementOutcome {
        // The explicit terminal outcome (spec §8.4): no generation is published for new UI work
        // — the phase STAYS Transitioning; the closed generation is never re-served.
        logger.e(IllegalStateException(reason), "replacement FATAL")
        return ReplacementOutcome.Fatal
    }

    private fun ReplacementOutcome.toSeamResult(): BackupResult<Unit> = when (this) {
        is ReplacementOutcome.Completed -> BackupResult.Success(Unit)
        is ReplacementOutcome.Failed -> BackupResult.Failure(error)
        ReplacementOutcome.Fatal -> BackupResult.Failure(
            BackupError.Io(IOException("replacement fatal: no generation serving; recovery required")),
        )
    }

    private class InFlightReplacement(
        val operation: ReplacementOperation,
        val outcome: CompletableDeferred<ReplacementOutcome>,
    )

    /**
     * The per-transaction CoroutineContext marker (review v2 condition 4): installed around the
     * candidate preflight so the coordinator's rollback call is detected as the current
     * transaction's rollback branch, never a nested transaction.
     */
    private class ReplacementTransaction(
        var nextDbGeneration: Int,
    ) : AbstractCoroutineContextElement(Key) {

        @Volatile
        var candidate: RuntimeGeneration? = null

        @Volatile
        var rolledBack = false

        companion object Key : CoroutineContext.Key<ReplacementTransaction>
    }

    private fun buildGeneration(database: AppDatabase, dbGeneration: Int): RuntimeGeneration {
        val id = nextGenerationId.getAndIncrement()
        val lifetime = AppScopeLifetime()
        return RuntimeGeneration(
            id = id,
            dbGeneration = dbGeneration,
            database = database,
            graph = graphFactory(applicationContext, database, imageStorage, lifetime, this),
            lifetime = lifetime,
            viewModelStore = ViewModelStore(),
        )
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

    /** A quiesce step or the candidate preflight failed; generation N keeps serving, intact. */
    data class Aborted(val reason: String, val serving: RuntimeGeneration) : ReinitializeOutcome

    /** The caller's expected generation was already replaced; no second transition ran. */
    data class AlreadyReplaced(val serving: RuntimeGeneration) : ReinitializeOutcome
}

/**
 * The injectable seams of one transition (grouped so [AppRuntime]'s surface stays a handful of
 * factories): the Quiescing drains, the main-thread dispatcher for ViewModelStore clears, and the
 * bounded-await budgets. Production wiring lives in `BaseApplication`; tests substitute
 * deterministic drains and virtual-time budgets.
 */
internal data class RuntimeTransitionPolicy(
    val drainWorkers: suspend () -> Unit = {},
    val drainSnackbarResolves: suspend () -> Unit = {},
    val pendingSnackbarCount: () -> Int = { 0 },
    val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
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

/** Typed result of a replacement transaction. */
internal sealed interface ReplacementOutcome {

    /**
     * The transaction completed. [generation] is the published in-process successor under
     * [ReplacementPolicy.RebuildInProcess]; `null` under [ReplacementPolicy.RestartProcess] —
     * the outgoing generation is terminal and the caller's process restart follows, as today.
     */
    data class Completed(val generation: RuntimeGeneration?) : ReplacementOutcome

    /** A failure with generation N still serving (pre-close), or today's production post-close shape. */
    data class Failed(val error: BackupError) : ReplacementOutcome

    /** Both construction and rollback recovery failed after close — no generation serving. */
    data object Fatal : ReplacementOutcome
}

/**
 * The generation graph constructor: `buildAppGraph`'s shape — every `create()` root threaded by
 * the runtime, including the runtime itself as the [DatabaseReplacement] bound instance.
 */
internal typealias GraphFactory =
    (Context, AppDatabase, ImageStorage, AppScopeLifetime, DatabaseReplacement) -> AppGraph
