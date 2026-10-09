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
 * feature/all-trainings' Metro graph as a contributed [GraphExtension] of [AllTrainingsScope],
 * merged into the app graph in `:app` and inheriting its app-scoped bindings.
 */
@GraphExtension(AllTrainingsScope::class)
interface AllTrainingsGraph {

    /** Root accessor: the retained Store. */
    val allTrainingsStore: AllTrainingsStoreImpl

    @Binds
    val AllTrainingsInteractorImpl.bindInteractor: AllTrainingsInteractor

    @Binds
    val AllTrainingsHandlerStoreImpl.bindHandlerStore: AllTrainingsHandlerStore

    /**
     * GUARD: the creator name must be unique across all contributed extension factories.
     * See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAllTrainingsGraph(): AllTrainingsGraph
    }
}
