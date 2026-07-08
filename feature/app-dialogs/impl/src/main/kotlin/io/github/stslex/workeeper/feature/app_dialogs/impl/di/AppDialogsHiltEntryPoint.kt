// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * Hilt→Metro bridge for feature/app-dialogs:impl (KMP C.1 wave 4). Pulls the 5 app-scoped
 * `@Singleton` dependencies out of the Hilt `SingletonComponent` for [AppDialogGraph] as `@Provides`
 * bound instances.
 *
 * NO Context, NO dispatcher in the graph: the only Context is `@ApplicationContext` on the
 * Hilt-constructed `@Singleton` [AppDialogRepository] (its secondary `@Inject` ctor) — it stays
 * entirely on the Hilt side and never enters the Metro graph. [AppDialogObserverImpl] is bridged as
 * the concrete `@Singleton` (ChooseHandler injects the concrete, not the `AppDialogObserver`
 * interface). The producer-/consumer-side `AppDialogPublisher` / `AppDialogObserver` API bindings
 * stay in Hilt's `SingletonComponent` (untouched) — the api contract is preserved.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppDialogsHiltEntryPoint {

    fun appDialogRepository(): AppDialogRepository

    fun appDialogObserverImpl(): AppDialogObserverImpl

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder
}
