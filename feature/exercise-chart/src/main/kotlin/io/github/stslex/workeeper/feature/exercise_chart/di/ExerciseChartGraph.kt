// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractorImpl
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/exercise-chart's Metro graph, a contributed [GraphExtension] of [ExerciseChartScope];
 * the nullable `Screen.ExerciseChart` route arg enters as a bound instance on the factory.
 */
@GraphExtension(ExerciseChartScope::class)
interface ExerciseChartGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val exerciseChartStore: ExerciseChartStoreImpl

    /** Observability root: `:app`'s identity test asserts Default and IO stay distinct keys. */
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @Binds
    val ExerciseChartInteractorImpl.bindInteractor: ExerciseChartInteractor

    @Binds
    val ExerciseChartHandlerStoreImpl.bindHandlerStore: ExerciseChartHandlerStore

    /**
     * The creator method name must be UNIQUE across contributed extension factories — they all
     * merge into `AppGraph`. See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createExerciseChartGraph(@Provides screen: Screen.ExerciseChart): ExerciseChartGraph
    }
}
