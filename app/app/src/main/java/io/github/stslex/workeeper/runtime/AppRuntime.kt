// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModelStore
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.data.database.AppDatabase
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
import java.util.concurrent.atomic.AtomicInteger

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
    private val graphFactory: (Context, AppDatabase, ImageStorage, AppScopeLifetime) -> AppGraph,
    private val preflight: suspend (RuntimeGeneration) -> StartupOutcome,
    private val policy: RuntimeTransitionPolicy = RuntimeTransitionPolicy(),
) : AppReinitializationHost {

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

    private fun buildGeneration(database: AppDatabase, dbGeneration: Int): RuntimeGeneration {
        val id = nextGenerationId.getAndIncrement()
        val lifetime = AppScopeLifetime()
        return RuntimeGeneration(
            id = id,
            dbGeneration = dbGeneration,
            database = database,
            graph = graphFactory(applicationContext, database, imageStorage, lifetime),
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
