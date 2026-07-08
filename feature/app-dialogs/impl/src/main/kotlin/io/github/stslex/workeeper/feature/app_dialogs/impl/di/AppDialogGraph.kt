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
 * The single Metro dependency graph for feature/app-dialogs:impl (KMP C.1 wave 4) — the Metro
 * analogue of the deleted Hilt `AppDialogViewModelModule` + the `ViewModelComponent` tier. Scoped to
 * [AppDialogsScope].
 *
 * PLAIN Store (`AppDialogStoreImpl` is `@Inject`, not assisted — `AppFeature` has no route arg): the
 * graph exposes the Store directly as [appDialogStore]. The 5 app-scoped deps are Hilt-owned
 * `@Singleton`s handed in as `@Provides` bound instances; the one `@Binds` (AppDialogHandlerStore)
 * migrates from the deleted `AppDialogViewModelModule`.
 *
 * NO Context / NO dispatcher in this graph — the only Context is `@ApplicationContext` on the
 * Hilt-constructed `@Singleton` [AppDialogRepository], resolved entirely on the Hilt side. The
 * SingletonComponent `AppDialogPublisher` / `AppDialogObserver` API bindings stay in Hilt (untouched).
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
