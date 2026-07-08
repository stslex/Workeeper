// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractorImpl
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/all-exercises (KMP C.1 wave 2) — the Metro analogue
 * of the deleted Hilt `AllExercisesModule` + the `ViewModelComponent` tier. Scoped to
 * [AllExercisesScope].
 *
 * PLAIN Store (not assisted — a BottomBar destination with no route args): the graph exposes the
 * Store directly as [allExercisesStore]. The 8 app-scoped deps are Hilt-owned `@Singleton`s handed
 * in as `@Provides` bound instances; the two `@Binds` (AllExercisesInteractor, AllExercisesHandlerStore)
 * migrate from the module. `@DefaultDispatcher` stays QUALIFIED (`includeJavax`). No Context.
 */
@DependencyGraph(scope = AllExercisesScope::class)
internal interface AllExercisesGraph {

    /** Root accessor: the retained Store (plain, non-assisted). Metro constructs it, wiring its deps. */
    val allExercisesStore: AllExercisesStoreImpl

    @Binds
    val AllExercisesInteractorImpl.bindInteractor: AllExercisesInteractor

    @Binds
    val AllExercisesHandlerStoreImpl.bindHandlerStore: AllExercisesHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides exerciseRepository: ExerciseRepository,
            @Provides tagRepository: TagRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): AllExercisesGraph
    }
}
