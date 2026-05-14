// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface ArchiveModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: ArchiveInteractorImpl): ArchiveInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ArchiveHandlerStoreImpl): ArchiveHandlerStore
}
