package io.github.stslex.workeeper.feature.exercise.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface ExerciseModule {

    @Binds
    @ViewModelScoped
    fun bindExerciseInteractor(impl: ExerciseInteractorImpl): ExerciseInteractor

    @Binds
    @ViewModelScoped
    fun bindExerciseHandlerStore(impl: ExerciseHandlerStoreImpl): ExerciseHandlerStore
}
