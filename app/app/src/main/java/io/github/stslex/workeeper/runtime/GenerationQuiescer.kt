// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import androidx.lifecycle.ViewModelStore
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.database.AppDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Closes admission and tears down generations without publishing runtime state. */
internal class GenerationQuiescer(
    private val uiGate: UiAdmissionGate,
    private val workerGate: WorkerAdmissionGate,
    private val policy: RuntimeTransitionPolicy,
    private val closeDatabase: (AppDatabase) -> Unit,
    private val logger: Logger,
) {

    /** Closes reversible admission barriers; returns an abort reason or null. */
    suspend fun quiesce(outgoing: RuntimeGeneration): String? {
        // Retire UI admission atomically with the zero-token observation.
        if (!uiGate.awaitRetired(outgoing.id, policy.uiDisposalTimeoutMillis)) {
            return "ui region did not dispose in time"
        }
        // Close worker admission, then drain admitted leases.
        workerGate.close()
        if (!workerGate.awaitDrained(policy.drainTimeoutMillis)) {
            return "worker lease drain timed out"
        }
        // Drain and fence snackbar routing atomically.
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

    /** Clears the store, then cancels and joins the lifetime before database close. */
    suspend fun tearDown(outgoing: RuntimeGeneration): String? {
        if (!clearStoreBounded(outgoing.viewModelStore)) {
            return "outgoing ViewModelStore clear failed or timed out"
        }
        val joined = withTimeoutOrNull(policy.drainTimeoutMillis) {
            outgoing.lifetime.cancelAndJoin()
        }
        if (joined == null) {
            return "outgoing lifetime did not join in time (unjoinable DB-bound job)"
        }
        return null
    }

    /** Bounded detached clear prevents a wedged main dispatcher from stranding the transition. */
    private suspend fun clearStoreBounded(store: ViewModelStore): Boolean {
        val cleared = CompletableDeferred<Boolean>()
        CoroutineScope(policy.mainDispatcher + SupervisorJob()).launch {
            cleared.complete(runCatching { store.clear() }.isSuccess)
        }
        return withTimeoutOrNull(policy.drainTimeoutMillis) { cleared.await() } ?: false
    }

    /** Clears, joins, then closes an unpublished candidate; failure stops the replacement ladder. */
    suspend fun tearDownCandidate(
        candidate: RuntimeGeneration,
        closeCandidateDatabase: Boolean,
    ): Boolean {
        if (!clearStoreBounded(candidate.viewModelStore)) {
            logger.e(
                IllegalStateException("candidate VM clear failed or timed out"),
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
        // Close is idempotent, including an already-invalidated inline candidate.
        return runCatching { closeDatabase(candidate.database) }
            .onFailure { logger.e(it, "candidate database close failed — ladder must stop") }
            .isSuccess
    }
}
