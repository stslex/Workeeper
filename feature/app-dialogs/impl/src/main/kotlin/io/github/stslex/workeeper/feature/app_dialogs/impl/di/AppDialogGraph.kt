// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * Contributed [GraphExtension] of [AppDialogsScope], merged into the app graph and inheriting its
 * app-scoped bindings. Screen-less feature, so the creator takes no route arg.
 */
@GraphExtension(AppDialogsScope::class)
interface AppDialogGraph {

    /** Root accessor: the retained Store, mounted Activity-scoped via `AppFeature`. */
    val appDialogStore: AppDialogStoreImpl

    /** Observability roots for `AppDialogExtensionIdentityTest`; no production consumer. */
    val appDialogRepository: AppDialogRepository

    val appDialogObserverImpl: AppDialogObserverImpl

    @Binds
    val AppDialogHandlerStoreImpl.bindHandlerStore: AppDialogHandlerStore

    /** Creator name must be unique across all contributed extension factories. */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAppDialogGraph(): AppDialogGraph
    }
}
