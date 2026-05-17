// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import javax.inject.Singleton

/**
 * Application-graph bindings for app-dialogs. Producer-side [AppDialogPublisher]
 * binds to the singleton [AppDialogRepository]; producers and consumers share
 * the DataStore writer — no second instance, no in-memory fork.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppDialogsModule {

    @Binds
    @Singleton
    abstract fun bindAppDialogPublisher(impl: AppDialogRepository): AppDialogPublisher
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
