// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractorImpl
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/single-training's Metro graph, a contributed [GraphExtension] built once per navigation
 * entry with that entry's `Screen.Training` arg as a bound instance (shape B).
 */
@GraphExtension(SingleTrainingScope::class)
interface SingleTrainingGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val singleTrainingStore: SingleTrainingStoreImpl

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    /** Observability root: the deepest app-scoped stack this extension inherits. */
    val sessionConflictResolver: SessionConflictResolver

    @Binds
    val SingleTrainingInteractorImpl.bindInteractor: SingleTrainingInteractor

    @Binds
    val SingleTrainingHandlerStoreImpl.bindHandlerStore: SingleTrainingHandlerStore

    /** GUARD: the creator method name must be unique across all contributed factories. */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSingleTrainingGraph(@Provides screen: Screen.Training): SingleTrainingGraph
    }
}
