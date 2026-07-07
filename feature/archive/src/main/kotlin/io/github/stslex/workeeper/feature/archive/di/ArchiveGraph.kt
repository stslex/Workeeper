// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractorImpl
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/archive (KMP C.1 M0) — the Metro analogue
 * of the deleted Hilt `ArchiveModule` plus the `ViewModelComponent` tier. Scoped to
 * [ArchiveScope] (=== Hilt `@ViewModelScoped`).
 *
 * The 8 app-scoped deps are Hilt-OWNED `@Singleton`s handed in as `@Provides` bound
 * instances (the Dagger `@BindsInstance` equivalent) via [Factory] — the graph ADOPTS them,
 * it does NOT construct them. There is deliberately no parent `AppScope` graph and no
 * `@GraphExtension`: nothing app-scoped is Metro-constructed, so there is nothing for a
 * parent graph to own. The two `@Binds` (Interactor, HandlerStore) migrate here from the
 * deleted `ArchiveModule`. [archiveStore] is the single root the flip point pulls.
 */
@DependencyGraph(scope = ArchiveScope::class)
internal interface ArchiveGraph {

    /** Root accessor: the retained Store. Metro constructs [ArchiveStoreImpl], wiring its deps. */
    val archiveStore: ArchiveStoreImpl

    // --- @Binds migrated from the deleted ArchiveModule (abstract property, impl → interface) ---
    @Binds
    val ArchiveInteractorImpl.bindInteractor: ArchiveInteractor

    @Binds
    val ArchiveHandlerStoreImpl.bindHandlerStore: ArchiveHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides navigator: Navigator,
            @Provides exerciseRepository: ExerciseRepository,
            @Provides trainingRepository: TrainingRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            // QUALIFIED across the bridge: @DefaultDispatcher (javax.inject.Qualifier) is read by
            // Metro via `metro { interop { includeJavax() } }`, so the binding key is
            // (CoroutineDispatcher + @DefaultDispatcher). A feature bridging a second dispatcher
            // (e.g. @IODispatcher) therefore gets a DISTINCT key — no collision, no strip.
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): ArchiveGraph
    }
}
