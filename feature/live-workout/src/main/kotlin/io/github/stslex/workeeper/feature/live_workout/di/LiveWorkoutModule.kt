// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface LiveWorkoutModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: LiveWorkoutInteractorImpl): LiveWorkoutInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: LiveWorkoutHandlerStoreImpl): LiveWorkoutHandlerStore
}
