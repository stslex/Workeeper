// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import javax.inject.Singleton

/**
 * Binds the producer-side [AppDialogPublisher] to the same singleton
 * [AppDialogRepository] instance that `AppDialogHost` reads from. Producer
 * and consumer share the DataStore writer — no second instance, no in-memory
 * fork.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppDialogsModule {

    @Binds
    @Singleton
    abstract fun bindAppDialogPublisher(impl: AppDialogRepository): AppDialogPublisher
}
