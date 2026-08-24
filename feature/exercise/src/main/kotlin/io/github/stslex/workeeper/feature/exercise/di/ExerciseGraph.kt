// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/exercise's Metro graph, a contributed [GraphExtension] of [ExerciseScope]: one extension
 * per navigation entry, parameterised by its `Screen.Exercise` bound instance. The three accessors
 * below have no production consumer — `ExerciseExtensionIdentityTest` in `:app` reads them.
 */
@GraphExtension(ExerciseScope::class)
interface ExerciseGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val exerciseStore: ExerciseStoreImpl

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val ExerciseInteractorImpl.bindInteractor: ExerciseInteractor

    @Binds
    val ExerciseHandlerStoreImpl.bindHandlerStore: ExerciseHandlerStore

    /** GUARD: the creator name must be unique — every factory is merged into AppGraph. */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createExerciseGraph(@Provides screen: Screen.Exercise): ExerciseGraph
    }
}
