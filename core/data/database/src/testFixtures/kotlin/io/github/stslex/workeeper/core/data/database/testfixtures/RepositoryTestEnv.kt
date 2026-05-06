// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.testfixtures

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
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
 */
@TestOnly
class RepositoryTestEnv {

    private val database: AppDatabase = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    val transition: DbTransitionRunner = object : DbTransitionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = database.withTransaction(block)
    }

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
