package io.github.stslex.workeeper.feature.all_exercises.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter

@Module
@InstallIn(ViewModelComponent::class)
interface AllExercisesModule {

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ExerciseHandlerStoreImpl): ExerciseHandlerStore

    @Binds
    @ViewModelScoped
    fun bindHandlerStoreEmitter(impl: ExerciseHandlerStoreImpl): HandlerStoreEmitter
}
