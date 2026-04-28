// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal interface ArchiveModule {

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ArchiveHandlerStoreImpl): ArchiveHandlerStore
}
