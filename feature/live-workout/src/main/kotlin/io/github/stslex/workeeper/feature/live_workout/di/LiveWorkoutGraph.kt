// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractorImpl
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/live-workout's Metro graph, a contributed [GraphExtension] of [LiveWorkoutScope].
 * The `Screen.LiveWorkout` route arg is a factory-bound instance, not an assisted store param.
 */
@GraphExtension(LiveWorkoutScope::class)
interface LiveWorkoutGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val liveWorkoutStore: LiveWorkoutStoreImpl

    /** Observability root: the session write path this extension inherits rather than rebuilds. */
    val sessionRepository: SessionRepository

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @Binds
    val LiveWorkoutInteractorImpl.bindInteractor: LiveWorkoutInteractor

    @Binds
    val LiveWorkoutHandlerStoreImpl.bindHandlerStore: LiveWorkoutHandlerStore

    /**
     * GUARD: the creator name must be unique across contributed factories — all of them merge
     * into `AppGraph`, so two `create()` declarations collide.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createLiveWorkoutGraph(@Provides screen: Screen.LiveWorkout): LiveWorkoutGraph
    }
}
