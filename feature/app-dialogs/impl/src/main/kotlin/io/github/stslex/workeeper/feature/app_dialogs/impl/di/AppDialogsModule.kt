// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import javax.inject.Singleton

/**
 * Application-graph bindings for app-dialogs. Producer-side [AppDialogPublisher]
 * binds to the singleton [AppDialogRepository]; consumer-side
 * [AppDialogObserver] binds to [AppDialogObserverImpl] over the same
 * repository. Producer + consumer share the persistence layer — no second
 * instance, no in-memory fork.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppDialogsModule {

    @Binds
    @Singleton
    abstract fun bindAppDialogPublisher(impl: AppDialogRepository): AppDialogPublisher

    @Binds
    @Singleton
    abstract fun bindAppDialogObserver(impl: AppDialogObserverImpl): AppDialogObserver
}

/**
 * ViewModel-graph bindings for the new layered MVI presentation layer. The
 * Activity-scoped `@HiltViewModel AppDialogStoreImpl` injects handlers and
 * a `HandlerStore` from this graph, so they live as long as the Store does.
 */
@Module
@InstallIn(ViewModelComponent::class)
internal interface AppDialogViewModelModule {

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: AppDialogHandlerStoreImpl): AppDialogHandlerStore
}
