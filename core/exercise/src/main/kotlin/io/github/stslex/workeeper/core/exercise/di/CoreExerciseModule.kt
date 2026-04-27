package io.github.stslex.workeeper.core.exercise.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.exercise.session.PerformedExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SessionRepositoryImpl
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepositoryImpl
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.TagRepositoryImpl
import io.github.stslex.workeeper.core.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingExerciseRepositoryImpl
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
    fun bindTrainingRepository(impl: TrainingRepositoryImpl): TrainingRepository

    @Binds
    @Singleton
    fun bindTrainingExerciseRepository(
        impl: TrainingExerciseRepositoryImpl,
    ): TrainingExerciseRepository

    @Binds
    @Singleton
    fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    fun bindPerformedExerciseRepository(
        impl: PerformedExerciseRepositoryImpl,
    ): PerformedExerciseRepository

    @Binds
    @Singleton
    fun bindSetRepository(impl: SetRepositoryImpl): SetRepository
}
