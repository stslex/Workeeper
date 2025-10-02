package io.github.stslex.workeeper.core.exercise.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.labels.LabelRepository
import io.github.stslex.workeeper.core.exercise.labels.LabelRepositoryImpl
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreExerciseModule {

    @Binds
    @Singleton
    fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    fun bindLabelRepository(impl: LabelRepositoryImpl): LabelRepository

    @Binds
    @Singleton
    fun bindTrainingRepository(impl: TrainingRepositoryImpl): TrainingRepository
}
