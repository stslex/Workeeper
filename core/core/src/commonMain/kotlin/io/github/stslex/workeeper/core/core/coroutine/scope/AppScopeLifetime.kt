// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.coroutine.scope

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlin.coroutines.CoroutineContext

/**
 * The deterministic owner of every app-scoped job and collector in ONE runtime generation
 * (KMP Phase 5, `kmp-phase-5-startup-processor.md` §8.2).
 *
 * Before Phase 5 the app-scope singletons each minted an anonymous
 * `CoroutineScope(SupervisorJob() + dispatcher)` that nothing could ever cancel — five sites,
 * measured: the two `BaseApplication` startup chores, `RestoreDialogChoiceObserver`,
 * `DriveBackupAuth`, `SnapshotExportRunnerImpl`. This class replaces that pattern: one lifetime
 * enters the app graph as a `create()` bound-instance root, consumers derive their scopes from it
 * via [childScope], and the generation owner ends ALL of them with one [cancelAndJoin] during the
 * replacement state machine's Quiescing stage.
 *
 * [childScope] hands each consumer its own `SupervisorJob` **parented to [job]** — per-consumer
 * failure isolation is exactly what the old anonymous scopes had (a failed child launch never
 * cancels its siblings), while parenting is what the old scopes lacked: cancelling [job] reaches
 * every derived scope, and joining it awaits their completion, which is what makes "no
 * database-bound job outlives its generation" enforceable rather than aspirational.
 *
 * Plain class, deliberately NOT a Metro binding annotation carrier: the graph receives it as a
 * bound instance because its lifetime is decided OUTSIDE the graph (by the application/runtime
 * host that also decides the graph's own lifetime) — a graph cannot own the thing that outlives
 * or ends it.
 */
class AppScopeLifetime(parent: Job? = null) {

    /** The generation root job. Supervisor: one consumer's failure never ends the generation. */
    val job: CompletableJob = SupervisorJob(parent)

    val isActive: Boolean get() = job.isActive

    /**
     * A consumer-owned scope tied to this lifetime. Its own `SupervisorJob(job)` keeps the
     * consumer's launches isolated from siblings (same semantics as the anonymous scope it
     * replaces) while remaining reachable from [job] for cancellation and join. [context] is
     * typically just the consumer's dispatcher. The supervisor is the RIGHT operand of the
     * `plus` deliberately: `CoroutineContext.plus` lets the right side win per key, so a Job
     * smuggled in via [context] can never displace the lifetime-parented supervisor and detach
     * the scope from its generation — the exact plus-order trap `tech-debt.md` records for
     * `AppCoroutineScopeImpl`.
     */
    fun childScope(context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + SupervisorJob(job))

    /** Signals cancellation without awaiting completion. Idempotent. */
    fun cancel() {
        job.cancel()
    }

    /**
     * Ends the generation's work: cancels [job] and awaits every derived scope's launches —
     * including their `finally` blocks — before returning. The Quiescing contract
     * ("await disposal/cancellation of old-generation consumers before `close()`") is this call.
     */
    suspend fun cancelAndJoin() {
        job.cancelAndJoin()
    }
}
