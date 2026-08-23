// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.database.AppDatabase
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The QUIESCENCE half of a generation transition (Phase 5, spec §8.4): the three admission
 * barriers a transition must close before anything irreversible, the outgoing generation's
 * teardown, and the single candidate-teardown path. Extracted from [AppRuntime] because it is a
 * cohesive concern with one job — deciding when no old-generation work can still touch the
 * database — while the runtime keeps everything that PUBLISHES state (phases, Fatal, the
 * generation sequence).
 *
 * Every method here is TOTAL: it reports a reason instead of throwing, because its callers are
 * the transaction machines, which must resolve every path to a published state.
 */
internal class GenerationQuiescer(
    private val uiGate: UiAdmissionGate,
    private val workerGate: WorkerAdmissionGate,
    private val policy: RuntimeTransitionPolicy,
    private val closeDatabase: (AppDatabase) -> Unit,
    private val logger: Logger,
) {

    /**
     * The ABORTABLE quiesce (spec §8.4 steps 1–3): every step is REVERSIBLE — nothing of the
     * outgoing generation is torn down. Returns the abort reason, or null when quiesced.
     */
    suspend fun quiesce(outgoing: RuntimeGeneration): String? {
        // Step 1: UI regions — the retire CAS closes admission for the outgoing id atomically
        // with the observation that no region holds a token, so no late admission can pass it.
        if (!uiGate.awaitRetired(outgoing.id, policy.uiDisposalTimeoutMillis)) {
            return "ui region did not dispose in time"
        }
        // Step 2: worker leases — close admission, await previously admitted runs.
        workerGate.close()
        if (!workerGate.awaitDrained(policy.drainTimeoutMillis)) {
            return "worker lease drain timed out"
        }
        // Step 3: in-flight snackbar routings (deferred-delete commits) — awaited AND fenced in
        // one atomic step, so no new routing can start behind the zero observation.
        val fenced = withTimeoutOrNull(policy.drainTimeoutMillis) { policy.fenceSnackbarResolves() }
        if (fenced == null) {
            policy.unfenceSnackbarResolves()
            return "snackbar resolve drain timed out"
        }
        policy.pendingSnackbarCount().takeIf { it > 0 }?.let { queued ->
            logger.w {
                "$queued queued snackbar model(s) held at the generation boundary — " +
                    "discarded on commit, preserved on abort"
            }
        }
        return null
    }

    /** Reopens every barrier — the ABORT path, and the tail of a committed handover. */
    fun reopen(outgoingId: Int) {
        workerGate.reopen()
        uiGate.reopen(outgoingId)
        policy.unfenceSnackbarResolves()
    }

    /**
     * Tears down the OUTGOING generation — the transition's first irreversible action, so the
     * caller crosses PONR before invoking this. Total: returns a degradation reason or null.
     * The runtime-owned ViewModelStore clears first (which now genuinely ends each Store's
     * work), then the lifetime's jobs — Store jobs included, since they are parented to it —
     * are cancelled and JOINED, so every `finally` that touches the database completes BEFORE
     * the database closes.
     */
    suspend fun tearDown(outgoing: RuntimeGeneration): String? {
        val cleared = runCatching {
            withContext(policy.mainDispatcher) { outgoing.viewModelStore.clear() }
        }
        if (cleared.isFailure) {
            return "outgoing ViewModelStore clear failed: ${cleared.exceptionOrNull()}"
        }
        val joined = withTimeoutOrNull(policy.drainTimeoutMillis) {
            outgoing.lifetime.cancelAndJoin()
        }
        if (joined == null) {
            return "outgoing lifetime did not join in time (unjoinable DB-bound job)"
        }
        return null
    }

    /**
     * THE ONE candidate teardown path, used by every candidate that must not be published
     * (preflight failure, inline-rollback invalidation, partial construction):
     *
     *  1. publication is already prevented (the caller never published this candidate);
     *  2. clear its ViewModel ownership;
     *  3. cancel AND bounded-JOIN its lifetime — a candidate's own jobs can hold the candidate
     *     database, so their `finally` blocks must finish first;
     *  4. only then close the database;
     *  5. any failure returns false → the caller stops the ladder (Fatal) and performs no later
     *     rename, because an unjoined job or an unknown-state handle may still hold the file.
     */
    suspend fun tearDownCandidate(
        candidate: RuntimeGeneration,
        closeCandidateDatabase: Boolean,
    ): Boolean {
        val cleared = runCatching {
            withContext(policy.mainDispatcher) { candidate.viewModelStore.clear() }
        }
        if (cleared.isFailure) {
            logger.e(
                cleared.exceptionOrNull() ?: IllegalStateException("candidate VM clear failed"),
                "candidate ViewModelStore clear failed — the ladder must stop",
            )
            return false
        }
        val joined = withTimeoutOrNull(policy.drainTimeoutMillis) {
            candidate.lifetime.cancelAndJoin()
        }
        if (joined == null) {
            logger.e(
                IllegalStateException("candidate lifetime did not join"),
                "candidate jobs unjoinable — the candidate database cannot be closed safely",
            )
            return false
        }
        if (!closeCandidateDatabase) return true
        // close() is idempotent — safe even when the inline rollback already closed it.
        return runCatching { closeDatabase(candidate.database) }
            .onFailure { logger.e(it, "candidate database close failed — ladder must stop") }
            .isSuccess
    }
}
