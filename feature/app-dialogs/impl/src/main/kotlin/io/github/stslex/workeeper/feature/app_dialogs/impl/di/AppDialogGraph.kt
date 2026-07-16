// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * The single Metro dependency graph for feature/app-dialogs:impl. Scoped to [AppDialogsScope].
 *
 * PLAIN Store (`AppDialogStoreImpl` is `@Inject`, not assisted — `AppFeature` has no route arg): the
 * graph exposes the Store directly as [appDialogStore]. The 5 app-scoped deps are `@SingleIn(AppScope)`
 * bindings owned by the app graph, handed in as `@Provides` bound instances; the one `@Binds`
 * (AppDialogHandlerStore) is declared on this graph.
 *
 * NO Context / NO dispatcher in this graph — the only Context is on the app-graph-owned
 * `@SingleIn(AppScope)` [AppDialogRepository], resolved entirely on the app-graph side.
 */
@DependencyGraph(scope = AppDialogsScope::class)
internal interface AppDialogGraph {

    /** Root accessor: the retained Store (plain, non-assisted). Mounted root/Activity-scoped via AppFeature. */
    val appDialogStore: AppDialogStoreImpl

    @Binds
    val AppDialogHandlerStoreImpl.bindHandlerStore: AppDialogHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides appDialogRepository: AppDialogRepository,
            @Provides appDialogObserver: AppDialogObserverImpl,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
        ): AppDialogGraph
    }
}
