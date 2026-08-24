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
 * feature/archive's Metro graph, contributed as a [GraphExtension] of [ArchiveScope] and merged
 * into the app graph in `:app`, whose app-scoped bindings it inherits.
 */
@GraphExtension(ArchiveScope::class)
interface ArchiveGraph {

    /** Root accessor for the Store. Read it exactly once per created extension. */
    val archiveStore: ArchiveStoreImpl

    /** Observability accessor: the concrete handler-store key. See [handlerStoreByInterfaceKey]. */
    val handlerStoreByConcreteKey: ArchiveHandlerStoreImpl

    /**
     * Observability accessor: the interface handler-store key. Paired with the concrete one so a
     * test can assert `@SingleIn(ArchiveScope::class)` collapses both keys to one instance.
     */
    val handlerStoreByInterfaceKey: ArchiveHandlerStore

    @Binds
    val ArchiveInteractorImpl.bindInteractor: ArchiveInteractor

    @Binds
    val ArchiveHandlerStoreImpl.bindHandlerStore: ArchiveHandlerStore

    /**
     * GUARD: the creator method name must be unique across all contributed extension factories —
     * they all merge into `AppGraph`. See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createArchiveGraph(): ArchiveGraph
    }
}
