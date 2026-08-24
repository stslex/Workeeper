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
 * Reusable in-memory Room test environment for repository unit tests: real `AppDatabase`, real
 * DAOs, real `DbTransitionRunner`. Robolectric-required; see documentation/testing.md.
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
        // Room 3 equivalent of `withTransaction {}`; `coroutineScope` nests inside so `async {}`
        // children reuse the transaction's connection instead of contending with it.
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
