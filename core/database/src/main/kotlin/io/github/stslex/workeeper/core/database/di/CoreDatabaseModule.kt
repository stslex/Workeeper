package io.github.stslex.workeeper.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.AppDatabase
import io.github.stslex.workeeper.core.database.common.DbTransition
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDatabaseModule {

    @Provides
    @Singleton
    internal fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room
        .databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.NAME,
        )
        .build()

    @Provides
    @Singleton
    internal fun provideTransition(
        db: AppDatabase,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ): DbTransition = object : DbTransition {
        override suspend fun <T> invoke(block: suspend () -> T): T = withContext(ioDispatcher) {
            db.withTransaction(block)
        }
    }

    @Provides
    @Singleton
    internal fun provideTrainingDao(db: AppDatabase): TrainingDao = db.trainingDao

    @Provides
    @Singleton
    internal fun provideTrainingExerciseDao(db: AppDatabase): TrainingExerciseDao =
        db.trainingExerciseDao

    @Provides
    @Singleton
    internal fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao

    @Provides
    @Singleton
    internal fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao

    @Provides
    @Singleton
    internal fun providePerformedExerciseDao(
        db: AppDatabase,
    ): PerformedExerciseDao = db.performedExerciseDao

    @Provides
    @Singleton
    internal fun provideSetDao(db: AppDatabase): SetDao = db.setDao

    @Provides
    @Singleton
    internal fun provideTagDao(db: AppDatabase): TagDao = db.tagDao

    @Provides
    @Singleton
    internal fun provideExerciseTagDao(db: AppDatabase): ExerciseTagDao = db.exerciseTagDao

    @Provides
    @Singleton
    internal fun provideTrainingTagDao(db: AppDatabase): TrainingTagDao = db.trainingTagDao
}
