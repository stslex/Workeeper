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
 * feature/home's Metro graph, a contributed [GraphExtension] of [HomeScope] merged into the app
 * graph. Interface and factory are public because `:app` generates the extension impl.
 */
@GraphExtension(HomeScope::class)
interface HomeGraph {

    /** Root accessor: the retained Store (plain, non-assisted). */
    val homeStore: HomeStoreImpl

    @Binds
    val HomeInteractorImpl.bindInteractor: HomeInteractor

    @Binds
    val HomeHandlerStoreImpl.bindHandlerStore: HomeHandlerStore

    /** GUARD: the creator name must be unique across all contributed extension factories. */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createHomeGraph(): HomeGraph
    }
}
