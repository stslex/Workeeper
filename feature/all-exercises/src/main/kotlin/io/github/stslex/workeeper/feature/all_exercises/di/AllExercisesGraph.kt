// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractorImpl
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl

/**
 * feature/all-exercises' Metro graph as a CONTRIBUTED [GraphExtension] of [AllExercisesScope]. The
 * factory carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in
 * `:app` and inherits ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance
 * `@Provides` are gone and `createAllExercisesGraph()` takes no arguments. The two `@Binds`
 * (interactor, handler store) stay.
 *
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [AllExercisesScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(AllExercisesScope::class)
interface AllExercisesGraph {

    /** Root accessor: the retained Store (plain, non-assisted). Metro constructs it, wiring its deps. */
    val allExercisesStore: AllExercisesStoreImpl

    @Binds
    val AllExercisesInteractorImpl.bindInteractor: AllExercisesInteractor

    @Binds
    val AllExercisesHandlerStoreImpl.bindHandlerStore: AllExercisesHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAllExercisesGraph(): AllExercisesGraph
    }
}
