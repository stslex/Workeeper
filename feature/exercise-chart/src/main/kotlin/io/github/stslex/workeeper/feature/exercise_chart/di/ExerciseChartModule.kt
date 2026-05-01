// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface ExerciseChartModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: ExerciseChartInteractorImpl): ExerciseChartInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ExerciseChartHandlerStoreImpl): ExerciseChartHandlerStore
}
