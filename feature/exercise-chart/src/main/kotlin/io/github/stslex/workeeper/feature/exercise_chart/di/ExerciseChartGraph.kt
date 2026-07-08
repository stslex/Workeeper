// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractorImpl
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/exercise-chart (KMP C.1 wave 3). Scoped to
 * [ExerciseChartScope]. ASSISTED Store (`Screen.ExerciseChart` route arg): the graph exposes the
 * assisted [ExerciseChartStoreImpl.Factory] — never the Store. 8 app-scoped `@Provides` bound
 * instances; two `@Binds` migrate from the deleted module. `@DefaultDispatcher` stays QUALIFIED
 * (`includeJavax`). No Context.
 */
@DependencyGraph(scope = ExerciseChartScope::class)
internal interface ExerciseChartGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: ExerciseChartStoreImpl.Factory

    @Binds
    val ExerciseChartInteractorImpl.bindInteractor: ExerciseChartInteractor

    @Binds
    val ExerciseChartHandlerStoreImpl.bindHandlerStore: ExerciseChartHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides exerciseRepository: ExerciseRepository,
            @Provides sessionRepository: SessionRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): ExerciseChartGraph
    }
}
