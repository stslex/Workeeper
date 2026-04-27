// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.HomeInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface HomeModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: HomeInteractorImpl): HomeInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: HomeHandlerStoreImpl): HomeHandlerStore
}
