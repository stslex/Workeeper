// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractorImpl
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl

/**
 * feature/archive's Metro graph as a CONTRIBUTED [GraphExtension] of [ArchiveScope]. The factory carries
 * `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and inherits
 * ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance `@Provides` are gone and
 * `createArchiveGraph()` takes no arguments. The two `@Binds` (interactor, handler store) stay.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [ArchiveScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(ArchiveScope::class)
interface ArchiveGraph {

    /** Root accessor: the retained Store. Metro constructs [ArchiveStoreImpl], wiring its deps. */
    val archiveStore: ArchiveStoreImpl

    // --- @Binds migrated from the deleted ArchiveModule (abstract property, impl → interface) ---
    @Binds
    val ArchiveInteractorImpl.bindInteractor: ArchiveInteractor

    @Binds
    val ArchiveHandlerStoreImpl.bindHandlerStore: ArchiveHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createArchiveGraph(): ArchiveGraph
    }
}
