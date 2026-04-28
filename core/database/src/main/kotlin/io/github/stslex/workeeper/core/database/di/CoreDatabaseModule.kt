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
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDatabaseModule {

    @Suppress("MagicNumber")
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.Companion.NAME,
    )
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)
        .build()

    @Provides
    @Singleton
    fun provideTrainingDao(db: AppDatabase): TrainingDao = db.trainingDao

    @Provides
    @Singleton
    fun provideTrainingExerciseDao(db: AppDatabase): TrainingExerciseDao = db.trainingExerciseDao

    @Provides
    @Singleton
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao

    @Provides
    @Singleton
    fun providePerformedExerciseDao(db: AppDatabase): PerformedExerciseDao = db.performedExerciseDao

    @Provides
    @Singleton
    fun provideSetDao(db: AppDatabase): SetDao = db.setDao

    @Provides
    @Singleton
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao

    @Provides
    @Singleton
    fun provideExerciseTagDao(db: AppDatabase): ExerciseTagDao = db.exerciseTagDao

    @Provides
    @Singleton
    fun provideTrainingTagDao(db: AppDatabase): TrainingTagDao = db.trainingTagDao
}
