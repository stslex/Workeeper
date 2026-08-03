// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.HomeInteractorImpl
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl

/**
 * feature/home's Metro graph as a CONTRIBUTED [GraphExtension] of [HomeScope]. The factory carries
 * `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and inherits
 * ALL of its app-scoped bindings — the 9 formerly hand-threaded bound-instance `@Provides` are gone and
 * `createHomeGraph()` takes no arguments. The two `@Binds` (interactor, handler store) stay.
 *
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [HomeScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(HomeScope::class)
interface HomeGraph {

    /** Root accessor: the retained Store (plain, non-assisted). */
    val homeStore: HomeStoreImpl

    @Binds
    val HomeInteractorImpl.bindInteractor: HomeInteractor

    @Binds
    val HomeHandlerStoreImpl.bindHandlerStore: HomeHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createHomeGraph(): HomeGraph
    }
}
