// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractorImpl
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/all-trainings (KMP C.1 wave 3). Scoped to
 * [AllTrainingsScope]. PLAIN Store (a BottomBar destination): the graph exposes the Store directly.
 * 8 app-scoped `@Provides` bound instances; two `@Binds` migrate from the deleted module.
 * `@DefaultDispatcher` stays QUALIFIED (`includeJavax`). No Context.
 */
@DependencyGraph(scope = AllTrainingsScope::class)
internal interface AllTrainingsGraph {

    /** Root accessor: the retained Store (plain, non-assisted). */
    val allTrainingsStore: AllTrainingsStoreImpl

    @Binds
    val AllTrainingsInteractorImpl.bindInteractor: AllTrainingsInteractor

    @Binds
    val AllTrainingsHandlerStoreImpl.bindHandlerStore: AllTrainingsHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides trainingRepository: TrainingRepository,
            @Provides tagRepository: TagRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): AllTrainingsGraph
    }
}
