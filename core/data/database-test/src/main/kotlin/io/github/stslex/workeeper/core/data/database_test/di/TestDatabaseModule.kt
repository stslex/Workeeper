// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database_test.di

import android.content.Context
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.di.CoreDatabaseModule
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * Test-only replacement for `CoreDatabaseModule`.
 *
 * Keep the @Provides surface here in lock-step with the production module
 * (`core/data/database/.../di/CoreDatabaseModule.kt`). Adding a DAO provider in
 * production without mirroring it here surfaces at injection time as a Hilt error
 * along the lines of "Cannot provide ... ExerciseTagDao".
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoreDatabaseModule::class],
)
object TestDatabaseModule {

    @Provides
    @Singleton
    internal fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = InMemoryDatabaseProvider.create(context)

    @Provides
    @Singleton
    internal fun provideTransition(
        db: AppDatabase,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ): DbTransitionRunner = object : DbTransitionRunner {

        override suspend fun <T> invoke(
            block: suspend CoroutineScope.() -> T,
        ): T = withContext(ioDispatcher) {
            db.withTransaction {
                coroutineScope { block() }
            }
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
