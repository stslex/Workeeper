// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.HomeInteractorImpl
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/home (KMP C.1 wave 3). Scoped to [HomeScope].
 * PLAIN Store (a BottomBar destination): the graph exposes the Store directly. 9 app-scoped
 * `@Provides` bound instances; two `@Binds` migrate from the deleted module. `@DefaultDispatcher`
 * stays QUALIFIED (`includeJavax`). No Context.
 */
@DependencyGraph(scope = HomeScope::class)
internal interface HomeGraph {

    /** Root accessor: the retained Store (plain, non-assisted). */
    val homeStore: HomeStoreImpl

    @Binds
    val HomeInteractorImpl.bindInteractor: HomeInteractor

    @Binds
    val HomeHandlerStoreImpl.bindHandlerStore: HomeHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides trainingRepository: TrainingRepository,
            @Provides sessionRepository: SessionRepository,
            @Provides sessionConflictResolver: SessionConflictResolver,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): HomeGraph
    }
}
