// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.coroutine.scope

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlin.coroutines.CoroutineContext

/**
 * Deterministic owner of every app-scoped job in one runtime generation: consumers derive scopes
 * via [childScope], and [cancelAndJoin] ends all of them. See the Phase-5 startup-processor spec.
 */
class AppScopeLifetime(parent: Job? = null) {

    /** The generation root job. Supervisor: one consumer's failure never ends the generation. */
    val job: CompletableJob = SupervisorJob(parent)

    val isActive: Boolean get() = job.isActive

    /**
     * A consumer-owned scope tied to this lifetime, isolated from siblings by its own supervisor.
     * GUARD: the supervisor stays the right `plus` operand, or a Job in [context] detaches it.
     */
    fun childScope(context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + SupervisorJob(job))

    /** Signals cancellation without awaiting completion. Idempotent. */
    fun cancel() {
        job.cancel()
    }

    /** Cancels [job] and awaits every derived scope's launches, `finally` blocks included. */
    suspend fun cancelAndJoin() {
        job.cancelAndJoin()
    }
}
