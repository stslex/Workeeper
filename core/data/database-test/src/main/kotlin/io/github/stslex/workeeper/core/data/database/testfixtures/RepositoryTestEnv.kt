// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.testfixtures

import android.app.Application
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.TestOnly

/**
 * Reusable in-memory Room test environment for repository unit tests.
 *
 * The fixture wires the same `AppDatabase` Room sees in production, every real DAO, and a
 * real `DbTransitionRunner` backed by `withTransaction`. Repository tests construct their
 * `*RepositoryImpl` against these wired DAOs, exercise the public API, and read state back
 * through the same DAO surface to assert real persistence.
 *
 * Lifecycle: instantiate in `@BeforeEach`, call [close] in `@AfterEach`. Robolectric is
 * required (the in-memory builder needs an Android `Context`); add the
 * `RobolectricExtension` to your test class along with
 * `@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])`.
 *
 * Stays on [AndroidSQLiteDriver] after the production flip to `BundledSQLiteDriver`: the
 * bundled android variant carries Android-ABI natives only, and loading it under Robolectric
 * on a desktop JVM fails with `UnsatisfiedLinkError` (measured). Driver behaviour is a
 * device-suite concern; this fixture's oracle value is repository logic over a real schema.
 */
@TestOnly
class RepositoryTestEnv {

    private val database: AppDatabase = Room
        .inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext<Application>(),
        )
        .setDriver(AndroidSQLiteDriver())
        .allowMainThreadQueries()
        .build()

    val transition: DbTransitionRunner = object : DbTransitionRunner {
        // Room 3: `withTransaction {}` → `useWriterConnection { it.immediateTransaction {} }`
        // (the migration guide's documented equivalent). `coroutineScope` is nested INSIDE so
        // the receiver passed to `block` inherits the transaction context; any `async {}` children
        // launched inside `block` (e.g. `TrainingRepositoryImpl.getTraining`) reuse the transaction's
        // connection instead of contending with it on the single-connection in-memory SQLite that
        // Robolectric provides. This is the same primitive as production (DbCascadeBindingContainer).
        override suspend fun <T> invoke(
            block: suspend CoroutineScope.() -> T,
        ): T = database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                coroutineScope {
                    block()
                }
            }
        }
    }

    /** The raw [AppDatabase] — for tests that exercise the Room 3 connection API directly. */
    fun rawDatabase(): AppDatabase = database

    val sessionDao get() = database.sessionDao
    val performedExerciseDao get() = database.performedExerciseDao
    val setDao get() = database.setDao
    val trainingDao get() = database.trainingDao
    val trainingExerciseDao get() = database.trainingExerciseDao
    val exerciseDao get() = database.exerciseDao
    val tagDao get() = database.tagDao
    val exerciseTagDao get() = database.exerciseTagDao
    val trainingTagDao get() = database.trainingTagDao

    fun close() {
        database.close()
    }

    class TestApplication : Application()
}
