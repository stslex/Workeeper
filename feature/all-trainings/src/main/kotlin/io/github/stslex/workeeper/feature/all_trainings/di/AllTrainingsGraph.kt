// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractorImpl
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl

/**
 * feature/all-trainings' Metro graph as a CONTRIBUTED [GraphExtension] of [AllTrainingsScope]. The
 * factory carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in
 * `:app` and inherits ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance
 * `@Provides` are gone (a BottomBar destination carries no screen args, so the factory has zero params).
 * The two `@Binds` (interactor, handler store) stay. Interface + factory are `public` because `:app`
 * generates the extension impl and references them; [AllTrainingsScope] may stay `internal` (Metro reads
 * the scope KClass at IR level).
 */
@GraphExtension(AllTrainingsScope::class)
interface AllTrainingsGraph {

    /** Root accessor: the retained Store (plain, non-assisted). */
    val allTrainingsStore: AllTrainingsStoreImpl

    @Binds
    val AllTrainingsInteractorImpl.bindInteractor: AllTrainingsInteractor

    @Binds
    val AllTrainingsHandlerStoreImpl.bindHandlerStore: AllTrainingsHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible") the moment a second feature is
     * ported. Binding rule for all 13 — see documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAllTrainingsGraph(): AllTrainingsGraph
    }
}
