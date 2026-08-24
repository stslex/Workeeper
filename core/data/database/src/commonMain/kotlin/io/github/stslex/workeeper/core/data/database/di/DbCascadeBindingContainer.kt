// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.di

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Metro container for the DB-cascade bindings: the 9 Room DAOs and [DbTransitionRunner],
 * all derived from the [AppDatabase] `create()` root.
 */
@BindingContainer
@ContributesTo(AppScope::class)
public object DbCascadeBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    public fun provideDbTransitionRunner(
        db: AppDatabase,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ): DbTransitionRunner = object : DbTransitionRunner {

        override suspend fun <T> invoke(
            block: suspend CoroutineScope.() -> T,
        ): T = withContext(ioDispatcher) {
            // GUARD: `coroutineScope` nests INSIDE `immediateTransaction` so `async {}` children
            // join the same transaction; hoisting it out silently breaks child-write rollback.
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    coroutineScope {
                        block()
                    }
                }
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    public fun provideTrainingDao(db: AppDatabase): TrainingDao = db.trainingDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideTrainingExerciseDao(db: AppDatabase): TrainingExerciseDao = db.trainingExerciseDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao

    @Provides
    @SingleIn(AppScope::class)
    public fun providePerformedExerciseDao(db: AppDatabase): PerformedExerciseDao = db.performedExerciseDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideSetDao(db: AppDatabase): SetDao = db.setDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideTagDao(db: AppDatabase): TagDao = db.tagDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideExerciseTagDao(db: AppDatabase): ExerciseTagDao = db.exerciseTagDao

    @Provides
    @SingleIn(AppScope::class)
    public fun provideTrainingTagDao(db: AppDatabase): TrainingTagDao = db.trainingTagDao
}
