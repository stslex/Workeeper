package io.github.stslex.workeeper.feature.all_exercises.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal interface AllExercisesModule {

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ExerciseHandlerStoreImpl): ExerciseHandlerStore
}
