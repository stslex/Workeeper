// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface PastSessionModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: PastSessionInteractorImpl): PastSessionInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: PastSessionHandlerStoreImpl): PastSessionHandlerStore
}
