package io.github.stslex.workeeper.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.database.AppDatabase
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.migrations.MIGRATION_1_2
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.Companion.NAME,
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideTrainingsDao(db: AppDatabase): TrainingDao = db.trainingDao

    @Provides
    @Singleton
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao

    @Provides
    @Singleton
    fun provideLabelsDao(db: AppDatabase): TrainingLabelDao = db.labelsDao
}
