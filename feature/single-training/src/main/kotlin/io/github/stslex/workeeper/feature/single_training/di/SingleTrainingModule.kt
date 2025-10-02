package io.github.stslex.workeeper.feature.single_training.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.interactor.SingleTrainingInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface SingleTrainingModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: SingleTrainingInteractorImpl): SingleTrainingInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: TrainingHandlerStoreImpl): TrainingHandlerStore
}
