// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface AllExercisesModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: AllExercisesInteractorImpl): AllExercisesInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: AllExercisesHandlerStoreImpl): AllExercisesHandlerStore
}
