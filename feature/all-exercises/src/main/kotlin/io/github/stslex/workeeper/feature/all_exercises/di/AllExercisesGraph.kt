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
 * feature/all-exercises' Metro graph: a contributed [GraphExtension] of [AllExercisesScope] merged
 * into the app graph. Interface and factory are public because `:app` generates the impl.
 */
@GraphExtension(AllExercisesScope::class)
interface AllExercisesGraph {

    /** Root accessor: the retained Store. Metro constructs it and wires its deps. */
    val allExercisesStore: AllExercisesStoreImpl

    @Binds
    val AllExercisesInteractorImpl.bindInteractor: AllExercisesInteractor

    @Binds
    val AllExercisesHandlerStoreImpl.bindHandlerStore: AllExercisesHandlerStore

    /**
     * GUARD: the creator method name must be unique across all contributed extension factories —
     * they all merge into `AppGraph`, and two `create()` declarations collide.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAllExercisesGraph(): AllExercisesGraph
    }
}
