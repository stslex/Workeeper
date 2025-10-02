package io.github.stslex.workeeper.feature.charts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractor
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface ChartsModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: ChartsInteractorImpl): ChartsInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ChartsHandlerStoreImpl): ChartsHandlerStore
}
