// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.publisher.AppDialogPublisherImpl
import javax.inject.Singleton

/**
 * Application-graph bindings for app-dialogs. Producer-side [AppDialogPublisher]
 * binds to the thin [AppDialogPublisherImpl] facade (which delegates to the
 * singleton `AppDialogRepository`); consumer-side [AppDialogObserver] binds
 * to [AppDialogObserverImpl] over the same repository. Producer + consumer
 * share the persistence layer — no second instance, no in-memory fork.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppDialogsModule {

    @Binds
    @Singleton
    abstract fun bindAppDialogPublisher(impl: AppDialogPublisherImpl): AppDialogPublisher

    @Binds
    @Singleton
    abstract fun bindAppDialogObserver(impl: AppDialogObserverImpl): AppDialogObserver
}
// AppDialogViewModelModule removed: the ViewModel tier flipped to Metro (KMP C.1 wave 4). Its
// @Binds AppDialogHandlerStore migrated to AppDialogGraph. The SingletonComponent api bindings
// above (AppDialogPublisher / AppDialogObserver) stay in Hilt — the api contract is unchanged.
