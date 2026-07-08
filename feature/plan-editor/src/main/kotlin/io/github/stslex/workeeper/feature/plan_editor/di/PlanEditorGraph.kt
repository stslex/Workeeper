// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractorImpl
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/plan-editor (KMP C.1 wave 3). Scoped to
 * [PlanEditorScope]. ASSISTED Store (`Screen.PlanEditor` route arg): the graph exposes the assisted
 * [PlanEditorStoreImpl.Factory] — never the Store. 8 app-scoped `@Provides` bound instances; two
 * `@Binds` migrate from the deleted module. `@DefaultDispatcher` stays QUALIFIED (`includeJavax`).
 * No Context.
 */
@DependencyGraph(scope = PlanEditorScope::class)
internal interface PlanEditorGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: PlanEditorStoreImpl.Factory

    @Binds
    val PlanEditorInteractorImpl.bindInteractor: PlanEditorInteractor

    @Binds
    val PlanEditorHandlerStoreImpl.bindHandlerStore: PlanEditorHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides exerciseRepository: ExerciseRepository,
            @Provides trainingExerciseRepository: TrainingExerciseRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): PlanEditorGraph
    }
}
