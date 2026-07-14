// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.di

import androidx.room.withTransaction
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
import kotlinx.coroutines.withContext

/**
 * App-Scope Collapse Step 5 (5a). The DB-cascade "derived" bindings — the 9 Room DAOs +
 * [DbTransitionRunner] — Metro-owned, deriving from the [AppDatabase] **`create()` bound-instance root**
 * (§Test-override root / §D3a in the execution spec). `AppDatabase` itself stays a `create()` root (a
 * test-override I/O boundary the seam swaps in-memory); everything here is DERIVED from it, so it moves off
 * the `create()` param list and into this contributed container.
 *
 * `@BindingContainer @ContributesTo(AppScope)` aggregates into the app-scope `@DependencyGraph` (mirrors the
 * dispatcher / resource-wrapper containers). Each `@Provides` reads `AppDatabase` FROM the graph (the root) —
 * a forward dependency, NOT a back-edge through the `appGraph` accessor (no C2 `@IO`→appGraph cycle:
 * [provideDbTransitionRunner]'s `@IODispatcher` resolves to the direct `Dispatchers.IO` from
 * `DispatchersBindingContainer`, never routed through `appGraph`).
 *
 * The DAOs mirror `db.<dao>` exactly as the deleted Hilt `CoreDatabaseModule` did; `DbTransitionRunner`
 * carries the identical `db.withTransaction` + `@IODispatcher` construction (former `CoreDatabaseModule.kt:51`).
 * Container is `public` for cross-module aggregation (D1).
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
            db.withTransaction {
                block()
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
