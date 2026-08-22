// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.di.AppGraph

/**
 * ONE runtime generation — the R2 replacement invariant's coherent handover unit
 * (`kmp-phase-5-startup-processor.md` §0/§8.1): *"No database-bound object may outlive its
 * RuntimeGeneration, and the database, Metro graph, lifetime, ViewModel/navigation ownership,
 * and generation ID must be handed over as one coherent unit."*
 *
 * Immutable by construction; [AppRuntime] publishes whole values atomically, so every reader
 * observes generation N or N+1, never a mixture.
 *
 * [dbGeneration] vs [id]: graph-only reinitialization hands the SAME [database] object into the
 * next generation (R2: "normal graph-only lifecycle work continues reusing the current database
 * instance") — [id] increments, [dbGeneration] does not. A file-swap replacement increments both
 * and carries a freshly built [database] (Room 3 `close()` is terminal for the object — §7.1,
 * measured on device).
 *
 * Implements [ViewModelStoreOwner] so the UI layer can provide it as the root
 * `LocalViewModelStoreOwner`: the store survives Activity recreation (this object is
 * runtime-held) and dies deterministically at generation disposal (`viewModelStore.clear()`),
 * which is what keeps `AppRootViewModel` / the app-dialog Store from crossing generations.
 */
internal class RuntimeGeneration(
    val id: Int,
    val dbGeneration: Int,
    val database: AppDatabase,
    val graph: AppGraph,
    val lifetime: AppScopeLifetime,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner

/**
 * What the runtime is doing right now. [Serving] carries the one published generation;
 * [Transitioning] is the window in which NO generation is published to new UI work — the UI
 * composes a neutral interstitial, and seam reads that still hold the outgoing generation get
 * loud, bounded failures if that generation is terminal (§9).
 */
internal sealed interface RuntimePhase {

    data class Serving(val generation: RuntimeGeneration) : RuntimePhase

    data object Transitioning : RuntimePhase

    /**
     * The explicit terminal state: after the point of no return, both generation construction
     * and rollback recovery failed. No generation is serving; `AppRuntime.currentGeneration`
     * and lease acquisition THROW (a closed generation is never exposed through the holders),
     * and nothing converts Fatal back to Serving. Surfacing recovery UI for this state is the
     * calling host's wiring (Phase 7 / instrumentation).
     */
    data object Fatal : RuntimePhase
}
