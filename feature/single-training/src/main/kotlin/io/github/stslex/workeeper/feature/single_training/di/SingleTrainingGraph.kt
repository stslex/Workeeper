// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractorImpl
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/single-training (KMP C.1 wave 1) — the Metro
 * analogue of the deleted Hilt `SingleTrainingModule` + the `ViewModelComponent` tier. Scoped to
 * [SingleTrainingScope].
 *
 * ASSISTED Store: `SingleTrainingStoreImpl` takes the `Screen.Training` route arg via `@Assisted`, so
 * the graph exposes the assisted [SingleTrainingStoreImpl.Factory] as its root — NEVER the Store.
 *
 * The 13 app-scoped deps are Hilt-owned `@Singleton`s handed in as `@Provides` bound instances. The
 * two `@Binds` (SingleTrainingInteractor, SingleTrainingHandlerStore) migrate from the module.
 * `@DefaultDispatcher` + `@MainImmediateDispatcher` stay QUALIFIED (`includeJavax`) → distinct keys.
 * No Context param — this feature injects none.
 */
@DependencyGraph(scope = SingleTrainingScope::class)
internal interface SingleTrainingGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: SingleTrainingStoreImpl.Factory

    // Bridge-observability accessors (inert roots): expose the two qualified dispatchers as the graph
    // resolves them, so the real graph is self-verifying. Consumed by SingleTrainingGraphBridgeTest.
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    @Binds
    val SingleTrainingInteractorImpl.bindInteractor: SingleTrainingInteractor

    @Binds
    val SingleTrainingHandlerStoreImpl.bindHandlerStore: SingleTrainingHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides trainingRepository: TrainingRepository,
            @Provides trainingExerciseRepository: TrainingExerciseRepository,
            @Provides exerciseRepository: ExerciseRepository,
            @Provides tagRepository: TagRepository,
            @Provides sessionRepository: SessionRepository,
            @Provides sessionConflictResolver: SessionConflictResolver,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            @Provides @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
        ): SingleTrainingGraph
    }
}
