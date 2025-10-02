package io.github.stslex.workeeper.feature.all_trainings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface AllTrainingsModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: AllTrainingsInteractorImpl): AllTrainingsInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: TrainingHandlerStoreImpl): TrainingHandlerStore

    @Binds
    @ViewModelScoped
    fun bindHandlerStoreEmitter(impl: TrainingHandlerStoreImpl): HandlerStoreEmitter
}
